#!/usr/bin/env bash
set -euo pipefail

SURICATA_CFG_YAML="k8s/suricata-config.yaml"
SURICATA_DEPLOY_YAML="k8s/suricata.yaml"
NAMESPACE="project-fwk"

# CLI flags
APPLY=0
EXPLICIT_IFACE=""
while [[ ${1:-} != "" ]]; do
  case "$1" in
    --apply)
      APPLY=1
      shift
      ;;
    --iface)
      EXPLICIT_IFACE="$2"
      shift 2
      ;;
    --help|-h)
      echo "Usage: $0 [--apply] [--iface <iface>]"
      echo "  --apply    : apply ConfigMap to cluster and restart deployment"
      echo "  --iface    : override detected interface"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2; exit 1
      ;;
  esac
done

IFACE=${EXPLICIT_IFACE:-$(ip route get 8.8.8.8 2>/dev/null | awk '/dev/ { for(i=1;i<=NF;i++) if($i=="dev") print $(i+1) }' | head -n1)}
if [ -z "$IFACE" ]; then
  echo "Failed to detect default interface. Try specifying manually."
  exit 1
fi
echo "Detected interface: $IFACE"

IP_REF=$(ip -o -f inet addr show dev "$IFACE" | awk '{print $4}' | head -n1)
if [ -z "$IP_REF" ]; then
  echo "No IPv4 address found on $IFACE. Please ensure interface has IPv4."
  exit 1
fi
echo "Detected IP/prefix: $IP_REF"

NET=$(
    python3 -c 'import ipaddress,sys; print(ipaddress.ip_interface(sys.argv[1]).network)' "$IP_REF"
)
if [ -z "$NET" ]; then
  echo "Failed to compute network from $IP_REF"
  exit 1
fi
echo "Computed network: $NET"

# Prepare a rendered suricata.yaml based on the docker template (safer than editing embedded ConfigMap)
TMP_DIR="tmp"
mkdir -p "$TMP_DIR"
RENDERED="$TMP_DIR/suricata.rendered.yaml"
cp docker/suricata/suricata.yaml "$RENDERED"

# Update HOME_NET and af-packet interface in the rendered file
sed -i -E "s|(^\s*HOME_NET:\s*).*$|\1\"[${NET}]\"|" "$RENDERED"
sed -i -E "s|(^\s*interface:\s*).*$|\1${IFACE}|" "$RENDERED"

cp "$SURICATA_DEPLOY_YAML" "${SURICATA_DEPLOY_YAML}.bak"

# also update docker template and Dockerfile so rebuilt images include the detected iface/network
DOCKER_SURICATA_YAML="docker/suricata/suricata.yaml"
DOCKER_DOCKERFILE="docker/Dockerfile.suricata"

if [ -f "$DOCKER_SURICATA_YAML" ]; then
  cp "$DOCKER_SURICATA_YAML" "${DOCKER_SURICATA_YAML}.bak"
  # update HOME_NET and interface in the docker template as well (optional)
  sed -i -E "s|(^\s*HOME_NET:\s*).*$|\1\"[${NET}]\"|" "$DOCKER_SURICATA_YAML"
  sed -i -E "s|(^\s*interface:\s*).*$|\1${IFACE}|" "$DOCKER_SURICATA_YAML"
  echo "Updated $DOCKER_SURICATA_YAML -> HOME_NET=${NET}, interface=${IFACE}"
fi

if [ -f "$DOCKER_DOCKERFILE" ]; then
  cp "$DOCKER_DOCKERFILE" "${DOCKER_DOCKERFILE}.bak"
  # update ENV NETWORK_INTERFACE in Dockerfile if present
  sed -i -E "s|(^ENV\s+NETWORK_INTERFACE=).*$|\1${IFACE}|" "$DOCKER_DOCKERFILE"
  echo "Updated $DOCKER_DOCKERFILE -> NETWORK_INTERFACE=${IFACE}"
fi

# update the deployment -i argument in k8s manifest
sed -i -E "s|(\"?-i\"?,?[[:space:]]+\"?)[^\"[:space:],]+(\"?)|\1${IFACE}\2|g" "$SURICATA_DEPLOY_YAML"

echo "Rendered suricata config: $RENDERED"

if [ "$APPLY" -eq 1 ]; then
  echo "Applying ConfigMap to cluster and restarting deployment..."
  /usr/local/bin/kubectl create configmap suricata-config -n "$NAMESPACE" \
    --from-file=suricata.yaml="$RENDERED" --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -
  # ensure rules configmap updated from repo rules
  if [ -f docker/suricata/custom.rules ]; then
    /usr/local/bin/kubectl create configmap suricata-rules -n "$NAMESPACE" \
      --from-file=custom.rules=docker/suricata/custom.rules --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -
  fi
  /usr/local/bin/kubectl rollout restart deployment/suricata -n "$NAMESPACE"
  echo "Applied and restarted. Check: /usr/local/bin/kubectl get pods -n $NAMESPACE"
else
  echo "Local-only mode (no cluster apply). To apply run:"
  echo "  /usr/local/bin/kubectl create configmap suricata-config -n $NAMESPACE --from-file=suricata.yaml=$RENDERED --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -"
  echo "  /usr/local/bin/kubectl apply -f $SURICATA_DEPLOY_YAML && /usr/local/bin/kubectl rollout restart deployment/suricata -n $NAMESPACE"
fi

echo "Done. Suricata files updated locally: interface=${IFACE}, HOME_NET=${NET}"