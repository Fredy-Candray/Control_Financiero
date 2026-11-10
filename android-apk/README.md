# APK Android de pruebas locales

## Versión 0.6: Ahorro y Liquidez

Incluye el módulo conectado al backend y la base local: metas, reservas virtuales, gastos fijos, presupuestos y alertas internas. Requiere la migración `V20261109__ahorro_liquidez.sql` y el backend actualizado; no contiene datos de ejemplo ni cuentas preconfiguradas. Conserva el splash de 2 segundos.

## Versión 0.5: organización de finanzas

Incluye «Comparar con el banco» dentro de Cuentas, «Avisos por revisar» dentro de Movimientos y «Dinero prestado» independiente en el menú. Mantiene el splash de 2 segundos. No requiere cambios de base de datos.

## Versión 0.4: splash screen

En 0.4.3 la presentación permanece visible durante 2 segundos y luego se desvanece. Para cambios exclusivamente nativos, `build.ps1 -WebAssets <carpeta-apk-assets>` permite reutilizar de forma explícita una interfaz compilada sin modificar el frontend.

Arranque con el logo de COFI centrado sobre azul marino y transición suave al inicio. Android 12 o posterior utiliza su splash nativo; Android 8–11 muestra la presentación de marca con el nombre y lema. No realiza llamadas de red ni comprueba el servidor durante esta pantalla. Al recrear la actividad no se repite la presentación. Se conserva la firma de pruebas para actualizar la instalación anterior.

La preparación de la variante de publicación, su estado de validación y los pendientes reales están en [PUBLICACION.md](PUBLICACION.md). El archivo APK generado por `build.ps1` sigue siendo exclusivamente de pruebas locales.

## Versión 0.3 local

Mejora el inicio y la barra superior, incluye ayuda para la dirección y una explicación de privacidad. Acepta IP privada y puerto sin escribir el protocolo y rechaza servidores públicos. Bloquea enlaces y recursos externos dentro de la app local. El cambio de servidor limpia cookies y almacenamiento web de la instalación anterior. No consulta servicios remotos al abrir.

Esta app instala un cliente Android real, con icono y WebView propio. No utiliza Railway. El servidor y la base de datos siguen en la laptop: no hay una copia de los saldos dentro del APK.

## Versión 0.2

Incluye la interfaz compilada de COFI dentro del APK y una pantalla de inicio que abre sin conexión. No contiene direcciones remotas preconfiguradas ni credenciales. Solo consulta el servidor cuando pulsas «Conectar a COFI». Comprueba `/api/auth/me` sin enviar credenciales, con tiempo de espera limitado, antes de abrir el sistema. Si falla, permite corregir la dirección o reintentar en la misma pantalla.

Las operaciones y los saldos siguen requiriendo el backend disponible. Incluir la interfaz no convierte el teléfono en servidor ni implementa movimientos sin conexión. En las pruebas locales, la computadora debe estar encendida. La disponibilidad permanente se configurará por separado cuando el propietario lo solicite.

La compilación incluye el frontend actual desde los paquetes ya instalados, verifica la firma del APK y genera un archivo SHA256. No descarga dependencias. Las comprobaciones de validación pueden ejecutarse con `javac` sobre `src/app/micofi/android/ServerAddress.java` y `tests/app/micofi/android/ServerAddressTest.java` y después `java app.micofi.android.ServerAddressTest` usando la carpeta de clases como classpath.

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
