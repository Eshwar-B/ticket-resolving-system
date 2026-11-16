# Enterprise AI Platform — STRIDE Threat Model

> **Status:** Authoritative Security Specification  
> **Standard:** Microsoft STRIDE / OWASP Top 10 / OWASP LLM Top 10  
> **Cost Model:** ₹0 Local-First Architecture  

---

## 1. System Trust Boundaries

```
[ External User / Attacker ]
          │ (Untrusted Network Boundary)
          ▼
┌────────────────────────────────────────┐
│  Boundary 1: Spring Cloud Gateway      │  <-- JWT Validation, Rate Limiting
└──────────────────┬─────────────────────┘
                   │ (Authenticated Internal Network)
      ┌────────────┴────────────┐
      ▼                         ▼
┌───────────────┐         ┌────────────────────────┐
│ Boundary 2:   │         │ Boundary 3:            │
│ Java Control  │◄───────►│ Python AI Plane        │  <-- Input/Output Guardrails,
│ Plane Services│         │ (LangGraph Supervisor) │      Tool Allow-listing
└───────┬───────┘         └───────────┬────────────┘
        │                             │
  (Tenant RLS)                  (Tenant Filter)
        ▼                             ▼
┌───────────────┐         ┌────────────────────────┐
│ Boundary 4:   │         │ Boundary 5:            │
│ PostgreSQL 16 │         │ Qdrant Vector DB       │  <-- Mandatory Tenant Filter
│ (Logical DBs) │         │ & S3Mock (MinIO)       │      `tenants/{id}/...` Prefix
└───────────────┘         └────────────────────────┘
        │                             │
        └──────────────┬──────────────┘
                       ▼
              ┌─────────────────┐
              │ Boundary 6:     │
              │ Apache Kafka    │  <-- Tenant Partition Keys,
              │ (KRaft Cluster) │      Dead Letter Queues (DLQ)
              └─────────────────┘
```

---

## 2. STRIDE Threat Analysis Matrix

| Threat Category | Target Boundary | Potential Attack Vector | Platform Countermeasure & Defense | Verification Status |
|---|---|---|---|---|
| **Spoofing** | Gateway / Keycloak | Forged or expired JWT token; client-supplied `X-Tenant-Id` header | Asymmetric RS256 signature verification; tenant identity derived **strictly server-side** from JWT claims. | **VERIFIED** (Phase 2 Test) |
| **Tampering** | PostgreSQL Databases | SQL injection; cross-tenant row updates | Parameterized Hibernate queries; PostgreSQL kernel-enforced Row-Level Security (`SET LOCAL app.tenant_id = ?`). Non-superuser `app_user`. | **VERIFIED** (Phase 2 & 3 Tests) |
| **Tampering** | Webhook Deliveries | Man-in-the-middle webhook body alteration; replay attacks | Webhook requests signed with HMAC-SHA256 (`X-Hub-Signature-256`); timestamp replay window (< 5 minutes). | **VERIFIED** (Phase 8 Test) |
| **Repudiation** | Mutating Transactions | Disputed approvals or status changes | Immutable, append-only `audit_events` table in `usage_db`; correlation IDs and W3C `traceparent` logging. | **VERIFIED** (Phase 8 Test) |
| **Information Disclosure** | RAG Vector Search | Cross-tenant document embedding retrieval; prompt leakage | Qdrant mandatory payload filter `{must: [{key: "tenant_id", match: {value: tenantId}}]}`; MinIO tenant prefix isolation; PII redaction guardrail. | **VERIFIED** (Phase 5 & 9 Tests) |
| **Denial of Service** | API Endpoints & LLM | Resource exhaustion via burst traffic or infinite supervisor loops | Sliding window rate limiting in Redis (`RateLimitService`); supervisor recursion limit (max 5 turns); container RAM limits. | **VERIFIED** (Phase 8 & 9 Tests) |
| **Elevation of Privilege** | Multi-Agent Loop | Direct prompt injection instructing AI to execute refunds or bypass approvals | Two-tier architecture: Critic Agent rejects ungrounded actions; Workflow Service enforces mandatory Human-in-the-Loop for financial actions. | **VERIFIED** (Phase 6 & 9 Tests) |

---

## 3. Defense-in-Depth Invariants

1. **Kernel RLS:** Even if an application bug omits `WHERE tenant_id = ?`, PostgreSQL RLS policies unconditionally block unauthorized rows.
2. **Deterministic Tool Allow-listing:** Agents only invoke registered Python functions with Pydantic typed schemas. No dynamic code evaluation or raw SQL execution is permitted.
3. **Zero Secrets in Repository:** Pre-commit secret scanning and environment variable injection for credentials.
