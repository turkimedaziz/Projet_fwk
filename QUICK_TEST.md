# Quick Project Testing Guide

## Full Integration Test

Run the comprehensive test script:

```bash
sudo ./test_full_project.sh
```

This will test:
- ✅ All pods are running
- ✅ Database connectivity (Postgres)
- ✅ Elasticsearch connectivity
- ✅ Redis connectivity and caching
- ✅ Suricata IDS functionality
- ✅ Scan-app API endpoints
- ✅ Dashboard accessibility
- ✅ End-to-end alert processing flow

## Individual Component Tests

### 1. Check Infrastructure Status
```bash
sudo ./check_status.sh
```

### 2. Test Redis Integration
```bash
sudo ./test_redis_integration.sh
```

### 3. Verify Redis Fix
```bash
sudo ./verify_redis_fix.sh
```

### 4. Test Redis Caching
```bash
sudo ./test_redis_caching.sh
```

## Manual Testing

### Check Pods
```bash
sudo /usr/local/bin/kubectl get pods -n project-fwk
```

### Check Services
```bash
sudo /usr/local/bin/kubectl get svc -n project-fwk
```

### Test API Endpoints
```bash
# Get alerts
curl http://localhost:30082/api/suricata/alerts

# Get statistics
curl http://localhost:30082/api/suricata/statistics

# Get recent alerts
curl http://localhost:30082/api/suricata/alerts/recent?limit=10
```

### Check Redis Cache
```bash
# Get Redis pod
REDIS_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=redis -o jsonpath='{.items[0].metadata.name}')

# List all daily threat keys
sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli KEYS "daily_threats:*"

# Check today's alerts
TODAY=$(date +%Y-%m-%d)
sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LLEN "daily_threats:$TODAY"

# View sample alerts
sudo /usr/local/bin/kubectl exec -n project-fwk $REDIS_POD -- redis-cli LRANGE "daily_threats:$TODAY" 0 4
```

### Check Suricata Logs
```bash
SURICATA_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=suricata -o jsonpath='{.items[0].metadata.name}')
sudo /usr/local/bin/kubectl exec -n project-fwk $SURICATA_POD -- tail -20 /var/log/suricata/eve.json
```

### Check Scan-App Logs
```bash
SCAN_APP_POD=$(sudo /usr/local/bin/kubectl get pods -n project-fwk -l app=scan-app -o jsonpath='{.items[0].metadata.name}')
sudo /usr/local/bin/kubectl logs $SCAN_APP_POD -n project-fwk --tail=50
```

## Access Points

- **Dashboard**: http://localhost:30081
- **API**: http://localhost:30082/api/suricata/alerts
- **API Statistics**: http://localhost:30082/api/suricata/statistics

## Generate Test Traffic

To trigger Suricata alerts:

```bash
# From another machine or terminal
curl http://192.168.169.146:30082/api/suricata/alerts
ping 192.168.169.146
nmap -p 80,443 192.168.169.146
```

## Troubleshooting

If tests fail:

1. **Check pod logs**:
   ```bash
   sudo /usr/local/bin/kubectl logs <pod-name> -n project-fwk
   ```

2. **Describe pod for events**:
   ```bash
   sudo /usr/local/bin/kubectl describe pod <pod-name> -n project-fwk
   ```

3. **Restart a pod**:
   ```bash
   sudo /usr/local/bin/kubectl delete pod <pod-name> -n project-fwk
   ```

4. **Rebuild and redeploy**:
   ```bash
   ./rebuild_and_setup.sh
   sudo /usr/local/bin/kubectl apply -f k8s/
   ```

