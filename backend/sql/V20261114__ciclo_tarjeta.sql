SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.cuentas', 'fecha_corte') IS NULL
    ALTER TABLE dbo.cuentas ADD fecha_corte date NULL;
IF COL_LENGTH('dbo.cuentas', 'fecha_pago') IS NULL
    ALTER TABLE dbo.cuentas ADD fecha_pago date NULL;
IF COL_LENGTH('dbo.cuentas', 'pendiente_ciclo') IS NULL
    ALTER TABLE dbo.cuentas ADD pendiente_ciclo decimal(18,2) NULL;
COMMIT;
