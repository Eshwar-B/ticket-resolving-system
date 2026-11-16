# Operational Runbook: Kafka Consumer Lag Spike

## 1. Symptoms & Alert
- **Prometheus Alert:** `KafkaConsumerLagSpike`
- **Threshold:** Lag > 100 uncommitted offsets for > 2 minutes on topic `ticket.events.v1` or `workflow.events.v1`.
- **Impact:** Delayed ticket triage, asynchronous AI processing backlog, SLA clock ticking.

---

## 2. Immediate Diagnostic Steps
1. **Inspect Consumer Group Offsets:**
   ```bash
   docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --describe --group ai-ticket-consumers
   ```
2. **Check Container CPU & Memory:**
   ```bash
   docker stats enterprise-ai-kafka enterprise-ai-ticket-service
   ```
3. **Inspect Consumer Worker Logs for Blocked Calls:**
   ```bash
   docker logs --tail 100 enterprise-ai-workflow-service | grep -E "ERROR|WARN|Timeout"
   ```

---

## 3. Remediation Procedure
1. **Poison Message Bottleneck:** If lag is stuck on a single partition with repeated failures, ensure the dead-letter topic interceptor routes it to `.dlq`.
2. **Partition Scaling:** If message volume exceeds consumer throughput, increase partitions:
   ```bash
   docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 \
     --alter --topic ticket.events.v1 --partitions 6
   ```
3. **Restart Stuck Consumer:**
   ```bash
   docker restart enterprise-ai-ticket-service
   ```
4. **Verification:** Confirm lag returns to 0 on Grafana dashboard `platform-control-plane-overview`.
