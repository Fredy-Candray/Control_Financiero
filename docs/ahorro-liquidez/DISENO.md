# Ahorro y Liquidez — diseño para COFI

Estado: implementado localmente el 9 de noviembre de 2026. El módulo está en Finanzas → Ahorro y Liquidez. Este documento conserva la propuesta original; consulta `IMPLEMENTACION.md` para el funcionamiento integrado. Los importes de la maqueta siguen siendo ficticios.

## 1. Qué reutiliza y qué incorpora

COFI ya usa Angular standalone, Spring Boot, SQL Server, sesiones de usuario y USD. `cuentas.saldo_actual` es dinero en efectivo/débito, pero en crédito representa **crédito disponible**. No se pueden sumar ambos como si fueran dinero. `DashboardService` ya calcula la deuda como `max(0, limite_credito - saldo_actual)` y contempla tarjetas antiguas sin límite. Reportes guarda `META` y `PRESUPUESTO` en `planes_reporte`, aunque el acumulado de metas es declarado manualmente y la categoría de presupuesto es un texto.

El nuevo módulo será el propietario de metas, reservas, presupuestos y compromisos. Reportes los consultará para mostrar/exportar resultados: no mantendrá una segunda copia editable. Cuentas, Movimientos, Calendario y Dinero prestado conservan sus responsabilidades.

## 2. Reglas financieras

```text
Dinero en cuentas = SUM(saldos de EFECTIVO y DEBITO propios, en USD)
Deuda tarjetas   = SUM(deuda actual de cada tarjeta propia)
Fijos pendientes = SUM(importe acordado - pagos vinculados) de compromisos vigentes
Dinero Libre    = Dinero en cuentas - Deuda tarjetas - Fijos pendientes
Reservado metas = SUM(reservas - liberaciones de metas)
Sin asignar     = Dinero Libre - Reservado metas
```

- PAY entra como débito. El crédito disponible y el dinero que otras personas deben al usuario **no** entran en dinero en cuentas.
- Se incluyen saldos y deudas de cuentas conservadas, aunque estén inactivas, para no ocultarlos por desactivación. La interfaz señala esas cuentas; no permite nuevas reservas en ellas.
- Una tarjeta sin límite necesita el cálculo legado centralizado que hoy usa Dashboard o revisión; no se asume deuda cero. Si falta información confiable, el resumen se declara incompleto y no permite reservar.
- Los importes negativos de Dinero Libre/Sin asignar se muestran como déficit; no se ocultan con un cero.
- Deuda de tarjetas es actual, no solo la generada en el mes elegido. El selector mensual afecta presupuestos y vencimientos del plan, no convierte los saldos actuales en históricos.
- Fijos pendientes incluye vencidos de meses anteriores y los que vencen hasta el final del mes del plan. Excluye cancelados y obligaciones posteriores al horizonte. Cambiar el mes no elimina atrasos.
- El crédito de tarjetas pagado de más no se convierte automáticamente en efectivo. Una deuda personal tampoco se mezcla silenciosamente con esta fórmula: esta versión sigue la fórmula solicitada, que descuenta tarjetas y fijos.
- Una reserva para una meta es una **asignación virtual**: no genera GASTO, INGRESO ni TRANSFERENCIA, no modifica `cuentas.saldo_actual` y no aumenta el patrimonio. Una transferencia real a una cuenta de ahorro se registra por separado con el flujo existente.

### Evitar el doble descuento de gastos fijos

Antes de pagar: cuentas $1,000, tarjetas $0, fijo pendiente $100 → Libre $900.

| Operación | Cuentas | Deuda tarjeta | Fijo pendiente | Libre |
|---|---:|---:|---:|---:|
| Pagar el fijo con débito, vinculando el gasto | 900 | 0 | 0 | 900 |
| Pagarlo con tarjeta, vinculando el gasto | 1,000 | 100 | 0 | 900 |
| Después pagar esa tarjeta desde débito | 900 | 0 | 0 | 900 |

El importe de un fijo ya pagado deja de reservarse. Vincular el movimiento no vuelve a mover dinero. El pago de tarjeta nunca cuenta como otro gasto presupuestario. Si el correo no identifica qué fijo se pagó, se registra el gasto real normalmente y se pide vincularlo; no se inventa la relación por categoría o monto. La pantalla indica que la obligación sigue pendiente de conciliación.

### Reservas y avance de metas

`avance = reservado_actual / objetivo * 100`; la barra se limita visualmente a 100 %. No se permiten aportes por encima del objetivo en esta primera versión.

Para reservar $X deben cumplirse dentro de una transacción:

1. Cuenta propia, activa, USD, de efectivo o débito.
2. $X positivo y con hasta dos decimales.
3. $X ≤ saldo de esa cuenta menos todas sus reservas vigentes.
4. $X ≤ Dinero Libre menos todas las reservas vigentes.
5. La meta está activa y su reservado más $X no supera el objetivo.

Liberar fondos solo reduce la reserva, nunca acredita una cuenta. Puede ser parcial. Archivar una meta con fondos exige liberarlos primero; al archivar se vuelve a comprobar, bajo bloqueo, que no queden reservas. Bajar el objetivo por debajo del reservado devuelve 409 y pide liberar antes; no se elimina historia.

Si una compra posterior reduce el dinero que respalda las reservas, se registra la compra real sin bloquearla, incluidas las importaciones del banco. Se marca falta de cobertura global y/o por cuenta, se conserva la reserva nominal y se pide al usuario reajustarla. No se distribuye el déficit entre metas arbitrariamente ni se presenta una reserva sin respaldo como dinero protegido en el banco.

## 3. Base de datos

Esquema propuesto en `schema.propuesto.sql`. No está en el directorio de migraciones activas y exige habilitar explícitamente su ejecución. Usa DECIMAL(18,2), relaciones por propietario, índices, control de versión y referencias idempotentes.

| Tabla | Propósito | Regla principal |
|---|---|---|
| `metas_ahorro` | Objetivo, nombre, plazo, estado y procedencia de Reportes | No almacena otro saldo acumulado editable |
| `reservas_meta` | Libro de RESERVA/LIBERACION por meta y cuenta | Inmutable; saldo derivado; misma propiedad |
| `gastos_fijos_ahorro` | Plantilla mensual de un compromiso acordado | Día 1–31; si no existe, último día del mes |
| `compromisos_ahorro` | Obligación concreta por mes, importe y vencimiento | Una instancia por plantilla/mes |
| `pagos_compromiso_ahorro` | Aplicación parcial o total de un GASTO existente | Vincula; no vuelve a descontar dinero |
| `presupuestos_ahorro` | Tope mensual global o por categoría normalizada | Un tope por usuario/alcance/categoría/mes |
| `alertas_ahorro` | Bandeja interna y evidencia del límite detectado | Una alerta por presupuesto/umbral/mes |
| `eventos_ahorro` | Cola transaccional local de recálculo | Persistente: no pierde eventos al reiniciar |

No se guarda Dinero Libre como un saldo que haya que sincronizar: se calcula desde las fuentes. Los porcentajes y totales también son derivados. `version` se mapea con `@Version`; las escrituras con versión obsoleta responden 409.

### Continuidad de los datos de Reportes

Antes de implementar: respaldo, ensayo de migración y conteos por usuario. No copiar presupuestos/metas indiscriminadamente y dejarlos editables en ambos sitios.

1. Metas: importar nombre, objetivo y plazo una sola vez mediante `origen_plan_id`. El acumulado antiguo pasa a `acumulado_legado_informado`, visible como pendiente de confirmar. No se convierte en reserva ni se descuenta dinero sin identificar la cuenta que lo respalda.
2. Presupuestos: resolver el nombre de categoría antiguo a `categorias.id`; los nombres ambiguos o inexistentes quedan pendientes de revisión. Conservar el ID de origen para evitar duplicados.
3. Cambiar las lecturas de Reportes/PDF a las nuevas tablas y retirar la escritura de `META`/`PRESUPUESTO` en `/reportes/planes`. Los planes `DEUDA` no se migran en este módulo.
4. Conservar los registros originales para trazabilidad durante el corte. Nunca sumar el acumulado legado además del libro de reservas.

## 4. Backend

### Servicios

- `LiquidezService`: snapshot consistente del resumen; reutiliza un cálculo único de deuda compartido con Dashboard/Reportes, no una fórmula distinta por pantalla.
- `MetaAhorroService`: CRUD lógico, reservar/liberar y libro de operaciones.
- `CompromisoAhorroService`: plantillas, generación mensual idempotente, cancelación, importes reales y vínculos con gastos.
- `PresupuestoAhorroService`: límites, consumo mensual y revisión de cambios de categoría.
- `AlertaAhorroWorker`: consume eventos persistidos, evalúa umbrales e inserta alertas sin duplicarlas.
- `AhorroQueryController`: consulta resumida para Angular, con `calculadoEn`, período, moneda y estado de completitud.

Toda consulta deriva el usuario de la sesión y comprueba que está activo. No recibe un `usuarioId` del cliente. Relaciones entre usuarios se rechazan con 404. Reutilizar `UsuarioRepository.findLockedById` para serializar escrituras monetarias/reservas, con orden de bloqueo estable. Los saldos reales siguen siendo propiedad de `MovimientoService` y de los importadores existentes.

La referencia Java en `referencia/LiquidezReferencia.java` especifica cálculo en BigDecimal y umbrales. No es un servicio Spring activado ni sustituye las transacciones y repositorios.

### API propuesta

Base `/api/ahorro-liquidez`, sesión y permisos propios del módulo. Todas las escrituras validan propiedad, moneda, escala y versión; creación, reserva, liberación y vínculos llevan `referenciaCliente` UUID.

| Método y ruta | Resultado |
|---|---|
| GET `/resumen?mes=2026-11` | Cuentas, deudas, fijos pendientes, libre, reservado, sin asignar y alertas |
| GET/POST `/metas` | Listar/crear metas |
| GET/PUT/DELETE `/metas/{id}` | Detalle/editar/archivar lógicamente; versión requerida |
| GET `/metas/{id}/reservas` | Libro paginado |
| POST `/metas/{id}/reservas` | `{cuentaId,monto,referenciaCliente,version}` |
| POST `/metas/{id}/liberaciones` | `{cuentaId,monto,referenciaCliente,version}` |
| GET/POST `/fijos` | Plantillas mensuales |
| PUT/DELETE `/fijos/{id}` | Editar/archivar sin alterar obligaciones históricas |
| GET `/compromisos?mes=...` | Pendientes y vencidos hasta el horizonte |
| POST `/compromisos/{id}/pagos` | Vincular importe a un GASTO propio existente |
| DELETE `/compromisos/{id}/pagos/{vinculoId}` | Anular vínculo con nota y versión; conservar auditoría |
| POST `/compromisos/{id}/cancelacion` | Cancelar obligación sin crear un gasto |
| GET/POST `/presupuestos` | Consulta/alta de límites |
| PUT/DELETE `/presupuestos/{id}` | Editar/archivar límite |
| GET `/alertas?estado=NUEVA&pagina=0` | Bandeja paginada |
| PATCH `/alertas/{id}/lectura` | Marcar leída idempotentemente |

Los DELETE son lógicos. Anular un vínculo no borra el movimiento bancario; vuelve a dejar ese importe pendiente en el compromiso. Si se corrige un gasto, sus aplicaciones no pueden quedar por encima del importe real; se solicita resolver la diferencia en una transacción. No hay botón que pague al banco.

Ejemplo de resumen (datos ficticios):

```json
{"mes":"2026-11","moneda":"USD","calculadoEn":"2026-11-09T14:00:00-06:00","estado":"COMPLETO","dineroEnCuentas":"1800.00","deudaTarjetas":"450.00","gastosFijosPendientes":"300.00","dineroLibre":"1050.00","reservadoMetas":"600.00","sinAsignar":"450.00","alertasNuevas":1}
```

Los decimales del contrato viajan como cadenas exactas; el cliente transforma a centavos para gráficas y usa formato monetario al mostrar. No recalcula la decisión de permitir una reserva. Los totales se leen en un snapshot transaccional coherente: varios SELECT independientes sin protección no garantizan consistencia. El diseño de implementación debe usar bloqueo breve del usuario o aislamiento snapshot habilitado y comprobado para esa base, sin cambiar opciones globales de SQL Server a ciegas.

## 5. Motor de alertas

Consumo de presupuesto = suma de `GASTO` en el mes completo del banco de datos del usuario, con `[inicioMes, inicioMesSiguiente)` en America/El_Salvador. Incluye efectivo, PAY/débito y compras con crédito. Excluye transferencias propias, pagos de tarjeta, principal prestado y devoluciones de principal. En presupuestos globales incluye Sin categoría; uno por categoría usa el ID, no el texto. Los límites globales y por categoría se evalúan de manera independiente: no se suman como obligaciones de liquidez.

- Aviso anticipado opcional desde 80 %; umbral configurable entre 1 y 99.
- Exactamente 100 % se muestra «Al límite»; la alerta de exceso se dispara al **superar** el monto, conforme al requerimiento.
- La comparación usa importes exactos, no porcentajes redondeados.
- Restricción única `(usuario,presupuesto,mes,umbral)` evita alertas por cada refresco/reintento. La lectura no vuelve a disparar el mismo evento.
- Capturar importe gastado y límite que originaron la alerta. Si cambian los datos, actualizar vigencia/estado; no reescribir la evidencia ni volver a notificar el mismo cruce del mes. El usuario podrá consultar alertas resueltas.
- Recalcular tras altas/correcciones de movimientos, cambios de categoría o fecha, importaciones, ajustes de cuentas, cambios de presupuesto, reservas y aplicaciones a fijos. Cambiar el presupuesto también puede provocar el primer aviso.
- Dentro de la transacción origen se inserta un evento. Worker local con reintentos reclama lotes con bloqueo y marca procesado solo tras evaluar y persistir. La unicidad protege ante dos workers. No basta un listener en memoria que pierda la notificación si el proceso cae después del commit.
- Primera entrega: bandeja y aviso dentro de COFI. No se envían correos, WhatsApp ni notificaciones push. Integrar esos canales requiere autorización y entrega persistente separada; un error del canal no deshace un gasto.

Con la app abierta, consultar cambios cada 15 segundos y al recuperar el foco; tras una escritura propia refrescar inmediatamente. No lanzar lecturas solapadas; cancelar al salir y reintentar con espera si falla. Mostrar «Actualizado a…» y un estado de desconexión. Esto es actualización periódica, no una promesa de tiempo real instantáneo. Un servidor local apagado no puede procesar eventos ni entregar alertas.

## 6. Frontend

Ruta `/ahorro-liquidez`, bajo Finanzas. El prototipo `maqueta.html` usa solo recursos locales e importes ficticios; no lee cookies de COFI ni llama a APIs.

Pestañas: **Resumen · Metas · Gastos fijos · Presupuestos**. Una campana abre la bandeja de alertas, sin convertir cada advertencia en un modal bloqueante.

Resumen: tarjeta principal «Dinero Libre» con desglose desplegable; tarjetas de dinero en cuentas, deuda de tarjetas y fijos pendientes. Una segunda franja separa «Reservado en metas» de «Sin asignar». Mostrar un aviso persistente si hay déficit o datos incompletos.

Gráficos: composición del dinero mediante barra apilada (deuda/fijos/reservas/sin asignar) solo cuando todas las partes sean no negativas y cuadren; en déficit sustituir por desglose numérico, sin fabricar un gráfico de pastel negativo. Metas con barras y texto `reservado / objetivo`, porcentaje y fecha. Presupuestos con utilización, importe restante/excedido y estado textual, no solo colores.

Metas: crear/editar/archivar, reservar y liberar fondos, selector de cuenta con saldo no reservado, historial de asignaciones y estados vacíos. Fijos: fechas, importe pendiente, vínculo de pago y atraso. Presupuestos: mes, categoría o Todas, límite y umbral. Listas de 6 elementos con paginación centrada, igual que COFI.

En móvil: una columna, botones táctiles de al menos 44 px y acciones en el flujo de la página; en tablet dos columnas; escritorio cuatro tarjetas. Respetar tema claro/oscuro y `prefers-reduced-motion`. Gráficos acompañados de valores legibles, foco visible y avisos accesibles. Formularios mantienen lo escrito al fallar, impiden doble envío y distinguen errores 400/401/404/409/503.

## 7. Pruebas de aceptación e implementación por etapas

1. Saldo efectivo/débito $1,800, deuda $450, fijos pendientes $300: Libre $1,050; con $600 reservados, Sin asignar $450.
2. Reservar/liberar no altera saldos, patrimonio ni gastos.
3. Pago de fijo con débito o crédito vinculado no produce doble descuento; pagar luego la tarjeta mantiene Libre.
4. Pago parcial deja solo el importe faltante. Un mismo gasto no puede aplicarse por encima de su importe entre varias obligaciones.
5. Dos solicitudes concurrentes no sobrerreservan ni duplican operaciones; el mismo UUID devuelve la operación original.
6. Límites: 79.99 %, 80 %, 100 % y 100 % + $0.01; un reintento no duplica alertas.
7. Transferencias propias y pago de tarjetas no consumen presupuesto. Reasignar categoría cambia los presupuestos afectados.
8. Una compra real se registra aunque deje reservas sin respaldo; queda el aviso de cobertura.
9. Un usuario no puede ver ni reservar cuentas/metas/compromisos de otro.
10. Corte de proceso entre commit y worker: el evento pendiente se recupera. No se notifica nada de una transacción revertida.
11. Históricos importados llegan una sola vez; acumulados declarados de Reportes no se suman a fondos reservados sin confirmar.
12. Probar 360, 768 y 1440 px, teclado, ambos temas, desconexión y respuestas tardías.

Orden: aprobar reglas y maqueta → respaldo y migración ensayada → resumen de solo lectura → metas/reservas → fijos y vínculos → presupuestos/alertas → Reportes/PDF → APK. Antes de aplicar SQL se deben revisar los nombres, constraints y datos reales del esquema. Esta entrega no cambia el comportamiento actual del Dashboard ni del indicador «Dinero disponible».
