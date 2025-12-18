#!/bin/bash
set -e

echo "=== Verifying Redis Connection Fix ==="
echo ""

# Get the current scan-app pod name
SCAN_APP_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=scan-app -o jsonpath='{.items[0].metadata.name}')
echo "Checking pod: $SCAN_APP_POD"
echo ""

echo "1. Checking for Redis connection errors..."
echo "   (Should see no 'Unable to connect to localhost' errors)"
REDIS_ERRORS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 2>&1 | grep -i "Unable to connect to localhost" || echo "NONE")
if [ "$REDIS_ERRORS" = "NONE" ]; then
    echo "   ✅ No localhost connection errors found!"
else
    echo "   ❌ Still seeing localhost connection errors:"
    echo "$REDIS_ERRORS" | head -3 | sed 's/^/     /'
fi
echo ""

echo "2. Checking for successful Redis operations..."
REDIS_SUCCESS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 2>&1 | grep -i "Cached alert.*Redis" || echo "NONE")
if [ "$REDIS_SUCCESS" != "NONE" ]; then
    echo "   ✅ Found successful Redis caching operations!"
    echo "$REDIS_SUCCESS" | head -3 | sed 's/^/     /'
else
    echo "   ⚠️  No Redis caching operations logged yet (may need to wait for alerts)"
fi
echo ""

echo "3. Checking for Redis connection failure warnings..."
REDIS_WARNINGS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 2>&1 | grep -i "Failed to cache alert in Redis" || echo "NONE")
if [ "$REDIS_WARNINGS" = "NONE" ]; then
    echo "   ✅ No Redis caching failures!"
else
    echo "   ⚠️  Still seeing Redis caching failures:"
    echo "$REDIS_WARNINGS" | head -2 | sed 's/^/     /'
fi
echo ""

echo "4. Checking environment variables in pod..."
echo "   Verifying SPRING_DATA_REDIS_HOST is set correctly..."
REDIS_HOST=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SCAN_APP_POD -- env | grep SPRING_DATA_REDIS_HOST || echo "NOT_FOUND")
if [ "$REDIS_HOST" != "NOT_FOUND" ]; then
    echo "   ✅ Found: $REDIS_HOST"
else
    echo "   ❌ SPRING_DATA_REDIS_HOST not found!"
fi

REDIS_PORT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SCAN_APP_POD -- env | grep SPRING_DATA_REDIS_PORT || echo "NOT_FOUND")
if [ "$REDIS_PORT" != "NOT_FOUND" ]; then
    echo "   ✅ Found: $REDIS_PORT"
else
    echo "   ❌ SPRING_DATA_REDIS_PORT not found!"
fi
echo ""

echo "5. Testing Redis connectivity from scan-app pod..."
echo "   (Testing if scan-app can reach Redis service)"
REDIS_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=redis -o jsonpath='{.items[0].metadata.name}')
REDIS_SVC_IP=$(sudo /usr/local/bin/kubectl get svc -n project-fwk redis -o jsonpath='{.spec.clusterIP}')
echo "   Redis service IP: $REDIS_SVC_IP"

# Try to ping Redis service from scan-app pod
PING_RESULT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SCAN_APP_POD -- sh -c "ping -c 1 redis.project-fwk.svc.cluster.local >/dev/null 2>&1 && echo 'SUCCESS' || echo 'FAILED'" 2>/dev/null || echo "UNKNOWN")
if [ "$PING_RESULT" = "SUCCESS" ]; then
    echo "   ✅ Can reach Redis service!"
else
    echo "   ⚠️  Cannot ping Redis service (may be normal if ping is not available)"
fi
echo ""

echo "=== Summary ==="
if [ "$REDIS_ERRORS" = "NONE" ] && [ "$REDIS_WARNINGS" = "NONE" ]; then
    echo "✅ Redis connection appears to be working!"
    echo ""
    echo "Next: Wait for Suricata to generate alerts, then check Redis:"
    echo "  sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS 'daily_threats:*'"
else
    echo "⚠️  May still have Redis connection issues - check logs above"
fi

