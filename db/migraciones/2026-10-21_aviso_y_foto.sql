-- Migración para bases ya creadas con la versión anterior de ddl.sql (local y Cloud SQL).
-- Aviso de privacidad aceptado al registrarse y foto de perfil.
USE ensayos_db;

ALTER TABLE usuarios
    ADD COLUMN aviso_privacidad_version VARCHAR(20) NULL AFTER fecha_registro,
    ADD COLUMN aviso_privacidad_fecha   DATETIME    NULL AFTER aviso_privacidad_version,
    ADD COLUMN foto_perfil              VARCHAR(500) NULL AFTER aviso_privacidad_fecha;
