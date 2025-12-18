#!/bin/bash
set -e

echo "=== Testing Redis Integration ==="
echo ""

# Get current pod names
SCAN_APP_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=scan-app -o jsonpath='{.items[0].metadata.name}')
REDIS_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=redis -o jsonpath='{.items[0].metadata.name}')

echo "1. Checking scan-app logs for Redis connection..."
echo "   (Looking for Redis-related messages in scan-app logs)"
sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=100 | grep -i redis || echo "   No Redis errors found (good!)"
echo ""

echo "2. Checking Redis connectivity..."
echo "   Testing Redis connection from scan-app pod..."
sudo /usr/local/bin/kubectl exec -n project-fwk $SCAN_APP_POD -- sh -c "command -v redis-cli >/dev/null 2>&1 || echo 'redis-cli not installed in scan-app (this is normal)'" || true
echo ""

echo "3. Checking Redis keys for daily threats..."
TODAY=$(date +%Y-%m-%d)
echo "   Looking for keys matching: daily_threats:$TODAY"
KEYS=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS "daily_threats:*" 2>/dev/null || echo "")
if [ -z "$KEYS" ]; then
    echo "   No daily_threats keys found yet (alerts may not have been generated)"
else
    echo "   Found keys:"
    echo "$KEYS" | sed 's/^/     /'
fi
echo ""

echo "4. Checking Redis database size..."
DBSIZE=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli DBSIZE 2>/dev/null || echo "0")
echo "   Redis database contains $DBSIZE keys"
echo ""

echo "5. Testing Redis by checking a specific date key..."
if [ ! -z "$KEYS" ]; then
    FIRST_KEY=$(echo "$KEYS" | head -1 | tr -d '\r\n')
    echo "   Checking key: $FIRST_KEY"
    COUNT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LLEN "$FIRST_KEY" 2>/dev/null || echo "0")
    echo "   This key contains $COUNT alerts"
    if [ "$COUNT" -gt 0 ]; then
        echo "   Sample alert (first one):"
        sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LRANGE "$FIRST_KEY" 0 0 2>/dev/null | head -5 | sed 's/^/     /' || true
    fi
else
    echo "   No keys found to check"
fi
echo ""

echo "6. Checking Suricata logs for recent alerts..."
SURICATA_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=suricata -o jsonpath='{.items[0].metadata.name}')
echo "   Checking if Suricata is generating alerts..."
ALERT_COUNT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SURICATA_POD -- sh -c "grep -c '\"event_type\":\"alert\"' /var/log/suricata/eve.json 2>/dev/null || echo '0'")
echo "   Found $ALERT_COUNT alerts in Suricata logs"
echo ""

echo "=== Summary ==="
if [ "$DBSIZE" -gt 0 ]; then
    echo "✅ Redis is working and contains data"
else
    echo "⚠️  Redis is running but no data yet - generate some network traffic to trigger alerts"
fi

if [ "$ALERT_COUNT" -gt 0 ]; then
    echo "✅ Suricata is generating alerts"
    if [ "$DBSIZE" -eq 0 ]; then
        echo "⚠️  Alerts exist but not cached in Redis yet - check scan-app logs for Redis connection issues"
    fi
else
    echo "⚠️  No alerts generated yet - Suricata may need network traffic to detect"
fi

echo ""
echo "=== Next Steps ==="
echo "1. Generate network traffic to trigger Suricata alerts:"
echo "   curl http://192.168.169.146:30082/api/suricata/alerts"
echo "   ping 192.168.169.146"
echo ""
echo "2. Check scan-app logs for Redis caching:"
echo "   sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk | grep -i redis"
echo ""
echo "3. Access the dashboard:"
echo "   http://localhost:30081"

