# Cloud Architecture Reference & Infracost Estimate

> **GOVERNANCE MANDATE:**
> **"Terraform was validated and cost-estimated, not applied to a real cloud."**
> In strict compliance with the **Cost = ₹0** rule, all local development and integration testing runs on Windows 11 using local-first open-source tooling (Docker Compose, `kind`, PostgreSQL, Redis, Kafka KRaft, MinIO, Qdrant). No cloud resources were provisioned and no billable API calls were made.

---

## Architectural Mapping: Local-First vs Cloud-Native Target

| Component | Local ₹0 Development (Active) | Cloud-Native Target (Terraform Reference) | Monthly Cost Est. (AWS ap-south-1) |
|---|---|---|---|
| **Container Orchestration** | Docker Compose / local `kind` | Amazon EKS (1.30 Managed Control Plane) | ~$73.00 |
| **Compute Nodes** | Host CPU / GPU (AMD Ryzen + RTX 3050) | 2x `m6i.large` EC2 Managed Node Group | ~$135.00 |
| **Relational Database** | PostgreSQL 16 Alpine container | Amazon RDS PostgreSQL 16 Multi-AZ (`db.t4g.medium`) | ~$124.00 |
| **Event Bus** | Apache Kafka 3.8.0 KRaft container | Amazon MSK Kafka 3.6 (`kafka.t3.small` x2) | ~$76.00 |
| **Object Storage** | MinIO Container | Amazon S3 Standard + Infrequent Access | ~$3.00 |
| **Cache / Rate Limiting** | Redis 7 Alpine container | Amazon ElastiCache Redis (`cache.t4g.micro`) | ~$18.00 |
| **Total Estimated Cloud Spend** | **₹0 (Actual Spend)** | **~$429.00 / month (~₹35,800)** | (Validated only, ₹0 spent) |

---

## Static Security & Hygiene Verification
The Terraform configuration in `infra/terraform/` was designed with:
1. **Network Isolation:** Private subnets for all databases, caches, and MSK brokers with no direct internet ingress.
2. **Encryption in Transit & Rest:** S3 server-side AES256 default encryption; MSK and RDS TLS encryption.
3. **IAM Least Privilege:** Dedicated cluster role with explicit assume-role policies.
