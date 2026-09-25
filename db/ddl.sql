-- DDL TT2 — Esquema completo (8 tablas)
-- Basado en la sección 4.7 de TT2_V1 (con las correcciones de correcciones_TT2.md, grupo B)
-- Orden de eliminación en cascada (RN-WEB-05): auditoría → calificaciones → ensayos →
-- inscripciones → tareas → grupos → usuario. Lo resuelven las FK con ON DELETE CASCADE.

CREATE DATABASE IF NOT EXISTS ensayos_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;
USE ensayos_db;

-- Tabla 59 — usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario     BIGINT          NOT NULL AUTO_INCREMENT,
    correo         VARCHAR(100)    NOT NULL,
    password_hash  VARCHAR(255)    NULL,          -- NULL solo para el administrador (OTP)
    nombre         VARCHAR(100)    NOT NULL,
    apellidos      VARCHAR(100)    NOT NULL,
    rol            ENUM('ALUMNO','PROFESOR','ADMINISTRADOR') NOT NULL,
    estado         ENUM('ACTIVA','SUSPENDIDA') NOT NULL DEFAULT 'ACTIVA',
    fecha_registro DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_usuario),
    UNIQUE KEY uq_usuarios_correo (correo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 60 — tokens (verificación de registro, OTP admin, refresh, recuperación)
CREATE TABLE IF NOT EXISTS tokens (
    id_token            BIGINT         NOT NULL AUTO_INCREMENT,
    id_usuario          BIGINT         NULL,      -- NULL mientras el registro está pendiente
    token               VARCHAR(255)   NOT NULL,  -- código de 6 dígitos o SHA-256 del refresh
    tipo                ENUM('VERIFICACION','OTP','REFRESH','RECUPERACION') NOT NULL,
    intentos            TINYINT        NOT NULL DEFAULT 0,
    utilizado           TINYINT(1)     NOT NULL DEFAULT 0,
    fecha_creacion      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion    DATETIME       NOT NULL,
    correo_pendiente    VARCHAR(100)   NULL,
    rol_pendiente       ENUM('ALUMNO','PROFESOR') NULL,
    nombre_pendiente    VARCHAR(100)   NULL,
    apellidos_pendiente VARCHAR(100)   NULL,
    PRIMARY KEY (id_token),
    KEY idx_tokens_tipo_token (tipo, token),
    KEY idx_tokens_correo_pendiente (correo_pendiente),
    CONSTRAINT fk_tokens_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 61 — grupos
CREATE TABLE IF NOT EXISTS grupos (
    id_grupo       BIGINT          NOT NULL AUTO_INCREMENT,
    id_profesor    BIGINT          NOT NULL,
    nombre         VARCHAR(100)    NOT NULL,
    codigo_acceso  CHAR(6)         NOT NULL,
    estado         ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    fecha_creacion DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_grupo),
    UNIQUE KEY uq_grupos_codigo (codigo_acceso),
    UNIQUE KEY uq_grupos_profesor_nombre (id_profesor, nombre),       -- RN-WEB-06
    CONSTRAINT fk_grupos_profesor
        FOREIGN KEY (id_profesor) REFERENCES usuarios (id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 62 — inscripciones (alumno ↔ grupo)
CREATE TABLE IF NOT EXISTS inscripciones (
    id_inscripcion    BIGINT       NOT NULL AUTO_INCREMENT,
    id_alumno         BIGINT       NOT NULL,
    id_grupo          BIGINT       NOT NULL,
    fecha_inscripcion DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_inscripcion),
    UNIQUE KEY uq_inscripciones_alumno_grupo (id_alumno, id_grupo),   -- RN-WEB-01
    CONSTRAINT fk_inscripciones_alumno
        FOREIGN KEY (id_alumno) REFERENCES usuarios (id_usuario)
        ON DELETE CASCADE,
    CONSTRAINT fk_inscripciones_grupo
        FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 63 — tareas
CREATE TABLE IF NOT EXISTS tareas (
    id_tarea       BIGINT          NOT NULL AUTO_INCREMENT,
    id_grupo       BIGINT          NOT NULL,
    nombre         VARCHAR(150)    NOT NULL,
    fecha_apertura DATETIME        NOT NULL,
    fecha_cierre   DATETIME        NOT NULL,
    fecha_creacion DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_tarea),
    UNIQUE KEY uq_tareas_grupo_nombre (id_grupo, nombre),              -- CU-WEB-05 E2
    CONSTRAINT chk_tareas_fechas CHECK (fecha_cierre > fecha_apertura), -- CU-WEB-05 E1
    CONSTRAINT fk_tareas_grupo
        FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 64 — ensayos (entregas)
CREATE TABLE IF NOT EXISTS ensayos (
    id_ensayo      BIGINT          NOT NULL AUTO_INCREMENT,
    id_alumno      BIGINT          NOT NULL,
    id_tarea       BIGINT          NOT NULL,
    nombre_archivo VARCHAR(255)    NOT NULL,
    ruta_archivo   VARCHAR(500)    NOT NULL,
    texto_extraido LONGTEXT        NULL,
    tamano_bytes   INT             NOT NULL,
    estado         ENUM('EN_REVISION','CALIFICADO','POSIBLE_PLAGIO','ERROR') NOT NULL DEFAULT 'EN_REVISION',
    fecha_entrega  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_ensayo),
    UNIQUE KEY uq_ensayos_alumno_tarea (id_alumno, id_tarea),          -- RN-IA-06
    CONSTRAINT chk_ensayos_tamano CHECK (tamano_bytes BETWEEN 1 AND 10485760), -- RN-IA-02
    CONSTRAINT fk_ensayos_alumno
        FOREIGN KEY (id_alumno) REFERENCES usuarios (id_usuario)
        ON DELETE CASCADE,
    CONSTRAINT fk_ensayos_tarea
        FOREIGN KEY (id_tarea) REFERENCES tareas (id_tarea)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 65 — calificaciones (1:1 con ensayos)
CREATE TABLE IF NOT EXISTS calificaciones (
    id_calificacion       BIGINT        NOT NULL AUTO_INCREMENT,
    id_ensayo             BIGINT        NOT NULL,
    calificacion_final    DECIMAL(4,2)  NOT NULL,
    similitud_plagio      DECIMAL(5,4)  NULL,
    reporte_json          JSON          NOT NULL,
    modificado_por_docente TINYINT(1)   NOT NULL DEFAULT 0,
    fecha_evaluacion      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion    DATETIME      NULL,
    PRIMARY KEY (id_calificacion),
    UNIQUE KEY uq_calificaciones_ensayo (id_ensayo),
    CONSTRAINT chk_calificaciones_rango CHECK (calificacion_final BETWEEN 0 AND 10), -- RN-IA-04
    CONSTRAINT fk_calificaciones_ensayo
        FOREIGN KEY (id_ensayo) REFERENCES ensayos (id_ensayo)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla 66 — auditoria_modificaciones (ajustes manuales del profesor, CU-WEB-02)
CREATE TABLE IF NOT EXISTS auditoria_modificaciones (
    id_auditoria        BIGINT        NOT NULL AUTO_INCREMENT,
    id_calificacion     BIGINT        NOT NULL,
    id_profesor         BIGINT        NOT NULL,
    criterio_modificado VARCHAR(100)  NOT NULL,
    valor_original      DECIMAL(4,2)  NOT NULL,
    valor_ajustado      DECIMAL(4,2)  NOT NULL,
    fecha_modificacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_auditoria),
    CONSTRAINT fk_auditoria_calificacion
        FOREIGN KEY (id_calificacion) REFERENCES calificaciones (id_calificacion)
        ON DELETE CASCADE,
    CONSTRAINT fk_auditoria_profesor
        FOREIGN KEY (id_profesor) REFERENCES usuarios (id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
