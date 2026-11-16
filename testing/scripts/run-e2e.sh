#!/usr/bin/env bash
# Levanta Postgres + backend, corre la suite Cucumber y deja la base con la
# población demo (staging/poblacion_demo.sql) para seguir probando a mano.
# Requiere Docker Compose y Node en el host. SKIP_DEMO=1 saltea la población.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "Levantando Postgres y backend (docker-compose.e2e.yml)..."
docker compose -f docker-compose.e2e.yml up -d --build --wait

export API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"
export PGHOST="${PGHOST:-127.0.0.1}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-APP}"
export PGPASSWORD="${PGPASSWORD:-APP}"
export PGDATABASE="${PGDATABASE:-labprog}"

echo "Esperando GET ${API_BASE_URL%/api}/ ..."
for _ in $(seq 1 60); do
  if curl -sf "${API_BASE_URL%/api}/" >/dev/null; then
    break
  fi
  sleep 3
done
curl -sf "${API_BASE_URL%/api}/" >/dev/null

cd "$ROOT/testing"
if [ -f package-lock.json ]; then
  npm ci
else
  npm install
fi

# La suite deja la base con datos de prueba. Pase o falle, se repuebla después
# y el script termina con el resultado de los tests.
set +e
npm test
resultado=$?
set -e

if [ "${SKIP_DEMO:-0}" != "1" ]; then
  echo "Cargando la población demo..."
  npm run db:demo
fi

exit "$resultado"
