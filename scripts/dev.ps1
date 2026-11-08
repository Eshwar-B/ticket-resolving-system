# ==============================================================================
# Enterprise AI Platform — PowerShell Developer CLI
# Usage: .\scripts\dev.ps1 <command>
# ==============================================================================
param (
    [Parameter(Position=0)]
    [ValidateSet("up-core", "up-obs", "up-tools", "down", "status", "logs", "test-infra", "migrate")]
    [string]$Command = "status"
)

switch ($Command) {
    "up-core" {
        Write-Host "Starting Core Infrastructure (PostgreSQL, Redis, Kafka KRaft)..." -ForegroundColor Cyan
        docker-compose --profile core up -d
    }
    "up-obs" {
        Write-Host "Starting Observability Stack (OTel Collector)..." -ForegroundColor Cyan
        docker-compose --profile obs up -d
    }
    "up-tools" {
        Write-Host "Starting Developer Tools Profile..." -ForegroundColor Cyan
        docker-compose --profile tools up -d
    }
    "down" {
        Write-Host "Stopping all platform containers..." -ForegroundColor Cyan
        docker-compose --profile full down
    }
    "status" {
        Write-Host "Platform Container Status:" -ForegroundColor Cyan
        docker-compose --profile core ps
        Write-Host "`nContainer Resource Consumption:" -ForegroundColor Cyan
        docker stats --no-stream
    }
    "logs" {
        docker-compose --profile core logs -f
    }
    "test-infra" {
        & "$PSScriptRoot\test-connectivity.ps1"
    }
    "migrate" {
        Write-Host "Executing Flyway database migrations..." -ForegroundColor Cyan
        docker-compose --profile tools run --rm flyway -url=jdbc:postgresql://postgres:5432/ticket_db -user=postgres -password=postgres_dev_password migrate
    }
}

