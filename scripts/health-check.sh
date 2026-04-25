#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# LawForYou Platform — Health Check (Mac / Linux / Git Bash)
# Usage:  chmod +x scripts/health-check.sh && ./scripts/health-check.sh
# ─────────────────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Colour

pass=0
fail=0

print_result() {
    local name="$1"
    local ok="$2"
    local detail="$3"
    printf "  %-28s" "$name"
    if [ "$ok" = "true" ]; then
        printf "${GREEN}UP  ${NC} %s\n" "$detail"
        pass=$((pass + 1))
    else
        printf "${RED}DOWN${NC} %s\n" "$detail"
        fail=$((fail + 1))
    fi
}

check_http() {
    local name="$1"
    local url="$2"
    local response
    local http_code

    http_code=$(curl -s -o /tmp/lfy_health_body -w "%{http_code}" --max-time 3 "$url" 2>/dev/null)
    local curl_exit=$?

    if [ $curl_exit -ne 0 ] || [ -z "$http_code" ]; then
        print_result "$name" "false" "connection refused / timeout"
        return
    fi

    if [ "$http_code" -lt 400 ] 2>/dev/null; then
        local detail="HTTP $http_code"
        # If it's an actuator health endpoint, show the status field
        if echo "$url" | grep -q "actuator/health"; then
            local status
            status=$(cat /tmp/lfy_health_body 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
            [ -n "$status" ] && detail="$detail  [$status]"
        fi
        print_result "$name" "true" "$detail"
    else
        print_result "$name" "false" "HTTP $http_code"
    fi
}

check_container() {
    local name="$1"
    local container="$2"

    local state
    state=$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null)

    if [ -z "$state" ]; then
        print_result "$name" "false" "container not found"
        return
    fi

    local health
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$container" 2>/dev/null)

    if [ "$state" = "running" ]; then
        print_result "$name" "true" "state=$state  health=$health"
    else
        print_result "$name" "false" "state=$state  health=$health"
    fi
}

# ─────────────────────────────────────────────────────────────────────────────

echo ""
echo -e "  ${CYAN}LawForYou - Service Health Check${NC}"
echo -e "  ${GRAY}────────────────────────────────────────────────────────${NC}"

echo ""
echo -e "  ${GRAY}HTTP Endpoints${NC}"

# Infrastructure UIs
check_http "Prometheus"           "http://localhost:9090/-/ready"
check_http "Grafana"              "http://localhost:3000/api/health"
check_http "Jaeger UI"            "http://localhost:16686/"
check_http "MinIO Console"        "http://localhost:9091/minio/health/live"
check_http "MailHog UI"           "http://localhost:8025/"

# Java services (Spring Actuator)
check_http "Config Server"        "http://localhost:8888/actuator/health"
check_http "Eureka Server"        "http://localhost:8761/actuator/health"
check_http "API Gateway"          "http://localhost:8080/actuator/health"
check_http "User Service"         "http://localhost:8081/actuator/health"
check_http "Case Service"         "http://localhost:8082/actuator/health"
check_http "Document Service"     "http://localhost:8083/actuator/health"

# NestJS service
check_http "Communication Svc"    "http://localhost:8084/health"

echo ""
echo -e "  ${GRAY}Docker Containers${NC}"

check_container "PostgreSQL"   "lawforyou-postgres"
check_container "Redis"        "lawforyou-redis"
check_container "ZooKeeper"    "lawforyou-zookeeper"
check_container "Kafka"        "lawforyou-kafka"
check_container "MongoDB"      "lawforyou-mongodb"
check_container "MinIO"        "lawforyou-minio"
check_container "MailHog"      "lawforyou-mailhog"
check_container "Prometheus"   "lawforyou-prometheus"
check_container "Grafana"      "lawforyou-grafana"
check_container "Jaeger"       "lawforyou-jaeger"

# ─────────────────────────────────────────────────────────────────────────────

echo ""
echo -e "  ${GRAY}────────────────────────────────────────────────────────${NC}"
total=$((pass + fail))
if [ $fail -eq 0 ]; then
    echo -e "  ${GREEN}All $total checks passed.${NC}"
else
    echo -e "  ${YELLOW}$pass/$total passed, $fail failed.${NC}"
fi

echo ""
echo -e "  ${GRAY}Eureka dashboard: http://localhost:8761${NC}"
echo -e "  ${GRAY}MailHog inbox:    http://localhost:8025${NC}"
echo -e "  ${GRAY}MinIO console:    http://localhost:9091  (minioadmin/minioadmin)${NC}"
echo -e "  ${GRAY}Grafana:          http://localhost:3000  (admin/admin)${NC}"
echo -e "  ${GRAY}Jaeger UI:        http://localhost:16686${NC}"
echo ""

# Clean up temp file
rm -f /tmp/lfy_health_body

# Exit with non-zero if any check failed
exit $fail

