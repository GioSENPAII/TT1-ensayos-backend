-- Datos iniciales de la nube: administrador real + cuentas y grupo de prueba.
-- Contraseña de las cuentas de prueba: Prueba123
SET NAMES utf8mb4;
USE ensayos_db;

-- RN-AUTH-05: única cuenta de administrador, sin contraseña (inicia sesión por OTP).
-- El OTP de cada inicio de sesión llega a este correo.
INSERT INTO usuarios (correo, password_hash, nombre, apellidos, rol)
VALUES ('glongoria.3a.is@gmail.com', NULL, 'Administrador', 'Sistema', 'ADMINISTRADOR');

INSERT INTO usuarios (correo, password_hash, nombre, apellidos, rol) VALUES
('profesor.prueba@ipn.mx',
 '$2a$10$h9u2vzWq1QzhmlSPTKDZ5.Hndz84fHshi0ZHrD357/4li63E1Kohy', 'Profesor', 'Prueba', 'PROFESOR'),
('alumno.prueba@alumno.ipn.mx',
 '$2a$10$h9u2vzWq1QzhmlSPTKDZ5.Hndz84fHshi0ZHrD357/4li63E1Kohy', 'Alumno', 'Prueba', 'ALUMNO');

-- Un grupo con dos tareas abiertas; el alumno de prueba ya está inscrito
INSERT INTO grupos (id_profesor, nombre, codigo_acceso)
SELECT id_usuario, 'Sistemas Operativos 3CM1', 'SO3CM1'
FROM usuarios WHERE correo = 'profesor.prueba@ipn.mx';

INSERT INTO tareas (id_grupo, nombre, fecha_apertura, fecha_cierre)
SELECT id_grupo, 'Ensayo Unidad 1', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY
FROM grupos WHERE codigo_acceso = 'SO3CM1';

INSERT INTO tareas (id_grupo, nombre, fecha_apertura, fecha_cierre)
SELECT id_grupo, 'Ensayo Unidad 2', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 15 DAY
FROM grupos WHERE codigo_acceso = 'SO3CM1';

INSERT INTO inscripciones (id_alumno, id_grupo)
SELECT u.id_usuario, g.id_grupo
FROM usuarios u JOIN grupos g ON g.codigo_acceso = 'SO3CM1'
WHERE u.correo = 'alumno.prueba@alumno.ipn.mx';
