#!/usr/bin/env bash
# Corre en la VM, invocado por GitHub Actions.
# Hace pull de las imágenes ya buildeadas y levanta el compose.
# No toca .env ni borra volúmenes (pgdata guarda la base).
set -euo pipefail

cd /home/ubuntu/cheturno

REQUIRED_ENV=(DB_USERNAME DB_PASSWORD JWT_SECRET MAIL_PASSWORD)
# SHA-256 de la clave JWT de ejemplo que estuvo publicada en el repo. El backend no arranca con ella.
PUBLISHED_JWT_SHA256="3354cfcf65c7b2e6840ea6f880aede239750cb1d063950c4e8fd9fbf9ec73a32"
# SHA-256 (sin espacios) de la contraseña SMTP que estuvo publicada en el repo.
PUBLISHED_MAIL_SHA256="a9796d04a783164791029eb4fbbd7b6e0e0171227bbcf1375b5da3df955e3a77"

if [[ ! -f .env ]]; then
  echo "Falta /home/ubuntu/cheturno/.env con: ${REQUIRED_ENV[*]}."
  exit 1
fi

# Lee una variable del .env sin ejecutarlo ni imprimirla.
env_value() {
  local line
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?$1=" .env | tail -n 1 || true)"
  line="${line#*=}"
  line="${line%$'\r'}"
  line="${line%"${line##*[![:space:]]}"}"
  if [[ "$line" =~ ^\"(.*)\"$ || "$line" =~ ^\'(.*)\'$ ]]; then
    line="${BASH_REMATCH[1]}"
  fi
  printf '%s' "$line"
}

# Se revisa antes de bajar imágenes o recrear contenedores: si falta algo, el sitio
# sigue con la versión que ya estaba corriendo.
missing=()
for var in "${REQUIRED_ENV[@]}"; do
  if [[ -z "$(env_value "$var")" ]]; then
    missing+=("$var")
  fi
done
if (( ${#missing[@]} > 0 )); then
  echo "En /home/ubuntu/cheturno/.env faltan o están vacías: ${missing[*]}."
  echo "No se tocó ningún contenedor. Completalas (ver deploy/.env.example) y volvé a correr el deploy."
  exit 1
fi

if [[ "$(env_value JWT_SECRET | sha256sum | cut -d' ' -f1)" == "$PUBLISHED_JWT_SHA256" ]]; then
  echo "JWT_SECRET del .env es la clave de ejemplo que estuvo publicada en el repositorio."
  echo "No se tocó ningún contenedor. Generá una propia con 'openssl rand -base64 48', guardala en .env y volvé a correr el deploy."
  exit 1
fi

if [[ "$(env_value MAIL_PASSWORD | tr -d '[:space:]' | sha256sum | cut -d' ' -f1)" == "$PUBLISHED_MAIL_SHA256" ]]; then
  echo "AVISO: MAIL_PASSWORD del .env es la contraseña de aplicación que estuvo publicada en el repositorio."
  echo "AVISO: cualquiera que haya leído el repo puede usar esa casilla. Revocala en la cuenta de Google, generá otra y actualizá el .env."
fi

if [[ ! "${IMAGE_TAG:-}" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "IMAGE_TAG tiene que ser el SHA completo (40 hex). Llegó: '${IMAGE_TAG:-}'."
  exit 1
fi

if [[ -z "${GHCR_USER:-}" || -z "${GHCR_TOKEN:-}" ]]; then
  echo "Faltan GHCR_USER y GHCR_TOKEN para hacer docker login."
  exit 1
fi

if docker info >/dev/null 2>&1; then
  docker_cmd() { docker "$@"; }
else
  echo "El usuario no llega a Docker sin sudo. Uso sudo -n."
  docker_cmd() { sudo -n -E docker "$@"; }
fi

cleanup() {
  docker_cmd logout ghcr.io >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Login en ghcr.io como ${GHCR_USER} (el token no se imprime)."
printf '%s' "$GHCR_TOKEN" | docker_cmd login ghcr.io -u "$GHCR_USER" --password-stdin
unset GHCR_TOKEN

PREVIOUS=""
if [[ -f .deployed-sha ]]; then
  PREVIOUS="$(tr -d '[:space:]' < .deployed-sha)"
fi

compose() {
  docker_cmd compose --env-file .env --env-file image-tag.env -p cheturno -f docker-compose.yml "$@"
}

write_tag() {
  printf 'IMAGE_TAG=%s\n' "$1" > image-tag.env
}

health_ok() {
  local i front_code api_code
  for i in $(seq 1 30); do
    front_code="$(curl -sS -o /tmp/cheturno-front.html -w '%{http_code}' --compressed --max-time 20 https://cheturno.site/ || true)"
    api_code="$(curl -sS -o /tmp/cheturno-api.json -w '%{http_code}' --compressed --max-time 20 https://cheturno.site/api/auth/activation-info || true)"
    if [[ "$front_code" == "200" ]] \
      && grep -q '<app-root' /tmp/cheturno-front.html \
      && [[ "$api_code" == "200" ]] \
      && grep -Eq '"status_code"[[:space:]]*:[[:space:]]*200' /tmp/cheturno-api.json; then
      echo "Health check OK en el intento ${i}. Frontend HTTP ${front_code}. API HTTP ${api_code}."
      return 0
    fi
    echo "Intento ${i}/30: frontend HTTP ${front_code:-error}, api HTTP ${api_code:-error}."
    sleep 10
  done
  echo "----- último HTML (recorte) -----"
  head -c 400 /tmp/cheturno-front.html 2>/dev/null || true
  echo
  echo "----- última respuesta de /api/auth/activation-info (recorte) -----"
  head -c 400 /tmp/cheturno-api.json 2>/dev/null || true
  echo
  return 1
}

dump_logs() {
  compose ps || true
  compose logs --tail=80 backend frontend caddy || true
}

deploy_tag() {
  local tag="$1"
  write_tag "$tag"
  echo "Pull de backend y frontend con IMAGE_TAG=${tag}."
  # Solo las imágenes que publicamos. Postgres y Caddy no se re-bajan en cada deploy
  # (la VM tiene 1 GB de RAM y el volumen de la base no se toca).
  compose pull backend frontend || return 1
  compose up -d || return 1
}

if ! deploy_tag "$IMAGE_TAG"; then
  echo "pull o up falló para ${IMAGE_TAG}."
  dump_logs
elif health_ok; then
  printf '%s\n' "$IMAGE_TAG" > .deployed-sha
  echo "Deploy de ${IMAGE_TAG} sano. Limpio imágenes que ningún contenedor usa."
  # Nunca volume prune: borraría pgdata.
  docker_cmd image prune -af || echo "Aviso: docker image prune falló. El sitio quedó arriba."
  exit 0
else
  echo "Health check falló para ${IMAGE_TAG}."
  dump_logs
fi

if [[ -n "$PREVIOUS" && "$PREVIOUS" != "$IMAGE_TAG" && "$PREVIOUS" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "Vuelvo al SHA que estaba corriendo: ${PREVIOUS}."
  if deploy_tag "$PREVIOUS" && health_ok; then
    echo "El sitio respondió otra vez con ${PREVIOUS}. Este deploy igual queda en rojo."
  else
    echo "El rollback automático tampoco dejó el sitio sano."
    dump_logs
  fi
elif [[ -f .previous-config/docker-compose.yml ]]; then
  echo "Primer deploy fallido. Restauro el docker-compose.yml y el Caddyfile que había en el servidor."
  cp .previous-config/docker-compose.yml docker-compose.yml
  if [[ -f .previous-config/Caddyfile ]]; then
    cp .previous-config/Caddyfile Caddyfile
  fi
  docker_cmd compose --env-file .env -p cheturno -f docker-compose.yml up -d || true
  echo "Quedó levantado el compose anterior. Este deploy queda en rojo."
else
  echo "No hay SHA anterior ni copia del compose manual para volver atrás."
fi

exit 1
