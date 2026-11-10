-- COFI local: Ahorro y Liquidez. Crear respaldo verificado antes de aplicar.
SET XACT_ABORT ON;
IF OBJECT_ID('dbo.metas_ahorro','U') IS NOT NULL RETURN;
BEGIN TRANSACTION;
-- Claves compuestas de propiedad para impedir relaciones entre usuarios.
-- Reutilizar índices equivalentes existentes al convertir esto en migración.
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.cuentas') AND name='ux_cuenta_ahorro_propietario')
    CREATE UNIQUE INDEX ux_cuenta_ahorro_propietario ON dbo.cuentas(id,usuario_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.movimientos') AND name='ux_movimiento_ahorro_propietario')
    CREATE UNIQUE INDEX ux_movimiento_ahorro_propietario ON dbo.movimientos(id,usuario_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.planes_reporte') AND name='ux_plan_ahorro_propietario')
    CREATE UNIQUE INDEX ux_plan_ahorro_propietario ON dbo.planes_reporte(id,usuario_id);

CREATE TABLE dbo.metas_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    nombre NVARCHAR(100) NOT NULL,
    objetivo DECIMAL(18,2) NOT NULL CHECK(objetivo>0),
    fecha_objetivo DATE NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'ACTIVA' CHECK(estado IN('ACTIVA','ARCHIVADA')),
    origen_plan_id BIGINT NULL,
    acumulado_legado_informado DECIMAL(18,2) NULL CHECK(acumulado_legado_informado>=0),
    aporte_mensual DECIMAL(18,2) NULL CHECK(aporte_mensual>=0),
    referencia_cliente UNIQUEIDENTIFIER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    actualizada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_meta_propietario UNIQUE(id,usuario_id),
    CONSTRAINT uq_meta_creacion UNIQUE(usuario_id,referencia_cliente),
    CONSTRAINT fk_meta_plan_origen FOREIGN KEY(origen_plan_id,usuario_id) REFERENCES dbo.planes_reporte(id,usuario_id)
);
CREATE UNIQUE INDEX ux_meta_plan_origen ON dbo.metas_ahorro(usuario_id,origen_plan_id) WHERE origen_plan_id IS NOT NULL;
CREATE INDEX ix_metas_usuario_estado ON dbo.metas_ahorro(usuario_id,estado,id);

CREATE TABLE dbo.reservas_meta (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    meta_id BIGINT NOT NULL,
    cuenta_id BIGINT NOT NULL,
    tipo VARCHAR(12) NOT NULL CHECK(tipo IN('RESERVA','LIBERACION')),
    monto DECIMAL(18,2) NOT NULL CHECK(monto>0),
    referencia_cliente UNIQUEIDENTIFIER NOT NULL,
    nota NVARCHAR(255) NULL,
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_reserva_reintento UNIQUE(usuario_id,referencia_cliente),
    CONSTRAINT fk_reserva_meta FOREIGN KEY(meta_id,usuario_id) REFERENCES dbo.metas_ahorro(id,usuario_id),
    CONSTRAINT fk_reserva_cuenta FOREIGN KEY(cuenta_id,usuario_id) REFERENCES dbo.cuentas(id,usuario_id)
);
CREATE INDEX ix_reserva_usuario_meta ON dbo.reservas_meta(usuario_id,meta_id,id) INCLUDE(cuenta_id,tipo,monto);
CREATE INDEX ix_reserva_usuario_cuenta ON dbo.reservas_meta(usuario_id,cuenta_id) INCLUDE(meta_id,tipo,monto);
-- No saldo editable: SUM(CASE tipo WHEN 'RESERVA' THEN monto ELSE -monto END).
-- Cuenta activa/no crédito, saldo suficiente, objetivo y no negatividad agregada
-- requieren validación transaccional del servicio bajo bloqueo del propietario.

CREATE TABLE dbo.gastos_fijos_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    nombre NVARCHAR(100) NOT NULL,
    monto_acordado DECIMAL(18,2) NOT NULL CHECK(monto_acordado>0),
    categoria_id BIGINT NULL REFERENCES dbo.categorias(id),
    dia_pago TINYINT NOT NULL CHECK(dia_pago BETWEEN 1 AND 31),
    inicio DATE NOT NULL CHECK(DAY(inicio)=1),
    activa BIT NOT NULL DEFAULT 1,
    referencia_cliente UNIQUEIDENTIFIER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_fijo_propietario UNIQUE(id,usuario_id),
    CONSTRAINT uq_fijo_creacion UNIQUE(usuario_id,referencia_cliente)
);
CREATE TABLE dbo.compromisos_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    gasto_fijo_id BIGINT NOT NULL,
    periodo DATE NOT NULL CHECK(DAY(periodo)=1),
    vencimiento DATE NOT NULL,
    monto_acordado DECIMAL(18,2) NOT NULL CHECK(monto_acordado>0),
    estado VARCHAR(12) NOT NULL DEFAULT 'VIGENTE' CHECK(estado IN('VIGENTE','CANCELADO')),
    nota NVARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT ck_compromiso_mes CHECK(vencimiento>=periodo AND vencimiento<DATEADD(month,1,periodo)),
    CONSTRAINT uq_compromiso_propietario UNIQUE(id,usuario_id),
    CONSTRAINT uq_compromiso_generacion UNIQUE(usuario_id,gasto_fijo_id,periodo),
    CONSTRAINT fk_compromiso_fijo FOREIGN KEY(gasto_fijo_id,usuario_id) REFERENCES dbo.gastos_fijos_ahorro(id,usuario_id)
);
CREATE INDEX ix_compromisos_horizonte ON dbo.compromisos_ahorro(usuario_id,estado,vencimiento) INCLUDE(monto_acordado);

CREATE TABLE dbo.pagos_compromiso_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    compromiso_id BIGINT NOT NULL,
    movimiento_id BIGINT NOT NULL,
    monto DECIMAL(18,2) NOT NULL CHECK(monto>0),
    referencia_cliente UNIQUEIDENTIFIER NOT NULL,
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    anulada_en DATETIME2(3) NULL,
    motivo_anulacion NVARCHAR(255) NULL,
    CONSTRAINT ck_pago_anulacion CHECK(anulada_en IS NULL OR COALESCE(LEN(LTRIM(RTRIM(motivo_anulacion))),0)>0),
    CONSTRAINT uq_pago_compromiso_reintento UNIQUE(usuario_id,referencia_cliente),
    CONSTRAINT fk_pago_compromiso FOREIGN KEY(compromiso_id,usuario_id) REFERENCES dbo.compromisos_ahorro(id,usuario_id),
    CONSTRAINT fk_pago_movimiento FOREIGN KEY(movimiento_id,usuario_id) REFERENCES dbo.movimientos(id,usuario_id)
);
CREATE UNIQUE INDEX ux_pago_compromiso_activo ON dbo.pagos_compromiso_ahorro(usuario_id,compromiso_id,movimiento_id) WHERE anulada_en IS NULL;
CREATE INDEX ix_pago_compromiso_suma ON dbo.pagos_compromiso_ahorro(usuario_id,compromiso_id) INCLUDE(monto,anulada_en);
CREATE INDEX ix_pago_movimiento_suma ON dbo.pagos_compromiso_ahorro(usuario_id,movimiento_id) INCLUDE(monto,anulada_en);
-- Verificar SUM de aplicaciones activas <= importe del GASTO y del compromiso;
-- si la factura real supera lo acordado, ajustar el compromiso explícitamente.

CREATE TABLE dbo.presupuestos_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    nombre NVARCHAR(100) NOT NULL,
    periodo DATE NOT NULL CHECK(DAY(periodo)=1),
    categoria_id BIGINT NULL REFERENCES dbo.categorias(id), -- NULL y confirmado: todas
    categoria_legada NVARCHAR(100) NULL,
    pendiente_categoria BIT NOT NULL DEFAULT 0,
    limite DECIMAL(18,2) NOT NULL CHECK(limite>0),
    alerta_anticipada BIT NOT NULL DEFAULT 1,
    umbral_anticipado TINYINT NOT NULL DEFAULT 80 CHECK(umbral_anticipado BETWEEN 1 AND 99),
    activo BIT NOT NULL DEFAULT 1,
    origen_plan_id BIGINT NULL,
    referencia_cliente UNIQUEIDENTIFIER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_presupuesto_propietario UNIQUE(id,usuario_id),
    CONSTRAINT uq_presupuesto_creacion UNIQUE(usuario_id,referencia_cliente),
    CONSTRAINT fk_presupuesto_plan FOREIGN KEY(origen_plan_id,usuario_id) REFERENCES dbo.planes_reporte(id,usuario_id)
);
CREATE UNIQUE INDEX ux_presupuesto_global ON dbo.presupuestos_ahorro(usuario_id,periodo) WHERE categoria_id IS NULL AND activo=1 AND pendiente_categoria=0;
CREATE UNIQUE INDEX ux_presupuesto_categoria ON dbo.presupuestos_ahorro(usuario_id,periodo,categoria_id) WHERE categoria_id IS NOT NULL AND activo=1 AND pendiente_categoria=0;
CREATE UNIQUE INDEX ux_presupuesto_plan_origen ON dbo.presupuestos_ahorro(usuario_id,origen_plan_id) WHERE origen_plan_id IS NOT NULL;

CREATE TABLE dbo.alertas_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    presupuesto_id BIGINT NOT NULL,
    periodo DATE NOT NULL CHECK(DAY(periodo)=1),
    umbral TINYINT NOT NULL CHECK(umbral BETWEEN 1 AND 100), -- 100: exceso estricto
    gastado_detectado DECIMAL(18,2) NOT NULL CHECK(gastado_detectado>=0),
    limite_detectado DECIMAL(18,2) NOT NULL CHECK(limite_detectado>0),
    estado VARCHAR(12) NOT NULL DEFAULT 'NUEVA' CHECK(estado IN('NUEVA','LEIDA','RESUELTA')),
    creada_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    leida_en DATETIME2(3) NULL,
    resuelta_en DATETIME2(3) NULL,
    CONSTRAINT uq_alerta_cruce UNIQUE(usuario_id,presupuesto_id,periodo,umbral),
    CONSTRAINT fk_alerta_presupuesto FOREIGN KEY(presupuesto_id,usuario_id) REFERENCES dbo.presupuestos_ahorro(id,usuario_id)
);
CREATE INDEX ix_alertas_bandeja ON dbo.alertas_ahorro(usuario_id,estado,id DESC);

CREATE TABLE dbo.eventos_ahorro (
    id BIGINT IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES dbo.usuarios(id),
    tipo VARCHAR(32) NOT NULL,
    agregado_id BIGINT NULL,
    periodo_anterior DATE NULL,
    periodo_actual DATE NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN('PENDIENTE','PROCESANDO','PROCESADO','ERROR')),
    intentos INT NOT NULL DEFAULT 0 CHECK(intentos>=0),
    creado_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    disponible_en DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    bloqueo_hasta DATETIME2(3) NULL,
    procesado_en DATETIME2(3) NULL,
    error_codigo VARCHAR(60) NULL -- Sin cuerpos de correo, tokens ni datos privados.
);
CREATE INDEX ix_eventos_ahorro_pendientes ON dbo.eventos_ahorro(estado,disponible_en,id) INCLUDE(usuario_id,bloqueo_hasta);
-- Insertar evento en la MISMA transacción que la escritura original.
-- Reclamo con UPDLOCK/READPAST, arrendamiento con caducidad y recuperación
-- de PROCESANDO vencidos. Evaluación + alerta + PROCESADO se confirman juntas.

-- Importar planes sin tocar saldos ni generar reservas ficticias.
IF EXISTS(SELECT 1 FROM dbo.planes_reporte WHERE tipo IN('META','PRESUPUESTO') AND (monto<=0 OR nombre IS NULL OR (tipo='PRESUPUESTO' AND TRY_CONVERT(date,mes+'-01') IS NULL)))
    THROW 51002, 'Hay planes antiguos inválidos; revisar antes de migrar.',1;
INSERT dbo.metas_ahorro(usuario_id,nombre,objetivo,fecha_objetivo,origen_plan_id,acumulado_legado_informado,aporte_mensual,referencia_cliente)
SELECT usuario_id,nombre,monto,fecha_objetivo,id,COALESCE(acumulado,0),aporte_mensual,NEWID() FROM dbo.planes_reporte WHERE tipo='META';
INSERT dbo.presupuestos_ahorro(usuario_id,nombre,periodo,categoria_id,categoria_legada,pendiente_categoria,limite,origen_plan_id,referencia_cliente)
SELECT p.usuario_id,p.nombre,CONVERT(date,p.mes+'-01'),CASE WHEN c.cantidad=1 THEN c.id ELSE NULL END,p.categoria,1,p.monto,p.id,NEWID()
FROM dbo.planes_reporte p OUTER APPLY(SELECT COUNT(*) cantidad,MAX(id) id FROM dbo.categorias WHERE LTRIM(RTRIM(nombre))=LTRIM(RTRIM(p.categoria))) c WHERE p.tipo='PRESUPUESTO';
;WITH candidatos AS(SELECT id,ROW_NUMBER() OVER(PARTITION BY usuario_id,periodo,categoria_id ORDER BY id) fila FROM dbo.presupuestos_ahorro WHERE categoria_id IS NOT NULL)
UPDATE b SET pendiente_categoria=0 FROM dbo.presupuestos_ahorro b JOIN candidatos c ON c.id=b.id WHERE c.fila=1;
INSERT dbo.eventos_ahorro(usuario_id,tipo) SELECT DISTINCT usuario_id,'MIGRACION' FROM dbo.presupuestos_ahorro;
COMMIT;
