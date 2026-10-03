#!/usr/bin/env bash
# Despliegue del backend en Google Cloud (proyecto aplicacion-desarrollo, región northamerica-south1).
#
# La infraestructura ya existe (se creó una sola vez, ver deploy/README.md). Este script solo
# compila una imagen nueva y la publica en Cloud Run. Uso, desde ensayos-backend/:
#   ./deploy/desplegar.sh
set -euo pipefail
cd "$(dirname "$0")/.."

PROYECTO=aplicacion-desarrollo
REGION=northamerica-south1
SERVICIO=ensayos-backend
INSTANCIA_SQL=${PROYECTO}:${REGION}:ensayos-db
BUCKET=aplicacion-desarrollo-ensayos-pdfs
BUCKET_SIMILITUD=aplicacion-desarrollo-ensayos-similitud
CUENTA=ensayos-backend@$PROYECTO.iam.gserviceaccount.com
IA_URL=https://essay-grader-api-895184601103.northamerica-south1.run.app
CORREO_GMAIL=g8938793@gmail.com
IMAGEN=$REGION-docker.pkg.dev/$PROYECTO/ensayos-repo/ensayos-backend:$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M)

echo "==> Compilando $IMAGEN"
gcloud builds submit --project "$PROYECTO" --region "$REGION" --tag "$IMAGEN" .

echo "==> Publicando en Cloud Run"
# --no-cpu-throttling: la calificación corre en segundo plano después de responder 202 (RNF-09);
# sin CPU asignada fuera de las peticiones, Cloud Run la congelaría.
# --max-instances 1: limita el costo y evita que dos instancias retomen la misma entrega al arrancar.
gcloud run deploy "$SERVICIO" --project "$PROYECTO" --region "$REGION" \
  --image "$IMAGEN" \
  --service-account "$CUENTA" \
  --add-cloudsql-instances "$INSTANCIA_SQL" \
  --allow-unauthenticated \
  --no-cpu-throttling --cpu 1 --memory 1Gi \
  --min-instances 0 --max-instances 1 --timeout 300 \
  --set-env-vars "^;^TZ=America/Mexico_City;DB_URL=jdbc:mysql:///ensayos_db?cloudSqlInstance=$INSTANCIA_SQL&socketFactory=com.google.cloud.sql.mysql.SocketFactory&serverTimezone=America/Mexico_City&characterEncoding=UTF-8;DB_USER=app_user;STORAGE_TYPE=gcs;STORAGE_BUCKET=$BUCKET;STORAGE_SIMILITUD_BUCKET=$BUCKET_SIMILITUD;AI_MODE=real;AI_BASE_URL=$IA_URL;MAIL_USERNAME=$CORREO_GMAIL" \
  --set-secrets "DB_PASSWORD=ensayos-db-password:latest,JWT_SECRET=ensayos-jwt-secret:latest,MAIL_PASSWORD=ensayos-mail-password:latest,AI_API_KEY=ensayos-ai-api-key:latest"

URL=$(gcloud run services describe "$SERVICIO" --project "$PROYECTO" --region "$REGION" --format 'value(status.url)')
echo "==> Listo: $URL/api/v1"
