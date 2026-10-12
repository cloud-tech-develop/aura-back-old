-- V198 — Acción especial "Editar datos de la empresa" (caja.usuarios:EDITAR_EMPRESA).
--
-- Hasta hoy la resolución DIAN, los rangos de Factus y el logo solo se cambiaban
-- desde la plataforma. Con esta acción el administrador de cada empresa los
-- actualiza desde "Mi perfil". hereda_de NULL: al subir esto solo la tienen los
-- perfiles de acceso total (Administrador); el SUPER_ADMIN la tiene por rol.
-- Las credenciales de Factus siguen siendo solo de la plataforma.

INSERT INTO permiso_accion_especial (submodulo_id, codigo, nombre, descripcion, hereda_de, orden)
SELECT s.id, 'EDITAR_EMPRESA', 'Editar datos de la empresa',
       'Cambiar nombre comercial, logo, contacto, resolución de facturación y rangos de numeración',
       NULL, 2
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = 'usuarios'
WHERE m.codigo = 'caja'
ON CONFLICT (submodulo_id, codigo) DO NOTHING;
