-- SQL Server. Additive, rerunnable migration; no financial tables or event IDs are changed.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.categorias_calendario','U') IS NULL
BEGIN
    CREATE TABLE dbo.categorias_calendario (
        usuario_id BIGINT NOT NULL,
        id VARCHAR(64) NOT NULL,
        nombre NVARCHAR(80) NOT NULL,
        color VARCHAR(7) NOT NULL,
        nombre_clave NVARCHAR(100) NOT NULL,
        activa BIT NOT NULL,
        recuperada BIT NOT NULL,
        CONSTRAINT pk_categorias_calendario PRIMARY KEY (usuario_id,id),
        CONSTRAINT fk_categorias_calendario_usuario FOREIGN KEY (usuario_id) REFERENCES dbo.usuarios(id),
        CONSTRAINT uq_categoria_calendario_nombre UNIQUE (usuario_id,nombre_clave),
        CONSTRAINT ck_categoria_calendario_color CHECK (color LIKE '#[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]')
    );
END;
-- Recover every key already referenced by an event, even if its original browser is unavailable.
-- Browser import can subsequently restore the original name/color on recovered rows only.
INSERT INTO dbo.categorias_calendario (usuario_id,id,nombre,color,nombre_clave,activa,recuperada)
SELECT DISTINCT e.usuario_id,e.categoria_id,
    CASE e.categoria_id WHEN 'meeting' THEN N'Reunión' WHEN 'payment' THEN N'Pago de tarjeta'
        WHEN 'task' THEN N'Tarea' WHEN 'event' THEN N'Evento' ELSE N'Categoría recuperada '+LEFT(e.categoria_id,12) END,
    CASE e.categoria_id WHEN 'payment' THEN '#ef3340' WHEN 'task' THEN '#f0c419' WHEN 'event' THEN '#f28c28' ELSE '#16a06a' END,
    CASE e.categoria_id WHEN 'meeting' THEN N'reunión' WHEN 'payment' THEN N'pago de tarjeta'
        WHEN 'task' THEN N'tarea' WHEN 'event' THEN N'evento' ELSE '~recuperada:'+e.categoria_id END,
    1,1
FROM dbo.eventos_calendario e
WHERE e.categoria_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM dbo.categorias_calendario c WHERE c.usuario_id=e.usuario_id AND c.id=e.categoria_id
);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='fk_evento_categoria_usuario')
    ALTER TABLE dbo.eventos_calendario WITH CHECK ADD CONSTRAINT fk_evento_categoria_usuario
        FOREIGN KEY (usuario_id,categoria_id) REFERENCES dbo.categorias_calendario(usuario_id,id);
COMMIT;
