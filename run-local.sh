#!/usr/bin/env bash
# Arranca el backend contra la base de datos local de Docker.
# Requiere: docker compose up -d  (desde esta carpeta)
set -e
cd "$(dirname "$0")"

# Lee las credenciales de Gmail de env.yaml (no está en git) si existe
if [ -f env.yaml ]; then
  export MAIL_USERNAME=$(grep '^MAIL_USERNAME:' env.yaml | cut -d: -f2- | xargs)
  export MAIL_PASSWORD=$(grep '^MAIL_PASSWORD:' env.yaml | cut -d: -f2- | xargs)
fi

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
