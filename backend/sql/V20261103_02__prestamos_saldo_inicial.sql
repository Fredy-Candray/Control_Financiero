-- Existing receivables: starts tracking today without creating cash movements.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.prestamos_otorgados','registro_inicial') IS NULL
    ALTER TABLE dbo.prestamos_otorgados ADD registro_inicial BIT NOT NULL CONSTRAINT df_otorgado_registro_inicial DEFAULT(0) WITH VALUES;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='ck_otorgado_fecha')
    ALTER TABLE dbo.prestamos_otorgados DROP CONSTRAINT ck_otorgado_fecha;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='ck_otorgado_fecha_v2')
    EXEC('ALTER TABLE dbo.prestamos_otorgados WITH CHECK ADD CONSTRAINT ck_otorgado_fecha_v2 CHECK(registro_inicial=1 OR vencimiento IS NULL OR vencimiento>=CONVERT(date,fecha))');
COMMIT;
