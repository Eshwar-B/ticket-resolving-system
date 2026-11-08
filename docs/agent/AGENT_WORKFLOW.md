# Autonomous Agent Workflow & Operating Procedures (docs/agent-workflow.md)

This document formalizes the multi-agent operating model and Antigravity execution rules.

---

## 1. Operating Rules & Safety Boundaries

- **Local-First & Zero Paid Services:** Strict enforcement of open-source local tooling (Docker Compose, PostgreSQL, Redis, Kafka KRaft, OpenTelemetry).
- **Git Restrictions:** Do not initialize or execute Git commands until instructed by the user.
- **Hardware Constraints:** 16 GB RAM total ceiling. Never launch all profiles simultaneously.
- **Multi-Tenancy & Data Isolation:** Server-side tenant derivation from validated Keycloak JWTs only. Postgres Row-Level Security with `SET LOCAL app.tenant_id = ...`. Tenant-scoped Redis keys (`t:{tenant_id}:...`) and Kafka partition keys (`tenantId`).
- **Service Boundaries:** Independent logical databases per service. No shared tables or JPA cross-mapping.

---

## 2. 10-Step Phase Execution Cycle

1. **Analyze:** Inspect blueprint sections, constraints, and dependencies.
2. **Plan:** Draft tasks, dependencies, data models, and acceptance criteria.
3. **Delegate:** Map independent sub-tasks to specialized roles.
4. **Implement:** Write production-quality code, configs, and migrations.
5. **Integrate:** Wire services across REST/Kafka boundaries.
6. **Test:** Run unit, integration, isolation, and failure tests.
7. **Review:** Conduct independent architecture, security, and quality review.
8. **Fix:** Resolve any defects, warnings, or risks discovered.
9. **Document:** Author reverse-engineering guides in `docs/reverse-engineering/`.
10. **Close Phase:** Update tracking docs and generate final acceptance report.
