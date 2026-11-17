# Chapter 9: Hybrid RAG Pipeline and Knowledge Management

---

## 9.1 Why Naive RAG Fails in Enterprise Support

Many proof-of-concept AI systems rely on **Naive RAG** (Retrieval-Augmented Generation), where documents are chunked, embedded into vectors, and queried solely via cosine similarity in a vector database.

In real-world enterprise environments, pure vector search produces severe failures:
1. **Keyword Blindness:** Vector embeddings compress broad semantic concepts into high-dimensional space. However, they frequently fail on exact alphanumeric identifiers, such as error code `ERR_504_GATEWAY_TIMEOUT`, serial number `SN-88192-X`, or specific product SKU `PRO-4K-V2`.
2. **False Semantic Drifts:** A query like *"Can I get a refund after 30 days?"* might match a policy chunk titled *"30-day warranty repairs"* simply because the concepts of time, goods, and transactions are semantically close, leading to incorrect policy quotations.
3. **Multi-Tenant Leakage Risks:** Without hardware-level and query-level tenant filters, vector queries can accidentally retrieve neighboring companies' confidential proprietary runbooks.

To overcome these challenges, the platform implements an enterprise-grade **Hybrid RAG Pipeline** combining **Dense Semantic Vector Search (Qdrant)**, **Sparse Full-Text Keyword Search (PostgreSQL `tsvector`)**, and **Reciprocal Rank Fusion (RRF)**.

```
                          USER QUERY / TICKET DESCRIPTION
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 │                                               │
                 ▼                                               ▼
      [Dense Semantic Branch]                         [Sparse Keyword Branch]
  Generate 768-dim Embedding                      Generate tsquery from keywords
     (nomic-embed-text)                            to_tsquery('english', ...)
                 │                                               │
                 ▼                                               ▼
       [Qdrant Vector DB]                             [PostgreSQL knowledge_db]
  HNSW Cosine Similarity Search                   Full-Text Inverted Index Search
  Filter: tenant_id = current                     Filter: tenant_id = current
                 │                                               │
                 ▼                                               ▼
         Dense Result Set                                Sparse Result Set
     (Ranked by Cosine Score)                        (Ranked by ts_rank Score)
                 │                                               │
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                        [RECIPROCAL RANK FUSION (RRF)]
                            k = 60 constant offset
                      Normalizes & Combines Rankings
                                         │
                                         ▼
                           Top-K Re-ranked Documents
                         (With Chunk IDs & Metadata)
                                         │
                                         ▼
                        [LLM PROMPT GROUNDING CONTEXT]
```

### The Metaphor: The Library Research Team
Imagine researching a specific legal case in a world-class law library:
- **Dense Vector Search (The Concept Researcher):** Reads through entire legal philosophies and jurisprudence. Understands when two cases discuss the same broad ethical principle even if they use completely different wording.
- **Sparse Keyword Search (The Index Clerk):** Flips through the physical back-of-the-book alphabetical index looking for the exact statute number: *"Section 14-B, Paragraph 2"*.
- **Reciprocal Rank Fusion (The Senior Partner):** Takes both lists, compares which precedents appear near the top of both analyses, discards the noise, and hands the litigator the most authoritative 3 case files.

---

## 9.2 The Document Ingestion Pipeline

When an enterprise administrator uploads a knowledge base document (PDF, Markdown, HTML, or DOCX), it traverses an asynchronous, event-driven ingestion pipeline:

```
[Admin Uploads PDF]
       │
       ▼
[Knowledge Service: Port 8082] ──► Stores raw file in MinIO (tenants/{tid}/kb/{doc_id}.pdf)
       │
       ▼ Emits Event: kb.document.uploaded
[Kafka Broker: Port 9092]
       │
       ▼ Consumed by Ingestion Worker (Python / FastAPI)
[Document Extraction & Normalization]
- Strips headers, footers, page numbers
- Extracts clean markdown text and tables
       │
       ▼
[Recursive Character Text Splitting]
- Chunk Size: 512 tokens (~1,800 characters)
- Chunk Overlap: 64 tokens (~220 characters)
- Split Delimiters: ["\n\n", "\n", ". ", " "]
       │
       ▼
[Dual Indexing Execution]
       ├─────────────────────────────────────────┐
       ▼                                         ▼
[Generate Embeddings]                  [Store Relational & TSVECTOR]
- nomic-embed-text (768 dim)           - INSERT INTO knowledge_chunks
- POST /collections/kb_chunks/points   - Set tsv = to_tsvector('english', chunk_text)
  (Payload: tenant_id, doc_id, text)   - Commit DB Transaction
```

---

## 9.3 The Reciprocal Rank Fusion (RRF) Algorithm

Dense vector search returns a cosine similarity score bounded between $-1.0$ and $+1.0$. PostgreSQL Full-Text Search returns a `ts_rank` score that is an unbounded positive float (e.g., $0.05$ to $4.8$).

Because these scores follow fundamentally different distributions and scales, simple linear combination ($Score_{final} = 0.5 \cdot Cosine + 0.5 \cdot BM25$) produces volatile, biased results.

The platform utilizes **Reciprocal Rank Fusion (RRF)**, an algorithm that evaluates relative rank order rather than raw score magnitudes:

$$RRF\_Score(d \in D) = \sum_{m \in M} \frac{1}{k + r_m(d)}$$

Where:
- $D$ is the set of candidate document chunks.
- $M$ is the set of retrieval methods (in our system, $M = \{\text{Qdrant Dense}, \text{PostgreSQL Sparse}\}$).
- $r_m(d)$ is the rank position of document $d$ in the result list from method $m$ (1-indexed: $1, 2, 3, \dots$).
- $k$ is a smoothing constant, set to $60$ (the empirical standard established in information retrieval literature to prevent high-ranking outliers from dominating the sum).

### Python Implementation of RRF:
```python
def reciprocal_rank_fusion(
    vector_results: List[Dict[str, Any]], 
    keyword_results: List[Dict[str, Any]], 
    k: int = 60, 
    top_n: int = 5
) -> List[Dict[str, Any]]:
    rrf_scores = {}
    doc_lookup = {}

    # 1. Process Vector Ranks
    for rank, doc in enumerate(vector_results, start=1):
        doc_id = doc["chunk_id"]
        doc_lookup[doc_id] = doc
        rrf_scores[doc_id] = rrf_scores.get(doc_id, 0.0) + (1.0 / (k + rank))

    # 2. Process Keyword Full-Text Ranks
    for rank, doc in enumerate(keyword_results, start=1):
        doc_id = doc["chunk_id"]
        if doc_id not in doc_lookup:
            doc_lookup[doc_id] = doc
        rrf_scores[doc_id] = rrf_scores.get(doc_id, 0.0) + (1.0 / (k + rank))

    # 3. Sort by aggregated RRF score descending
    sorted_doc_ids = sorted(rrf_scores.keys(), key=lambda x: rrf_scores[x], reverse=True)

    # 4. Return top-N documents
    final_docs = []
    for doc_id in sorted_doc_ids[:top_n]:
        item = doc_lookup[doc_id]
        item["rrf_score"] = rrf_scores[doc_id]
        final_docs.append(item)

    return final_docs
```

---

## 9.4 Hallucination Mitigation and Source Citations

To be usable in enterprise legal and customer-facing workflows, AI outputs must be **100% grounded** in approved documentation. The platform enforces grounding through three distinct mechanisms:

### 1. Mandatory Structured Citations
The resolution prompt requires the model to annotate every factual assertion with an exact document reference:
```
You are an enterprise support intelligence system. You may ONLY answer questions
using the verified knowledge chunks provided inside <<<CONTEXT>>>.
For EVERY claim or policy statement you make, you MUST cite the chunk ID in brackets.
Example: "Returns are accepted within 30 days of delivery [Doc: Return_Policy, Chunk: 4]."
If the provided <<<CONTEXT>>> does not contain sufficient facts to answer, you must state:
"I am unable to answer based on available documentation. Escalating to human agent."
```

### 2. The Critic Reflection Verification Loop
Before any drafted response is dispatched to a customer or committed to a ticket, the LangGraph **Critic Node** parses all bracketed citations `[Doc: ..., Chunk: ...]`. It verifies that:
1. Every cited chunk actually exists in the retrieved context set.
2. The assertion made in the draft logically matches the cited text.
3. If an assertion has no citation or quotes hallucinated rules, the Critic returns a low score ($< 0.80$), triggering an automated correction cycle.

---

## 9.5 Semantic Caching with Redis

In enterprise customer support, up to **40% of inbound inquiries** are near-duplicates (e.g. *"What is your refund window?"*, *"How many days do I have to return an item?"*, *"Can I return after 2 weeks?"*).

Re-running vector embeddings and full LLM synthesis for identical inquiries wastes compute resources and increases latency. The platform implements **Semantic Caching** in Redis:

```
[Incoming Ticket Query]
          │
          ▼
[Generate MD5 / SHA-256 Hash of Normalized Query]
          │
          ▼
[Check Redis Key: t:{tenant_id}:kb_cache:{hash}]
          │
    ┌─────┴─────────────────┐
  Cache HIT               Cache MISS
    │                       │
    ▼                       ▼
Return Cached Response   Execute Full Hybrid RAG
Latency: < 5 ms          Latency: 1,200 ms
Cost: $0.00              Store Result in Redis (TTL: 15 mins)
```
