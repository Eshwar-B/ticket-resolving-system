# Runbook: Ollama Local LLM Degradation / Unavailability

## Symptom
- Prometheus alert: `OllamaDegradationDetected` or AI supervisor circuit breaker tripping.
- Logs from `ai-plane` showing connection timeouts (`ConnectTimeout`, `ReadTimeout`) to `http://ollama:11434` or GPU/CPU execution latency exceeding 30s.
- `ai_plane_inference_latency_seconds` P95 > 15s.

## Impact
- Autonomous ticket resolution pauses or degrades.
- Fallback mock or degraded response mode activated.
- Core business operations (ticket submission, customer retrieval, knowledge indexing) continue functioning synchronously because of strict architectural isolation.

## Verification Steps
1. Verify Ollama container status:
   ```bash
   docker ps --filter "name=ollama"
   ```
2. Check Ollama API health endpoint directly:
   ```bash
   curl -s http://localhost:11434/api/tags
   ```
3. Inspect AI plane application logs:
   ```bash
   docker logs --tail 100 enterprise-ai-plane
   ```
4. Check GPU memory / VRAM or CPU exhaustion on host:
   ```powershell
   Get-Process -Name ollama*
   ```

## Immediate Mitigation
1. **Restart Ollama Container:**
   ```bash
   docker compose restart ollama
   ```
2. **Switch AI Plane to Degraded Fallback Mode:**
   If Ollama is unable to run due to host VRAM or RAM exhaustion, switch the AI plane to use the lightweight Mock/Degraded Adapter:
   ```bash
   # Set environment variable in .env
   AI_ADAPTER_TYPE=mock
   docker compose up -d ai-plane
   ```
3. **Verify Fallback Operation:**
   Submit a test ticket via Ticket Service (`POST /api/v1/tickets`) and verify that ticket ingestion succeeds and transitions to `TRIAGED` or `MANUAL_REVIEW` without dropping requests.
