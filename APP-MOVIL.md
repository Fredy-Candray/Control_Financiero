# COFI en Android y iPhone

La aplicación instalable (PWA) utiliza el mismo sitio HTTPS, backend y base de datos que COFI web. No necesita APK ni una cuenta adicional. No se publica en Play Store ni App Store.

## Instalación

- Android: abrir https://micofi.up.railway.app en Chrome, pulsar «Instalar COFI» y después «Instalar aplicación». Si el navegador no ofrece el aviso, usar su menú ⋮ → Instalar aplicación o Agregar a la pantalla principal.
- iPhone: abrir la misma dirección en Safari → Compartir → Agregar a inicio. Activar «Abrir como app web» si aparece y pulsar Agregar.
- Abrir COFI desde el icono e iniciar sesión con las mismas credenciales. La sesión inicial puede ser independiente de la del navegador.

## Funcionamiento

Las cuentas, movimientos, informes y conexión con Gmail pertenecen al mismo sistema de Railway. La importación de correos continúa en el servidor aunque se cierre la app, mientras el servicio esté operativo. Las notificaciones dentro de COFI se muestran con la app abierta; esta entrega no agrega notificaciones push con la app cerrada.

Se necesita internet para leer o registrar datos. El service worker solo utiliza la red, no escribe Cache Storage ni almacena respuestas de API o informes. Si falla la navegación, muestra un aviso sin información financiera. Se conserva el mecanismo de sesión y la opción «Mantener sesión iniciada» existente.

Las actualizaciones de COFI se reciben al abrir o recargar la app. No hay una copia independiente del frontend que deba distribuirse por tiendas. La instalación no cambia las limitaciones de disponibilidad del alojamiento Railway.

## Verificación

Desde `frontend`: `node scripts/verify-pwa.cjs` y `npm run build`. Verificar en HTTPS el manifiesto `/manifest.webmanifest`, el worker `/cofi-sw.js`, la instalación real en Android/iPhone y que la sesión y descarga de PDF funcionen en el dispositivo. La emulación de tamaño de pantalla no sustituye la instalación en teléfonos reales.
