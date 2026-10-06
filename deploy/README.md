# Despliegue en Google Cloud

**URL de la API:** `https://ensayos-backend-990972460164.northamerica-south1.run.app/api/v1`

- **Proyecto:** `aplicacion-desarrollo`
- **Región:** `northamerica-south1` (Querétaro)

## Recursos

| Recurso | Nombre | Notas |
|---|---|---|
| Cloud Run | `ensayos-backend` | 1 vCPU, 1 GiB, de 0 a 1 instancias, **CPU siempre asignada** (la calificación es asíncrona) |
| Cloud SQL | `ensayos-db` (MySQL 8.0, db-f1-micro) | Base de datos `ensayos_db`, zona horaria `-06:00`, `utf8mb4` |
| Cloud Storage | `gs://aplicacion-desarrollo-ensayos-pdfs` | PDFs de las entregas. Privado; solo lo lee y escribe el backend |
| Cloud Storage | `gs://aplicacion-desarrollo-ensayos-similitud` | Histórico del motor de IA, cada ensayo como `{sha256}.pdf`. Privado; el backend solo lo lee (`roles/storage.objectViewer`) |
| Cloud Storage | `gs://aplicacion-desarrollo-descargas` | **Público (solo lectura de objetos)**: el APK de la app, `ensayos-movil.apk`. Se publica con `ensayos_movil/scripts/publicar_apk.sh` |
| Artifact Registry | `ensayos-repo` | Imágenes del backend |
| Cuenta de servicio | `ensayos-backend@aplicacion-desarrollo.iam.gserviceaccount.com` | Cloud SQL Client, acceso a los objetos del bucket y a 4 secretos |

### Secretos (Secret Manager)

| Secreto | Uso |
|---|---|
| `ensayos-db-password` | Contraseña de `app_user`. Tiene solo SELECT, INSERT, UPDATE y DELETE sobre `ensayos_db` |
| `ensayos-db-root-password` | Contraseña de `root`. **No** la usa el backend; solo sirve para cambios de esquema |
| `ensayos-jwt-secret` | Clave nueva para firmar los JWT. **No** es la que quedó publicada en el historial de git |
| `ensayos-mail-password` | Contraseña de aplicación de Gmail |
| `ensayos-ai-api-key` | Llave del microservicio de IA |
| `ensayos-apk-keystore` / `ensayos-apk-key-properties` | Respaldo de la llave de firma del APK y su contraseña. Para recuperarla: `gcloud secrets versions access latest --secret ensayos-apk-keystore --out-file=llaves/ensayos-release.jks` (y lo mismo con `key.properties`) |

Ninguna contraseña está en el código ni en `env.yaml`. Para leer una: `gcloud secrets versions access latest --secret <nombre>`.

## Publicar una versión nueva del backend

```bash
cd ensayos-backend
./deploy/desplegar.sh      # compila con Cloud Build y publica en Cloud Run
```

## Conectarse a la base de datos de la nube

```bash
cloud-sql-proxy --gcloud-auth --port 3307 aplicacion-desarrollo:northamerica-south1:ensayos-db
# en otra terminal:
MYSQL_PWD=$(gcloud secrets versions access latest --secret ensayos-db-root-password) \
  mysql -h 127.0.0.1 -P 3307 -u root ensayos_db
```

Los cambios de esquema (`db/ddl.sql`) se aplican a mano con `root`. El backend usa `ddl-auto=validate` y no puede crear ni modificar tablas.

## Datos iniciales (`db/seed_nube.sql`)

- **Administrador:** `glongoria.3a.is@gmail.com`. Entra por OTP; el código le llega a ese correo.
- **Cuentas de prueba** (contraseña `Prueba123`): `alumno.prueba@alumno.ipn.mx` y `profesor.prueba@ipn.mx`.
- **Grupo `SO3CM1`** con dos tareas abiertas.

## App móvil apuntando a la nube

```bash
flutter run --dart-define=API_URL=https://ensayos-backend-990972460164.northamerica-south1.run.app/api/v1
```

## Costos

- **Cloud SQL** es el único recurso que cobra aunque no se use, unos 10 a 15 USD al mes.
  - Detener la instancia cuando no se ocupe: `gcloud sql instances patch ensayos-db --activation-policy NEVER`
  - Volver a encenderla: `--activation-policy ALWAYS`
- **Cloud Run** cobra solo mientras la instancia está encendida, que es hasta unos 15 minutos después de la última petición.

## Actualizar el histórico de similitud

Cuando el compañero de IA cambie su histórico, reemplaza el contenido del bucket. Los archivos deben llamarse `{sha256 del archivo}.pdf`, el mismo hash que devuelve el motor en `document_hash`:

```bash
# Comprobar que cada nombre coincide con su hash (no debe imprimir nada)
for f in carpeta/*.pdf; do [ "$(shasum -a 256 "$f" | cut -c1-64).pdf" = "$(basename "$f")" ] || echo "MAL: $f"; done
# Sincronizar (borra del bucket lo que ya no está en la carpeta)
gcloud storage rsync --delete-unmatched-destination-objects carpeta gs://aplicacion-desarrollo-ensayos-similitud
```

No hace falta redesplegar el backend.
