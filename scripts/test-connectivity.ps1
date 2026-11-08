# ==============================================================================
# Enterprise AI Platform — Phase 1 Infrastructure Verification Script
# Tests PostgreSQL, Redis, Kafka KRaft, and Flyway Migrations
# ==============================================================================

$ErrorActionPreference = "Continue"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   Enterprise AI Platform: Phase 1 Infrastructure Test   " -ForegroundColor Cyan
Write-Host "========================================================"

$testResults = [ordered]@{}

# ------------------------------------------------------------------------------
# 1. Test PostgreSQL
# ------------------------------------------------------------------------------
Write-Host "`n[1/4] Testing PostgreSQL Connectivity and Databases..." -ForegroundColor Yellow
$pgReady = docker exec enterprise-ai-postgres pg_isready -U postgres
if ($LASTEXITCODE -eq 0) {
    Write-Host "  [OK] PostgreSQL is ready: $pgReady" -ForegroundColor Green
    $testResults["Postgres_Ready"] = "PASS"
    
    # Verify per-service databases exist
    $dbList = docker exec enterprise-ai-postgres psql -U postgres -d postgres -t -c "SELECT datname FROM pg_database WHERE datname IN ('ticket_db', 'identity_db', 'ai_db');"
    Write-Host "  Provisioned Databases detected:" -ForegroundColor Gray
    Write-Host $dbList -ForegroundColor Gray
    if (($dbList -match "ticket_db") -and ($dbList -match "identity_db")) {
        Write-Host "  [OK] Per-service databases verified." -ForegroundColor Green
        $testResults["Postgres_Databases"] = "PASS"
    } else {
        Write-Host "  [FAIL] Missing expected databases." -ForegroundColor Red
        $testResults["Postgres_Databases"] = "FAIL"
    }
} else {
    Write-Host "  [FAIL] PostgreSQL is not ready." -ForegroundColor Red
    $testResults["Postgres_Ready"] = "FAIL"
}

# ------------------------------------------------------------------------------
# 2. Test Redis
# ------------------------------------------------------------------------------
Write-Host "`n[2/4] Testing Redis Connectivity and Key Namespace..." -ForegroundColor Yellow
$redisPing = docker exec enterprise-ai-redis redis-cli -a redis_dev_password ping 2>$null
if ($redisPing -match "PONG") {
    Write-Host "  [OK] Redis PING successful: PONG" -ForegroundColor Green
    
    # Test multi-tenant key scoping convention: t:{tenant_id}:...
    $setResult = docker exec enterprise-ai-redis redis-cli -a redis_dev_password set "t:test-tenant:health" "phase1-healthy" 2>$null
    $val = docker exec enterprise-ai-redis redis-cli -a redis_dev_password get "t:test-tenant:health" 2>$null
    if ($val -match "phase1-healthy") {
        Write-Host "  [OK] Redis multi-tenant key read and write verified." -ForegroundColor Green
        $testResults["Redis_Connectivity"] = "PASS"
    } else {
        Write-Host "  [FAIL] Redis key verification failed." -ForegroundColor Red
        $testResults["Redis_Connectivity"] = "FAIL"
    }
} else {
    Write-Host "  [FAIL] Redis ping failed." -ForegroundColor Red
    $testResults["Redis_Connectivity"] = "FAIL"
}

# ------------------------------------------------------------------------------
# 3. Test Kafka (KRaft mode)
# ------------------------------------------------------------------------------
Write-Host "`n[3/4] Testing Apache Kafka (KRaft Mode)..." -ForegroundColor Yellow

# Create test topic
$createTopic = docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic platform.test.health --partitions 3 --replication-factor 1
Write-Host "  Topic check/create: $createTopic" -ForegroundColor Gray

# Produce message
$testMsg = '{"event":"Phase1InfrastructureTest","status":"OK","producer":"test-script"}'
$testMsg | docker exec -i enterprise-ai-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic platform.test.health

# Consume message
Write-Host "  Consuming test message from Kafka..." -ForegroundColor Gray
$consumed = docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic platform.test.health --from-beginning --max-messages 1 --timeout-ms 10000

if ($consumed -match "Phase1InfrastructureTest") {
    Write-Host "  [OK] Kafka KRaft produce and consume verified successfully!" -ForegroundColor Green
    Write-Host "  Message payload: $consumed" -ForegroundColor Gray
    $testResults["Kafka_KRaft"] = "PASS"
} else {
    Write-Host "  [FAIL] Kafka message consumption failed or timed out." -ForegroundColor Red
    $testResults["Kafka_KRaft"] = "FAIL"
}

# ------------------------------------------------------------------------------
# 4. Test Flyway Migration Execution
# ------------------------------------------------------------------------------
Write-Host "`n[4/4] Testing Flyway Migration Infrastructure..." -ForegroundColor Yellow
$flywayOut = docker-compose --profile tools run --rm flyway -url=jdbc:postgresql://postgres:5432/ticket_db -user=postgres -password=postgres_dev_password migrate
Write-Host "  Flyway migrate completed." -ForegroundColor Gray

# Verify table in Postgres ticket_db
$tableCheck = docker exec enterprise-ai-postgres psql -U postgres -d ticket_db -t -c "SELECT id, component_name, status FROM platform_infrastructure_status;"
if (($tableCheck -match "infra-phase-1") -and ($tableCheck -match "INITIALIZED")) {
    Write-Host "  [OK] Flyway baseline migration table verified in ticket_db." -ForegroundColor Green
    $testResults["Flyway_Migration"] = "PASS"
} else {
    Write-Host "  [FAIL] Flyway baseline migration verification failed." -ForegroundColor Red
    $testResults["Flyway_Migration"] = "FAIL"
}

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "                  TEST SUMMARY MATRIX                   " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
foreach ($k in $testResults.Keys) {
    $res = $testResults[$k]
    if ($res -eq "PASS") {
        Write-Host "  $k : PASS" -ForegroundColor Green
    } else {
        Write-Host "  $k : FAIL" -ForegroundColor Red
    }
}
Write-Host "========================================================"
