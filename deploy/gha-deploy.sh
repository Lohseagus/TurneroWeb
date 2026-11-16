#!/usr/bin/env bash
# Corre en el runner de GitHub Actions. Copia la config de prod y ejecuta remote-deploy.sh.
# Secretos por entorno: SSH_HOST, SSH_USER, SSH_PRIVATE_KEY, GHCR_TOKEN, GHCR_USER, IMAGE_TAG.
set -euo pipefail

: "${SSH_HOST:?Falta SSH_HOST}"
: "${SSH_USER:?Falta SSH_USER}"
: "${SSH_PRIVATE_KEY:?Falta SSH_PRIVATE_KEY}"
: "${GHCR_TOKEN:?Falta GHCR_TOKEN}"
: "${GHCR_USER:?Falta GHCR_USER}"
: "${IMAGE_TAG:?Falta IMAGE_TAG}"

if [[ ! "$IMAGE_TAG" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "IMAGE_TAG tiene que ser un SHA de 40 caracteres."
  exit 1
fi

install -m 700 -d ~/.ssh
printf '%s\n' "$SSH_PRIVATE_KEY" > ~/.ssh/cheturno_deploy
chmod 600 ~/.ssh/cheturno_deploy
ssh-keyscan -H "$SSH_HOST" >> ~/.ssh/known_hosts

SSH=(ssh -i ~/.ssh/cheturno_deploy -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=40)
SCP=(scp -i ~/.ssh/cheturno_deploy -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes)
TARGET="${SSH_USER}@${SSH_HOST}"

"${SSH[@]}" "$TARGET" "mkdir -p /home/ubuntu/cheturno"
# Antes del primer deploy guardamos el compose manual, por si hay que volver atrás.
"${SSH[@]}" "$TARGET" "if [ ! -f /home/ubuntu/cheturno/.deployed-sha ]; then mkdir -p /home/ubuntu/cheturno/.previous-config; cp -a /home/ubuntu/cheturno/docker-compose.yml /home/ubuntu/cheturno/Caddyfile /home/ubuntu/cheturno/.previous-config/ 2>/dev/null || true; fi"
"${SCP[@]}" docker-compose.prod.yml "${TARGET}:/home/ubuntu/cheturno/docker-compose.yml"
"${SCP[@]}" deploy/Caddyfile "${TARGET}:/home/ubuntu/cheturno/Caddyfile"
"${SCP[@]}" deploy/remote-deploy.sh "${TARGET}:/home/ubuntu/cheturno/remote-deploy.sh"
"${SSH[@]}" "$TARGET" "chmod 755 /home/ubuntu/cheturno/remote-deploy.sh"

remote_cmd=$(printf 'IMAGE_TAG=%q GHCR_USER=%q GHCR_TOKEN=%q bash /home/ubuntu/cheturno/remote-deploy.sh' \
  "$IMAGE_TAG" "$GHCR_USER" "$GHCR_TOKEN")
"${SSH[@]}" "$TARGET" "$remote_cmd"
