# COFI: migración privada y controles de despliegue

## Perfiles

`railway` conserva la instalación de prueba en el esquema `public`, sin Gmail.
Para datos reales se utiliza `SPRING_PROFILES_ACTIVE=railway,cloud`, con PostgreSQL
privado, TLS y `DB_SCHEMA` apuntando al esquema previamente verificado.
`COFI_MAINTENANCE` comienza en `true`; `GMAIL_IMPORT_ENABLED` comienza en `false`.
No habilitar ninguno hasta verificar el respaldo y pausar el importador local.

## Respaldo y restauración

`CloudArchiveTool` exporta las catorce tablas de SQL Server en una transacción de
lectura consistente. Conserva identificadores, saldos, imágenes, registros de
deduplicación y fechas de inicio de importación. Convierte contraseñas antiguas
sin hash a BCrypt en la copia, conservando la contraseña que utiliza la persona.

El respaldo se comprime y cifra con AES-256-GCM. La clave viaja por una variable
privada y nunca dentro de la imagen o el repositorio. El archivo local de clave
está protegido con Windows DPAPI para la cuenta que realiza la migración.
Los respaldos y herramientas temporales permanecen en `output/` y `tmp/`, ignorados
por Git y excluidos del contexto Docker del sitio.

La tarea temporal utiliza `railway,cloud,migration`, sin servidor web ni procesos
programados. Solo admite conexiones PostgreSQL con host `*.railway.internal`.
Crea un esquema separado; rechaza destinos con datos de negocio existentes.
Restaura en una transacción y compara huellas SHA-256 de todas las filas antes
de confirmar. No recalcula saldos. Restablece las secuencias de identificadores.
Eliminar la tarea y su clave privada al finalizar. Conservar el respaldo cifrado
y la clave DPAPI por separado del repositorio para recuperación.

## Controles aplicados

- Sesión activa requerida para toda API privada; aislamiento por usuario y rol.
- Comprobación de propiedad al editar eventos y asociar cuentas del calendario.
- Rotación de sesión al iniciar sesión y duración limitada.
- Registro público deshabilitado en el perfil real.
- Cookies HTTPS, HttpOnly y SameSite; comprobación de origen en escrituras.
- Límite de intentos de inicio de sesión y recuperación por dirección IP.
- CSP, restricción de marcos, ausencia de caché en respuestas de API y errores
  sin detalles internos.
- Actualización de Angular y comprobación de dependencias de producción.

Estas comprobaciones no equivalen a una garantía absoluta ni a una auditoría
externa de penetración. El límite de intentos es por instancia y se reinicia con
el proceso. Las sesiones también se pierden al reiniciar el servidor.

## Gmail y operación

Guardar OAuth y la clave de cifrado solamente en variables privadas de Railway.
Conservar las asociaciones BAC/PAY y los registros de correos ya procesados.
Nunca ejecutar importadores locales y de nube sobre copias independientes activas.
La copia local queda como referencia; desactivar su importador después del corte.

Para reconectar Gmail, el cliente OAuth de Google debe admitir el callback HTTPS
`/api/gmail-bac/callback` del dominio de COFI. Una conexión existente puede funcionar
con su refresh token, pero una reconexión necesita ese callback registrado.
No transferir SMTP sin autorización independiente: enviar notificaciones por correo
y leer avisos bancarios son configuraciones distintas.

Railway Free/Trial bloquea SMTP. Para conservar los recordatorios por correo y la
recuperación de contraseña sin cambiar de plan, `MAIL_PROVIDER=gmail-api` utiliza
HTTPS y un permiso independiente `gmail.send` de la cuenta remitente.
`MAIL_FROM` identifica esa cuenta; `GMAIL_SENDER_REFRESH_TOKEN` contiene su refresh
token cifrado con `GmailTokenCipher`. No usar el token de lectura bancaria para enviar.
El consentimiento del remitente se obtiene con PKCE, verificando su dirección de
correo antes de guardar el permiso cifrado. No se almacena su contraseña de Gmail.
El entorno local conserva `MAIL_PROVIDER=smtp` por defecto.

El plan gratuito o de prueba de Railway depende de sus cuotas. No activar planes
pagados automáticamente; comprobar disponibilidad antes de depender de él de forma
permanente. Preparar respaldos periódicos para el uso continuado.
