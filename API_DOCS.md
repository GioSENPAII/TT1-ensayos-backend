# Documentación de la API — Ensayos ESCOM-IPN

> **Cambios recientes (autenticación TT2):**
> - `verify-token` ahora **requiere `correo`**.
> - El `refreshToken` ya no es un JWT: es una cadena opaca de un solo uso. Cada renovación devuelve uno nuevo.
> - Nuevos endpoints: `resend-token`, `refresh-token`, `logout`, `forgot-password`, `reset-password`, `admin/request-otp` y `admin/verify-otp`.
> - Contraseñas: mínimo 8 caracteres, con al menos una mayúscula, una minúscula y un número.
> - Los errores usan el formato RFC 9457 y siguen incluyendo el campo `error`.

## Información general

| Dato | Valor |
|------|-------|
| URL base (nube) | `https://ensayos-backend-990972460164.northamerica-south1.run.app/api/v1` |
| URL base (local) | `http://127.0.0.1:8080/api/v1` (emulador Android: `http://10.0.2.2:8080/api/v1`) |
| Formato | JSON (`Content-Type: application/json`) |
| Autenticación | `Authorization: Bearer <accessToken>` |

El despliegue en la nube está documentado en `deploy/README.md`. En la nube, el administrador es `glongoria.3a.is@gmail.com`; en local, `admin.ensayos@ipn.mx`.

### Formato de errores (RFC 9457)

Todos los errores tienen esta forma. El campo `error` repite `detail`, para compatibilidad con los clientes que ya existían:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Código incorrecto. Te quedan 4 intentos.",
  "instance": "/api/v1/auth/verify-token",
  "error": "Código incorrecto. Te quedan 4 intentos."
}
```

En los errores de validación se agrega `campos`, con el mensaje de cada campo inválido: `{"campos": {"password": "La contraseña debe tener..."}}`.

| Código | Cuándo |
|---|---|
| 400 | Datos inválidos, código incorrecto o expirado |
| 401 | Credenciales incorrectas, token ausente, inválido o expirado |
| 403 | Cuenta suspendida o rol sin permiso |
| 404 | Recurso inexistente |
| 409 | El correo ya tiene cuenta |
| 429 | Reenvío de código antes de 60 s |
| 503 | Falla el servicio de correo |

### Códigos de 6 dígitos

Se usan para la verificación de registro (15 min), el OTP del administrador (10 min) y la recuperación de contraseña (15 min).
- Son de **un solo uso**.
- Admiten **máximo 5 intentos**; después quedan invalidados y hay que pedir otro.
- Pedir un código nuevo invalida el anterior.

---

## Registro (CU-AUTH-01 / CU-AUTH-02)

### POST `/auth/register` — paso 1: enviar código
```json
{ "nombre": "Juan", "apellidos": "Pérez López", "correo": "juan.perez@alumno.ipn.mx", "rol": "ALUMNO" }
```
- `rol` puede ser `ALUMNO` (correo `@alumno.ipn.mx`) o `PROFESOR` (correo `@ipn.mx`).
- **200:** `{ "message": "Código de verificación enviado a juan.perez@alumno.ipn.mx" }`
- **400:** el dominio no corresponde al rol. **409:** el correo ya está registrado.

### POST `/auth/resend-token` — reenviar código (E3)
```json
{ "correo": "juan.perez@alumno.ipn.mx" }
```
- **200:** código reenviado.
- **404:** no hay un registro pendiente.
- **429:** esperar 60 s entre reenvíos.

### POST `/auth/verify-token` — paso 2: verificar y crear cuenta
```json
{ "correo": "juan.perez@alumno.ipn.mx", "token": "482910", "password": "MiContrasena123" }
```
- **200:** respuesta de sesión (ver abajo).
- **400:** código incorrecto o expirado, intentos agotados, o contraseña fuera de la política.

---

## Sesión

### POST `/auth/login` (CU-AUTH-04)
```json
{ "correo": "juan.perez@alumno.ipn.mx", "password": "MiContrasena123" }
```
**200 — respuesta de sesión** (la misma en `verify-token`, `admin/verify-otp` y `refresh-token`):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "pPBqsl7Fcp5vwVR8Hqb3ZRdfpilIkoIbHlT15wpaIyU",
  "nombre": "Juan",
  "correo": "juan.perez@alumno.ipn.mx",
  "rol": "ALUMNO"
}
```
- **401:** `"Correo o contraseña incorrectos"`. El mensaje es genérico a propósito.
- **403:** `"Tu cuenta ha sido suspendida. Contacta al administrador."`

### POST `/auth/refresh-token` — renovar la sesión
```json
{ "refreshToken": "pPBqsl7F..." }
```
- **200:** respuesta de sesión con un **nuevo par** de tokens. El refresh anterior deja de servir.
- **401:** el token es inválido o expiró. Si alguien presenta un refresh que **ya se había usado**, se cierran **todas** las sesiones del usuario (protección contra robo de tokens).
- **403:** la cuenta fue suspendida.

Úsalo cuando cualquier endpoint responda **401**: renueva la sesión y repite la petición original. Si la renovación también falla, manda al usuario al login.

### POST `/auth/logout` (CU-AUTH-05)
```json
{ "refreshToken": "pPBqsl7F..." }
```
- **204** sin cuerpo.
- Borra los tokens guardados en el cliente aunque la petición falle.
- El access token sigue siendo válido hasta que expire (15 min como máximo).

---

## Recuperar contraseña (CU-AUTH-06)

### POST `/auth/forgot-password`
```json
{ "correo": "juan.perez@alumno.ipn.mx" }
```
**200** siempre, con `"Si el correo está registrado, recibirás un código de recuperación."` Nunca revela si la cuenta existe.

### POST `/auth/reset-password`
```json
{ "correo": "juan.perez@alumno.ipn.mx", "codigo": "731045", "password": "NuevaContra1" }
```
- **200:** contraseña actualizada. Además se cierran todas las sesiones abiertas.
- **400:** código inválido o contraseña fuera de la política.

---

## Administrador por OTP (CU-AUTH-03)

### POST `/auth/admin/request-otp`
```json
{ "correo": "admin.ensayos@ipn.mx" }
```
**200** siempre, con `"Si el correo corresponde al administrador, recibirás un código de acceso."`

### POST `/auth/admin/verify-otp`
```json
{ "correo": "admin.ensayos@ipn.mx", "otp": "904213" }
```
- **200:** respuesta de sesión con `rol: "ADMINISTRADOR"`.
- **400:** código inválido o expirado.

El administrador **no puede** entrar por `/auth/login`, porque no tiene contraseña.

---

## Endpoints protegidos

Incluye en cada petición: `Authorization: Bearer <accessToken>`.

- **Access token:** JWT HS256 que dura 15 minutos. Claims: `sub` (correo), `rol` (`ROLE_ALUMNO`, `ROLE_PROFESOR` o `ROLE_ADMINISTRADOR`), `correo` y `nombre`.
- **Refresh token:** dura 15 días. Es opaco y solo sirve para `/auth/refresh-token` y `/auth/logout`.
- **Control por rol:**
  - `/students/me/**` → solo Alumno.
  - `/users/**` → solo Administrador.
  - Sin token → **401**. Con un rol no permitido → **403**.

---

## Grupos (CU-WEB-01, CU-WEB-04, CU-ALU-01)

Las fechas usan el formato ISO local, sin zona horaria, por ejemplo `"2026-10-10T23:59:00"`. Todas están en hora del centro de México.

### Profesor
| Método | Endpoint | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/groups` | `{ "nombre": "SO 3CM1" }` | **201** grupo creado. El código de 6 caracteres lo genera el servidor. **409** si ya tienes un grupo con ese nombre |
| GET | `/groups` | — | **200** `[grupo]` con tus grupos |
| GET | `/groups/{id}` | — | **200** grupo. **403** si el grupo no es tuyo. **404** si no existe |
| PATCH | `/groups/{id}/status` | `{ "estado": "INACTIVO" }` | **200** grupo. Un grupo `INACTIVO` no acepta nuevas inscripciones (RN-WEB-01) |
| DELETE | `/groups/{id}` | — | **204**. Borra en cascada tareas, entregas y calificaciones |
| GET | `/groups/{id}/students` | — | **200** `[{ id, nombre, apellidos, correo, fechaInscripcion }]` en orden alfabético |
| DELETE | `/groups/{id}/students/{studentId}` | — | **204**. Saca al alumno del grupo y borra sus entregas en ese grupo |

`grupo` = `{ id, nombre, codigoAcceso, estado: "ACTIVO"|"INACTIVO", fechaCreacion, totalAlumnos, totalTareas }`

### Alumno
| Método | Endpoint | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/students/me/groups` | — | **200** `[{ id, nombre, profesor, estado, fechaInscripcion }]` |
| POST | `/groups/join` | `{ "codigo": "SO3CM1" }` | **201** el grupo. **404** si el código no es válido. **409** si el grupo está inactivo o ya eres parte de él |

## Tareas (CU-WEB-05)

| Método | Endpoint | Rol | Cuerpo / notas |
|---|---|---|---|
| POST | `/assignments` | Profesor | `{ groupId, nombre, fechaApertura, fechaCierre }` → **201**. **400** si el cierre no es posterior a la apertura. **409** si el nombre se repite en el grupo |
| GET | `/assignments?groupId=1` | Profesor / Alumno | Profesor: todas las tareas del grupo, con `tieneEntregas`. Alumno inscrito: solo las que ya abrieron, con `entrega` y `pendiente`. **403** si el alumno no está inscrito |
| PUT | `/assignments/{id}` | Profesor | Mismo cuerpo que POST, sin `groupId`. Si ya hay entregas, solo se puede **extender** `fechaCierre`; cualquier otro cambio responde **409** |
| DELETE | `/assignments/{id}` | Profesor | **204**. Borra la tarea y sus entregas |

`tarea` = `{ id, groupId, grupo, nombre, fechaApertura, fechaCierre, disponibilidad: "PROXIMA"|"ABIERTA"|"CERRADA", tieneEntregas?, entrega?: { id, estado, fechaEntrega }, pendiente? }`. Si no aparece `entrega` en la vista del alumno, significa "Sin entregar".

El administrador recibe **403** en grupos y tareas (RN-WEB-03).

---

## Entregas y calificaciones (CU-ALU-02/03/04, CU-WEB-02)

| Método | Endpoint | Rol | Notas |
|---|---|---|---|
| POST | `/submissions` | Alumno | `multipart/form-data` con `file` (PDF de 10 MB como máximo) y `assignmentId`. Responde **202 Accepted** en menos de 1 s, con la entrega en `EN_REVISION` y el encabezado `Location`. **La calificación se hace en segundo plano**: consulta `GET /submissions/{id}` cada 2 o 3 s hasta que el estado cambie |
| GET | `/submissions/{id}` | Alumno dueño / Profesor del grupo | La entrega y su `reporte` |
| GET | `/submissions/{id}/grading` | Alumno dueño / Profesor del grupo | Solo el `reporte`. **404** si todavía no está calificada |
| PATCH | `/submissions/{id}/grading` | Profesor | `{ "criterio": "Introducción", "puntaje": 0.5 }`. Valida que el puntaje esté entre 0 y el máximo, recalcula la calificación final y guarda la auditoría |
| GET | `/submissions?assignmentId=1` | Profesor | Entregas de una tarea, cada una con los datos del `alumno` |
| GET | `/submissions/{id}/file` | Profesor del grupo | Descarga el **PDF que subió el alumno**, en cualquier estado |
| GET | `/submissions/{id}/similar-file` | Profesor del grupo | Solo si `posiblePlagio` es `true`: descarga el **ensayo del histórico** con mayor similitud |
| GET | `/students/me/submissions` | Alumno | Historial, de la entrega más reciente a la más antigua |

**Errores al subir un ensayo:**

| Código | Causa |
|---|---|
| 415 | El archivo no es PDF (se revisa la extensión **y** el contenido) |
| 413 | El archivo pesa más de 10 MB |
| 422 | El PDF no tiene texto seleccionable (es un escaneo), está dañado o tiene contraseña (RN-IA-01) |
| 409 | La tarea no está abierta, ya cerró (RN-WEB-02), el ensayo ya fue calificado (RN-WEB-04) o se está calificando |
| 403 | El alumno no está inscrito en el grupo de la tarea |

**Estados de una entrega:** `EN_REVISION` → `CALIFICADO`, `POSIBLE_PLAGIO` o `ERROR`.
- **Calificación asíncrona (RNF-09):** con la IA activa tarda unos 2 o 3 s; si estaba inactiva (arranque en frío), unos 30 s.
- El backend reintenta las fallas transitorias de la IA (hasta 3 veces). Si la IA falla repetidamente, un *circuit breaker* marca las entregas como `ERROR` de inmediato durante 30 s.
- Si el backend se reinicia, retoma solo las entregas que quedaron en `EN_REVISION`.
- Con `ERROR` (el motor de IA falló), el alumno **puede volver a enviar** su ensayo. La respuesta trae un campo `mensaje` que explica qué pasó.
- `POSIBLE_PLAGIO` **conserva la calificación** que dio el motor (RN-IA-03).
- **Regla de plagio del backend:** hay alerta solo si la **primera coincidencia** que devuelve el motor tiene una similitud **mayor a 0.98**. Se configura con `app.ia.umbral-plagio`. La bandera `detectado` del motor, que usa 0.92, no se toma en cuenta.
  - Cuando no se supera el umbral, `posiblePlagio` es `false`.
  - Además se omite la observación del motor sobre similitud histórica y `requiereRevisionDocente` queda en `false` si el motor solo lo pedía por plagio.

`reporte` = `{ calificacionFinal, calificacionMaxima, observacion, fechaEvaluacion, modificadoPorDocente, fechaModificacion?, posiblePlagio, banderas: { requiereRevisionDocente, faltaContextoIntro, abusoVinetas }, criterios: [{ criterio, puntajeObtenido, puntajeMaximo, nivel, detalles, detallesMotor, modificadoPorDocente, puntajeIa? }] }`.
- `nivel` puede ser `ALTO` (100 %), `MEDIO` (parcial) o `BAJO` (0 %).
- `detalles` es la **descripción de la Tabla 14** (rúbrica institucional) que corresponde a ese nivel. Muéstrala tal cual en la web, como hace la app (RNF-14).
- `detallesMotor` es el texto técnico que devolvió el motor de IA (por ejemplo "Evaluado por CU-IA-09").
- Solo el profesor recibe además `similitudMaxima` y `coincidencias`.
- `puntajeIa` es el valor original del motor. Aparece solo en los criterios que el docente ajustó.

### Descarga de PDFs (profesor)

En el detalle de una entrega:
- Siempre muestra el botón **"Descargar ensayo"** → `GET /submissions/{id}/file`.
- Si `reporte.posiblePlagio` es `true`, muestra también **"Descargar ensayo similar"** → `GET /submissions/{id}/similar-file`.

Ambos responden `200` con `Content-Type: application/pdf` y el encabezado `Content-Disposition: attachment; filename="..."`:
- `/file` usa el nombre original del archivo. Si tiene acentos o `ñ`, viene también en `filename*` (UTF-8).
- `/similar-file` usa `{document_hash}.pdf`, el hash de `coincidencias[0]` (la de mayor similitud).

El navegador **no** puede leer los buckets directamente (son privados): la descarga siempre pasa por el backend con el token. Con Axios:

```js
const res = await api.get(`/submissions/${id}/similar-file`, { responseType: 'blob' });
const cd = res.headers['content-disposition'] ?? '';
const utf8 = /filename\*=UTF-8''([^;]+)/.exec(cd)?.[1];           // nombres con acentos
const nombre = utf8 ? decodeURIComponent(utf8) : /filename="([^"]+)"/.exec(cd)?.[1] ?? 'ensayo.pdf';
const url = URL.createObjectURL(res.data);
const a = Object.assign(document.createElement('a'), { href: url, download: nombre });
a.click();
URL.revokeObjectURL(url);
```

| Código | Causa |
|---|---|
| 403 | No es el profesor del grupo de la entrega (o el rol no es Profesor) |
| 404 | La entrega no existe · el PDF no está disponible · `similar-file` en una entrega sin alerta de plagio · el histórico no tiene el ensayo con ese hash |

Con `responseType: 'blob'`, el cuerpo del error también llega como `Blob`; léelo con `JSON.parse(await err.response.data.text())`.

**Histórico de similitud:** los ensayos con los que compara el motor de IA se guardan en el bucket `gs://aplicacion-desarrollo-ensayos-similitud` como `{sha256}.pdf` (en local, en `data/similitud/`). Solo se comparan contra ese histórico: un ensayo copiado de otro alumno del sistema **no** se detecta (limitación conocida).

### Motor de IA

El backend reenvía el PDF, **con su nombre original**, al microservicio `POST /v1/grade`. Su respuesta se guarda completa en `calificaciones.reporte_json`.

- `AI_MODE=simulado` (**por defecto**): es un motor local que devuelve el mismo formato, sin enviar archivos a la nube.
  - Si el nombre del archivo contiene `plagio`, simula una alerta de plagio.
  - Si contiene `error`, simula una falla del motor.
- `AI_MODE=real ./run-local.sh`: usa el microservicio real en Cloud Run. Necesita `AI_BASE_URL` y `AI_API_KEY` en `env.yaml`.

---

## Administrador (CU-WEB-03, RF-ADM-01 a 03)

Todos requieren el token de una sesión de **administrador** (obtenida con `admin/verify-otp`). Cualquier otro rol recibe **403**. El administrador **nunca** ve ensayos ni calificaciones (RN-WEB-03), solo conteos.

| Método | Endpoint | Notas |
|---|---|---|
| GET | `/admin/metrics` | `{ alumnos, profesores, cuentasActivas, cuentasSuspendidas, gruposActivos, ensayosProcesados, ensayosEnRevision }` |
| GET | `/users?rol=&estado=&q=&page=0&size=20` | Directorio **paginado** de alumnos y profesores. `rol` puede ser `ALUMNO` o `PROFESOR`; `estado`, `ACTIVA` o `SUSPENDIDA`; `q` busca en el correo o el nombre. Devuelve `{ contenido: [cuenta], pagina, tamano, totalElementos, totalPaginas }` |
| GET | `/users/{id}` | La cuenta y su `impacto`, para el diálogo de confirmación antes de eliminar: `{ inscripciones?, grupos?, tareas?, entregas, advertencia }`. `advertencia` trae el texto de CU-WEB-03 4c/4d con los números reales |
| PATCH | `/users/{id}/status` | `{ "estado": "SUSPENDIDA" }` o `{ "estado": "ACTIVA" }`. Al suspender, **se cierran las sesiones abiertas** de esa cuenta |
| DELETE | `/users/{id}` | **204**. Elimina en cascada y en una sola transacción. Profesor: sus grupos, tareas, entregas y calificaciones; los alumnos conservan sus cuentas. Alumno: sus inscripciones, entregas y calificaciones. Los PDF se borran del disco |

`cuenta` = `{ id, correo, nombre, apellidos, rol, estado, fechaRegistro }`

La cuenta del administrador **no se puede consultar, suspender ni eliminar** desde estos endpoints (**403**, CU-WEB-03 E1).

---

## Cuentas de prueba (BD local, `seed_local.sql`)

| Correo | Contraseña | Rol |
|---|---|---|
| `alumno.prueba@alumno.ipn.mx` | `Prueba123` | ALUMNO |
| `profesor.prueba@ipn.mx` | `Prueba123` | PROFESOR |
| `admin.ensayos@ipn.mx` | — (OTP) | ADMINISTRADOR |

Con `MAIL_ENABLED=false ./run-local.sh` no se envían correos: el código se escribe en el log del backend (`[MAIL DESACTIVADO] ... | código: 123456`).
