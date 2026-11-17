# Chapter 10: Enterprise Security and Multi-Tenancy

---

## 10.1 Multi-Tenancy Architectural Models

When architecting an enterprise B2B SaaS platform, selecting the correct multi-tenancy model dictates the system's operational cost, scalability, and security posture.

| Multi-Tenancy Model | Description | Pros | Cons | Verdict for Platform |
|---|---|---|---|---|
| **Database-Per-Tenant** | Each customer company gets a completely isolated PostgreSQL database instance. | Absolute physical isolation; simple backup/restore per tenant. | Extreme memory footprint. 100 tenants $\times$ 7 microservices = 700 databases! Exceeds 16 GB RAM instantly. | **Rejected** due to host hardware limits |
| **Schema-Per-Tenant** | Single database engine, but each tenant has their own isolated schema (`tenant_a.tickets`, `tenant_b.tickets`). | Logical schema isolation; shared connection pool. | Complex DDL migrations across hundreds of schemas; connection pool contention. | **Rejected** due to operational complexity |
| **Shared Database with PostgreSQL RLS** | All tenants share the same database tables. Every row includes `tenant_id`. PostgreSQL kernel enforces Row-Level Security via `SET LOCAL app.tenant_id`. | Minimal memory footprint; shared connection pools; single migration script; engine-enforced isolation. | Requires strict discipline in connection interceptors to guarantee session variable initialization. | **Selected & Implemented** |

### The Metaphor: The High-Rise Luxury Apartment Building
Imagine a premier high-rise residential building:
- **Database-Per-Tenant:** Building a separate detached private house for every resident. Extremely expensive, requires massive land area (RAM), and costs a fortune to maintain.
- **Shared DB without RLS (The Big Dormitory):** Everyone lives in one giant room and relies on residents promising not to open someone else’s luggage (`WHERE tenant_id = ?` in code). One forgetful roommate and privacy is destroyed!
- **Shared DB with PostgreSQL RLS (The Modern High-Rise):** Everyone lives in the same reinforced concrete building (Single database engine), but every resident has an encrypted keycard that only activates the elevator to their exact floor and unlocks their private apartment door. The physical lock mechanism (PostgreSQL engine) guarantees you cannot enter another apartment, regardless of what button you press.

---

## 10.2 Defense-in-Depth Multi-Tenant Segregation Matrix

Multi-tenancy in our platform is not a single checkbox—it is enforced across every layer of the distributed stack:

```
+─────────────────────────────────────────────────────────────────────────+
|               DEFENSE-IN-DEPTH MULTI-TENANCY TOPOLOGY                   |
+─────────────────────────────────────────────────────────────────────────+
| LAYER 1: IDENTITY & EDGE GATEWAY                                        |
| - Keycloak authenticates user and embeds signed "tenant_id" into JWT.   |
| - Spring Cloud Gateway validates JWKS cryptographic signature.          |
| - Gateway strips any incoming client "X-Tenant-ID" and injects verified |
|   "X-Tenant-ID" claim from the decoded JWT.                             |
+─────────────────────────────────────────────────────────────────────────+
| LAYER 2: SERVICE APPLICATION RUNTIME                                    |
| - ThreadLocal TenantContext stores tenant_id per HTTP request thread.   |
| - Method-level Spring Security validates roles (@PreAuthorize).         |
+─────────────────────────────────────────────────────────────────────────+
| LAYER 3: RELATIONAL DATABASE (PostgreSQL 16)                            |
| - Transaction interceptor executes: SET LOCAL app.tenant_id = '...';    |
| - Engine RLS Policy filters every SELECT, UPDATE, and DELETE.           |
+─────────────────────────────────────────────────────────────────────────+
| LAYER 4: VECTOR STORAGE (Qdrant)                                        |
| - Mandatory Payload Filter: {"must": [{"key": "tenant_id", "match":..}]}|
+─────────────────────────────────────────────────────────────────────────+
| LAYER 5: IN-MEMORY CACHE (Redis 7)                                      |
| - Enforced hierarchical key prefixing: "t:{tenant_id}:{subsystem}:..."  |
+─────────────────────────────────────────────────────────────────────────+
| LAYER 6: OBJECT STORAGE (MinIO)                                         |
| - Strict folder prefixing: "tenants/{tenant_id}/..."                    |
| - Presigned URLs generated only after service-layer tenant validation.  |
+─────────────────────────────────────────────────────────────────────────+
| LAYER 7: EVENT BUS (Apache Kafka)                                       |
| - Kafka partition key set to tenant_id; payloads carry tenant metadata. |
+─────────────────────────────────────────────────────────────────────────+
```

---

## 10.3 Role-Based Access Control (RBAC) Hierarchy

The platform implements fine-grained Role-Based Access Control (RBAC) managed through Keycloak realm roles:

```
                      [PLATFORM_ADMIN]
             (Full system access, cross-tenant monitoring)
                             │
                             ▼
                      [TENANT_ADMIN]
             (Manage tenant users, configure workflows,
              upload knowledge base docs, set budgets)
                             │
                             ▼
                      [SUPPORT_AGENT]
             (View assigned tickets, approve AI actions,
              add internal notes, trigger refunds)
                             │
                             ▼
                        [CUSTOMER]
             (Submit tickets, view own ticket status,
              respond to agent inquiries)
```

### Method-Level Security in Spring Boot:
```java
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @PostMapping("/{id}/approve-refund")
    @PreAuthorize("hasRole('TENANT_ADMIN') or (hasRole('SUPPORT_AGENT') and #amount <= 100.00)")
    public ResponseEntity<Void> approveRefund(@PathVariable UUID id, @RequestParam Double amount) {
        ticketService.approveRefund(id, amount);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 10.4 Token Lifecycle and Instant Session Revocation

Authentication tokens use the standard OpenID Connect (OIDC) specification:
- **Access Tokens:** Short-lived (15 minutes), signed via RSA-256 (RS256).
- **Refresh Tokens:** Long-lived (7 days), stored securely in HTTP-only, SameSite cookies.

### The Immediate Revocation Problem
Because JWTs are stateless, if an employee is terminated or a credential is compromised, standard JWTs remain valid until their expiration timestamp (15 minutes).

### The Redis Blacklist Solution:
When an administrator clicks *"Revoke Session"* or a user logs out:
1. Keycloak emits a logout webhook or the client calls `POST /api/v1/auth/logout`.
2. The Gateway extracts the JWT’s unique identifier (`jti` claim).
3. The Gateway writes a blacklist entry into Redis:
   ```
   SET t:{tenant_id}:revoked:{jti} "true" EX 900
   ```
4. The API Gateway’s `AuthenticationFilter` checks this Redis key for every request. If present, the request is immediately rejected with **HTTP 401 Unauthorized**.

---

## 10.5 Data Privacy, PII Masking, and Audit Logging

Enterprise support tickets frequently contain sensitive Personally Identifiable Information (PII) such as credit card numbers, Social Security Numbers, phone numbers, and home addresses.

Feeding raw PII into LLMs or vector databases creates severe compliance violations under GDPR, HIPAA, and PCI-DSS.

```
Incoming Ticket Description:
"My card 4532-8819-2049-1102 was charged $49.99. Call me at 555-0199."
                           │
                           ▼
               [PII Sanitization Filter]
         (High-performance Regex + NLP Scrubber)
                           │
                           ▼
Scrubbed Content Stored & Sent to LLM:
"My card [CREDIT_CARD_REDACTED] was charged $49.99. Call me at [PHONE_REDACTED]."
```

### Immutable Compliance Audit Trail (`audit_db`)

Every security-sensitive operation (user login, role change, ticket status override, financial refund approval, knowledge base document deletion) is recorded in an append-only audit ledger:

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    client_ip VARCHAR(45) NOT NULL,
    user_agent TEXT,
    payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Deny all UPDATE and DELETE privileges on audit_logs
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM enterprise_app_user;
```
This guarantees that even if an attacker gains compromised application credentials, historical audit logs cannot be modified or erased.
