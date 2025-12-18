#!/bin/bash

echo "=== Focused Attack Detection Test ==="
echo "Testing attacks that should definitely trigger Suricata alerts"
echo ""

# Wait function
wait_and_check() {
    local test_name="$1"
    echo "Waiting for Suricata to process $test_name..."
    sleep 5
    echo "New alerts:"
    curl -s http://localhost:3000/api/suricata/alerts/recent?limit=3 | jq -r '.[] | "  [\(.severity)] \(.signature)"' 2>/dev/null || echo "  No alerts fetched"
    echo ""
}

# Test 1: Nmap with specific flags that trigger alerts
echo "=== Test 1: Nmap Stealth Scan ==="
echo "Running Nmap SYN scan (should trigger HIGH alert)..."
sudo nmap -sS -p 80,443,8080 -T4 scanme.nmap.org 2>&1 | grep -E "(open|filtered|closed)" | head -5
wait_and_check "Nmap scan"

# Test 2: Nmap with OS detection
echo "=== Test 2: Nmap OS Detection ==="
echo "Running OS fingerprinting (should trigger alert)..."
sudo nmap -O --osscan-guess scanme.nmap.org 2>&1 | grep -E "(OS|Running)" | head -3
wait_and_check "OS detection"

# Test 3: Nmap version scan
echo "=== Test 3: Service Version Detection ==="
echo "Detecting service versions..."
sudo nmap -sV -p 22,80 scanme.nmap.org 2>&1 | grep -E "(open|version)" | head -5
wait_and_check "version scan"

# Test 4: Multiple HTTP requests to vulnerable test site
echo "=== Test 4: Web Application Attacks ==="
echo "Testing SQL injection patterns..."

# Various SQL injection attempts
curl -s "http://testphp.vulnweb.com/artists.php?artist=1'+OR+'1'='1" -o /dev/null
curl -s "http://testphp.vulnweb.com/artists.php?artist=1'+UNION+SELECT+1,2,3--" -o /dev/null
curl -s "http://testphp.vulnweb.com/artists.php?artist=1';DROP+TABLE+users--" -o /dev/null

echo "Testing XSS patterns..."
curl -s "http://testphp.vulnweb.com/search.php?test=<script>alert('XSS')</script>" -o /dev/null
curl -s "http://testphp.vulnweb.com/search.php?test=<img+src=x+onerror=alert(1)>" -o /dev/null

echo "Testing directory traversal..."
curl -s "http://testphp.vulnweb.com/../../etc/passwd" -o /dev/null
curl -s "http://testphp.vulnweb.com/../../../windows/system32/config/sam" -o /dev/null

wait_and_check "web attacks"

# Test 5: Known attack tool user agents
echo "=== Test 5: Attack Tool User Agents ==="
echo "Simulating known attack tools..."

curl -s -A "sqlmap/1.4.7#stable" http://testphp.vulnweb.com/ -o /dev/null
sleep 1
curl -s -A "Nikto/2.1.6" http://testphp.vulnweb.com/ -o /dev/null  
sleep 1
curl -s -A "Metasploit RSPEC" http://testphp.vulnweb.com/ -o /dev/null
sleep 1
curl -s -A "w3af.org" http://testphp.vulnweb.com/ -o /dev/null

wait_and_check "attack tools"

# Test 6: Rapid connection attempts (potential DDoS)
echo "=== Test 6: Rapid Connection Attempts ==="
echo "Simulating connection flood..."
for i in {1..50}; do
    curl -s --max-time 0.5 http://testphp.vulnweb.com/ -o /dev/null &
done
wait_and_check "connection flood"

# Test 7: SSH brute force simulation
echo "=== Test 7: SSH Brute Force ==="
echo "Multiple SSH connection attempts..."
for i in {1..5}; do
    timeout 2 ssh -o ConnectTimeout=1 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null admin@scanme.nmap.org 2>/dev/null &
done
wait_and_check "SSH attempts"

# Test 8: Suspicious DNS queries
echo "=== Test 8: DNS Queries to Suspicious Domains ==="
echo "Querying potentially malicious domains..."
nslookup malware.testcategory.com 8.8.8.8 > /dev/null 2>&1
nslookup phishing.testcategory.com 8.8.8.8 > /dev/null 2>&1
nslookup c2.botnet.example.com 8.8.8.8 > /dev/null 2>&1
nslookup trojan.malicious.net 8.8.8.8 > /dev/null 2>&1
wait_and_check "DNS queries"

# Test 9: ICMP flood (limited)
echo "=== Test 9: ICMP Flood ==="
echo "Sending rapid ping packets..."
sudo ping -f -c 50 -W 1 scanme.nmap.org > /dev/null 2>&1
wait_and_check "ICMP flood"

# Test 10: Port scan with specific pattern
echo "=== Test 10: Sequential Port Scan ==="
echo "Scanning sequential ports..."
for port in {20..30}; do
    timeout 0.1 nc -zv scanme.nmap.org $port 2>/dev/null &
done
wait_and_check "port scan"

echo ""
echo "==================================================================="
echo "                    FINAL RESULTS"
echo "==================================================================="
echo ""

echo "Alert Statistics:"
curl -s http://localhost:3000/api/suricata/statistics | jq '{
  Total: .totalAlerts,
  Critical: .criticalAlerts,
  High: .highAlerts,
  Medium: .mediumAlerts,
  Low: .lowAlerts,
  "Last Hour": .alertsLastHour,
  "Last 24h": .alertsLast24Hours
}' 2>/dev/null

echo ""
echo "Top 10 Alert Signatures:"
curl -s http://localhost:3000/api/suricata/statistics | jq -r '.topSignatures | to_entries | sort_by(-.value) | .[0:10] | .[] | "  \(.value)x - \(.key)"' 2>/dev/null

echo ""
echo "Recent Alerts (Last 20):"
curl -s http://localhost:3000/api/suricata/alerts/recent?limit=20 | jq -r '.[] | "\(.timestamp | split("T")[1] | split(".")[0]) | [\(.severity)] \(.signature)"' 2>/dev/null

echo ""
echo "==================================================================="
echo "View full dashboard at: http://localhost:3000"
echo "==================================================================="
