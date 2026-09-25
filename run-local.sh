#!/usr/bin/env bash
# Arranca el backend contra la base de datos local de Docker.
# Requiere: docker compose up -d  (desde esta carpeta)
set -e
cd "$(dirname "$0")"

# Lee las credenciales de Gmail de env.yaml (no está en git) si existe
if [ -f env.yaml ]; then
  export MAIL_USERNAME=$(grep '^MAIL_USERNAME:' env.yaml | cut -d: -f2- | xargs)
  export MAIL_PASSWORD=$(grep '^MAIL_PASSWORD:' env.yaml | cut -d: -f2- | xargs)
  export AI_BASE_URL=$(grep '^AI_BASE_URL:' env.yaml | cut -d: -f2- | xargs)
  export AI_API_KEY=$(grep '^AI_API_KEY:' env.yaml | cut -d: -f2- | xargs)
fi

# Motor de IA: el microservicio real si hay llave en env.yaml. AI_MODE=simulado ./run-local.sh para no
# enviarle archivos (el motor real guarda cada ensayo en su historial de plagio).
if [ -n "$AI_API_KEY" ]; then
  export AI_MODE=${AI_MODE:-real}
fi

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
