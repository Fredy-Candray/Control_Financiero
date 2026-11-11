# Restablecimiento de contraseñas

COFI conserva dos opciones independientes:

1. En Usuarios, un administrador activo puede pulsar el ícono de llave y confirmar el restablecimiento de una cuenta activa. La contraseña temporal será `cofi`, en minúsculas, almacenada con BCrypt. Ninguna cuenta existente se cambia automáticamente al instalar esta mejora.
2. Desde «¿Olvidaste tu contraseña?» se solicita el enlace al correo registrado. El enlace vence en 30 minutos y permite elegir directamente una contraseña personal. El envío necesita que la cuenta emisora de COFI conserve su autorización vigente.

Después de entrar con la contraseña temporal, el usuario debe crear y confirmar una nueva de al menos ocho caracteres. Hasta completarlo, el backend impide consultar o modificar sus datos financieros. La nueva contraseña tiene un máximo de 72 bytes UTF-8 por el formato BCrypt.

Ambas opciones incrementan la versión de acceso: las sesiones anteriores dejan de ser válidas y los enlaces de recuperación pendientes se invalidan. Después del cambio personal se inicia sesión de nuevo. El administrador también puede restablecer su propia cuenta; en ese caso sale del sistema para ingresar con la temporal.

Migración local: `backend/sql/V20261110__restablecer_password.sql`. Agrega `cambio_password_pendiente` y `version_acceso` a `usuarios`, con valores iniciales que conservan el acceso de las cuentas existentes. Restaurar el respaldo actualizado o aplicar esta migración antes de iniciar el backend nuevo en otra computadora.

El correo de recuperación existente se conserva; esta mejora no cambia Gmail ni publica el sistema. Las pruebas de envío utilizan servicios simulados y no envían mensajes reales.
