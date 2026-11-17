# Chapter 13: End-to-End Business Workflows

---

## 13.1 Workflow 1: The Empty Package Refund Request (Full 11-Stage Walkthrough)

To understand how every distributed subsystem coordinates under real-world business conditions, we trace a complex enterprise scenario: **A customer receives an empty cardboard box with a torn seal and submits a refund request for a $45.00 order.**

```
+──────────────────────────────────────────────────────────────────────────────────────────────────+
|                    THE 11-STAGE END-TO-END BUSINESS WORKFLOW TRACE                               |
+──────────────────────────────────────────────────────────────────────────────────────────────────+

 [STAGE 1] Customer Submits Ticket via React Frontend
           POST /api/v1/tickets with JWT Bearer Token and order metadata
             │
             ▼
 [STAGE 2] Spring Cloud API Gateway Ingestion & Validation
           - Verifies Keycloak JWT signature via JWKS
           - Evaluates Redis token bucket rate limit (Key: t:{tid}:ratelimit)
           - Injects verified X-Tenant-ID, X-User-ID, and W3C traceparent headers
             │
             ▼
 [STAGE 3] Ticket Service Database Persistence & Transactional Outbox
           - Begins local PostgreSQL transaction in ticket_db
           - Executes: SET LOCAL app.tenant_id = 'tenant-c8b1'
           - Inserts ticket record (status: 'OPEN', priority: 'MEDIUM')
           - Inserts event into outbox_events table (event_type: 'TicketCreated')
           - Commits local transaction (Guaranteed zero data loss!)
           - Returns HTTP 201 Created with ticket ID to client (< 45 ms)
             │
             ▼
 [STAGE 4] Transactional Outbox Publisher Relays to Kafka
           - Background poller reads unprocessed outbox records
           - Publishes event to Kafka topic: ticket.events.v1
           - Partition Key = tenant-c8b1 (Guarantees in-order processing)
           - Receives Kafka broker ACK (acks=all) and marks outbox record processed
             │
             ▼
 [STAGE 5] AI Orchestrator Ingests Ticket from Kafka
           - Python consumer dequeues message and checks idempotent inbox
           - Restores W3C trace context from Kafka headers
           - Initializes LangGraph AgentState with raw ticket description
             │
             ▼
 [STAGE 6] LangGraph Triage Agent Execution
           - Local Ollama LLM (llama3:8b) runs structured entity extraction
           - Classifies category as 'SHIPPING_DAMAGE_EMPTY_PACKAGE'
           - Assigns priority: 'HIGH' (Customer is distressed)
           - Extracts Order ID: 'ORD-99124' and Claimed Amount: '$45.00'
             │
             ▼
 [STAGE 7] Hybrid RAG Retrieval (Qdrant Dense + PostgreSQL Sparse)
           - Generates 768-dim query embedding via nomic-embed-text
           - Dense Search: Queries Qdrant collection kb_chunks with tenant filter
           - Sparse Search: Queries PostgreSQL knowledge_chunks using tsvector
           - Reciprocal Rank Fusion (RRF, k=60) merges results
           - Top retrieved chunk: "Return Policy Section 4.2: Packages delivered empty
             or with tampered seals qualify for immediate refund up to $50.00."
             │
             ▼
 [STAGE 8] Resolution Agent Draft & Critic Reflection Loop
           - Resolution Agent drafts empathetic response citing Section 4.2
           - Formulates proposed action: issue_refund(order_id='ORD-99124', amount=45.00)
           - Critic Node inspects draft: Verifies $45.00 is <= $50.00 policy ceiling
           - Critic checks for hallucinations against retrieved text
           - Passes validation: Confidence = 0.92, Critique Score = 0.95
             │
             ▼
 [STAGE 9] Autonomous Execution & Tool Invocation
           - Policy router confirms confidence > 0.85 and amount <= $50.00
           - AI plane invokes payment sandbox tool to issue refund credit
           - Emits event: ai.execution.completed to Kafka
             │
             ▼
 [STAGE 10] Ticket Service Updates Lifecycle State
            - Ticket Service consumes ai.execution.completed
            - Sets ticket status to 'RESOLVED' and appends AI explanation note
            - Stores resolution event in ticket_events table
            - Writes Outbox event: ticket.resolved
             │
             ▼
 [STAGE 11] Notification Service Dispatches Customer Receipt
            - Notification Service consumes ticket.resolved
            - Deduplicates against inbox_events
            - Renders email template with refund confirmation and tracking details
            - Dispatches SMTP notification to customer: "Your refund has been processed!"
```

---

## 13.2 Workflow 2: Autonomous Resolution (High-Confidence Policy Match)

When an inbound inquiry matches an unambiguous policy and involves low risk (e.g. password reset guide, software download link, or standard shipping timeline query), the system resolves the ticket **100% autonomously without human intervention**.

```
                   AUTONOMOUS RESOLUTION STATE TRANSITION

       [NEW TICKET] ──► [TRIAGE: TECHNICAL] ──► [HYBRID RAG HIT]
                                                       │
                                                       ▼
                                            [CRITIC VERIFICATION]
                                            - Grounded in doc? YES
                                            - Confidence >= 0.85? YES
                                            - Sensitive action? NO
                                                       │
                                                       ▼
                                            [AUTONOMOUS RESOLUTION]
                                            - Post comment to ticket
                                            - Move status: RESOLVED
                                            - Email user resolution
                                            - SLA: Completed in 4.2s
```

### Business Impact:
- **Cost Reduction:** Eliminates 40% to 60% of repetitive Tier-1 agent labor.
- **Instant Customer Gratification:** Ticket turnaround drops from 4 hours to under 10 seconds.
- **Zero Hallucination Guarantee:** The Critic Reflection loop guarantees answers are verbatim grounded in official documentation.

---

## 13.3 Workflow 3: Human-in-the-Loop Escalation (High-Value / Ambiguous Exception)

When an inquiry involves high financial risk (e.g. refund claim of $450.00) or low model confidence (confidence score $< 0.85$), the platform automatically triggers the **Human-in-the-Loop (HITL) Workflow**:

```
                  HUMAN-IN-THE-LOOP (HITL) TIMELINE

  [AI Plane Triage]
  Detected Refund Claim: $450.00
  Exceeds Autonomous Policy Threshold ($50.00)!
           │
           ▼
  [AI Plane Action]
  - Generates comprehensive analysis summary
  - Cites relevant policy clauses
  - Drafts suggested response for human agent
  - Sets routing_action = ESCALATE_HUMAN
           │
           ▼ Emits event: ai.execution.v1
  [Workflow Service: Port 8083]
  - Consumes event and creates a Workflow Instance in workflow_db
  - Creates a Workflow Task assigned to role 'SUPPORT_SUPERVISOR'
  - Sets Ticket status to 'PENDING_HUMAN_APPROVAL'
           │
           ▼
  [Support Supervisor Dashboard (React UI)]
  - Supervisor sees alert on dashboard: "Action Required: Ticket #4491"
  - Reviews AI analysis: "Customer claims empty package for $450 laptop."
  - Reviews AI suggested draft and attached carrier photo
  - Supervisor options:
    [APPROVE REFUND]  ──► Triggers billing payout & sends email
    [REQUEST PHOTO]   ──► Sends carrier affidavit request to customer
    [REJECT CLAIM]    ──► Explains rejection based on policy
           │
           ▼
  [Supervisor Clicks 'APPROVE REFUND']
  - Workflow Service completes task, resumes saga, and updates Ticket DB
```

---

## 13.4 Workflow 4: SLA Breach Monitoring and Dynamic Escalation

In enterprise customer support, Enterprise Service Level Agreements (SLAs) legally mandate maximum response times based on priority:
- **Critical (P1):** Resolution within 1 hour.
- **High (P2):** Resolution within 4 hours.
- **Medium (P3):** Resolution within 24 hours.
- **Low (P4):** Resolution within 72 hours.

### Automated SLA Monitoring Implementation:
1. `workflow-service` runs a distributed background scheduler (using Spring `@Scheduled` with Redis distributed locks):
   ```sql
   SELECT id, tenant_id, priority, created_at FROM tickets 
   WHERE status NOT IN ('RESOLVED', 'CLOSED') 
   AND created_at < (NOW() - INTERVAL '3 hours 30 minutes') 
   AND priority = 'HIGH';
   ```
2. Any ticket approaching within 30 minutes of SLA breach is immediately escalated:
   - Priority updated from `HIGH` to `CRITICAL`.
   - Event `ticket.sla.warning` published to Kafka.
   - `notification-service` broadcasts an urgent alert via Webhook to the on-call support team Slack/Teams channel.
   - The ticket is re-positioned at the very top of the agent triage queue.
