# COFI: prueba gratuita en Railway

Esta configuración crea una instalación nueva con PostgreSQL. No migra SQL Server,
cuentas reales, movimientos, imágenes personales ni autorizaciones de Gmail.
El perfil local de SQL Server continúa disponible.

## Servicios

1. Proyecto `COFI-prueba`, en el plan Free/Trial, sin tarjeta ni actualización a Hobby.
2. Un PostgreSQL NUEVO dentro del mismo proyecto, con volumen persistente.
3. Un servicio COFI construido desde el Dockerfile de la raíz del repositorio.
   No configurar `backend` ni `frontend` como directorio raíz.
4. Una sola réplica. Usar el dominio HTTPS generado por Railway.

La imagen reúne Angular y Spring Boot: el navegador accede a `/api` en el mismo
dominio. Las rutas de Angular se pueden abrir y recargar directamente.

## Variables del servicio COFI

Configurar referencias al servicio PostgreSQL (sustituir su nombre si es distinto):

```text
SPRING_PROFILES_ACTIVE=railway
PGHOST=${{Postgres.PGHOST}}
PGPORT=${{Postgres.PGPORT}}
PGDATABASE=${{Postgres.PGDATABASE}}
PGUSER=${{Postgres.PGUSER}}
PGPASSWORD=${{Postgres.PGPASSWORD}}
```

Railway proporciona `PORT` y `RAILWAY_PUBLIC_DOMAIN`. El healthcheck es
`/api/health`. La base debe quedar en la red privada: no crear un proxy TCP público.
No copiar `application-local.properties`, `.env`, tokens ni contraseñas locales.
La lista permitida de `.dockerignore` evita incluirlos en el contexto Docker.

## Ajustes del servicio en Railway

Configurar en Settings (la plataforma ya no admite Config as Code para nuevos
servicios; no depender de `railway.toml`):

- Builder: Dockerfile; Dockerfile path: `Dockerfile`.
- Directorio raíz: raíz del repositorio.
- Healthcheck path: `/api/health`; timeout: 180 segundos.
- Restart policy: On Failure, con 3 reintentos.
- Réplicas: 1; no contratar regiones ni recursos adicionales.
- Dominio HTTPS gratuito generado por Railway, apuntando al puerto 8080.

El perfil de prueba deshabilita Gmail, PAY y el envío de correos/WhatsApp aunque
existan las variables habituales. Para correo real se requiere otro despliegue,
revisión de seguridad y una migración planificada que evite importaciones duplicadas.

## Verificación

- Revisar que el plan siga siendo Free/Trial, el saldo de crédito y el consumo.
- Esperar a que PostgreSQL y COFI estén activos y el healthcheck pase.
- Abrir el dominio HTTPS; crear una cuenta con datos ficticios y contraseña propia
  que no se use en otros servicios.
- Agregar cuentas y movimientos de prueba; recargar y verificar persistencia.
- Comprobar sesión, cierre de sesión, rutas directas y acceso desde móvil.
- Verificar que `/api/cuentas/usuario/1` y `/api/gmail-bac/notifications` rechacen
  peticiones sin sesión. No publicar una cuenta con contraseña de ejemplo.

## Límites y operación

La gratuidad tiene límites de recursos y crédito; no garantiza operación 24/7.
No habilitar planes de pago automáticamente ni agregar complementos de pago.
Para consultar Gmail continuamente en el futuro, desactivar la suspensión del
servicio, medir el costo y revisar el estado OAuth de Google.
Los respaldos de datos reales y una restauración comprobada son requisitos de
la futura migración. No borrar el sistema local al probar Railway.

El despliegue de prueba no equivale a una auditoría completa ni a autorización
para exponer los datos bancarios reales.
