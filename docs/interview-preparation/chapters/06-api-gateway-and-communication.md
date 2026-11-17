# Chapter 6: API Gateway and Service Communication

---

## 6.1 The Reverse Proxy & Edge Gateway Architecture

In an enterprise microservices ecosystem, client applications (web browsers, mobile apps, third-party webhook integrations) should never establish direct network connections with individual backend microservices. Doing so creates chaotic cross-service dependencies, exposes private internal networks, forces every microservice to duplicate authentication logic, and prevents seamless backend refactoring.

The Enterprise AI Platform deploys a centralized **API Gateway** based on **Spring Cloud Gateway** (running on port `8080`), powered by an asynchronous, non-blocking **Netty** reactor engine.

```
                          CLIENT LAYER
               (React SPA, Mobile Clients, Webhooks)
                                │
                                │ HTTPS / REST (Port 8080)
                                ▼
         +───────────────────────────────────────────────+
         |            SPRING CLOUD API GATEWAY           |
         |  - TLS Termination                            |
         |  - Keycloak JWT Cryptographic Verification    |
         |  - Tenant Extraction & Header Mutation        |
         |  - Redis Token Bucket Rate Limiting (HTTP 429)|
         |  - CORS & Security Header Enforcement         |
         |  - W3C Distributed Tracing Injection (Baggage)|
         +───────────────────────────────────────────────+
                                │
        ┌───────────────┬───────┴───────┬───────────────┐
        │ /api/v1/      │ /api/v1/      │ /api/v1/      │ /api/v1/
        │ tickets/**    │ knowledge/**  │ workflows/**  │ ai/**
        ▼               ▼               ▼               ▼
+---------------+---------------+---------------+---------------+
| ticket-svc    | knowledge-svc | workflow-svc  | ai-orchestrator|
| Port: 8081    | Port: 8082    | Port: 8083    | Port: 8000    |
+---------------+---------------+---------------+---------------+
```

### The Metaphor: The International Airport Border Control
Imagine arriving at an international airport:
- **Clients:** International travelers carrying passports (JWT tokens).
- **API Gateway:** The primary Immigration and Border Control checkpoint. It verifies the passport hologram and biometric chip (Keycloak cryptographic signature).
- **Header Mutation:** The border officer stamps the traveler’s visa with an entry badge showing their verified nationality and visa privileges (`X-Tenant-ID`, `X-User-Roles`).
- **Rate Limiting:** The automated security turnstile. If 1,000 people rush through the gate at once, the barrier drops and metered pacing is enforced.
- **Internal Microservices:** The internal airport lounges and terminals. Because travelers have already cleared immigration, each lounge worker doesn't need to re-verify the full passport hologram—they simply inspect the stamped entry badge.

---

## 6.2 Spring Cloud Gateway Route Specifications

The API Gateway routes inbound traffic based on URL path predicates, applying filter chains to manipulate headers, enforce rate limits, and strip path prefixes.

### Gateway Route Configuration (`application.yml`)

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Route 1: Ticket Service
        - id: ticket-service
          uri: http://ticket-service:8081
          predicates:
            - Path=/api/v1/tickets/**
          filters:
            - name: AuthenticationFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
                key-resolver: "#{@tenantKeyResolver}"
            - RewritePath=/api/v1/tickets(?<segment>/?.*), /api/v1/tickets$\{segment}

        # Route 2: Knowledge Service
        - id: knowledge-service
          uri: http://knowledge-service:8082
          predicates:
            - Path=/api/v1/knowledge/**
          filters:
            - name: AuthenticationFilter
            - RewritePath=/api/v1/knowledge(?<segment>/?.*), /api/v1/knowledge$\{segment}

        # Route 3: Workflow Service
        - id: workflow-service
          uri: http://workflow-service:8083
          predicates:
            - Path=/api/v1/workflows/**
          filters:
            - name: AuthenticationFilter

        # Route 4: AI Orchestrator (Python FastAPI)
        - id: ai-orchestrator
          uri: http://ai-orchestrator:8000
          predicates:
            - Path=/api/v1/ai/**
          filters:
            - name: AuthenticationFilter
            - name: CircuitBreaker
              args:
                name: aiServiceCircuitBreaker
                fallbackUri: forward:/fallback/ai-service
```

---

## 6.3 Authentication Verification and Header Mutation

### 6.3.1 Cryptographic JWT Verification (JWKS)
When a request arrives at the Gateway, the `AuthenticationFilter` intercepts the `Authorization: Bearer <token>` header:
1. Validates the JWT signature against the Keycloak **JSON Web Key Set (JWKS)** endpoint (`http://keycloak:8080/realms/enterprise-ai/protocol/openid-connect/certs`).
2. Confirms token validity: checks `exp` (not expired), `nbf` (not before), and `iss` (valid issuer).
3. If invalid, the Gateway rejects the request immediately with **HTTP 401 Unauthorized**, protecting downstream services from denial-of-service or unauthenticated traffic.

### 6.3.2 Downstream Request Mutation
Upon successful verification, the Gateway extracts token claims and injects them as trusted internal HTTP headers:

```
+--------------------------------------------------------------------------+
|  JWT Claims Extracted:                                                   |
|  - sub: "usr_9981a-22d"                                                  |
|  - tenant_id: "tenant_c8b1-4f3"                                         |
|  - email: "eshwar@enterprise.com"                                        |
|  - realm_access.roles: ["TENANT_ADMIN", "SUPPORT_AGENT"]                 |
+--------------------------------------------------------------------------+
                                     │
                                     ▼
+--------------------------------------------------------------------------+
|  Gateway Mutates Request Headers Sent to Internal Services:              |
|  X-Tenant-ID: "tenant_c8b1-4f3"                                          |
|  X-User-ID: "usr_9981a-22d"                                              |
|  X-User-Roles: "TENANT_ADMIN,SUPPORT_AGENT"                              |
|  X-Trace-ID: "4bf92f3577b34da6a3ce929d0e0e4736"                          |
+--------------------------------------------------------------------------+
```

### Why This Is Secure
The internal microservices (`ticket-service`, `knowledge-service`, etc.) are placed inside a private Docker bridge network (`enterprise-net`). External traffic cannot reach them directly. Because only the Gateway can forward requests to internal ports, internal microservices can trust the `X-Tenant-ID` header injected by the Gateway.

---

## 6.4 Redis Token Bucket Rate Limiting

To prevent API abuse, denial-of-service attacks, and fair-share consumption across tenants, the Gateway implements a distributed **Token Bucket algorithm** backed by Redis.

```
                    Incoming Request
                           │
                           ▼
              ┌────────────────────────┐
              │ Extract X-Tenant-ID    │
              └────────────┬───────────┘
                           │ Key = "t:{tenant_id}:ratelimit:api"
                           ▼
              ┌────────────────────────┐
              │  Redis Lua Script:     │
              │  Evaluate Token Bucket │
              └────────────┬───────────┘
                           │
                ┌──────────┴──────────┐
          Tokens Available?     Bucket Empty?
                │                     │
                ▼                     ▼
        [Deduct 1 Token]       [HTTP 429 Too Many Requests]
        [Forward Request]      Headers:
                               Retry-After: 12
                               X-RateLimit-Limit: 100
                               X-RateLimit-Remaining: 0
```

### Key Resolver Implementation:
```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
            if (tenantId != null && !tenantId.isBlank()) {
                return Mono.just("tenant:" + tenantId);
            }
            // Fallback to client IP if tenant header is absent (pre-auth endpoints)
            return Mono.just(Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                    .getAddress().getHostAddress());
        };
    }
}
```

---

## 6.5 Standardized Error Handling (RFC 7807 Problem Details)

All microservices and the Gateway conform to **RFC 7807 (Problem Details for HTTP APIs)**. This prevents inconsistent error representations and enables automated client error handling.

### Example RFC 7807 Error Response (`application/problem+json`):

```json
{
  "type": "https://api.enterprise.ai/errors/ticket-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Ticket with ID '7f1a8c20-5c6a-4b11-9a99-b1d3e8f4a123' does not exist in tenant '3fa85f64-5717-4562-b3fc-2c963f66afa6'.",
  "instance": "/api/v1/tickets/7f1a8c20-5c6a-4b11-9a99-b1d3e8f4a123",
  "timestamp": "2026-11-17T14:40:00Z",
  "trace_id": "8a3e7b1c4d9e0f2a"
}
```

### Global Controller Advice in Spring Boot:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://api.enterprise.ai/errors/not-found"));
        problem.setProperty("trace_id", MDC.get("traceId"));
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Cross-tenant access violation");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://api.enterprise.ai/errors/forbidden"));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }
}
```

---

## 6.6 Synchronous REST vs. Asynchronous Kafka Decision Matrix

A critical design requirement in distributed architectures is choosing the correct communication protocol:

| Architectural Dimension | Synchronous REST (HTTP/JSON) | Asynchronous Event-Driven (Kafka) |
|---|---|---|
| **Latency Expectation** | Immediate (< 100 ms) | Eventual (500 ms - 30 sec) |
| **Coupling Level** | Tight (Caller blocks waiting for callee) | Completely Decoupled (Pub/Sub) |
| **Availability Dependency** | Callee must be online and healthy | Producer succeeds even if Consumer is down |
| **Failure Semantics** | Failures propagate directly to user | Failures caught, retried with DLQ |
| **Use Cases in Platform** | - User queries ticket details<br>- User uploads document<br>- Agent submits human approval | - Ticket creation triggers AI triage<br>- Document indexing pipeline<br>- Dispatching customer email/SMS |

```
                       WHEN TO USE REST VS. KAFKA

                 Does the caller need the data RIGHT NOW
                       to render the user interface?
                                   │
                    ┌──────────────┴──────────────┐
                   YES                            NO
                    │                             │
                    ▼                             ▼
           Use Synchronous REST          Can the work happen in the
             via API Gateway               background without the
         (e.g., GET /tickets/123)           user waiting blocked?
                                                  │
                                                  ▼
                                         Use Asynchronous Kafka
                                         (e.g., ticket.created)
```

---

## 6.7 Service Resilience: Circuit Breaker & Fallback Patterns

To prevent cascading failures across microservices, the API Gateway integrates **Resilience4j** circuit breakers on calls to downstream services, notably the AI Orchestrator:

```
[Normal State: CLOSED]
Requests flow normally to AI Orchestrator. Failure rate < 50%.
         │
         │ (If AI Orchestrator becomes unresponsive / high latency)
         ▼
[Tripped State: OPEN]
Gateway immediately intercepts calls without calling AI Orchestrator.
Returns instantaneous fallback response:
"AI triage currently queued. Ticket stored safely in backlog."
Prevents Gateway thread exhaustion!
         │
         │ (After 30 seconds wait)
         ▼
[Testing State: HALF-OPEN]
Gateway lets 5 trial requests through.
If they succeed ──► Returns to CLOSED.
If they fail    ──► Returns to OPEN for another 30 seconds.
```
