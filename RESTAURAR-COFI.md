# Restaurar COFI en otra computadora

El código se obtiene desde GitHub. La base se traslada por separado mediante el respaldo `.bak`; contiene usuarios, cuentas, saldos, movimientos, imágenes y las nuevas tablas de control y préstamos. El respaldo completo ya incluye las migraciones aplicadas: no hace falta recrear las tablas manualmente.

## Restaurar la base

1. Instalar SQL Server 2022 o una versión posterior compatible, y SQL Server Management Studio (SSMS). Un respaldo de SQL Server 2022 no se restaura en SQL Server 2019 ni anteriores.
2. Copiar el `.bak` y su archivo `.sha256` a la otra computadora. Comprobar el hash con `Get-FileHash -LiteralPath 'C:\ruta\respaldo.bak' -Algorithm SHA256` y compararlo con el `.sha256`.
3. En SSMS, conectarse a la instancia local con una cuenta que pueda restaurar bases. Ir a **Bases de datos → Restaurar base de datos → Dispositivo**, y seleccionar el `.bak`. El servicio de SQL Server necesita acceso de lectura a esa carpeta; puede usarse la carpeta de respaldos de la instancia.
4. Elegir `ControlFinanciero` como destino. En **Archivos**, reubicar los archivos de datos y log a carpetas de la nueva instancia; no conservar rutas de la computadora anterior. Si ya existe una base con datos en el destino, respaldarla y restaurar la copia con otro nombre para revisarla antes de sustituirla. No activar la sobrescritura de una base existente sin haber revisado cuál se reemplazará.
5. Completar la restauración y verificar que aparezcan las tablas, incluidas `prestamos_otorgados` y `conciliaciones_saldo`.

## Conectar la aplicación

1. Actualizar el proyecto con `git pull origin main`. Usar Java 17, Maven y la versión de Node compatible con el frontend del proyecto.
2. Crear el inicio de sesión SQL que utilizará la aplicación, con una contraseña propia configurada localmente. El usuario de base `control_financiero` ya viene dentro del respaldo; si se usa un inicio de sesión del mismo nombre, corregir su asociación en SSMS o ejecutar:

```sql
USE [ControlFinanciero];
ALTER USER [control_financiero] WITH LOGIN = [control_financiero];
```

3. Configurar `backend/application-local.properties` con el servidor, puerto o instancia y credenciales de esa computadora. El puerto `63646` del código es el de la instalación original y puede ser distinto. Para conservar el esquema restaurado, usar `spring.jpa.hibernate.ddl-auto=validate`.
4. Instalar dependencias del frontend con `npm ci`, compilar/iniciar el backend y abrir COFI. Comprobar cuentas y movimientos antes de registrar operaciones nuevas. Las contraseñas de los usuarios de COFI se conservan en el respaldo; el inicio de sesión SQL es una credencial distinta.

## Gmail y configuración privada

Las claves OAuth, la clave de cifrado y la configuración privada no están en GitHub. Para reutilizar el permiso Gmail guardado en la base restaurada se necesita la misma clave de cifrado, además de la configuración OAuth y las asociaciones bancarias. El permiso de envío de recordatorios también depende de la configuración local privada. Estos valores se trasladan por un medio privado, nunca mediante un commit ni pegándolos en un archivo público.

El respaldo es una fotografía de la base al momento de crearlo. Dos instalaciones locales no sincronizan entre sí los movimientos manuales, préstamos ni saldos. Elegir cuál será la instalación principal para el uso diario y la lectura de avisos Gmail antes de trabajar en ambas.

Los respaldos y el paquete de traslado se guardan en `output/`, excluido de Git. La verificación `RESTORE VERIFYONLY WITH CHECKSUM` comprueba la integridad del respaldo; la restauración en la otra computadora se hace siguiendo los pasos anteriores.
