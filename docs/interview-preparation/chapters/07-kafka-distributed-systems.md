# Chapter 7: Apache Kafka and Distributed Event-Driven Architecture

---

## 7.1 Distributed Messaging with Apache Kafka KRaft

In enterprise systems, synchronous HTTP point-to-point communication creates fragile architectures where the failure or slowdown of one service instantly cascades into caller timeouts. To achieve fault-tolerant decoupling, high-throughput buffering, and verifiable asynchronous state propagation, the platform utilizes **Apache Kafka** operating in **KRaft (Kafka Raft Metadata) mode** on host port `9092`.

```
                  THE CORE DISTRIBUTED EVENT BUS
                   (Apache Kafka KRaft Mode)
                               │
   ┌───────────────────────────┼───────────────────────────┐
   ▼                           ▼                           ▼
ticket.events.v1         ai.execution.v1         notification.events.v1
Key: tenant_id           Key: tenant_id          Key: tenant_id
Partitions: 3            Partitions: 3           Partitions: 3
Replication: 1           Replication: 1          Replication: 1
```

### Why Kafka KRaft?
Traditional Kafka clusters required a separate **Apache ZooKeeper** cluster for cluster coordination, partition leader election, and metadata management. 
- In KRaft mode, the Raft consensus algorithm is embedded directly inside Kafka brokers.
- This eliminates the operational overhead of managing ZooKeeper containers, drastically accelerates controller failover times, and saves approximately **500 MB of host RAM**, a non-negotiable optimization under our **16 GB RAM hardware discipline**.

### The Metaphor: The Inter-Department Pneumatic Tube System
Imagine an enormous hospital:
- If a surgeon in the Operating Room had to stop an operation, walk to the blood bank, wait in line at the counter, fill out paperwork, and carry the blood back (Synchronous REST), surgeries would constantly fail from delays.
- Instead, the hospital uses a high-speed pneumatic tube system (Kafka). The surgeon puts a labeled tube with patient details into the wall canister and presses send (Publishes to topic).
- The blood bank receives the canister, processes the order, and dispatches blood to the ward. If the blood bank is temporarily busy, canisters queue up safely in the tube rack without dropping to the floor (Zero message loss).

---

## 7.2 Topic Catalog and Partitioning Strategy

Every message published to Kafka contains:
1. **Record Key:** Always set to `tenant_id` or `tenant_id:ticket_id`.
2. **Record Value:** Standard JSON or CloudEvents envelope containing event type, timestamp, correlation ID, and payload.
3. **Record Headers:** W3C distributed tracing context (`traceparent`), tenant ID, and event version.

### 7.2.1 Canonical Topic Catalog

| Topic Name | Producer Services | Consumer Services | Partition Key | Retention | Purpose |
|---|---|---|---|---|---|
| `ticket.events.v1` | `ticket-service` | `ai-orchestrator`, `workflow-service`, `notification-service` | `tenant_id` | 7 days | Emits ticket lifecycle transitions (`TicketCreated`, `TicketAssigned`, `TicketResolved`) |
| `ai.triage.v1` | `ticket-service` | `ai-orchestrator` | `tenant_id` | 3 days | Dispatches newly ingested tickets to the AI execution plane for agentic classification |
| `ai.execution.v1` | `ai-orchestrator` | `workflow-service`, `ticket-service` | `tenant_id` | 3 days | Emits AI agent reasoning results, extracted entities, proposed solutions, and confidence scores |
| `workflow.events.v1` | `workflow-service` | `notification-service`, `ticket-service` | `tenant_id` | 7 days | Tracks human approval state machine transitions, saga execution, and task completions |
| `notification.events.v1` | All Services | `notification-service` | `tenant_id` | 1 day | Dispatches customer email, SMS, and in-app alert requests |
| `ticket.events.DLQ` | `ticket-service` consumer | Operational Alerting / Dead Letter Processor | `tenant_id` | 30 days | Dead Letter Queue for unrecoverable poisonous ticket event payloads |

### 7.2.2 The Partition Key Rule: In-Order Guarantee
Kafka guarantees message ordering **only within a single partition**. If events for the same ticket were published across different partitions randomly, a `TicketResolved` event could be consumed before the `TicketCreated` event, causing data corruption!

By setting the partition key to `tenant_id:ticket_id`:
- Kafka hashes the key: `hash(tenant_id:ticket_id) % num_partitions`.
- **All events for that specific ticket are guaranteed to land on the exact same partition in strict chronological order.**
- Meanwhile, different tickets and different tenants are hashed to separate partitions, enabling high-speed parallel consumption across worker threads.

---

## 7.3 Eliminating Dual-Writes: The Transactional Outbox Pattern

A classic distributed systems flaw is the **Dual-Write Problem**:

```
                       THE DUAL-WRITE FLAW
              ┌─────────────────────────────────────┐
              │  1. DB.save(ticket);                │
              │  2. kafkaTemplate.send("ticket");   │
              └─────────────────────────────────────┘
                                 │
           What if the database save succeeds, but the network
         disconnects or the server crashes before Kafka send?
                                 │
          ==> Data inconsistency: DB has ticket, but AI plane
              never receives event! Ticket remains stuck forever.
```

To solve this without heavy, slow two-phase commit (2PC) distributed transactions, the platform implements the **Transactional Outbox Pattern**:

```
+─────────────────────────────────────────────────────────────────────────+
|               TRANSACTIONAL OUTBOX ARCHITECTURAL PATTERN                |
+─────────────────────────────────────────────────────────────────────────+

  [Incoming HTTP Request: POST /api/v1/tickets]
                        │
                        ▼
  [Spring Boot Service Layer]
  @Transactional
  ┌──────────────────────────────────────────────────────────────┐
  │ 1. INSERT INTO tickets (...) VALUES (...);                   │
  │ 2. INSERT INTO outbox_events (aggregate_id, event_type,     │
  │                               payload, processed)            │
  │    VALUES (ticket_id, 'TicketCreated', '{...}', false);      │
  │                                                              │
  │ Local PostgreSQL Transaction COMMIT (Atomic & Guaranteed!)   │
  └──────────────────────────────────────────────────────────────┘
                        │
                        ▼
  [Outbox Publisher (Scheduled Worker / CDC)]
  ┌──────────────────────────────────────────────────────────────┐
  │ SELECT * FROM outbox_events WHERE processed = false          │
  │ FOR UPDATE SKIP LOCKED;                                      │
  │                                                              │
  │ For each event:                                              │
  │   1. kafkaTemplate.send("ticket.events.v1", key, payload);   │
  │   2. Await Kafka Producer ACK (acks=all);                    │
  │   3. UPDATE outbox_events SET processed = true,              │
  │      processed_at = NOW() WHERE id = event.id;               │
  └──────────────────────────────────────────────────────────────┘
                        │
                        ▼
             [Kafka Broker: Port 9092]
```

### Outbox Table Schema:
```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP WITH TIME ZONE,
    retry_count INT DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_outbox_unprocessed ON outbox_events(processed, created_at) 
WHERE processed = FALSE;
```

---

## 7.4 Zero-Duplicate Consumer: The Idempotent Inbox Pattern

Kafka guarantees **at-least-once delivery**. If a consumer finishes processing an event but network latency delays the consumer offset commit back to Kafka, Kafka assumes the consumer died and re-delivers the exact same message to another worker.

Without defensive consumer design, this results in duplicate actions: charging a customer twice, sending two duplicate refund checks, or opening duplicate tickets.

### The Idempotent Inbox Implementation:

```java
@Component
public class TicketEventConsumer {

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private TicketProcessingService processingService;

    @KafkaListener(topics = "ticket.events.v1", groupId = "ai-triage-group")
    @Transactional
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String eventId = extractHeader(record, "X-Event-ID");
        String tenantId = record.key();

        // 1. Check if event has already been processed
        if (inboxRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event detected: {}. Skipping execution.", eventId);
            ack.acknowledge();
            return;
        }

        try {
            // 2. Execute business processing
            processingService.processTicketEvent(tenantId, record.value());

            // 3. Record in Inbox within the same transaction
            InboxEvent inbox = new InboxEvent();
            inbox.setEventId(eventId);
            inbox.setTopic(record.topic());
            inbox.setReceivedAt(Instant.now());
            inboxRepository.save(inbox);

            // 4. Acknowledge offset to Kafka
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed processing event: {}", eventId, ex);
            throw ex; // Triggers Retry / DLQ pipeline
        }
    }
}
```

---

## 7.5 Fault Tolerance: Retry Topics and Dead Letter Queue (DLQ)

When an event handler fails (e.g., Qdrant is temporarily unreachable or an external billing API times out), the system must not drop the message or block the consumer thread indefinitely.

The platform employs a **3-tier exponential backoff retry topic topology**:

```
[Kafka Topic: ticket.events.v1]
         │
         ▼
[Consumer Worker 1] ──(Throws Exception: DB Timeout)
         │
         ▼ (Publish to Retry-1 with 5s delay)
[Kafka Topic: ticket.events.retry-5s]
         │
         ▼
[Consumer Worker 2] ──(Throws Exception: Still Down)
         │
         ▼ (Publish to Retry-2 with 25s delay)
[Kafka Topic: ticket.events.retry-25s]
         │
         ▼
[Consumer Worker 3] ──(Throws Exception: Corrupt JSON / Poison Pill)
         │
         ▼ (Exhausted all retries)
[Kafka Topic: ticket.events.DLQ (Dead Letter Queue)]
         │
         ▼
[Alert Triggered in Grafana / Admin UI Alert]
- Preserves raw event payload, error stack trace, and timestamps
- Admin can review poisonous message, fix bug, and click "Replay DLQ"
```

---

## 7.6 Distributed Saga Pattern for Long-Running Business Processes

In a microservices architecture, a complex workflow like a **Customer Refund Request** spans multiple service databases:
1. `ticket-service` (Marks ticket as pending refund)
2. `workflow-service` (Checks refund policy and triggers agentic verification)
3. `billing-service` / payment mock (Executes banking payout)
4. `notification-service` (Sends customer confirmation receipt)

Because we cannot use distributed database locks, the platform implements an **Orchestrated Saga** managed by `workflow-service`:

```
                       SAGA ORCHESTRATION TIMELINE

    Step 1: Ticket Service
    [Create Refund Request] ──► (Succeeds)
               │
               ▼
    Step 2: Workflow Service
    [AI Policy & Fraud Check] ──► (Succeeds)
               │
               ▼
    Step 3: Billing System
    [Execute Payout] ─────────► (FAILS: Account Invalid / Overdrawn)
               │
               ▼
    TRIGGER COMPENSATING TRANSACTIONS (Rollback Saga):
    1. Workflow Service marks saga as REJECTED_FAILED_PAYOUT.
    2. Workflow Service emits compensating event to Kafka.
    3. Ticket Service consumes compensating event and moves ticket
       status back to "INVESTIGATION_REQUIRED" with note:
       "Automated refund failed: Invalid banking details."
    4. Notification Service informs customer to update banking details.
```

---

## 7.7 Distributed Tracing Across Kafka Boundaries

When a user initiates an action via REST, the Gateway generates a W3C trace ID. To maintain end-to-end observability as the event travels across Kafka and into the Python AI plane, tracing headers are propagated in Kafka record headers:

```
+--------------------------------------------------------------------------+
|  Kafka Record:                                                           |
|  Headers:                                                                |
|    traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01  |
|    X-Tenant-ID: 3fa85f64-5717-4562-b3fc-2c963f66afa6                    |
|    X-Event-ID: evt_9981-229-aa4                                          |
|  Key: 3fa85f64-5717-4562-b3fc-2c963f66afa6                              |
|  Value: { "ticket_id": "...", "status": "OPEN", "priority": "HIGH" }     |
+--------------------------------------------------------------------------+
```

When `ai-orchestrator` (Python) consumes this message, it extracts `traceparent` and injects it into its OpenTelemetry tracer. In Tempo/Grafana, the engineer can see the entire single trace: from the initial user button click, through Gateway Netty threads, through Kafka brokers, into the Python LangGraph loop, and into the final email dispatch!
