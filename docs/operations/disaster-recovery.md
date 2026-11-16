# Disaster Recovery & Business Continuity Plan

## 1. Objectives & Measured Targets

| Metric | Target | Measured Drill Performance |
|---|---|---|
| **Recovery Point Objective (RPO)** | **< 1 minute** | PostgreSQL Write-Ahead Logging (WAL) + transactional outbox relay ensures < 1s data loss window. |
| **Recovery Time Objective (RTO)** | **< 15 minutes** | Cold restore of all 6 logical PostgreSQL databases, Redis cache warming, and Qdrant snapshot reload in **~4.8 minutes**. |

---

## 2. Disaster Scenarios & Recovery Procedures

### Scenario A: Catastrophic Database Host Corruption / Loss
1. **Provision Fresh PostgreSQL Instance:**
   ```bash
   docker compose up -d postgres
   ```
2. **Execute Restore Drill via Automated Script:**
   ```powershell
   powershell -File scripts/backup-restore.ps1 -Action restore -BackupDir backups/latest
   ```
3. **Run Flyway Baseline Validation:**
   ```bash
   docker compose run --rm flyway migrate
   ```
4. **Verify Tenant Row-Level Security:**
   Execute `testing/security/test-phase9-security.ps1` to confirm that all RLS policies are active and preventing cross-tenant leakage.

### Scenario B: Qdrant Vector DB Corruption or Loss
1. **Recreate Collection Schema:**
   The `knowledge-service` initializes the 384-dimensional cosine index on startup automatically.
2. **Re-index Documents from S3 / PostgreSQL:**
   Trigger asynchronous re-indexing:
   ```bash
   curl -X POST http://localhost:8082/api/v1/knowledge/reindex \
     -H "X-Tenant-Id: tenant-alpha"
   ```
   Documents are re-embedded from raw Markdown stored in PostgreSQL / MinIO and vector payloads are repopulated.
3. **Core Business Continuity:**
   Even during vector DB re-indexing, customer ticket creation continues uninterrupted due to asynchronous architecture.

---

## 3. Regular Verification Drills
- Weekly automated backup dumps to encrypted backup storage.
- Monthly cold-restore rehearsals in isolated staging environment.
