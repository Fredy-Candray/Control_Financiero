# Categorías del calendario en la base de datos

`categorias_calendario` guarda nombre, color e identificador por usuario. Sus claves son independientes de las categorías de movimientos financieros. La clave compuesta permite conservar `meeting`, `payment`, `task`, `event` y los identificadores personalizados sin compartir categorías entre usuarios.

## SQL Server local

Con el backend detenido, respaldar la base y ejecutar `backend/sql/V20261028__categorias_calendario.sql` contra la base local. Es una migración aditiva y repetible: crea la tabla, recupera las claves ya usadas por las actividades y agrega una clave foránea compuesta para impedir referencias entre usuarios. No modifica saldos ni movimientos.

Después iniciar el backend y abrir COFI en el navegador donde se crearon las categorías. La primera sincronización importa los nombres y colores locales y conserva una copia local anterior en la clave `.legacy-backup.v1`. El marcador `.database.v1` se escribe solo cuando el servidor confirma la importación. Si falla, el contenido local sigue disponible y puede reintentarse. Los nombres de categorías personalizadas sin copia local no se pueden deducir de los eventos; se muestran como categorías recuperadas hasta importarlas o editarlas.

La base de datos es la fuente principal. `localStorage` conserva una caché para mostrar los recordatorios; otro navegador consulta la misma base con la sesión del usuario. Una importación posterior no sobrescribe ediciones hechas en el servidor. Las categorías eliminadas conservan un registro inactivo para que un navegador antiguo no las vuelva a crear.

## Integridad

Los endpoints requieren una sesión válida; el usuario nunca se toma del cuerpo de la petición. Se validan nombres y colores en el servidor. Las operaciones bloquean la fila del usuario para coordinar cambios concurrentes. La eliminación reasigna las actividades a una categoría activa del mismo usuario en la misma transacción; nunca modifica los movimientos financieros.

Esta migración SQL es para SQL Server local. PostgreSQL requiere su migración equivalente antes de desplegar allí; esta entrega no cambia Railway.
