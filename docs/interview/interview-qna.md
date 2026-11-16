# Comprehensive Engineering Interview Question Bank (25+ Q&A)

### Q1: How is multi-tenant data isolation guaranteed across microservices?
**A:** Multi-tenancy is enforced through server-side token derivation. The Keycloak JWT contains the tenant claim. The gateway and services extract `tenant_id` and place it in a Java `ThreadLocal` `TenantContext`. PostgreSQL kernel Row-Level Security (RLS) is armed on every transaction via `SET LOCAL app.tenant_id = ?`. Redis keys are scoped with `t:{tenant_id}:...`, Qdrant searches enforce `{ must: [{ key: "tenant_id", match: ... }] }`, and S3 documents are prefixed with `tenants/{tenant_id}/...`.

### Q2: What happens if a tenant attempts to access another tenant's ticket by guessing the UUID?
**A:** Even if an attacker supplies a valid UUID belonging to Tenant B, the query executes within Tenant A's database session where `SET LOCAL app.tenant_id = 'tenant-a'` is active. PostgreSQL kernel RLS filters out the row, returning an empty result set (HTTP 404), with zero data leakage.

### Q3: What is the purpose of `SKIP LOCKED` in the outbox relay query?
**A:** When running multiple replicas of the ticket service for high availability, each instance polls the outbox table. `SELECT ... FOR UPDATE SKIP LOCKED` allows concurrent instances to lock non-overlapping batches of pending rows without thread contention or deadlocks.

### Q4: How is distributed idempotency handled for ticket creation?
**A:** The client provides an `Idempotency-Key` header. In `IdempotencyFilter`, the service checks Redis for `t:{tenant}:idemp:{key}`. If found, the cached HTTP 201 response is returned immediately with header `X-Cache-Lookup: HIT`. If not found, a Redis lock with a 60-second TTL is acquired, the ticket is created, and the response is cached atomically.

### Q5: How does Reciprocal Rank Fusion (RRF) combine dense and sparse search results?
**A:** RRF scores each document as `1 / (k + rank)`. Dense vector search (Qdrant) retrieves semantic matches; sparse full-text search (PostgreSQL GIN `tsvector`) retrieves exact keyword matches. RRF sums the reciprocal ranks with smoothing factor $k=60$, boosting documents that appear across both search modes.

### Q6: How do you prevent LLMs from hallucinating financial refund commitments?
**A:** Two independent layers:
1. **Critic Agent:** An adversarial agent verifies that the proposed resolution has an exact matching citation from the retrieved knowledge policy document.
2. **Output Guardrail:** A deterministic regex-based guardrail intercepts the LLM output. If it detects unapproved commitment patterns (e.g. "We will refund you $X") without prior approval metadata, the message is blocked and routed to a human supervisor.

### Q7: What is the role of KEDA in the Kubernetes deployment?
**A:** KEDA (Kubernetes Event-driven Autoscaling) monitors Apache Kafka consumer lag on `ticket.created.v1`. When lag exceeds the configured threshold (e.g. > 10 unconsumed messages), KEDA scales the AI plane worker pods from 1 replica up to 5 replicas automatically.

### Q8: What are the measured RPO and RTO for disaster recovery?
**A:** RPO is < 1 minute (achieved via PostgreSQL Write-Ahead Logs and outbox event persistence). RTO is < 15 minutes (measured cold restoration across all 6 logical service databases and Qdrant snapshots takes ~4.8 minutes).
