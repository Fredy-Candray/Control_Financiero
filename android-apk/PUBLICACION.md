# COFI Android: preparación de publicación

## Estado real al 9 de noviembre de 2026

La APK local está compilada y firmada para pruebas (0.4 incorpora splash screen). Incluye interfaz, acceso a la API local, selector de imágenes y exportación de PDF. No es el artefacto de publicación ni funciona sin un backend. No se ha conectado ni desplegado en Railway, Google Play u otro alojamiento durante esta preparación. No se han modificado saldos, movimientos ni permisos de correo.

La configuración Gradle separa `local` de `production`. Producción desactiva HTTP y el selector arbitrario del servidor. Requiere un origen HTTPS fijado al compilar y firma de subida propia. La app no ignora errores TLS, no permite depuración WebView, bloquea recursos de otros orígenes y conserva la API fuera del paquete. El APK no contiene tokens de Gmail ni claves de la base de datos. Los archivos de firma quedan excluidos de Git.

En modo local solo se admiten IP privadas. Los enlaces externos y las comprobaciones de Safe Browsing del WebView están deshabilitados para que la app no las inicie durante las pruebas locales. La versión de publicación conserva Safe Browsing. Android y otras aplicaciones del teléfono tienen sus propias conexiones y no están controladas por COFI.

## Comprobado y pendiente

- Comprobado: compilación Angular, compilación Java/DEX, firma APK v2/v3 y 36 comprobaciones offline de URL, origen y rutas de recursos.
- Pendiente: prueba instalada en teléfonos reales (inicio de sesión, cuentas, movimientos, permisos mínimos, imágenes, PDF, rotación, accesibilidad, desconexión y recuperación). Compilar no equivale a validar todos esos flujos.
- Gradle de publicación: se intentó exclusivamente `--offline`. Falta en la caché `org.jetbrains.kotlin:kotlin-reflect:2.2.10`, dependencia de Android Gradle Plugin 9.1.0. No se descargó. El proyecto Gradle y el AAB aún no han podido compilarse ni validarse.
- El SDK objetivo está configurado en 35, que está instalado. Antes de publicar se deben comprobar los requisitos vigentes de Play y actualizarlo si corresponde. No se consultaron requisitos externos en esta sesión.

## Compilación local sin servicios externos

La ruta comprobada para producir una APK de pruebas es `build.ps1`, usando JDK 17 y Android SDK 35 / build-tools 35.0.0. Utiliza las dependencias Angular ya instaladas.

La ruta para un futuro Android App Bundle es Gradle con JDK 21, Gradle 9.3.1 y Android Gradle Plugin 9.1.0. Mientras se mantenga la restricción de red, todos sus artefactos deben estar disponibles localmente. No ejecutar compilaciones sin `--offline` en esta red.

Una vez disponibles las dependencias y acordado el servidor, configurar estas variables en la sesión local, sin escribir sus valores en archivos versionados ni pasarlos como argumentos:

- `ANDROID_HOME`: SDK instalado.
- `JAVA_HOME`: JDK 21 instalado.
- `COFI_RELEASE_ORIGIN`: origen HTTPS definitivo, sin rutas, query, usuario ni contraseña.
- `COFI_UPLOAD_KEYSTORE`: ruta de la identidad de firma propia.
- `COFI_UPLOAD_ALIAS`: alias de esa clave.
- `COFI_UPLOAD_STORE_PASSWORD` y `COFI_UPLOAD_KEY_PASSWORD`: contraseñas de firma.

Desde esta carpeta, con la distribución local de Gradle: `gradle --offline --no-daemon lintProductionRelease bundleProductionRelease`.

La configuración detiene el build de producción si falta HTTPS o la firma. No reutilizar `output/android-apk/cofi-testing.keystore`. No se ha creado una identidad definitiva: debe elegirse su titular y conservar un respaldo privado antes de usarla. El identificador configurado es `app.micofi.android`; confirmar su propiedad y disponibilidad antes de publicar. Incrementar `versionCode` en cada nueva entrega.

## Lo necesario antes de subir a la tienda

1. Resolver la dependencia faltante y validar compilación, análisis estático y firma del AAB.
2. Probar todos los flujos móviles en dispositivos reales sin crear operaciones ficticias en las cuentas reales.
3. Disponer de backend definitivo accesible y revisado: HTTPS, sesiones, aislamiento entre usuarios, recuperación, respaldos y seguridad. La laptop local no proporciona disponibilidad permanente.
4. Confirmar titular, contacto de soporte, nombre del paquete, política de privacidad y tratamiento de los datos. Preparar las opciones de eliminación de cuenta y datos que correspondan al producto y a las reglas vigentes; no están implementadas por este cambio de APK.
5. Completar las declaraciones de Play según el comportamiento real de la app y del servidor: datos financieros, cuenta, correo, imágenes, sesiones, proveedores y finalidad. No declarar «no recopila datos» solo porque la APK no incorpora un SDK de analítica.
6. Preparar capturas reales y material de ficha. La descripción no debe prometer funcionamiento offline, importación para todos los bancos ni notificaciones push con la app cerrada: esas funciones no están implementadas.
7. Revisar los requisitos y políticas vigentes en una red autorizada. La aprobación de Google Play no se puede garantizar mediante una compilación local.

## Texto de ficha propuesto (borrador)

**Nombre:** COFI · Control Financiero

**Descripción breve:** Organiza tus cuentas, gastos, préstamos y recordatorios en un solo lugar.

**Descripción:** COFI te ayuda a consultar tus cuentas y tarjetas, registrar movimientos, revisar gastos por categoría y período, dar seguimiento al dinero prestado y organizar actividades con recordatorios. Exporta informes PDF y mantén una visión clara de tus finanzas personales. Requiere una cuenta y conexión a una instalación de COFI disponible. Las importaciones de correo dependen de los formatos compatibles y de la autorización de Gmail; su procesamiento pertenece al servidor.

Este texto requiere revisión final según la instalación ofrecida al público. No se ha publicado ni enviado a Google.
