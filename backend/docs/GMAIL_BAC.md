# Compras automáticas de BAC desde Gmail

La integración lee las alertas de `info@baccredomatic.com`, verifica el remitente y el resultado DMARC de Gmail, interpreta únicamente compras aprobadas y registra un GASTO en USD en la tarjeta del usuario identificada por banco y últimos cuatro dígitos. No necesita el número completo ni CVV.

## Qué incluye

- Tarjetas con banco, últimos cuatro dígitos y USD, límite editable y disponible inicial independiente del límite.
- En Cuentas, botón para preparar BAC VISA 3923 con límite y disponible inicial de $1,500, según los datos confirmados. Revisar y guardar el formulario crea la cuenta; no se modifica la base de datos sin una sesión del usuario.
- Lectura de Gmail mediante OAuth de Google, con permiso `gmail.readonly`, sin contraseña de correo.
- Refresh token cifrado AES-256-GCM en SQL Server; la clave vive en la configuración del proceso, no en la base de datos.
- Fecha bancaria conservada en America/El_Salvador. La fecha de procesamiento se guarda por separado en el historial.
- Registro automático sin confirmación de cada compra. Correos no aprobados/no auténticos se ignoran con explicación; formatos no interpretables, tarjetas desconocidas y disponible insuficiente quedan con ERROR y se reintentan al sincronizar. No se fuerza un saldo negativo ni se atribuye la compra a otra tarjeta.
- Desduplicación por ID Gmail y Message-ID del correo, historial persistente y transacción que aplica saldo, movimiento y estado IMPORTADO juntos. Bloqueo de conexión y de cuenta en SQL Server para evitar descuentos duplicados y actualizaciones simultáneas perdidas.
- Primera activación fija una fecha permanente. Se excluyen compras y mensajes anteriores. Al pausar y reanudar no se reinicia esa fecha; se recuperan los mensajes del intervalo y del tiempo que el equipo estuvo apagado.
- Sincronización cada 60 segundos, botón manual, pausar, reconectar y desconectar. El navegador puede estar cerrado; Spring y SQL Server deben seguir encendidos.

## Activación local

1. Crear un proyecto propio en Google Cloud y habilitar Gmail API.
2. Configurar la pantalla de consentimiento para uso personal. Si está en pruebas, agregar la dirección Gmail del usuario como usuario de prueba.
3. Crear cliente OAuth de tipo **Aplicación web**. Registrar exactamente la URI `http://localhost:8081/api/gmail-bac/callback`.
4. Configurar `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` y `GMAIL_ENCRYPTION_KEY` en las variables de entorno del proceso que ejecuta Spring. `gmail.env.example` describe las variables; copiar un archivo `.env` por sí solo no hace que Spring lo cargue. Alternativamente usar propiedades privadas `app.gmail.*` en `application-local.properties`, que ya está ignorado por Git.
5. Generar una clave de 32 bytes criptográficamente aleatorios codificados como Base64. Guardarla en un lugar protegido y conservarla para reinicios y migraciones. No publicarla ni reemplazarla mientras existan tokens cifrados. Para generar una clave en PowerShell: `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` (PowerShell 7 / .NET moderno).

   Para configurar localmente sin pegar secretos en el chat, descargar el JSON del cliente web desde Google y ejecutar `./setup-gmail.ps1 -GoogleClientFile "RUTA_DEL_JSON"` desde backend. Este script guarda las credenciales en el archivo privado ignorado por Git y genera la clave de cifrado si no existe. No imprime secretos y conserva las propiedades de la base de datos existentes. No ejecutar ese script para un despliegue en servidor: allí se deben suministrar las variables de entorno y las URLs HTTPS del dominio.
6. Reiniciar Spring. Hibernate `ddl-auto=update`, ya usado por el proyecto, crea las columnas de Cuenta y las tablas `gmail_connections` y `bank_email_imports`. Respaldar SQL Server antes de actualizar.
7. Entrar a Cuentas con la sesión habitual. Preparar/guardar BAC VISA 3923 si no existe; disponible inicial $1,500 confirmado. Si hay compras nuevas antes de activar, ajustar el disponible para que el punto de partida coincida con el banco.
8. Pulsar **Conectar Gmail**, revisar el permiso de lectura en Google y autorizar. No introducir credenciales de Google en la aplicación.
9. Pulsar **Activar importación automática**. Solo desde este momento se contabilizan nuevas compras. El EML recibido para pruebas no se importa como una compra real.
10. Consultar el historial de importación. Los gastos automáticos aparecen también en Movimientos, Dashboard y Reportes cuando esas vistas vuelven a cargar sus datos.

El permiso Gmail de lectura autoriza técnicamente la lectura del buzón; la aplicación limita sus consultas al remitente BAC. No elimina correos, no los marca como leídos y no envía correos desde esa conexión.

## Servidor 24/7

El código tiene un perfil `server` y configuración por entorno; no hay un servidor desplegado ni un dominio elegido.

- Usar SQL Server persistente y copias de seguridad que incluyan las nuevas tablas. Mantener la misma clave de cifrado al trasladar la base de datos.
- Ejecutar el JAR con un servicio administrado (systemd, servicio Windows o contenedor con reinicio automático). Configurar `SPRING_PROFILES_ACTIVE=server`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y las variables de Gmail.
- Servir Angular y `/api` bajo el mismo origen HTTPS mediante proxy inverso. El frontend usa `/api` fuera del servidor de desarrollo en puerto 4200; se puede configurar otro endpoint con una etiqueta meta `financial-api-base`.
- Registrar en Google la URI del nuevo dominio y configurar `GMAIL_REDIRECT_URI=https://DOMINIO/api/gmail-bac/callback`, `GMAIL_FRONTEND_URL=https://DOMINIO/cuentas` y `APP_CORS_ORIGINS=https://DOMINIO`.
- Solo el proxy confiable debe alcanzar el backend cuando se habilita `server.forward-headers-strategy=framework`. Restringir el puerto interno. El perfil activa cookies HTTPS, HttpOnly y SameSite=Lax.
- Hibernate actualiza el esquema, como en local. Para un entorno administrado, validar la actualización contra una copia de la base antes de desplegar; no borrar ni recrear las tablas de historial.
- Restaurar/revisar logs y errores de sincronización, revocaciones de Google y saldo inicial. No registrar códigos OAuth, tokens o correos originales en logs.
- Las operaciones de cuentas, movimientos y Gmail exigen sesión activa y verifican el usuario de las rutas; la creación toma el usuario de la sesión, no el cuerpo recibido. Solicitudes financieras de orígenes ajenos se rechazan. La integración no sustituye una auditoría completa de seguridad para exposición pública.

**Google y funcionamiento continuo:** en modo de pruebas externo, los refresh tokens con permisos Gmail expiran normalmente a los siete días. Para operación sostenida hay que resolver el estado de publicación y los requisitos de Google para el permiso restringido, o reconectar cuando expire. Una instalación 24/7 no elimina esas condiciones. No está configurado ese proceso de Google desde este repositorio.

## Alcance y límites

- Solo compras aprobadas del formato BAC probado; no procesa automáticamente devoluciones, reversos, cuotas, intereses, pagos de tarjeta ni cambios de límite. Sus formatos requieren reglas propias.
- USD es la moneda de la tarjeta configurada; el aviso de ejemplo no contiene una etiqueta de moneda. No convierte compras de otra moneda.
- Si el usuario registra una compra manualmente y luego llega su aviso, no hay un identificador bancario compartido con el registro manual que garantice detectar esa duplicación. Evitar registrar manualmente compras de la tarjeta automatizada; revisar el historial si se hizo.
- Para compras iniciales ya descontadas del disponible, no importar los correos antiguos. En una migración trasladar saldo, movimientos, activación e historial juntos.
- Sin credenciales Google y autorización real la interfaz muestra configuración pendiente. Las pruebas del lector no equivalen a una conexión Gmail activa ni verifican una compra contra el banco.

## Pruebas

### Transferencias PAY a BAC

Configurar `PAY_USER_ID`, `PAY_IMPORT_SINCE` (instante UTC), `PAY_DESTINATION_LAST_FOUR` (número de cuenta bancaria, no tarjeta), `PAY_ORIGIN_NAME` y `PAY_DESTINATION_NAME`. La importación permanece desactivada sin esta configuración. La conexión Gmail debe estar activa. Ambas cuentas deben ser débito, estar activas, pertenecer al usuario configurado y tener nombres únicos; el destino debe estar vinculado a BAC.

Solo se procesa una transferencia completada de PAY hacia la cuenta bancaria BAC configurada. Se registra `TRANSFERENCIA` como tipo y descripción: resta de Pay y suma al destino, conservando fecha y sin ingreso ni gasto. Valida remitente y DMARC, y deduplica por el ID de transacción PAY dentro de la misma transacción de base de datos que actualiza los saldos. No procesa todavía abonos PAY, pagos a terceros, compras ni pagos de tarjeta. Los avisos no reconocidos quedan como ERROR para revisión, sin alterar saldos. Los registros manuales equivalentes no se pueden deduplicar automáticamente: evitar introducirlos manualmente.

Para probar el correo PAY original, ejecutar `PayTransferParserTest` con `-Dpay.sample.path=RUTA_DEL_EML`; `PayTransferImporterTest` cubre destino, transferencia, descripción fija, repetición, activación, usuario y saldo insuficiente.

### Nuevos movimientos y saldos iniciales confirmados

`GMAIL_EVENTS_SINCE` establece un nuevo inicio UTC para los avisos BAC cuando el usuario ya incorporó movimientos manualmente a sus saldos reales. La fecha de la operación se compara con este inicio, por lo que una compra o aviso de pago antiguo con fecha verificable se ignora aunque el correo llegue tarde. La fecha de recepción del correo no reemplaza a la fecha de la operación. Un aviso sin fecha fiable o sin regla verificada queda PENDIENTE, sin cambiar saldos.

Con `PAY_EXTENDED=true`, `PAY_OWNER_NAME` y un nuevo `PAY_IMPORT_SINCE`, PAY registra abonos confirmados como INGRESO, transferencias a terceros como GASTO y Pay → BAC asociado como TRANSFERENCIA. Una transferencia a otra cuenta propia sin asociación queda PENDIENTE; un abono que identifica al propio usuario como remitente también queda PENDIENTE para evitar considerarlo ingreso. Los abonos sin remitente se registran como INGRESO; si corresponden a fondos propios, requieren reclasificación con identificación del origen. La descripción de Pay → BAC siempre es TRANSFERENCIA. Los registros anteriores y sus saldos no se recalculan.

La búsqueda incluye avisos transaccionales BAC de otros remitentes del dominio, además de compras. Se exige autenticidad DMARC y los avisos no interpretables aparecen en el historial con enlace a Gmail. Pagos de tarjeta, devoluciones, reversos y otras operaciones BAC aún requieren muestras verificadas para aplicar sus efectos automáticamente. No se puede detectar una operación que el banco no notifique por correo; el sistema no promete cubrir todos los movimientos sin una fuente adicional.

Ejecutar `mvn -Dtest=BacEmailParserTest,BacPurchaseImporterTest,GmailTokenCipherTest,CuentaCreditoServiceTest,MovimientoCompraTest,FinancialSessionInterceptorTest,ReporteControllerTest,ReporteServiceTest test`.

Para comprobar un archivo original sin copiarlo al repositorio, agregar `-Dbac.sample.path=RUTA_DEL_EML`. Los casos cubren formato quoted-printable, remitente/DMARC, estado aprobado, fechas, compras anteriores, pausa, tarjeta no asociada, duplicados y cifrado. Antes de activar en producción también hay que verificar OAuth real y transacciones/bloqueos con SQL Server.

Fuentes oficiales: [OAuth para servidores web](https://developers.google.com/identity/protocols/oauth2/web-server), [lectura de Gmail](https://developers.google.com/workspace/gmail/api/guides/list-messages), [caducidad de refresh tokens](https://developers.google.com/identity/protocols/oauth2), [permisos Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes).
