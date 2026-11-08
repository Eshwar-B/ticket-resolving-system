# Platform Blockers & Technical Risks (docs/blockers.md)

Active environment hurdles, architectural trade-offs, and open prerequisites.

---

## Active Hurdles & Status

| Blocker ID | Description | Impact | Status | Mitigation / Solution |
|---|---|---|---|---|
| **BLK-001** | **Docker Desktop daemon not running** | Prevents running `docker-compose up` and active container verification | **RESOLVED** | Docker Desktop started; core containers running healthy |
| **BLK-002** | **Git integration disabled** | Repository must not be initialized with Git until user explicitly authorizes | **COMPLIANT** | Workspace cleaned; no Git commands will run until instructed |

---

## Architectural Risks Tracked

1. **Hardware Memory Ceiling (16 GB):**
   - *Risk:* Running multiple Docker services simultaneously on a Windows machine with 16 GB RAM could exhaust host memory and cause WSL2 swapping.
   - *Status:* **Mitigated & Measured.** Live memory usage of Core Infrastructure (`postgres`, `redis`, `kafka`) was measured at **342.7 MiB**, leaving >15 GB headroom for future services.
2. **Kafka Single-Node KRaft in Local Development:**
   - *Risk:* Multi-broker replicas cannot be tested locally without exceeding memory budget.
   - *Status:* **Mitigated.** Verified single-node KRaft broker + controller with dual listeners, topic creation, and message production/consumption.
