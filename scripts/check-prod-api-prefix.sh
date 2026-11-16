#!/usr/bin/env bash
# El frontend de producción llama a la API con la ruta relativa /api.
# environment.apiUrl ya vale '/api', así que un servicio no puede sumar otro '/api'.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="$root/frontend/cli/src"
env_file="$src/environments/environment.prod.ts"

if ! grep -Eq "apiUrl:[[:space:]]*'/api'" "$env_file"; then
  echo "environment.prod.ts tiene que definir apiUrl: '/api' (ruta relativa, sin host)."
  exit 1
fi

matches="$(grep -RIn --include='*.ts' -E 'apiUrl\}/api' "$src" || true)"
if [[ -n "$matches" ]]; then
  echo "Doble prefijo: environment.apiUrl ya es '/api' y este servicio le agrega '/api' de nuevo."
  echo "$matches"
  exit 1
fi

echo "OK: producción usa /api sin doble prefijo."
