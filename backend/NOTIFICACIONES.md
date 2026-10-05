# Recordatorios del calendario

Los eventos y sus canales de aviso se guardan en SQL Server. Spring crea o actualiza las tablas al iniciar el backend (`spring.jpa.hibernate.ddl-auto=update`). Los eventos que ya existían en el navegador se migran al servidor cuando se abre Calendario por primera vez.

En cada evento se puede activar la notificación del navegador, correo electrónico y/o WhatsApp. El correo usa el correo registrado del usuario. El teléfono se guarda en el perfil cuando se habilita WhatsApp; escríbelo con prefijo internacional, por ejemplo `+50370000000`. WhatsApp requiere aceptación del usuario para recibir mensajes.

## Configuración de correo

Configura `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `MAIL_FROM` en el entorno del backend. Hay un ejemplo en `notifications.env.example`. No agregues credenciales reales al repositorio. Sin estos valores, los recordatorios de correo permanecen desactivados.

La misma configuración SMTP se usa para recuperar contraseñas. El enlace se envía al correo de la cuenta, vence a los 30 minutos y solo se puede usar una vez. `PASSWORD_RESET_URL` debe apuntar al frontend; para desarrollo local es `http://localhost:4200/reset-password`. Hibernate crea la tabla de tokens al iniciar Spring, porque el proyecto usa `spring.jpa.hibernate.ddl-auto=update`.

## Configuración de WhatsApp

La integración usa Meta WhatsApp Cloud API. Configura `WHATSAPP_GRAPH_VERSION`, `WHATSAPP_PHONE_NUMBER_ID` y `WHATSAPP_ACCESS_TOKEN`. En WhatsApp Manager, registra y obtiene aprobación para una plantilla de idioma español llamada `calendar_reminder` con el cuerpo:

`Recordatorio: {{1}} el {{2}} a las {{3}}.`

La plantilla debe tener los tres parámetros de texto, en ese orden. Si eliges otro nombre o idioma aprobado, actualiza `WHATSAPP_TEMPLATE` y `WHATSAPP_TEMPLATE_LANGUAGE`. Meta envía plantillas mediante el endpoint `/messages` con una plantilla aprobada; consulta la [colección oficial de WhatsApp Cloud API de Meta](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api).

## Ejecución

El backend comprueba los recordatorios cada minuto, registra cada entrega para evitar duplicados y vuelve a intentar entregas fallidas mientras el evento siga vigente. El servidor debe permanecer encendido para enviar correo y WhatsApp. Las notificaciones de navegador requieren que Calendario esté abierto en el navegador y que el usuario haya concedido el permiso del sistema.
