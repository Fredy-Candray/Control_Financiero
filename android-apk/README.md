# APK Android de pruebas locales

Esta app instala un cliente Android real, con icono y WebView propio. No utiliza Railway. El servidor y la base de datos siguen en la laptop: no hay una copia de los saldos dentro del APK.

## Compilar

Requiere JDK, Android SDK Platform 35 y Build Tools 35.0.0 ya instalados y sus licencias aceptadas. Desde la raíz ejecutar `./android-apk/build.ps1`. Opcionalmente indicar `-AndroidSdk` y `-Jdk`.

Produce `output/android-apk/COFI-pruebas.apk`, firmado con una clave local desechable de pruebas. Esa carpeta está excluida de Git. No utilizar esta firma para producción o tiendas.

## Servidor e instalación

En la laptop se puede ejecutar `INICIAR-COFI-ANDROID.cmd` para iniciar los procesos locales y mostrar las direcciones. No abre túneles, no cambia el firewall y mantiene desactivada la importación de correos en este backend de pruebas. Si ya hay un backend en el puerto 8081, conserva el proceso y sus ajustes existentes.

1. Ejecutar el backend local de COFI en el puerto 8081. Conservar las opciones de importación ya configuradas; no ejecutar dos importadores sobre los mismos avisos.
2. Compilar el frontend: desde `frontend`, `npm run build`.
3. Desde la raíz: `node frontend/scripts/serve-mobile.cjs`. Si se compiló a otra carpeta, indicar como argumento su carpeta `browser`.
4. El comando muestra la IP de la laptop y el puerto 8082. Conectar el teléfono a la misma red Wi-Fi y abrir `http://IP:8082/instalar` para descargar el APK.
5. Instalar y abrir «COFI Pruebas», introducir `http://IP:8082` y entrar con las credenciales locales. Android puede pedir autorización para instalar el archivo; la completa el propietario del teléfono.

Para detener el servidor, Ctrl+C. Si Windows bloquea el acceso, revisar una regla limitada al puerto 8082 en una red privada; no desactivar el firewall. La IP puede cambiar al reconectar: se actualiza desde «Servidor» en la app.

## Alcance de esta entrega

- Android 8/API 26 o posterior, con Android System WebView actualizado.
- Gestión de cuentas y movimientos usando el mismo frontend y API locales. Los datos locales pueden diferir de los de la instalación remota.
- Selector de imágenes mediante el selector del sistema; no requiere acceso general a archivos ni cámara.
- Guardado de PDF mediante el selector del sistema, con límite de 20 MiB y validación del encabezado PDF. Se utiliza un canal WebMessage dirigido al origen configurado, sin exponer una interfaz JavaScript Java global.
- HTTP solo se admite para IP privadas como entorno de pruebas; HTTPS requiere certificado válido. Usar una red de confianza. No se ignoran errores de certificados.
- La autorización/reconexión de Gmail debe realizarse desde el navegador de la laptop durante estas pruebas: Google no admite su autorización dentro de un WebView. El procesamiento existente pertenece al servidor y no requiere mantener abierta la app.
- Las notificaciones con la app cerrada no están implementadas. No se prometen equivalencias con notificaciones push nativas.
- La compilación y firma se verifican automáticamente. La instalación real, sesión, subida de imágenes y descarga de informes deben probarse en el teléfono antes de considerar la app validada.

La app no solicita permisos de SMS, contactos, ubicación ni micrófono. La selección del servidor es local; al cambiarlo elimina las cookies de la sesión de la app. La página de descarga expone solo el APK, no la clave de firma.
