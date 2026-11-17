# Chapter 11: Reliability, Scalability, and Performance Engineering

---

## 11.1 The 16 GB RAM Hardware Discipline

A core architectural constraint of the Enterprise AI Platform is **zero cloud hosting cost (Cost = ₹0)** and the requirement to operate completely on a standard developer laptop running Windows 11 with **16 GB of physical RAM**.

Running a full enterprise topology—consisting of 9 microservices, 7 relational databases, Apache Kafka, Qdrant vector database, Redis, MinIO object storage, Keycloak, Prometheus, Grafana, OpenTelemetry Collector, and an 8-billion parameter local LLM—on 16 GB of RAM is an extraordinary engineering challenge.

Without strict memory governance, the Windows operating system will initiate aggressive disk swapping, resulting in catastrophic system freezes and container out-of-memory (OOM) kills.

```
+─────────────────────────────────────────────────────────────────────────+
|               16 GB HOST RAM BUDGET ALLOCATION PROFILE                  |
+─────────────────────────────────────────────────────────────────────────+
| COMPONENT SUBSYSTEM                     | MEMORY CAP (LIMIT)            |
+─────────────────────────────────────────────────────────────────────────+
| Windows 11 OS & Background Services    | 2,560 MB (2.5 GB)             |
| Ollama (Llama-3-8B-Q4_K_M + Embeddings) | 5,120 MB (5.0 GB)             |
| PostgreSQL 16 (7 microservice DBs)     | 1,024 MB (1.0 GB shared pool) |
| Apache Kafka (KRaft mode, no ZK)       |   512 MB (0.5 GB)             |
| Qdrant Vector Database                 |   512 MB (0.5 GB)             |
| Redis 7 & MinIO Object Storage         |   384 MB                      |
| Keycloak IAM (Quarkus runtime)         |   512 MB                      |
| Spring Boot Services (4 core JVMs)     | 1,536 MB (4 x 384 MB max)     |
| Python FastAPI AI Plane & Workers      |   768 MB                      |
| Observability (OTel, Prometheus, Graf) |   768 MB                      |
| Safety Reserve & Buffer                | 2,864 MB                      |
+─────────────────────────────────────────────────────────────────────────+
| TOTAL ALLOCATED                         | 16,000 MB (16.0 GB)           |
+─────────────────────────────────────────────────────────────────────────+
```

### The Metaphor: The Submarine Life-Support System
Imagine a submarine operating at deep sea:
- Oxygen, electrical power, and fresh water are strictly finite resources.
- If the galley, the engine room, or the torpedo bay decides to consume power without limits, the entire submarine risks losing propulsion or life support.
- Every department is assigned an immovable energy quota. If a non-essential light is left on, circuit breakers trip immediately to preserve the core reactor. In our platform, container `mem_limit` and JVM `-Xmx` boundaries are those life-saving circuit breakers.

---

## 11.2 Docker Resource Constraints and JVM Tuning

To prevent any single container from expanding and starving neighboring processes, every service in `docker-compose.yml` specifies explicit memory limits and reservations:

```yaml
services:
  ticket-service:
    image: enterprise/ticket-service:latest
    environment:
      - JAVA_OPTS=-Xms192m -Xmx384m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 450M
        reservations:
          memory: 256M

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    environment:
      KAFKA_JVM_PERFORMANCE_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC"
    deploy:
      resources:
        limits:
          memory: 600M

  qdrant:
    image: qdrant/qdrant:v1.8.4
    environment:
      QDRANT__STORAGE__PERFORMANCE__MAX_SEARCH_THREADS: 2
    deploy:
      resources:
        limits:
          memory: 512M
```

### Critical JVM Flags Explained:
- `-Xms192m -Xmx384m`: Constrains the heap allocation between 192 MB and 384 MB, preventing the JVM from bloating to the default 25% of host RAM (which would be 4 GB per JVM!).
- `-XX:+UseG1GC`: Employs the Garbage-First Collector, which is optimized for multi-core machines with small heaps and bounded pause times.
- `-XX:MaxGCPauseMillis=100`: Directs GC to limit stop-the-world pauses to 100 milliseconds.
- `-XX:+ExitOnOutOfMemoryError`: If an OOM condition occurs, the container terminates immediately so Docker can restart it cleanly, rather than lingering in an unresponsive, corrupt zombie state.

---

## 11.3 Connection Pool Sizing with HikariCP

A common anti-pattern in microservice engineering is configuring excessively large database connection pools (e.g. 50 or 100 connections per service). 

When 6 microservices open 50 connections each, PostgreSQL is forced to manage 300 active connections. Each backend connection in PostgreSQL consumes 5 to 10 MB of RAM, and CPU context switching across 300 contending threads degrades database throughput by up to 80%.

The platform adheres to the PostgreSQL community connection pool formula:

$$Connections = (CPU\_Cores \times 2) + Effective\_Spindle\_Count$$

For an 8-core host workstation with SSD storage:
$$(8 \times 2) + 1 = 17 \text{ total connections across all services!}$$

### HikariCP Configuration in `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 8
      minimum-idle: 2
      idle-timeout: 30000
      max-lifetime: 1800000
      connection-timeout: 5000
      leak-detection-threshold: 4000
      pool-name: TicketHikariPool
```
- `maximum-pool-size: 8`: Prevents database thread thrashing.
- `leak-detection-threshold: 4000`: Logs a warning if a thread holds a database connection for longer than 4 seconds, identifying unclosed queries or slow transactions before they cause pool starvation.

---

## 11.4 Concurrency, Backpressure, and Load Shedding

When sudden traffic spikes occur (such as a major software outage causing 5,000 users to submit support tickets simultaneously), the platform applies multi-layered backpressure:

```
                            TRAFFIC SURGE
                       (5,000 Concurrent Requests)
                                   │
                                   ▼
                       [SPRING CLOUD GATEWAY]
           Netty Non-Blocking Event Loop (Fixed Thread Pool)
                                   │
                                   ├─► If Rate Limit Exceeded:
                                   │   Returns HTTP 429 Too Many Requests
                                   │
                                   ▼ (Allowed Traffic)
                           [TICKET SERVICE]
             Writes to Local DB & Transactional Outbox (Fast!)
                                   │
                                   ▼
                            [KAFKA BROKER]
                   Absorbs the Shockwave as a Buffer!
                 (Tickets queue safely on disk partitions)
                                   │
                                   ▼ (Controlled Pacing)
                        [AI EXECUTION WORKERS]
              Pulls batches of 5 tickets at a time from Kafka
                    Processes through local Ollama LLM
           GPU/CPU operates at smooth, stable 100% capacity
              NO CRASHES, NO OOM KILLS, NO DROPPED TICKETS!
```

---

## 11.5 Graceful Degradation and Circuit Breaking

If the AI Execution Plane becomes unresponsive or Ollama crashes under heavy load, the platform does not fail customer ticket creation.

Instead, the system activates **Graceful Degradation**:
1. The API Gateway detects consecutive timeouts to `ai-orchestrator` and opens the **Resilience4j Circuit Breaker**.
2. New tickets are accepted and saved in `ticket_db` with status `QUEUED_FOR_MANUAL_REVIEW`.
3. The customer receives an immediate confirmation: *"Your ticket #4491 has been received. Our support team has been notified."*
4. When the AI container recovers and restarts, a Kafka catch-up consumer automatically drains the backlog and performs retroactive triage without requiring any manual database interventions.

---

## 11.6 Service Level Indicators (SLIs) and Objectives (SLOs)

The platform is engineered to meet standard enterprise B2B SaaS Service Level Agreements (SLAs):

| Metric | Service Level Indicator (SLI) | Service Level Objective (SLO) | Alert Threshold |
|---|---|---|---|
| **System Availability** | Ratio of successful HTTP requests (non-5xx) to total requests | **99.9% uptime** per month | Availability < 99.5% over 5-minute window |
| **REST Read Latency** | Ticket details retrieval (`GET /api/v1/tickets/{id}`) | **p95 < 150 ms**, **p99 < 300 ms** | p95 > 500 ms for 3 minutes |
| **Ticket Ingestion Latency** | Time from `POST /tickets` to HTTP 201 response | **p95 < 250 ms**, **p99 < 500 ms** | p95 > 800 ms |
| **AI Triage Turnaround** | Time from ticket submission to automated classification event | **p95 < 5.0 seconds** (Local LLM inference) | Queue latency > 30 seconds |
| **RAG Retrieval Precision** | Relevant document retrieved in Top-3 RRF ranks | **> 90% relevance** on benchmark set | Top-1 hit rate < 75% |
| **Data Durability** | Zero loss of committed tickets across hardware reboots | **100% durability** via Transactional Outbox | Outbox retry count > 5 |
