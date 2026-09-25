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
| URL base (local) | `http://127.0.0.1:8080/api/v1` (emulador Android: `http://10.0.2.2:8080/api/v1`) |
| Formato | JSON (`Content-Type: application/json`) |
| Autenticación | `Authorization: Bearer <accessToken>` |

La cuenta de Google Cloud expiró, así que por ahora el backend solo corre en local (ver la sección "Base de datos local" más abajo).

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
| POST | `/submissions` | Alumno | `multipart/form-data` con `file` (PDF de 10 MB como máximo) y `assignmentId`. **201**: la entrega **ya calificada** (el proceso todavía es síncrono; ver C6) |
| GET | `/submissions/{id}` | Alumno dueño / Profesor del grupo | La entrega y su `reporte` |
| GET | `/submissions/{id}/grading` | Alumno dueño / Profesor del grupo | Solo el `reporte`. **404** si todavía no está calificada |
| PATCH | `/submissions/{id}/grading` | Profesor | `{ "criterio": "Introducción", "puntaje": 0.5 }`. Valida que el puntaje esté entre 0 y el máximo, recalcula la calificación final y guarda la auditoría |
| GET | `/submissions?assignmentId=1` | Profesor | Entregas de una tarea, cada una con los datos del `alumno` |
| GET | `/students/me/submissions` | Alumno | Historial, de la entrega más reciente a la más antigua |

**Errores al subir un ensayo:**

| Código | Causa |
|---|---|
| 415 | El archivo no es PDF (se revisa la extensión **y** el contenido) |
| 413 | El archivo pesa más de 10 MB |
| 422 | El PDF no tiene texto seleccionable (es un escaneo), está dañado o tiene contraseña (RN-IA-01) |
| 409 | La tarea no está abierta, ya cerró (RN-WEB-02), el ensayo ya fue calificado (RN-WEB-04) o se está calificando |
| 403 | El alumno no está inscrito en el grupo de la tarea |

**Estados de una entrega:** `EN_REVISION`, `CALIFICADO`, `POSIBLE_PLAGIO` o `ERROR`.
- Con `ERROR` (el motor de IA falló), el alumno **puede volver a enviar** su ensayo. La respuesta trae un campo `mensaje` que explica qué pasó.
- `POSIBLE_PLAGIO` **conserva la calificación** que dio el motor (RN-IA-03).

`reporte` = `{ calificacionFinal, calificacionMaxima, observacion, fechaEvaluacion, modificadoPorDocente, fechaModificacion?, posiblePlagio, banderas: { requiereRevisionDocente, faltaContextoIntro, abusoVinetas }, criterios: [{ criterio, puntajeObtenido, puntajeMaximo, detalles, modificadoPorDocente, puntajeIa? }] }`.
- Solo el profesor recibe además `similitudMaxima` y `coincidencias`.
- `puntajeIa` es el valor original del motor. Aparece solo en los criterios que el docente ajustó.

### Motor de IA

El backend reenvía el PDF, **con su nombre original**, al microservicio `POST /v1/grade`. Su respuesta se guarda completa en `calificaciones.reporte_json`.

- `AI_MODE=simulado` (**por defecto**): es un motor local que devuelve el mismo formato, sin enviar archivos a la nube.
  - Si el nombre del archivo contiene `plagio`, simula una alerta de plagio.
  - Si contiene `error`, simula una falla del motor.
- `AI_MODE=real ./run-local.sh`: usa el microservicio real en Cloud Run. Necesita `AI_BASE_URL` y `AI_API_KEY` en `env.yaml`.

---

## Cuentas de prueba (BD local, `seed_local.sql`)

| Correo | Contraseña | Rol |
|---|---|---|
| `alumno.prueba@alumno.ipn.mx` | `Prueba123` | ALUMNO |
| `profesor.prueba@ipn.mx` | `Prueba123` | PROFESOR |
| `admin.ensayos@ipn.mx` | — (OTP) | ADMINISTRADOR |

Con `MAIL_ENABLED=false ./run-local.sh` no se envían correos: el código se escribe en el log del backend (`[MAIL DESACTIVADO] ... | código: 123456`).
