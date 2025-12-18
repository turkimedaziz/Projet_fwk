#!/bin/bash
set -e

echo "1. Building Dashboard..."
docker build -t dashboard:latest dashboard/

echo "2. Building Scan App..."
docker build -t scan-app:latest scan/

echo "3. Building Suricata..."
docker build -t suricata:latest -f docker/Dockerfile.suricata docker/

echo "4. Pulling Infrastructure Images..."
docker pull postgres:latest
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.11.0
docker pull redis:latest
docker pull rediscommander/redis-commander:latest

echo "5. Importing images into K3s (streaming)..."
docker save dashboard:latest | sudo /usr/local/bin/k3s ctr images import -
docker save scan-app:latest | sudo /usr/local/bin/k3s ctr images import -
docker save suricata:latest | sudo /usr/local/bin/k3s ctr images import -
docker save postgres:latest | sudo /usr/local/bin/k3s ctr images import -
docker save docker.elastic.co/elasticsearch/elasticsearch:8.11.0 | sudo /usr/local/bin/k3s ctr images import -
docker save redis:latest | sudo /usr/local/bin/k3s ctr images import -
docker save rediscommander/redis-commander:latest | sudo /usr/local/bin/k3s ctr images import -

echo "6. Cleaning up..."
# No tar files to clean up

echo "8. Restarting Pods..."
/usr/local/bin/kubectl delete pods -n project-fwk --all

echo "Done! Watch the pods with: kubectl get pods -n project-fwk -w"
