#!/bin/bash

# ═══════════════════════════════════════════════════════════════════════════
# F1 Telemetry Replay System - Startup Script
# ═══════════════════════════════════════════════════════════════════════════

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# PIDs for cleanup
REDIS_PID=""
BACKEND_PID=""
PRODUCER_PID=""
FRONTEND_PID=""

# ═══════════════════════════════════════════════════════════════════════════
# Utility Functions
# ═══════════════════════════════════════════════════════════════════════════

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

cleanup() {
    echo ""
    log_warn "Shutting down services..."
    
    if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
        log_info "Stopping frontend server (PID: $FRONTEND_PID)"
        kill "$FRONTEND_PID" 2>/dev/null || true
    fi
    
    if [ -n "$PRODUCER_PID" ] && kill -0 "$PRODUCER_PID" 2>/dev/null; then
        log_info "Stopping Go producer (PID: $PRODUCER_PID)"
        kill "$PRODUCER_PID" 2>/dev/null || true
    fi
    
    if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        log_info "Stopping Spring backend (PID: $BACKEND_PID)"
        kill "$BACKEND_PID" 2>/dev/null || true
    fi
    
    if [ -n "$REDIS_PID" ] && kill -0 "$REDIS_PID" 2>/dev/null; then
        log_info "Stopping Redis (PID: $REDIS_PID)"
        kill "$REDIS_PID" 2>/dev/null || true
    fi
    
    log_info "Cleanup complete"
    exit 0
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        log_error "$1 is not installed or not in PATH"
        return 1
    fi
    return 0
}

wait_for_port() {
    local port=$1
    local name=$2
    local max_attempts=${3:-30}
    local attempt=1
    
    while ! nc -z localhost "$port" 2>/dev/null; do
        if [ $attempt -ge $max_attempts ]; then
            log_error "$name failed to start on port $port"
            return 1
        fi
        echo -n "."
        sleep 1
        ((attempt++))
    done
    echo ""
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════
# Service Start Functions
# ═══════════════════════════════════════════════════════════════════════════

start_redis() {
    log_step "Starting Redis..."
    
    # Check if Redis is already running
    if nc -z localhost 6379 2>/dev/null; then
        log_info "Redis is already running on port 6379"
        return 0
    fi
    
    if ! check_command redis-server; then
        log_error "Please install Redis: sudo dnf install redis"
        return 1
    fi
    
    redis-server --daemonize no --port 6379 --appendonly yes &
    REDIS_PID=$!
    
    echo -n "Waiting for Redis"
    if wait_for_port 6379 "Redis"; then
        log_info "Redis started (PID: $REDIS_PID)"
        return 0
    fi
    return 1
}

start_backend() {
    log_step "Starting Spring Boot backend..."
    
    cd "$PROJECT_ROOT/spring-backend"
    
    # Use Java 21 explicitly (required for Lombok compatibility)
    if [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
        export PATH="$JAVA_HOME/bin:$PATH"
        log_info "Using Java 21 from $JAVA_HOME"
    elif ! check_command java; then
        log_error "Please install Java 21: sudo dnf install java-21-openjdk"
        return 1
    fi
    
    if ! check_command mvn; then
        if [ -f "./mvnw" ]; then
            log_info "Using Maven wrapper"
            MVN_CMD="./mvnw"
        else
            log_error "Please install Maven: sudo dnf install maven"
            return 1
        fi
    else
        MVN_CMD="mvn"
    fi
    
    # Build if target doesn't exist
    if [ ! -f "target/spring-backend-0.0.1-SNAPSHOT.jar" ]; then
        log_info "Building Spring Boot application..."
        $MVN_CMD clean package -DskipTests -q
    fi
    
    # Run the application
    java -jar target/spring-backend-0.0.1-SNAPSHOT.jar \
        --spring.data.redis.host=localhost \
        --spring.data.redis.port=6379 \
        --server.port=8080 \
        > "$PROJECT_ROOT/logs/backend.log" 2>&1 &
    BACKEND_PID=$!
    
    echo -n "Waiting for backend"
    if wait_for_port 8080 "Spring Backend" 60; then
        log_info "Spring Backend started (PID: $BACKEND_PID)"
        return 0
    fi
    return 1
}

start_producer() {
    log_step "Starting Go producer..."
    
    cd "$PROJECT_ROOT/go-producer"
    
    if ! check_command go; then
        log_error "Please install Go: sudo dnf install golang"
        return 1
    fi
    
    # Build if needed
    if [ ! -f "producer" ]; then
        log_info "Building Go producer..."
        go build -o producer main.go
    fi
    
    # Run the producer
    REDIS_ADDR=localhost:6379 SESSION_KEY=9140 ./producer \
        > "$PROJECT_ROOT/logs/producer.log" 2>&1 &
    PRODUCER_PID=$!
    
    sleep 2
    if kill -0 "$PRODUCER_PID" 2>/dev/null; then
        log_info "Go Producer started (PID: $PRODUCER_PID)"
        return 0
    else
        log_error "Go Producer failed to start"
        return 1
    fi
}

start_frontend() {
    log_step "Starting frontend server..."
    
    cd "$PROJECT_ROOT/frontend"
    
    # Try python3 first, then python
    if check_command python3; then
        python3 -m http.server 3000 > "$PROJECT_ROOT/logs/frontend.log" 2>&1 &
        FRONTEND_PID=$!
    elif check_command python; then
        python -m http.server 3000 > "$PROJECT_ROOT/logs/frontend.log" 2>&1 &
        FRONTEND_PID=$!
    else
        log_error "Please install Python for the frontend server"
        return 1
    fi
    
    echo -n "Waiting for frontend"
    if wait_for_port 3000 "Frontend" 10; then
        log_info "Frontend started (PID: $FRONTEND_PID)"
        return 0
    fi
    return 1
}

# ═══════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════

main() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}       F1 Telemetry Replay System - Startup Script             ${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    
    # Setup signal handlers
    trap cleanup SIGINT SIGTERM
    
    # Create logs directory
    mkdir -p "$PROJECT_ROOT/logs"
    
    # Check for required tools
    log_step "Checking prerequisites..."
    
    MISSING_DEPS=0
    check_command nc || { log_warn "netcat (nc) not found - install with: sudo dnf install nmap-ncat"; MISSING_DEPS=1; }
    
    if [ $MISSING_DEPS -eq 1 ]; then
        log_warn "Some optional dependencies are missing, but continuing..."
    fi
    
    # Start services in order
    start_redis || { log_error "Failed to start Redis"; exit 1; }
    sleep 1
    
    start_backend || { log_error "Failed to start backend"; cleanup; exit 1; }
    sleep 1
    
    start_frontend || { log_error "Failed to start frontend"; cleanup; exit 1; }
    
    # Optionally start producer (can be started manually)
    echo ""
    read -p "Start Go producer to ingest data? [y/N] " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        start_producer || log_warn "Producer failed, but other services are running"
    fi
    
    # Print summary
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}                    All services started!                       ${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  ${CYAN}Frontend:${NC}     http://localhost:3000"
    echo -e "  ${CYAN}Backend API:${NC}  http://localhost:8080"
    echo -e "  ${CYAN}Redis:${NC}        localhost:6379"
    echo ""
    echo -e "  ${YELLOW}Logs:${NC}"
    echo -e "    Backend:  $PROJECT_ROOT/logs/backend.log"
    echo -e "    Producer: $PROJECT_ROOT/logs/producer.log"
    echo -e "    Frontend: $PROJECT_ROOT/logs/frontend.log"
    echo ""
    echo -e "  ${YELLOW}Press Ctrl+C to stop all services${NC}"
    echo ""
    
    # Wait for interrupt
    while true; do
        sleep 1
    done
}

# Run main function
main "$@"
