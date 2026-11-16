# Operational Runbook: Database Connection Pool Exhaustion

## 1. Symptoms & Alert
- **Prometheus Alert:** `DatabaseConnectionPoolNearExhaustion`
- **Threshold:** HikariCP active connections / max connections > 80% for > 2 minutes.
- **Log Signature:** `Connection is not available, request timed out after 10000ms`.
- **Impact:** HTTP 500 responses on mutating endpoints, degraded throughput.

---

## 2. Immediate Diagnostic Steps
1. **Query Active Connections per Database:**
   ```sql
   docker exec -it enterprise-ai-postgres psql -U postgres -c "
     SELECT datname, count(*) as active_conns, state 
     FROM pg_stat_activity 
     GROUP BY datname, state;"
   ```
2. **Find Long-Running / Idle in Transaction Queries:**
   ```sql
   docker exec -it enterprise-ai-postgres psql -U postgres -c "
     SELECT pid, now() - xact_start AS duration, query, state 
     FROM pg_stat_activity 
     WHERE state != 'idle' AND (now() - xact_start) > interval '5 seconds'
     ORDER BY duration DESC;"
   ```

---

## 3. Remediation Procedure
1. **Terminate Stuck Blocking Transactions:**
   ```sql
   docker exec -it enterprise-ai-postgres psql -U postgres -c "
     SELECT pg_terminate_backend(pid) 
     FROM pg_stat_activity 
     WHERE (now() - xact_start) > interval '30 seconds' AND pid != pg_backend_pid();"
   ```
2. **Dynamic Pool Tuning:** If legitimate volume has increased, adjust `maximum-pool-size` in `application.yml` (ensuring total connections across all 6 services remain < Postgres `max_connections` = 100).
3. **Verify Connection Pool Health:**
   ```powershell
   Invoke-RestMethod -Uri "http://localhost:8081/actuator/metrics/hikaricp.connections.active"
   ```
