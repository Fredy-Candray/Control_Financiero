-- SQL Server local. Additive migration; no balances or existing movement types change.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='uq_cuenta_usuario_id' AND object_id=OBJECT_ID('dbo.cuentas'))
    CREATE UNIQUE INDEX uq_cuenta_usuario_id ON dbo.cuentas(usuario_id,id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='uq_movimiento_usuario_id' AND object_id=OBJECT_ID('dbo.movimientos'))
    CREATE UNIQUE INDEX uq_movimiento_usuario_id ON dbo.movimientos(usuario_id,id);
IF OBJECT_ID('dbo.prestamos_otorgados','U') IS NULL
BEGIN
    CREATE TABLE dbo.prestamos_otorgados (
        id BIGINT IDENTITY PRIMARY KEY, usuario_id BIGINT NOT NULL,
        persona NVARCHAR(120) NOT NULL, monto DECIMAL(18,2) NOT NULL, pendiente DECIMAL(18,2) NOT NULL,
        fecha DATETIME2(6) NOT NULL, vencimiento DATE NULL, nota VARCHAR(255) NULL,
        movimiento_origen_id BIGINT NULL, referencia_cliente VARCHAR(36) NOT NULL,
        CONSTRAINT fk_otorgado_usuario FOREIGN KEY(usuario_id) REFERENCES dbo.usuarios(id),
        CONSTRAINT uq_otorgado_usuario UNIQUE(usuario_id,id),
        CONSTRAINT uq_otorgado_referencia UNIQUE(usuario_id,referencia_cliente),
        CONSTRAINT ck_otorgado_montos CHECK(monto>0 AND pendiente>=0 AND pendiente<=monto),
        CONSTRAINT ck_otorgado_fecha CHECK(vencimiento IS NULL OR vencimiento>=CONVERT(date,fecha)),
        CONSTRAINT fk_otorgado_movimiento FOREIGN KEY(usuario_id,movimiento_origen_id) REFERENCES dbo.movimientos(usuario_id,id)
    );
    CREATE UNIQUE INDEX uq_otorgado_movimiento ON dbo.prestamos_otorgados(movimiento_origen_id) WHERE movimiento_origen_id IS NOT NULL;
END;
IF COL_LENGTH('dbo.movimientos','prestamo_otorgado_id') IS NULL ALTER TABLE dbo.movimientos ADD prestamo_otorgado_id BIGINT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='fk_movimiento_otorgado_usuario')
    EXEC('ALTER TABLE dbo.movimientos WITH CHECK ADD CONSTRAINT fk_movimiento_otorgado_usuario FOREIGN KEY(usuario_id,prestamo_otorgado_id) REFERENCES dbo.prestamos_otorgados(usuario_id,id)');
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='ck_movimiento_tipo_v2') ALTER TABLE dbo.movimientos DROP CONSTRAINT ck_movimiento_tipo_v2;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='ck_movimiento_tipo_v3')
    ALTER TABLE dbo.movimientos WITH CHECK ADD CONSTRAINT ck_movimiento_tipo_v3 CHECK(tipo IN('INGRESO','GASTO','TRANSFERENCIA','PAGO_TARJETA','PAGO_PRESTAMO','PRESTAMO_OTORGADO','COBRO_PRESTAMO'));
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='ck_movimiento_otorgado_tipo')
    EXEC('ALTER TABLE dbo.movimientos WITH CHECK ADD CONSTRAINT ck_movimiento_otorgado_tipo CHECK((tipo IN(''PRESTAMO_OTORGADO'',''COBRO_PRESTAMO'') AND prestamo_otorgado_id IS NOT NULL) OR (tipo NOT IN(''PRESTAMO_OTORGADO'',''COBRO_PRESTAMO'') AND prestamo_otorgado_id IS NULL))');
IF OBJECT_ID('dbo.conciliaciones_saldo','U') IS NULL
    CREATE TABLE dbo.conciliaciones_saldo (
        id BIGINT IDENTITY PRIMARY KEY, usuario_id BIGINT NOT NULL, cuenta_id BIGINT NOT NULL,
        cuenta_nombre VARCHAR(100) NOT NULL, saldo_cofi DECIMAL(18,2) NOT NULL, saldo_banco DECIMAL(18,2) NOT NULL,
        diferencia DECIMAL(18,2) NOT NULL, fecha DATETIMEOFFSET(6) NOT NULL, nota VARCHAR(255) NULL, referencia_cliente VARCHAR(36) NOT NULL,
        CONSTRAINT fk_conciliacion_usuario FOREIGN KEY(usuario_id) REFERENCES dbo.usuarios(id),
        CONSTRAINT fk_conciliacion_cuenta FOREIGN KEY(usuario_id,cuenta_id) REFERENCES dbo.cuentas(usuario_id,id),
        CONSTRAINT uq_conciliacion_referencia UNIQUE(usuario_id,referencia_cliente),
        CONSTRAINT ck_conciliacion_diferencia CHECK(diferencia=saldo_banco-saldo_cofi)
    );
IF COL_LENGTH('dbo.bank_email_imports','revision_nota') IS NULL ALTER TABLE dbo.bank_email_imports ADD revision_nota VARCHAR(255) NULL;
IF COL_LENGTH('dbo.bank_email_imports','revisado_en') IS NULL ALTER TABLE dbo.bank_email_imports ADD revisado_en DATETIMEOFFSET(6) NULL;
COMMIT;
