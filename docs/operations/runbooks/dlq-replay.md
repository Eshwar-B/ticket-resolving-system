# Operational Runbook: Dead Letter Queue (DLQ) Inspection & Safe Replay

## 1. Symptoms & Alert
- **Prometheus Alert:** `DeadLetterQueueMessageSpike`
- **Threshold:** > 0 messages arriving on any `*.dlq` topic (e.g. `ticket.events.v1.dlq`).
- **Impact:** Poison or unprocessable messages bypassed from main processing pipeline to protect throughput.

---

## 2. Immediate Diagnostic Steps
1. **List DLQ Topics & Message Counts:**
   ```bash
   docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
     --bootstrap-server localhost:9092 \
     --topic ticket.events.v1.dlq --time -1
   ```
2. **Inspect Poison Message Payload & Error Headers:**
   ```bash
   docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic ticket.events.v1.dlq \
     --from-beginning --max-messages 5 --property print.headers=true
   ```

---

## 3. Root Cause Classification
- **Schema Evolution Failure:** Payload missing required JSON envelope attribute -> Update consumer schema / validator.
- **Corrupted Tenant ID:** Payload references non-existent tenant -> Manual audit intervention.
- **Downstream Timeout / Transient Outage:** Qdrant or DB was temporarily offline -> Safe for automatic replay.

---

## 4. Replay Procedure
Once root cause is remediated:
1. Re-publish verified DLQ messages back to the primary topic using Kafka producer:
   ```bash
   docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 --topic ticket.events.v1.dlq --max-messages 10 | \
   docker exec -i enterprise-ai-kafka /opt/kafka/bin/kafka-console-producer.sh \
     --bootstrap-server localhost:9092 --topic ticket.events.v1
   ```
2. Monitor consumer group lag to ensure messages process cleanly with HTTP 200/201.
