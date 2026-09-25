-- Datos de prueba — SOLO para desarrollo local. No ejecutar en producción.
-- Contraseña de las cuentas de prueba: Prueba123
SET NAMES utf8mb4;
USE ensayos_db;

-- RN-AUTH-05: única cuenta de administrador, sin contraseña (inicia sesión por OTP).
-- Cambiar el correo por uno real antes de probar el OTP.
INSERT INTO usuarios (correo, password_hash, nombre, apellidos, rol)
VALUES ('admin.ensayos@ipn.mx', NULL, 'Administrador', 'Sistema', 'ADMINISTRADOR');

INSERT INTO usuarios (correo, password_hash, nombre, apellidos, rol) VALUES
('profesor.prueba@ipn.mx',
 '$2a$10$h9u2vzWq1QzhmlSPTKDZ5.Hndz84fHshi0ZHrD357/4li63E1Kohy', 'Profesor', 'Prueba', 'PROFESOR'),
('alumno.prueba@alumno.ipn.mx',
 '$2a$10$h9u2vzWq1QzhmlSPTKDZ5.Hndz84fHshi0ZHrD357/4li63E1Kohy', 'Alumno', 'Prueba', 'ALUMNO');

-- Grupo activo con código para probar "Unirse a grupo" (CU-ALU-01)
INSERT INTO grupos (id_profesor, nombre, codigo_acceso)
SELECT id_usuario, 'Sistemas Operativos 3CM1', 'SO3CM1'
FROM usuarios WHERE correo = 'profesor.prueba@ipn.mx';

-- Grupo inactivo para probar la excepción E2 de CU-ALU-01
INSERT INTO grupos (id_profesor, nombre, codigo_acceso, estado)
SELECT id_usuario, 'Sistemas Operativos 3CM2', 'SO3CM2', 'INACTIVO'
FROM usuarios WHERE correo = 'profesor.prueba@ipn.mx';

-- Tarea abierta (desde ayer hasta dentro de 30 días) y tarea ya cerrada (RN-WEB-02)
INSERT INTO tareas (id_grupo, nombre, fecha_apertura, fecha_cierre)
SELECT id_grupo, 'Ensayo Unidad 1', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY
FROM grupos WHERE codigo_acceso = 'SO3CM1';

INSERT INTO tareas (id_grupo, nombre, fecha_apertura, fecha_cierre)
SELECT id_grupo, 'Ensayo Diagnóstico', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 5 DAY
FROM grupos WHERE codigo_acceso = 'SO3CM1';

-- El alumno de prueba ya está inscrito en SO3CM1 (las pruebas de integración de la app lo requieren)
INSERT INTO inscripciones (id_alumno, id_grupo)
SELECT u.id_usuario, g.id_grupo
FROM usuarios u JOIN grupos g ON g.codigo_acceso = 'SO3CM1'
WHERE u.correo = 'alumno.prueba@alumno.ipn.mx';
