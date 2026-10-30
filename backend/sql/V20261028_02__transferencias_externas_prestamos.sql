-- SQL Server. No outgoing payments, income or balance changes are created by this migration.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.prestamos_personales','U') IS NULL
BEGIN
    CREATE TABLE dbo.prestamos_personales (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        usuario_id BIGINT NOT NULL,
        acreedor NVARCHAR(120) NOT NULL,
        saldo_inicial DECIMAL(18,2) NOT NULL,
        saldo_pendiente DECIMAL(18,2) NOT NULL,
        tasa_interes DECIMAL(8,3) NOT NULL,
        fecha_registro DATETIME2 NOT NULL,
        referencia_cliente VARCHAR(36) NOT NULL,
        CONSTRAINT fk_prestamo_usuario FOREIGN KEY(usuario_id) REFERENCES dbo.usuarios(id),
        CONSTRAINT uq_prestamo_usuario_id UNIQUE(usuario_id,id),
        CONSTRAINT uq_prestamo_referencia UNIQUE(usuario_id,referencia_cliente),
        CONSTRAINT ck_prestamo_saldo CHECK(saldo_inicial>0 AND saldo_pendiente>=0 AND saldo_pendiente<=saldo_inicial),
        CONSTRAINT ck_prestamo_sin_interes CHECK(tasa_interes=0)
    );
END;
IF COL_LENGTH('dbo.movimientos','destinatario_externo') IS NULL ALTER TABLE dbo.movimientos ADD destinatario_externo NVARCHAR(120) NULL;
IF COL_LENGTH('dbo.movimientos','prestamo_id') IS NULL ALTER TABLE dbo.movimientos ADD prestamo_id BIGINT NULL;
IF COL_LENGTH('dbo.movimientos','referencia_cliente') IS NULL ALTER TABLE dbo.movimientos ADD referencia_cliente VARCHAR(36) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='uq_movimiento_referencia' AND object_id=OBJECT_ID('dbo.movimientos'))
    EXEC('CREATE UNIQUE INDEX uq_movimiento_referencia ON dbo.movimientos(usuario_id,referencia_cliente) WHERE referencia_cliente IS NOT NULL');
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='fk_movimiento_prestamo_usuario')
    EXEC('ALTER TABLE dbo.movimientos WITH CHECK ADD CONSTRAINT fk_movimiento_prestamo_usuario FOREIGN KEY(usuario_id,prestamo_id) REFERENCES dbo.prestamos_personales(usuario_id,id)');
DECLARE @oldConstraint SYSNAME;
DECLARE @dropSql NVARCHAR(MAX);
DECLARE constraints_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT name FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.movimientos')
    AND definition LIKE '%tipo%' AND definition LIKE '%INGRESO%' AND definition LIKE '%GASTO%'
    AND definition LIKE '%PAGO_TARJETA%' AND definition NOT LIKE '%monto%'
    AND definition NOT LIKE '%cuenta_%' AND name<>'ck_movimiento_tipo_v2';
OPEN constraints_cursor; FETCH NEXT FROM constraints_cursor INTO @oldConstraint;
WHILE @@FETCH_STATUS=0 BEGIN
    SET @dropSql=N'ALTER TABLE dbo.movimientos DROP CONSTRAINT '+QUOTENAME(@oldConstraint);
    EXEC sp_executesql @dropSql;
    FETCH NEXT FROM constraints_cursor INTO @oldConstraint;
END;
CLOSE constraints_cursor; DEALLOCATE constraints_cursor;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='ck_movimiento_tipo_v2')
    ALTER TABLE dbo.movimientos WITH CHECK ADD CONSTRAINT ck_movimiento_tipo_v2 CHECK(tipo IN('INGRESO','GASTO','TRANSFERENCIA','PAGO_TARJETA','PAGO_PRESTAMO'));
COMMIT;
