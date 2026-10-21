#!/usr/bin/env bash
# Aplica una migración de esquema a Cloud SQL con el usuario root (el backend no puede alterar tablas).
# Uso, desde ensayos-backend/:   ./deploy/migrar.sh db/migraciones/<archivo>.sql
# Después se despliega el backend nuevo: ./deploy/desplegar.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SQL=${1:?Indica el archivo .sql de la migración}
PROYECTO=aplicacion-desarrollo
INSTANCIA=$PROYECTO:northamerica-south1:ensayos-db
PUERTO=3307

[ -f "$SQL" ] || { echo "No existe $SQL"; exit 1; }

echo "==> Abriendo el proxy de Cloud SQL en el puerto $PUERTO"
cloud-sql-proxy --gcloud-auth --port "$PUERTO" "$INSTANCIA" > /dev/null 2>&1 &
PROXY=$!
trap 'kill $PROXY 2>/dev/null' EXIT
for _ in $(seq 1 20); do nc -z 127.0.0.1 "$PUERTO" 2>/dev/null && break; sleep 1; done

export MYSQL_PWD
MYSQL_PWD=$(gcloud secrets versions access latest --secret ensayos-db-root-password --project "$PROYECTO")

echo "==> Aplicando $SQL"
mysql -h 127.0.0.1 -P "$PUERTO" -u root < "$SQL"
echo "==> Columnas actuales de usuarios:"
mysql -h 127.0.0.1 -P "$PUERTO" -u root ensayos_db -e "SHOW COLUMNS FROM usuarios" | awk '{print "   " $1}'
