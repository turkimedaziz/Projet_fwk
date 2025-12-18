#!/bin/bash

echo "=== Suricata Attack Detection Test Suite ==="
echo "This script will simulate various attacks to test Suricata detection"
echo ""

# Function to wait and check alerts
check_alerts() {
    echo "Waiting 3 seconds for Suricata to process..."
    sleep 3
    echo "Recent alerts:"
    curl -s http://localhost:30080/api/suricata/alerts/recent?limit=5 | jq -r '.[] | "\(.timestamp) - \(.signature) (\(.severity))"' 2>/dev/null || echo "Could not fetch alerts"
    echo ""
}

# Test 1: SQL Injection attempt
echo "Test 1: SQL Injection Detection"
echo "Simulating SQL injection in HTTP request..."
curl -s "http://testphp.vulnweb.com/artists.php?artist=1' OR '1'='1" > /dev/null 2>&1
check_alerts

# Test 2: XSS attempt
echo "Test 2: Cross-Site Scripting (XSS) Detection"
echo "Simulating XSS attack..."
curl -s "http://testphp.vulnweb.com/search.php?test=<script>alert('XSS')</script>" > /dev/null 2>&1
check_alerts

# Test 3: Port scanning simulation
echo "Test 3: Port Scan Detection"
echo "Performing port scan on localhost..."
nmap -sS -p 1-100 localhost > /dev/null 2>&1 &
check_alerts

# Test 4: Suspicious user agent
echo "Test 4: Suspicious User-Agent Detection"
echo "Using suspicious user agent..."
curl -s -A "sqlmap/1.0" http://example.com > /dev/null 2>&1
check_alerts

# Test 5: Multiple rapid connections
echo "Test 5: Potential DDoS/Flooding Detection"
echo "Sending rapid requests..."
for i in {1..20}; do
    curl -s http://localhost:30080/api/suricata/statistics > /dev/null 2>&1 &
done
check_alerts

# Test 6: DNS queries to suspicious domains
echo "Test 6: Suspicious DNS Query Detection"
echo "Querying potentially malicious domains..."
nslookup malware.testcategory.com 8.8.8.8 > /dev/null 2>&1
nslookup phishing.testcategory.com 8.8.8.8 > /dev/null 2>&1
check_alerts

# Test 7: SSH brute force simulation
echo "Test 7: SSH Brute Force Detection"
echo "Attempting multiple SSH connections..."
for i in {1..5}; do
    timeout 1 ssh -o ConnectTimeout=1 -o StrictHostKeyChecking=no invalid@localhost 2>/dev/null &
done
check_alerts

echo "=== Test Suite Complete ==="
echo "Check your dashboard at http://localhost:30080 for detected alerts"
echo "Or run: curl http://localhost:30080/api/suricata/alerts/recent | jq"
