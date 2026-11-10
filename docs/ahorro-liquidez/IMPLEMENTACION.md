# Ahorro y Liquidez en COFI

Implementado localmente el 9 de noviembre de 2026. Ruta: `/ahorro-liquidez`, menú Finanzas.

## Funcionamiento

- Dinero libre: efectivo y débito menos deuda de tarjetas y compromisos fijos pendientes hasta el mes consultado. El crédito disponible no se suma al dinero propio.
- Las reservas para metas son asignaciones virtuales: no descuentan nuevamente saldos ni crean gastos. Se validan contra el dinero libre, la cuenta y el objetivo. Se pueden liberar; una meta se archiva después de liberar sus reservas.
- Los gastos fijos generan compromisos mensuales. Vincular un gasto ya registrado reduce el pendiente sin cobrarlo otra vez. Los compromisos existentes se ajustan individualmente; editar la plantilla afecta las nuevas generaciones.
- Los presupuestos mensuales pueden cubrir una categoría o todos los gastos, incluidos crédito y efectivo. Transferencias y pagos de tarjetas no suman gastos adicionales. Hay aviso anticipado opcional y alerta al superar el límite.
- Las alertas se guardan en la base de datos y se muestran dentro de COFI. Este módulo no agrega envíos de correo ni WhatsApp. El procesamiento requiere el backend encendido; la interfaz consulta cambios cada 15 segundos mientras está visible.
- Reportes consulta las metas y presupuestos del módulo, sin mantener otra copia editable.

## Base de datos y traslado

Migración aplicada: `backend/sql/V20261109__ahorro_liquidez.sql`. Incluye metas, reservas, gastos fijos, compromisos, aplicaciones de pagos, presupuestos, alertas y eventos pendientes. Los registros pertenecen a cada usuario; las actualizaciones usan versiones y referencias de operación para evitar duplicados.

Respaldo completo con el esquema nuevo: `output/backups/COFI_20261109_160526_full_transfer.bak`, comprobado con RESTORE VERIFYONLY y SHA-256. Para otra computadora, restaurar este respaldo y usar el backend actualizado. No subir respaldos ni secretos al repositorio.

La migración y las pruebas conservaron los hashes de cuentas, movimientos y planes anteriores. Las pruebas SQL utilizaron datos ficticios dentro de una transacción revertida.

## Validación

- Backend: compilación correcta; 156 pruebas ejecutadas, sin fallos ni errores, cuatro omitidas. Adicionalmente se ejecutaron la integración SQL del módulo y sus cálculos.
- Frontend: cinco pruebas satisfactorias de Ahorro y Liquidez y Reportes.
- APK local 0.6.0: compilada y firma de pruebas verificada; incluye el frontend nuevo. Necesita el backend local actualizado y conexión entre celular y computadora. No es una publicación en Google Play.
