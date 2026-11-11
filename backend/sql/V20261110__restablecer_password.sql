SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.usuarios','cambio_password_pendiente') IS NULL
    ALTER TABLE dbo.usuarios ADD cambio_password_pendiente bit NOT NULL CONSTRAINT DF_usuarios_cambio_password DEFAULT 0 WITH VALUES;
IF COL_LENGTH('dbo.usuarios','version_acceso') IS NULL
    ALTER TABLE dbo.usuarios ADD version_acceso bigint NOT NULL CONSTRAINT DF_usuarios_version_acceso DEFAULT 0 WITH VALUES;
COMMIT;
