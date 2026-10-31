-- Migración de datos para Cloud SQL: tres cuentas de administrador en lugar de una (correcciones_TT2.md J1).
-- Sin contraseña: cada una inicia sesión por OTP y el código le llega a su propio correo.
-- Se puede ejecutar más de una vez: INSERT IGNORE no duplica las cuentas que ya existen.
SET NAMES utf8mb4;
USE ensayos_db;

INSERT IGNORE INTO usuarios (correo, password_hash, nombre, apellidos, rol) VALUES
('porraszuniga@gmail.com',           NULL, 'Administrador', 'Sistema', 'ADMINISTRADOR'),
('joseeduardomayafabela@gmail.com',  NULL, 'Administrador', 'Sistema', 'ADMINISTRADOR');

SELECT correo, rol, estado FROM usuarios WHERE rol = 'ADMINISTRADOR';
