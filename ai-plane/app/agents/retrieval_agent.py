"""
Retrieval Agent for AI Resolution Plane.
Queries Knowledge Service Hybrid RAG to find matching policies and extracted citations.
"""
from typing import Dict, Any, List
from app.tools.knowledge_tools import search_knowledge

def run_retrieval(tenant_id: str, query: str, top_k: int = 3) -> Dict[str, Any]:
    """Retrieves grounded policy documentation and citation strings for the given query."""
    try:
        search_res = search_knowledge(tenant_id=tenant_id, query=query, top_k=top_k)
        results = search_res.get("results", [])
        
        snippets = []
        citations = []
        for r in results:
            content = r.get("content", "")
            citation = r.get("citation", "")
            snippets.append(content)
            if citation:
                citations.append(citation)

        return {
            "retrieved_snippets": snippets,
            "citations": citations,
            "total_hits": len(results),
            "rag_context": "\n\n".join(snippets)
        }
    except Exception as e:
        return {
            "retrieved_snippets": [],
            "citations": [],
            "total_hits": 0,
            "rag_context": "",
            "error": str(e)
        }
