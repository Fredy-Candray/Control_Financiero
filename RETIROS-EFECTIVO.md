# Retiros BAC de débito a efectivo

Se reconoce el aviso autenticado de `alerta@baccredomatic.com` con el texto explícito de retiro de cajero de una cuenta en USD, monto y fecha de operación. Requiere autenticación DMARC confirmada por Gmail, identificador del mensaje, monto positivo y fecha válida. No aplica a adelantos de tarjeta de crédito ni interpreta comisiones no descritas.

Un retiro reconocido crea TRANSFERENCIA desde una cuenta BAC de débito hacia la única cuenta Efectivo activa del usuario, conservando la fecha del banco. Reduce débito y aumenta efectivo por el mismo importe; no genera ingresos ni gastos ni cambia el total del dinero disponible.

La cuenta del aviso puede mostrar los últimos cuatro dígitos de la cuenta bancaria, distintos de la tarjeta de débito. La propiedad local opcional `app.gmail.account-aliases` permite asociaciones explícitas `usuario:ultimosCuatroCuenta:idCuentaCofi`, separadas por comas. Sin asociación, se busca una cuenta BAC de débito activa que coincida exactamente con los dígitos del aviso. No se infiere por nombre ni se comparten asociaciones entre usuarios.

Si no hay una sola cuenta compatible, falta Efectivo, el saldo no alcanza o aparece una posible transferencia manual del mismo importe y cuentas dentro de un día de la operación, el aviso queda pendiente/error sin repetir descuentos. Los posibles duplicados se revisan en Control y préstamos. Se conservan las protecciones por identificador Gmail y Message-ID frente al reprocesamiento.

Las pruebas incluyen un aviso ficticio con día de un solo dígito, moneda y autenticidad, importación a las cuentas correctas y una posible coincidencia manual. La propiedad de prueba `bac.withdrawal.sample` permite comprobar una muestra local sin versionar el correo real.
