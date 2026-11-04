# Control de cuentas y dinero prestado

La sección «Control y préstamos» tiene tres apartados por usuario, protegidos por sesión activa:

- Avisos pendientes: lista todos los avisos PENDIENTE o ERROR; permite vincular un movimiento propio del mismo monto cuando el aviso lo identifica, o marcarlo atendido con una nota obligatoria. No crea ni modifica dinero. Conserva el detalle original, nota de revisión y fecha. Para una operación todavía no registrada, se registra primero en Movimientos y luego se vincula.
- Conciliar saldos: compara el saldo observado en el banco con el saldo actual de COFI. En crédito se compara el crédito disponible. Guarda ambos valores y la diferencia como una observación fechada; no reemplaza saldos ni crea ajustes. Muestra las últimas 50 revisiones.
- Dinero prestado: registro principalmente manual, con persona, monto, cuenta de efectivo/débito, nota y fecha de devolución opcionales. Permite abonos parciales, historial y vincular salidas o entradas ya registradas. Es un seguimiento de capital sin intereses; no gestiona intereses, comisiones ni cobros automáticos.

Un préstamo nuevo descuenta una vez de la cuenta de origen y genera PRESTAMO_OTORGADO. Vincular un gasto ya descontado solo cambia su clasificación, sin repetir la salida. Una devolución nueva acredita la cuenta elegida y genera COBRO_PRESTAMO; vincular un ingreso existente no lo vuelve a acreditar. El capital prestado no se suma a gastos y el capital recuperado no se suma a ingresos nuevos. Sí aparecen en el flujo de efectivo y el historial. El patrimonio global incluye el capital pendiente por cobrar; no se atribuye toda esa deuda a una cuenta individual al filtrar reportes.

La opción «Dinero que ya me deben · sin descontar» registra solo el capital pendiente actual, sin cuenta de origen ni movimiento monetario. Permite una fecha de devolución ya vencida. El seguimiento y el reconocimiento del saldo por cobrar empiezan el día del registro; no inventa entregas, ingresos ni patrimonio anteriores. El botón «Registrar devolución» aparece en el préstamo una vez guardado. La categoría «Préstamo» por sí sola no crea seguimiento. No se convierten movimientos antiguos ni se deducen personas automáticamente. Si PAY informa una salida o entrada que podría coincidir con un préstamo o devolución manual ya registrado, se deja pendiente para vincularlo, en vez de afectar el saldo otra vez. Estas coincidencias son indicios para revisar, no una asociación automática por monto.

Las escrituras llevan referencias de cliente para reintentos idempotentes; las operaciones monetarias bloquean el usuario y las filas necesarias y se ejecutan en una transacción. SQL Server exige relaciones del mismo usuario y montos pendientes entre cero y el principal. No se admiten abonos superiores al pendiente ni origen/destino de crédito para estos préstamos.

## Instalación local

Con un respaldo completo verificado y el backend detenido, aplicar `backend/sql/V20261103__control_y_dinero_prestado.sql` después de las migraciones anteriores. Luego aplicar `backend/sql/V20261103_02__prestamos_saldo_inicial.sql` para habilitar los saldos iniciales pendientes sin movimientos. La migración crea tablas y columnas y amplía los tipos admitidos; no cambia datos financieros existentes. Reiniciar el backend con validación de esquema. La importación Gmail mantiene su configuración previa; esta función no la pausa.

La migración incluida corresponde a SQL Server. No desplegar este cambio en PostgreSQL sin preparar primero su migración equivalente. No incluye credenciales ni datos privados.

## Verificación

Pruebas de servicio cubren préstamos manuales, vínculos sin doble descuento, abonos parciales, reintentos, límites, propiedad de registros, conciliación sin ajustes y revisión de avisos. La prueba SQL opcional `ControlFinancieroSqlIntegrationTest` requiere `COFI_SQL_INTEGRATION=true`, utiliza el perfil `migration` sin tareas programadas y revierte todos sus datos ficticios al terminar. Requiere el esquema ya migrado y acceso al SQL Server local; no está habilitada en la ejecución habitual de pruebas.
