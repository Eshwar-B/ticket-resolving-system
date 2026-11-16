# Reverse Engineering: Reliability & The Transactional Outbox Pattern

## 1. Problem Statement: The Dual-Write Hazard
In event-driven microservices, updating an entity in PostgreSQL and publishing a notification to Apache Kafka is a classic dual-write distributed transaction problem:
- If the DB commit succeeds but Kafka publish fails (e.g., broker network timeout), the event is lost forever.
- If Kafka publish succeeds but DB commit fails (e.g., constraint violation), consumers process a phantom event that does not exist in the database.
- 2-Phase Commit (XA) protocols are slow, fragile, and not supported by Kafka.

---

## 2. Platform Implementation: Atomic Outbox Table

To achieve 100% reliability with zero dual-writes:
1. **Single Database Transaction:** When a ticket is created or modified, the ticket entity and an outbox record are committed atomically in the same local database transaction:
   ```sql
   INSERT INTO tickets (id, tenant_id, title, status, ...) VALUES (...);
   INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status)
   VALUES (gen_random_uuid(), 'TICKET', :ticketId, 'ticket.created.v1', :jsonPayload, 'PENDING');
   COMMIT;
   ```
2. **Reliable Relay Poller (`OutboxRelayService`):**
   A scheduled background worker queries pending outbox records every 1000ms:
   ```sql
   SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED;
   ```
   - Uses `FOR UPDATE SKIP LOCKED` so multiple instances of the service can poll simultaneously without race conditions or contention locks.
   - Publishes each event to Kafka topic `ticket.created.v1` with the `tenant_id` as the partition key (guaranteeing strict per-tenant in-order delivery).
   - Upon confirmed broker acknowledgment (`Future.get()` or reactive completion), updates status to `PUBLISHED`.
3. **Dead-Letter Queue (DLQ) & Retry Policy:**
   - If Kafka is unreachable, the event remains `PENDING` with exponential backoff (`retry_count`).
   - After 5 consecutive failures, the event transitions to `FAILED` and an alert is emitted to the operations dashboard.

---

## 3. Idempotent Consumer Inboxes
All consuming services (`workflow-service`, `notification-service`, `usage-audit-service`, `ai-plane`) implement an **idempotent inbox table** or Redis set tracking processed `event_id`s. Any replayed or duplicate message from Kafka is acknowledged and discarded with zero duplicate side effects.
