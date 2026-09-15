# Facturas: numeración, conceptos y guardado

## Estado del desarrollo

Última revisión: 14 de septiembre de 2026.

| Estado | Alcance |
|---|---|
| EXISTENTE | Modelo, tablas y consulta de detalle con conceptos. |
| APROBADO | Numeración automática, cálculo de conceptos, guardado conjunto y edición de borradores según este documento. |
| IMPLEMENTADO | Bloques 1 y 2: contrato final, validación, cálculo, numeración y guardado transaccional de cabecera y líneas. |
| VERIFICADO | Compilación y 40 pruebas focales, incluidas nueve con MySQL real aislado. |
| PENDIENTE | Adaptación del formulario y edición de borradores. |

El backend aplica el nuevo contrato. El formulario anterior no es compatible hasta que se adapte: ya no basta enviar número e importes manuales sin conceptos. No se ha modificado el frontend en el bloque 2.

## Base existente

`backupFacturacion360v1.sql` documenta:

- `facturas.idfactura INT`, clave primaria autoincremental.
- `num_factura VARCHAR(15) NOT NULL`, con índice `num_factura_UNIQUE`.
- Importes de factura y conceptos `DECIMAL(10,2)`.
- `conceptos.idfactura INT NOT NULL`, clave foránea hacia la factura.
- `idconcepto BIGINT`, cantidad entera y descripción de hasta 50 caracteres.
- Descuento `DECIMAL(5,2)` y porcentaje de IVA `DECIMAL(4,2)`.
- Tablas InnoDB, sin cascadas declaradas.

Estas restricciones proceden del archivo SQL. Las pruebas recrean únicamente las tablas necesarias en una instancia aislada y comprueban InnoDB, índice único y FK allí; no se ha consultado el esquema de `bd_facturacion`. No se ha modificado el esquema del proyecto.

## Contrato operativo del backend

`POST /factura` recibe `idCliente`, `fechaEmision`, `estado`, `observaciones` y `conceptos[]`.

Cada concepto aporta únicamente `descripcion`, `cantidad`, `precioUnitario`, `descuento` y `porcentajeIva`. El número y los importes resultantes son responsabilidad del servidor.

Un BORRADOR puede guardarse con cero conceptos. Para emitir es obligatorio al menos uno.

### Sustitución del contrato del bloque 1

`FacturaRequest.java` ya no contiene `numeroFactura`, `subtotal` ni `importeIva`. Los valores derivados enviados por el navegador no se utilizan como autoridad.

- Omitir `conceptos` o enviarlo como `null` se rechaza; un borrador vacío debe enviar `[]`.
- Se ha retirado el rechazo temporal del bloque 1 y conectado el guardado conjunto.
- El cálculo recibe solamente las líneas de entrada y el estado; no utiliza importes manuales de cabecera.

Los conceptos válidos se guardan con la cabecera, no mediante el flujo antiguo.

## Validación y cálculo implementados

`ConceptoRequest.java` exige descripción no vacía de hasta 50 caracteres, cantidad entera positiva, precio no negativo, descuento entre 0 y 100 e IVA entre 0 y 99,99. Los valores decimales de entrada admiten como máximo dos decimales y respetan los límites de sus columnas.

La fábrica JSON del DTO recibe la cantidad como `BigDecimal` y usa `intValueExact()`. Así rechaza fracciones y desbordamientos en lugar de truncarlos silenciosamente. No cambia la configuración JSON global.

`FacturaServiceImpl.calcularImportes()` valida la lista y sus líneas sin acceder al repositorio. Una lista nula o una línea nula se rechazan. Una lista vacía con estado BORRADOR produce importes `0.00`; con EMITIDA se rechaza.

El cálculo utiliza `BigDecimal` y `RoundingMode.HALF_UP`:

1. `bruto = cantidad × precioUnitario`
2. `descuentoImporte = bruto × descuento / 100`
3. `baseImponible = redondear(bruto − descuentoImporte, 2)`
4. `importeIva = redondear(baseImponible × porcentajeIva / 100, 2)`
5. `total = baseImponible + importeIva`

No se redondea el descuento por separado antes de calcular la base. Ejemplo: precio `0.05`, cantidad 1 y descuento del 10 % producen base `0.05`; con IVA del 10 %, el IVA es `0.01` y el total `0.06`.

Subtotal, IVA y total de cabecera son las sumas de los importes ya redondeados por línea. Se comprueban los resultados por línea y las sumas frente al máximo `99.999.999,99` de `DECIMAL(10,2)`.

El resultado `CalculoFactura`, definido dentro del servicio, contiene las líneas calculadas y los tres totales. Los conceptos calculados aún no tienen identificador persistido. La entrada no se modifica y la lista de salida no admite modificaciones.

El navegador ofrecerá una previsualización en un bloque posterior; Spring determinará lo almacenado.

## Numeración implementada

- Formato `F-AAAA-NNNN`, año de la fecha de emisión y cuatro dígitos correlativos.
- Asignación en el primer guardado confirmado. Número inmutable, separado del estado.
- Consultar el mayor número válido de la serie y año, incluyendo todos los estados y números manuales válidos.
- Respetar las equivalencias del índice único, incluida su colación.
- Sin números válidos: comenzar en `0001`. No rellenar huecos.
- Al alcanzar `9999`, rechazar nuevas altas sin reiniciar ni ampliar el formato.
- No modificar números históricos ni formatos ajenos.
- Máximo tres intentos totales ante colisión acreditada del número. Cada intento abre una transacción nueva y recalcula el número.
- Capturar la colisión fuera de la transacción fallida. No reintentar errores de validación, conceptos, conexión ni errores genéricos.

Un número calculado en una transacción revertida no pertenece todavía a una factura confirmada. No se crearán tablas de numeración ni endpoints de borrado.

La consulta usa `MAX(CAST(SUBSTRING(num_factura, 8) AS UNSIGNED))`, longitud exacta de 11 caracteres y `REGEXP_LIKE` con el patrón parametrizado `^F-AAAA-[0-9]{4}$`, excluyendo `0000`. El modo `i` incluye variantes minúsculas compatibles con la colación del esquema documentado. Sin coincidencias, `COALESCE` devuelve cero. La fecha debe tener un año entre 1000 y 9999, compatible con MySQL DATE y el formato.

El repositorio traduce la duplicidad únicamente durante el INSERT de cabecera: exige código MySQL 1062, SQLState 23000 y que el mensaje termine identificando exactamente `num_factura_UNIQUE` o `facturas.num_factura_UNIQUE`. Una identificación desconocida no se reintenta. El servicio captura exclusivamente esa excepción específica después del rollback. El agotamiento de intentos y el límite de numeración responden HTTP 409 con un mensaje explícito, sin exponer el SQL.

## Guardado conjunto implementado

Una petición incluye cabecera y conceptos. La validación y el cálculo puro se realizan una vez, antes del bucle de intentos. Cada ejecución de `TransactionTemplate`, con propagación `REQUIRES_NEW`, engloba la consulta del número, inserción de factura, recuperación de su identificador e inserción de todas las líneas.

Si falla cualquier escritura, se revierte el intento completo. No hay peticiones ni transacciones independientes por concepto. Se comunica éxito únicamente después de confirmar la transacción. Errores de conceptos, conexión, otros índices o SQL no acreditado como colisión de número no se reintentan.

## Edición de borradores aprobada, pendiente de implementar

`PUT /factura/{idFactura}/borrador` deberá:

1. Bloquear y leer la cabecera dentro de una transacción.
2. Comprobar en servidor que continúa en BORRADOR.
3. Validar y recalcular las líneas.
4. Mantener número y estado, permitiendo cambios de fecha solo dentro del mismo año del número.
5. Reemplazar los conceptos y actualizar los importes conjuntamente.

Se acepta que cambien los identificadores internos de las líneas. Un fallo restaurará mediante rollback tanto la cabecera como los conceptos anteriores.

En dos ediciones concurrentes prevalecerá el último guardado válido. No se incorpora control de versiones optimista. EMITIDA, PAGADA y ANULADA no permitirán editar directamente sus conceptos.

## Verificación del bloque 2

Desde la carpeta `facturacion360/`, selección sin integración:

```bash
bash mvnw -B -Dtest=FacturaGuardadoTests,FacturaServiceImplTests,FacturaRepositoryNumeracionTests,FacturaControllerTests test
```

Ejecución conjunta realizada el 14 de septiembre de 2026: código de salida 0, `BUILD SUCCESS`, 40 pruebas, cero fallos, errores u omisiones. Incluye compilación de fuentes y pruebas.

| Clase | Pruebas | Alcance |
|---|---:|---|
| `FacturaGuardadoTests` | 22 | Cálculo, validación y servicio con persistencia/transacciones simuladas |
| `FacturaServiceImplTests` | 1 | Prueba original de factura inexistente |
| `FacturaRepositoryNumeracionTests` | 3 | Clasificación estricta de colisiones con JDBC simulado |
| `FacturaControllerTests` | 5 | MockMvc, servicio real y repositorio simulado |
| `FacturaGuardadoIntegracionTests` | 9 | SQL, persistencia, rollback y concurrencia MySQL reales |

La integración utilizó MySQL 8.4.11 en una instancia temporal independiente, sin cargar archivos de configuración de MySQL, escuchando solamente en `127.0.0.1:19367`. Antes de escribir, las pruebas comprueban el UUID del servidor, su directorio de datos temporal y el esquema fijo `facturas_pruebas`. No arrancan el contexto completo Spring ni cargan su datasource de aplicación.

Comando conjunto ejecutado, expresado con parámetros del entorno temporal. La clave temporal no se incorpora al código ni a este documento. Maven/Surefire la registra como propiedad en los informes locales de `target`; esos informes no deben publicarse.

```bash
bash mvnw -B \
  -Dtest=FacturaControllerTests,FacturaGuardadoTests,FacturaServiceImplTests,FacturaRepositoryNumeracionTests,FacturaGuardadoIntegracionTests \
  -Dfacturas.mysql.puerto="$PUERTO_MYSQL_PRUEBAS" \
  -Dfacturas.mysql.servidor="$UUID_MYSQL_PRUEBAS" \
  -Dfacturas.mysql.directorio="$DIRECTORIO_MYSQL_PRUEBAS" \
  -Dfacturas.mysql.clave="$CLAVE_MYSQL_PRUEBAS" test
```

Requiere preparar previamente una instancia aislada, verificar su identidad y un usuario `pruebas_facturas` limitado a ese esquema, con permisos SELECT, INSERT, UPDATE, DELETE, CREATE y REFERENCES. No reutilizar el servicio real. Sin la propiedad de puerto, la clase de integración queda deshabilitada y no debe contarse como superada.

La instancia temporal se cerró mediante su socket propio al terminar las pruebas; no se detuvo el servicio MySQL existente. Se conservaron sus archivos temporales, sin borrarlos.

Cobertura:

- Borrador vacío, emisión sin líneas, listas o líneas nulas y estados inválidos.
- Descuentos, IVA cero, redondeo de base e IVA y suma de líneas ya redondeadas.
- Límites de descripción, cantidad, precio, descuento e IVA; valores nulos, negativos y precisión excesiva.
- Desbordamientos de base o total de línea y de sumas.
- Contrato HTTP nuevo y ausencia de autoridad de número/importes enviados por el cliente.
- Primer número, años independientes, huecos, formatos históricos inválidos, números manuales y estados diversos, incluida variante minúscula.
- Límite 9999, máximo de tres intentos y ausencia de reintentos ante errores ajenos, usando pruebas de servicio y de clasificación.
- Persistencia real de una o varias líneas y sus totales, FK e índice único en el esquema aislado.
- Entrada no modificada y lista calculada no modificable.

La prueba concurrente fuerza con una barrera que dos peticiones lean inicialmente el mismo máximo. Las dos escrituras reales terminan confirmadas con `0001` y `0002`, tras tres consultas de numeración: una alta reintenta después de la colisión real.

La prueba de rollback inyecta un total nulo en la segunda línea y usa la implementación real de inserción de conceptos. MySQL rechaza esa segunda escritura después de insertar la primera; se comprueba que no quedan cabecera ni líneas. El espía solo introduce el fallo; las escrituras y el rollback son reales.

Durante la preparación fallaron un permiso REFERENCES ausente y una fila sintética de cliente incompleta. Se corrigieron el usuario temporal y el fixture, sin alterar el esquema del proyecto. Una prueba HTTP detectó además que la conversión inicial truncaba `1.5` a 1. Tras corregir localmente el DTO, `1.5`, `"1.5"`, `2147483648` y `null` reciben HTTP 400 sin acceso al repositorio.

El método original de prueba de factura inexistente se conserva; solo se adaptó su repositorio falso a la interfaz. No se ejecutó `Facturacion360ApplicationTests` ni la suite completa. Los avisos de Jansi y carga dinámica de Mockito no impidieron las pruebas y no se alteró configuración para ocultarlos.

## Límites y siguientes bloques

No se han modificado esquema, configuración, dependencias, datos históricos ni interfaz en este bloque. Antes del uso real deberá verificarse `num_factura_UNIQUE`, la FK de conceptos y el motor InnoDB con autorización.

El índice único evita números duplicados, pero no dos facturas distintas por reenvío de la misma petición. La estrategia del máximo requiere conservar las facturas también frente a eliminaciones externas.

El siguiente bloque es adaptar el formulario al contrato ya operativo; después, edición de borradores y regresión conjunta. Requieren nueva autorización. No se ha probado el frontend con el nuevo contrato ni la edición, y no se ha verificado la base real.
