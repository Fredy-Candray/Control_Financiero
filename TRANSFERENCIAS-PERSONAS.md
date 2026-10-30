# Destinatarios externos y préstamos personales

En Movimientos, Transferencia permite elegir una cuenta propia u «Otras cuentas», con un campo para la persona o negocio que recibe. El formulario no muestra campos ni una sección de préstamos personales.

- Entre cuentas propias: conserva el tipo TRANSFERENCIA; resta del origen y suma al destino, sin crear ingreso ni gasto.
- Otra persona/negocio: registra un GASTO con destinatario externo, categoría opcional y descripción. Resta únicamente del origen, sin crear una cuenta ficticia para el destinatario. El gasto aparece en dashboard y reportes.
- La categoría «Préstamo» clasifica la salida, pero no crea una cuenta por cobrar ni gestiona su devolución. La funcionalidad de PAGO_PRESTAMO se conserva en el backend y los reportes para datos existentes; no está disponible en el formulario simplificado.

Las salidas PAY a terceros conservan el nombre «PAY a [destinatario]», sin inferir el motivo. El lápiz del historial permite asignar o cambiar su categoría sin volver a descontar dinero. En otros movimientos también permite editar el nombre. El aviso bancario original conserva su vínculo con el movimiento para evitar duplicados. Las compras cuyo concepto indique explícitamente «cuota» o «cuotas» reciben la categoría Cuota durante la importación.

La tabla `prestamos_personales` guarda el acreedor, saldo inicial declarado, saldo pendiente, fecha de registro y tasa cero. Registrar una deuda ya existente no acredita dinero ni inventa un ingreso previo. Esta versión cubre préstamos sin interés; no calcula intereses ni comisiones.

Los pagos llevan referencia de cliente y un índice único por usuario para evitar descuentos duplicados al reintentar una solicitud. Las cuentas y el préstamo se bloquean durante el pago; las relaciones en SQL Server impiden asociar un préstamo ajeno.

En reportes, devolver capital afecta el flujo de efectivo, pero no los gastos. El patrimonio de todas las cuentas descuenta también los préstamos personales registrados. Su historia empieza en la fecha de registro del saldo inicial; no se deducen obligaciones anteriores desconocidas. Al filtrar una cuenta concreta no se le atribuye toda la deuda personal.

## Migración local

Con el backend detenido y una copia de seguridad, aplicar `backend/sql/V20261028_02__transferencias_externas_prestamos.sql` a SQL Server. Agrega la tabla y columnas opcionales y amplía los tipos de movimiento. No crea pagos ni altera saldos. Las deudas iniciales reales son datos privados y no se incluyen en el script versionado.

Si no existe un aviso bancario compatible, el movimiento se registra manualmente. No se infieren destinatarios ni motivos desconocidos del correo.
