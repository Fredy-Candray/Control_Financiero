CREATE DATABASE ControlFinanciero;
GO

USE master;
GO

CREATE LOGIN control_financiero
WITH PASSWORD = 'PassUser!';
GO

USE ControlFinanciero;
GO

CREATE USER control_financiero
FOR LOGIN control_financiero;
GO

ALTER ROLE db_owner
ADD MEMBER control_financiero;
GO