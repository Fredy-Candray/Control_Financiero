# Preparación local de recordatorios WhatsApp

Esta entrega prepara el código y permite revisar un mensaje sin enviar nada. No crea cuentas de Meta, no instala servicios, no solicita credenciales, no publica cambios y no modifica los eventos, saldos o entregas de la base de datos durante las pruebas unitarias.

## Vista previa

En Calendario, abrir una actividad, seleccionar WhatsApp, completar teléfono, título, fecha y hora y pulsar **Vista previa sin enviar**. Se muestran el destino normalizado y el mensaje. Esta operación no guarda el formulario, no modifica el teléfono del perfil ni registra un envío. Para agendar la actividad se utiliza el botón de guardar normal, que sí conserva su comportamiento de persistencia.

Los números de ocho dígitos se interpretan como El Salvador (+503). Para otros países es necesario indicar + y el código de país. La vista previa es un borrador: el mensaje definitivo dependerá de la plantilla que se configure posteriormente.

## Mantener el modo local

Arrancar el backend con `--spring.profiles.active=preparacion-local` para bloquear los envíos programados de correo y WhatsApp y la importación automática de correo. Si ya hay un backend ejecutándose, los archivos modificados no cambian su configuración hasta recompilarlo y reiniciarlo. No ejecutar dos backends sobre los mismos datos.

WhatsApp también tiene un interruptor independiente `app.whatsapp.enabled`, desactivado por defecto. Aunque haya credenciales, no realiza solicitudes hasta habilitarlo explícitamente. El estado de configuración muestra campos faltantes, nunca sus valores.

## Configuración pendiente para otra red

El archivo `backend/whatsapp-local.properties.example` solo documenta los campos; no se carga automáticamente. Más adelante habrá que obtener y validar en Meta el número emisor, el acceso, la versión vigente de API y una plantilla. No se han validado ni contratado esos servicios en esta entrega.

Borrador del cuerpo de plantilla con tres parámetros, en este orden:

```
COFI · Recordatorio
Actividad: {{1}}
Fecha: {{2}}
Hora: {{3}} (El Salvador)
```

Las credenciales reales deben guardarse en variables de entorno o en `backend/application-local.properties`, excluido de Git. No ponerlas en el frontend, APK ni archivos versionados. La aceptación de un mensaje por el proveedor no prueba que el teléfono lo haya recibido; confirmar entrega real requerirá una prueba posterior y, si se desea seguimiento, implementar sus confirmaciones.

## Verificación sin internet

Con las dependencias ya instaladas, desde `backend`: `mvn -o -Dtest=WhatsAppReminderTest,CalendarEmailDeliveryTest test`. Las pruebas utilizan repositorios y transporte simulados: no envían mensajes ni usan la base real. Desde `frontend`: `npm run build` usa las dependencias locales existentes; no ejecutar `npm install` si se quiere evitar descargas.
