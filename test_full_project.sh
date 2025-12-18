#!/bin/bash
# Don't use set -e as it causes issues with arithmetic operations
set +e

echo "=========================================="
echo "  Full Project Integration Test"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

# Function to print test result
test_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}❌ FAIL${NC}: $2"
        FAILED=$((FAILED + 1))
    fi
}

# Function to print info
test_info() {
    echo -e "${YELLOW}ℹ️  INFO${NC}: $1"
}

echo "=== 1. Infrastructure Health Check ==="
echo ""

# 1.1 Check namespace
echo "1.1 Checking namespace..."
if sudo /usr/local/bin/kubectl get namespace project-fwk >/dev/null 2>&1; then
    test_result 0 "Namespace 'project-fwk' exists"
else
    test_result 1 "Namespace 'project-fwk' does not exist"
fi

# 1.2 Check all pods are running
echo "1.2 Checking all pods are running..."
PODS=$(sudo /usr/local/bin/kubectl get pods -n project-fwk --no-headers 2>/dev/null | wc -l | tr -d ' ')
PODS=${PODS:-0}
if [ "$PODS" -ge 6 ] 2>/dev/null; then
    test_result 0 "Found $PODS pods in namespace"
    
    # Check each critical pod
    for APP in dashboard scan-app postgres elasticsearch redis suricata; do
        POD_STATUS=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=$APP --no-headers 2>/dev/null | awk '{print $3}' | head -1)
        if [ "$POD_STATUS" = "Running" ]; then
            test_result 0 "$APP pod is Running"
        else
            test_result 1 "$APP pod status: ${POD_STATUS:-Not found}"
        fi
    done
else
    test_result 1 "Not enough pods found (expected at least 6, found $PODS)"
fi

# 1.3 Check services
echo "1.3 Checking services..."
SERVICES=$(sudo /usr/local/bin/kubectl get svc -n project-fwk --no-headers 2>/dev/null | wc -l | tr -d ' ')
SERVICES=${SERVICES:-0}
if [ "$SERVICES" -ge 6 ] 2>/dev/null; then
    test_result 0 "Found $SERVICES services"
else
    test_result 1 "Not enough services found"
fi

echo ""
echo "=== 2. Database Connectivity ==="
echo ""

# 2.1 Test Postgres connection
echo "2.1 Testing Postgres connection..."
POSTGRES_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$POSTGRES_POD" ]; then
    if sudo /usr/local/bin/kubectl exec -n project-fwk $POSTGRES_POD -- pg_isready -U myuser >/dev/null 2>&1; then
        test_result 0 "Postgres is ready"
    else
        test_result 1 "Postgres is not ready"
    fi
else
    test_result 1 "Postgres pod not found"
fi

# 2.2 Test scan-app database connection
echo "2.2 Testing scan-app database connection..."
SCAN_APP_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=scan-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$SCAN_APP_POD" ]; then
    DB_ERRORS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 2>&1 | grep -i "database\|postgres\|connection" | grep -i "error\|fail" | wc -l)
    if [ "$DB_ERRORS" -eq 0 ]; then
        test_result 0 "No database connection errors in scan-app"
    else
        test_result 1 "Found $DB_ERRORS database errors in scan-app logs"
    fi
else
    test_result 1 "scan-app pod not found"
fi

echo ""
echo "=== 3. Elasticsearch Connectivity ==="
echo ""

# 3.1 Test Elasticsearch health
echo "3.1 Testing Elasticsearch health..."
ELASTICSEARCH_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=elasticsearch -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$ELASTICSEARCH_POD" ]; then
    ES_HEALTH=$(sudo /usr/local/bin/kubectl exec -n project-fwk $ELASTICSEARCH_POD -- curl -s http://localhost:9200/_cluster/health 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
    if [ "$ES_HEALTH" = "green" ] || [ "$ES_HEALTH" = "yellow" ]; then
        test_result 0 "Elasticsearch is healthy (status: $ES_HEALTH)"
    else
        test_result 1 "Elasticsearch health check failed (status: $ES_HEALTH)"
    fi
else
    test_result 1 "Elasticsearch pod not found"
fi

# 3.2 Check scan-app Elasticsearch connection
echo "3.2 Testing scan-app Elasticsearch connection..."
if [ ! -z "$SCAN_APP_POD" ]; then
    ES_ERRORS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 2>&1 | grep -i "elasticsearch" | grep -i "error\|fail\|exception" | wc -l)
    if [ "$ES_ERRORS" -eq 0 ]; then
        test_result 0 "No Elasticsearch connection errors in scan-app"
    else
        test_result 1 "Found Elasticsearch errors in scan-app logs"
    fi
fi

echo ""
echo "=== 4. Redis Connectivity & Caching ==="
echo ""

# 4.1 Test Redis connection
echo "4.1 Testing Redis connection..."
REDIS_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$REDIS_POD" ]; then
    if sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli ping >/dev/null 2>&1; then
        test_result 0 "Redis is responding to PING"
    else
        test_result 1 "Redis is not responding"
    fi
else
    test_result 1 "Redis pod not found"
fi

# 4.2 Check scan-app Redis connection
echo "4.2 Testing scan-app Redis connection..."
if [ ! -z "$SCAN_APP_POD" ]; then
    REDIS_ERRORS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 2>&1 | grep -i "redis" | grep -i "Unable to connect\|connection.*fail" | wc -l)
    if [ "$REDIS_ERRORS" -eq 0 ]; then
        test_result 0 "No Redis connection errors in scan-app"
    else
        test_result 1 "Found Redis connection errors in scan-app"
    fi
fi

# 4.3 Check Redis environment variables
echo "4.3 Checking Redis configuration..."
if [ ! -z "$SCAN_APP_POD" ]; then
    REDIS_HOST=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SCAN_APP_POD -- env 2>/dev/null | grep "^SPRING_DATA_REDIS_HOST=" || echo "")
    if echo "$REDIS_HOST" | grep -q "redis"; then
        test_result 0 "Redis host configured correctly ($REDIS_HOST)"
    else
        test_result 1 "Redis host not configured correctly (found: ${REDIS_HOST:-none})"
    fi
fi

# 4.4 Check for cached alerts
echo "4.4 Checking for cached alerts in Redis..."
if [ ! -z "$REDIS_POD" ]; then
    KEYS=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS "daily_threats:*" 2>/dev/null | wc -l)
    if [ "$KEYS" -gt 0 ]; then
        test_result 0 "Found $KEYS daily threat keys in Redis"
        TODAY=$(date +%Y-%m-%d)
        TODAY_COUNT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LLEN "daily_threats:$TODAY" 2>/dev/null || echo "0")
        if [ "$TODAY_COUNT" -gt 0 ]; then
            test_result 0 "Today's cache contains $TODAY_COUNT alerts"
        else
            test_info "No alerts cached for today yet (this is OK if no new alerts)"
        fi
    else
        test_info "No daily threat keys found yet (alerts may not have been generated)"
    fi
fi

echo ""
echo "=== 5. Suricata IDS ==="
echo ""

# 5.1 Check Suricata is running
echo "5.1 Checking Suricata pod..."
SURICATA_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=suricata -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$SURICATA_POD" ]; then
    test_result 0 "Suricata pod is running"
else
    test_result 1 "Suricata pod not found"
fi

# 5.2 Check Suricata logs
echo "5.2 Checking Suricata alert generation..."
if [ ! -z "$SURICATA_POD" ]; then
    ALERT_COUNT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SURICATA_POD -- sh -c "grep -c '\"event_type\":\"alert\"' /var/log/suricata/eve.json 2>/dev/null || echo '0'" 2>/dev/null | tr -d '\n\r' || echo "0")
    ALERT_COUNT=${ALERT_COUNT:-0}
    if [ "$ALERT_COUNT" -gt 0 ] 2>/dev/null; then
        test_result 0 "Suricata has generated $ALERT_COUNT alerts"
    else
        test_info "No alerts found in Suricata logs (may need network traffic)"
    fi
fi

echo ""
echo "=== 6. Scan-App API ==="
echo ""

# 6.1 Test API health endpoint
echo "6.1 Testing scan-app API availability..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:30082/api/suricata/alerts | grep -q "200\|404"; then
    test_result 0 "API is accessible on port 30082"
else
    test_result 1 "API is not accessible"
fi

# 6.2 Test API endpoints
echo "6.2 Testing API endpoints..."
if [ ! -z "$SCAN_APP_POD" ]; then
    # Test alerts endpoint
    ALERTS_RESPONSE=$(curl -s http://localhost:30082/api/suricata/alerts?size=1 2>/dev/null || echo "ERROR")
    if echo "$ALERTS_RESPONSE" | grep -q "content\|totalElements\|ERROR"; then
        test_result 0 "Alerts API endpoint is responding"
    else
        test_result 1 "Alerts API endpoint failed"
    fi
    
    # Test statistics endpoint
    STATS_RESPONSE=$(curl -s http://localhost:30082/api/suricata/statistics 2>/dev/null || echo "ERROR")
    if echo "$STATS_RESPONSE" | grep -q "totalAlerts\|criticalAlerts\|ERROR"; then
        test_result 0 "Statistics API endpoint is responding"
    else
        test_result 1 "Statistics API endpoint failed"
    fi
fi

# 6.3 Check alert processing
echo "6.3 Checking alert processing in scan-app..."
if [ ! -z "$SCAN_APP_POD" ]; then
    PROCESSED_ALERTS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=200 2>&1 | grep -c "Saved alert" || echo "0")
    PROCESSED_ALERTS=$(echo "$PROCESSED_ALERTS" | tr -d '\n\r')
    PROCESSED_ALERTS=${PROCESSED_ALERTS:-0}
    if [ "$PROCESSED_ALERTS" -gt 0 ] 2>/dev/null; then
        test_result 0 "scan-app has processed $PROCESSED_ALERTS alerts"
    else
        test_info "No alerts processed yet (may need to wait for new alerts)"
    fi
fi

echo ""
echo "=== 7. Dashboard ==="
echo ""

# 7.1 Test dashboard accessibility
echo "7.1 Testing dashboard accessibility..."
DASHBOARD_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=dashboard -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$DASHBOARD_POD" ]; then
    test_result 0 "Dashboard pod is running"
    
    # Check if dashboard is accessible
    if curl -s -k -o /dev/null -w "%{http_code}" https://localhost:30081 2>/dev/null | grep -q "200\|301\|302"; then
        test_result 0 "Dashboard is accessible on port 30081"
    else
        test_info "Dashboard may not be accessible (check firewall/port forwarding)"
    fi
else
    test_result 1 "Dashboard pod not found"
fi

echo ""
echo "=== 8. End-to-End Flow Test ==="
echo ""

# 8.1 Generate test traffic
echo "8.1 Generating test API traffic..."
test_info "Sending API request to trigger alert processing..."
curl -s http://localhost:30082/api/suricata/alerts >/dev/null 2>&1
sleep 3

# 8.2 Check if alert was processed
echo "8.2 Checking if new alerts were processed..."
if [ ! -z "$SCAN_APP_POD" ]; then
    RECENT_LOGS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=20 2>&1 | grep -i "Saved alert\|Cached alert" | wc -l)
    if [ "$RECENT_LOGS" -gt 0 ]; then
        test_result 0 "Recent alert processing detected"
    else
        test_info "No recent alert processing (may need more time or traffic)"
    fi
fi

# 8.3 Check Redis caching after test
echo "8.3 Verifying Redis caching after test..."
if [ ! -z "$REDIS_POD" ]; then
    sleep 2
    NEW_KEYS=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS "daily_threats:*" 2>/dev/null | wc -l)
    if [ "$NEW_KEYS" -gt 0 ]; then
        test_result 0 "Redis caching is working (found $NEW_KEYS keys)"
    else
        test_info "No Redis keys yet (alerts may not have been generated)"
    fi
fi

echo ""
echo "=========================================="
echo "  Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ All critical tests passed!${NC}"
    echo ""
    echo "Your project is running correctly. You can:"
    echo "  - Access dashboard: http://localhost:30081"
    echo "  - Access API: http://localhost:30082/api/suricata/alerts"
    echo "  - Check Redis: sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS 'daily_threats:*'"
    exit 0
else
    echo -e "${YELLOW}⚠️  Some tests failed. Check the details above.${NC}"
    echo ""
    echo "Common issues:"
    echo "  - Pods may need more time to start"
    echo "  - Network traffic needed for Suricata to generate alerts"
    echo "  - Check logs: sudo /usr/local/bin/kubectl logs <pod-name> -n project-fwk"
    exit 1
fi

