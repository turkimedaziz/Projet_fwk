#!/bin/bash
set -e

echo "=== Testing Redis Alert Caching ==="
echo ""

REDIS_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=redis -o jsonpath='{.items[0].metadata.name}')
SCAN_APP_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=scan-app -o jsonpath='{.items[0].metadata.name}')
TODAY=$(date +%Y-%m-%d)

echo "1. Checking current Redis keys..."
KEYS=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS "daily_threats:*" 2>/dev/null || echo "")
if [ -z "$KEYS" ]; then
    echo "   ⚠️  No daily_threats keys found yet"
    echo ""
    echo "2. Checking if Suricata is generating new alerts..."
    SURICATA_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=suricata -o jsonpath='{.items[0].metadata.name}')
    RECENT_ALERTS=$(sudo /usr/local/bin/kubectl exec -n project-fwk $SURICATA_POD -- sh -c "tail -100 /var/log/suricata/eve.json | grep -c '\"event_type\":\"alert\"' 2>/dev/null || echo '0'")
    echo "   Found $RECENT_ALERTS alerts in last 100 lines of Suricata log"
    echo ""
    echo "3. Checking scan-app logs for recent alert processing..."
    RECENT_LOGS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=50 2>&1 | grep -i "Saved alert\|Cached alert" | tail -5 || echo "NONE")
    if [ "$RECENT_LOGS" != "NONE" ]; then
        echo "   Recent alert processing:"
        echo "$RECENT_LOGS" | sed 's/^/     /'
    else
        echo "   ⚠️  No recent alert processing found"
    fi
    echo ""
    echo "4. Triggering a test alert by accessing the API..."
    echo "   (This will generate HTTP traffic that Suricata should detect)"
    curl -s http://localhost:30082/api/suricata/alerts > /dev/null 2>&1 && echo "   ✅ API request sent" || echo "   ⚠️  API request failed"
    echo ""
    echo "   Waiting 5 seconds for alert processing..."
    sleep 5
    echo ""
    echo "5. Rechecking Redis keys..."
    NEW_KEYS=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS "daily_threats:*" 2>/dev/null || echo "")
    if [ ! -z "$NEW_KEYS" ]; then
        echo "   ✅ Found Redis keys!"
        echo "$NEW_KEYS" | sed 's/^/     /'
    else
        echo "   ⚠️  Still no keys - checking scan-app logs for errors..."
        REDIS_ERRORS=$(sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=20 2>&1 | grep -i "redis\|cache" || echo "NONE")
        if [ "$REDIS_ERRORS" != "NONE" ]; then
            echo "$REDIS_ERRORS" | sed 's/^/     /'
        fi
    fi
else
    echo "   ✅ Found daily_threats keys!"
    echo "$KEYS" | sed 's/^/     /'
    echo ""
    echo "2. Checking alert count for today ($TODAY)..."
    TODAY_KEY="daily_threats:$TODAY"
    COUNT=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LLEN "$TODAY_KEY" 2>/dev/null || echo "0")
    echo "   Today's key contains $COUNT alerts"
    echo ""
    if [ "$COUNT" -gt 0 ]; then
        echo "3. Showing sample alert from Redis..."
        SAMPLE=$(sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LRANGE "$TODAY_KEY" 0 0 2>/dev/null | head -10 || echo "")
        if [ ! -z "$SAMPLE" ]; then
            echo "$SAMPLE" | sed 's/^/     /'
        fi
    fi
fi

echo ""
echo "=== Summary ==="
if [ ! -z "$KEYS" ] || [ ! -z "$NEW_KEYS" ]; then
    echo "✅ Redis caching is working! Alerts are being stored in Redis."
else
    echo "⚠️  No alerts cached yet. This could mean:"
    echo "   - No new alerts have been generated since the fix"
    echo "   - Suricata needs more network traffic to detect"
    echo "   - Check scan-app logs: sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk | tail -50"
fi

