# Avisos de autorización de Gmail

COFI distingue el permiso de lectura de movimientos (por usuario) del permiso de envío de recordatorios (configuración de la instalación).

Una autorización rechazada con `invalid_grant`, un rechazo de autenticación de Gmail o la falta de permisos de Gmail generan el estado `REAUTH_REQUIRED`. Las interrupciones de red, los errores temporales y los límites de cuota no solicitan una nueva autorización. Los mensajes de Google no se exponen en la interfaz ni se guardan como errores completos.

## Detección y aviso

- La lectura se comprueba durante la sincronización habitual de cada conexión activa. La necesidad de reconectar queda guardada en su estado existente y desaparece después de reconectar o sincronizar correctamente.
- El permiso de envío se comprueba sin enviar mensajes: al iniciar el backend, después de unos 30 segundos, y cada 15 minutos. Un envío real también actualiza el estado. Al detectar un permiso inválido se evitan comprobaciones repetidas inútiles; renovar la credencial y reiniciar el backend vuelve a comprobarla.
- El navegador consulta el estado cada minuto y al volver a la ventana. Muestra un aviso persistente en las vistas autenticadas; no hay un modal que se repita cada minuto. Para lectura incluye un enlace a Cuentas. Para envío explica que la cuenta emisora debe ser autorizada de nuevo por quien administra la instalación.
- El modo `preparacion-local` no hace comprobaciones externas del permiso de envío. El perfil `migration` mantiene desactivada la planificación de tareas.

Los endpoints de estado requieren sesión. El estado compartido del envío no revela dirección emisora, credenciales ni respuestas del proveedor. No requiere cambios en las tablas de la base de datos.

Este aviso no evita la expiración ni garantiza entrega al teléfono. Las autorizaciones de Gmail emitidas mientras el proyecto OAuth de Google está en modo Pruebas pueden vencer a los siete días. El estado «En producción» y las verificaciones que correspondan deben revisarse aparte; esta mejora no cambia la configuración de Google ni Railway.
