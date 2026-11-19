-- DATOS-USR-H01: fecha en que el usuario acepto la politica de tratamiento de datos personales.
-- Queda vacia para las cuentas creadas antes de esta migracion.
ALTER TABLE usuario ADD COLUMN fecha_aceptacion_politica TIMESTAMP;
