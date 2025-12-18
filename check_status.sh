#!/bin/bash
set -e

echo "=== Checking Kubernetes Cluster Status ==="
echo ""

echo "1. Checking namespace..."
sudo /usr/local/bin/kubectl get namespace project-fwk 2>&1 || echo "Namespace not found - need to create it"

echo ""
echo "2. Checking all pods..."
sudo /usr/local/bin/kubectl get pods -n project-fwk 2>&1 || echo "Cannot access pods"

echo ""
echo "3. Checking deployments..."
sudo /usr/local/bin/kubectl get deployments -n project-fwk 2>&1 || echo "Cannot access deployments"

echo ""
echo "4. Checking services..."
sudo /usr/local/bin/kubectl get services -n project-fwk 2>&1 || echo "Cannot access services"

echo ""
echo "5. Checking PVCs..."
sudo /usr/local/bin/kubectl get pvc -n project-fwk 2>&1 || echo "Cannot access PVCs"

echo ""
echo "6. Checking pod events (last 10)..."
sudo /usr/local/bin/kubectl get events -n project-fwk --sort-by='.lastTimestamp' | tail -10 2>&1 || echo "Cannot access events"

echo ""
echo "=== To check specific pod logs, run: ==="
echo "sudo /usr/local/bin/kubectl logs <pod-name> -n project-fwk"
echo ""
echo "=== To describe a pod, run: ==="
echo "sudo /usr/local/bin/kubectl describe pod <pod-name> -n project-fwk"

