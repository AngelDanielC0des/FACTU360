# Facturas: numeración, conceptos y guardado

## Estado del desarrollo

Última revisión: 15 de septiembre de 2026.

| Estado | Alcance |
|---|---|
| EXISTENTE | Modelo, tablas y consulta de detalle con conceptos. |
| APROBADO | Numeración automática, cálculo de conceptos, guardado conjunto y edición de borradores según este documento. |
| IMPLEMENTADO | Bloques 1 y 2 de backend y bloque 3A de formulario de alta con conceptos. |
| VERIFICADO | Compilación, 40 pruebas Java (nueve con MySQL aislado) y 12 pruebas de navegador (dos E2E reales). |
| PENDIENTE | Bloque 3B y edición de borradores; no iniciados en 3A. |

El backend y, desde el bloque 3A, el formulario utilizan el nuevo contrato. El bloque 2 no modificó la interfaz; su adaptación y verificación se describen al final de este documento.

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

Los bloques 1 y 2 no modificaron esquema, configuración, dependencias, datos históricos ni interfaz. Antes del uso real deberá verificarse `num_factura_UNIQUE`, la FK de conceptos y el motor InnoDB con autorización.

El índice único evita números duplicados, pero no dos facturas distintas por reenvío de la misma petición. La estrategia del máximo requiere conservar las facturas también frente a eliminaciones externas.

La adaptación del alta se completó en 3A, según la evidencia siguiente. La edición y el bloque 3B requieren nueva autorización. No se ha verificado la base real.

## Bloque 3A: formulario de alta

Fecha: 15 de septiembre de 2026.

### IMPLEMENTADO

El formulario permite añadir, modificar y eliminar conceptos antes de guardar, mediante fichas sencillas dentro del modal existente. El modal tiene desplazamiento interno y conserva las acciones de guardar y cancelar visibles. No permite editar facturas ya guardadas.

Se envía una sola `POST /factura`, con esta estructura:

```json
{
  "idCliente": 1,
  "fechaEmision": "2028-09-15",
  "estado": "EMITIDA",
  "observaciones": "Ejemplo",
  "conceptos": [
    {"descripcion": "Servicio", "cantidad": 3, "precioUnitario": 19.99, "descuento": 10, "porcentajeIva": 21}
  ]
}
```

No se envían número ni importes derivados. La previsualización usa céntimos y centésimas de porcentaje para evitar errores de resta decimal; sigue siendo orientativa y Spring conserva la autoridad sobre los importes definitivos. El mensaje de éxito muestra el número y total recibidos del servidor.

Durante el guardado se bloquean controles, cierre y segundo envío. Ante errores se conserva lo escrito y se restauran los controles. La interfaz diferencia validación (400), conflicto (409), otros errores de servidor y conexión; no muestra cuerpos de error que puedan contener SQL o trazas. Se mantienen el contador de observaciones y los ajustes del formulario ya existentes.

La validación nativa comprueba campos obligatorios, cantidad entera y rangos/decimales. Se rechaza una descripción formada solo por espacios. BORRADOR puede enviar `conceptos: []`; EMITIDA sin conceptos muestra un error sin enviar la petición.

### VERIFICADO

- Checkpoint local de bloques 1+2: `337fb48`, 13 archivos de backend, pruebas y documentación; excluyó los cuatro archivos de interfaz preexistentes.
- Revalidación del checkpoint: 31 pruebas Java seleccionadas, cero fallos, errores u omisiones, salida 0.
- Regresión posterior: las 40 pruebas Java, incluidas las nueve MySQL, nuevamente superadas con salida 0. No se ejecutó la suite completa.
- `facturas-alta.spec.cjs`: 12 pruebas en Chrome, cero fallos y salida 0; diez de interfaz y dos E2E con POST real, sin simular controller, service ni persistencia.
- Sintaxis JavaScript y `git diff --check`: salida 0.

Las pruebas de interfaz verifican edición de líneas, redondeo, JSON exacto, una sola POST, bloqueo de otro submit pendiente, conservación de datos ante errores 400/409/500/conexión, borrador vacío, rechazo de emisión sin líneas, rangos, móvil a 390 px y foco por teclado. Se revisaron capturas de escritorio y móvil.

El E2E real creó una sola cabecera `F-2028-0001` con dos líneas:

| Concepto de prueba | Cantidad | Precio | Descuento | IVA | Base guardada | IVA guardado | Total guardado |
|---|---:|---:|---:|---:|---:|---:|---:|
| Servicio E2E | 3 | 19,99 | 10 % | 21 % | 53,97 | 11,33 | 65,30 |
| Material E2E | 2 | 5,00 | 0 % | 10 % | 10,00 | 1,00 | 11,00 |

Se comprobaron directamente en MySQL descripción, cantidad, precio, porcentajes e importes de ambas líneas, además de la cabecera: base `63.97`, IVA `12.33`, total `76.30`. La interfaz mostró el número y los importes confirmados. La prueba calcula el número esperado a partir del máximo previo, por lo que no exige que las siguientes ejecuciones reutilicen `0001`.

El E2E de error envió cantidad 2 y precio `99999999.99`: Spring rechazó el desbordamiento con HTTP 400, no aumentó el número de cabeceras y el formulario conservó los datos y habilitó Guardar.

Se corrigieron dos casos de previsualización: `2.30` con 5 % de descuento da `2.19`, y `2.32` con 6,25 % da `2.18`. Sus regresiones pasan. Durante el desarrollo de las pruebas se corrigieron el selector del botón con icono y la comprobación de bloqueo: se verifica sobre controles reales, no sobre el elemento `fieldset`. No se aumentaron tiempos de espera ni se omitieron casos.

### Entorno aislado y repetición

Se utilizó MySQL 8.4.11 en una instancia nueva, con directorio `/tmp/facturas-mysql-7GvI6j/datos/`, socket propio y puerto 19368. El servidor se identificó antes de preparar el esquema `facturas_pruebas`; las pruebas SQL anteriores preparan las tablas y el cliente sintético 1. No se cargó el dump completo ni datos históricos.

Spring se arrancó por separado en `127.0.0.1:18081`. Se sustituyó la ubicación habitual de configuración por una ubicación temporal vacía, se indicó el datasource aislado explícitamente y se deshabilitó la inicialización SQL. Los argumentos del proceso y sus conexiones TCP confirmaron que apuntaba al puerto MySQL 19368. El GET de clientes devolvió únicamente el cliente sintético. La identidad del MySQL consultado se comprueba también en las pruebas E2E; esta comprobación complementa, no sustituye, la del arranque de Spring.

Arranque usado, parametrizado; requiere preparar y verificar previamente la instancia aislada. La clave se proporciona solo al proceso, no se guarda en configuración:

```bash
SPRING_DATASOURCE_PASSWORD="$CLAVE_MYSQL_PRUEBAS" bash mvnw -B spring-boot:run \
  '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' \
  "-Dspring-boot.run.arguments=--spring.config.location=optional:file:$DIRECTORIO_TEMPORAL/sin-config.properties --spring.datasource.url=jdbc:mysql://127.0.0.1:$PUERTO_MYSQL_PRUEBAS/facturas_pruebas?sslMode=DISABLED&allowPublicKeyRetrieval=true --spring.datasource.username=pruebas_facturas --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver --spring.sql.init.mode=never --server.port=18081 --server.address=127.0.0.1"
```

Para las pruebas se reutilizaron Chrome, Playwright y Node 22 instalados. Node 18 no era compatible con el ejecutor; no se instalaron dependencias ni se cambió el runtime de la aplicación Spring.

Desde `facturacion360/`, con `NODE_PRUEBAS` apuntando al ejecutable Node 22 y `MODULOS_PRUEBAS` al directorio de módulos de Playwright ya instalado:

```bash
FACTURAS_URL_PRUEBAS=http://127.0.0.1:18081 \
FACTURAS_MYSQL_SOCKET="$DIRECTORIO_TEMPORAL/mysql.sock" \
FACTURAS_MYSQL_SERVIDOR="$UUID_MYSQL_PRUEBAS" \
FACTURAS_MYSQL_DIRECTORIO="$DIRECTORIO_TEMPORAL/datos/" \
NODE_PATH="$MODULOS_PRUEBAS" \
"$NODE_PRUEBAS" "$MODULOS_PRUEBAS/playwright/cli.js" test \
  -c src/test/js facturas-alta.spec.cjs --workers=1 --reporter=line \
  --max-failures=1 --output=target/playwright-3a-tercera
```

Los informes y capturas quedan locales en `target/`, excluidos de Git. Los informes Surefire pueden contener la clave temporal: no publicarlos. No se ejecutó nada contra `bd_facturacion`.

Al finalizar se cerraron la sesión de navegador, Spring y MySQL de pruebas; se conservaron los archivos temporales. El cierre deliberado de Spring con SIGTERM produjo salida 143 del proceso y salida 1 del comando `spring-boot:run`, después de completar el apagado ordenado. No es el resultado de las pruebas, que terminaron con salida 0.

### RESERVA y PENDIENTE

El bloqueo de Guardar mitiga envíos simultáneos, pero no garantiza idempotencia frente a reintentos posteriores. La edición de facturas existentes y el bloque 3B permanecen pendientes, sin iniciarse. Los cambios previos de extracción de estilos de filtros se conservan locales, fuera del commit de 3A.

## Bloque 3A.1: sugerencias de conceptos históricos

Fecha: 15 de septiembre de 2026. Base de trabajo: checkpoint `4ac314c` del bloque 3A.

### IMPLEMENTADO

`GET /factura/conceptos/sugerencias?texto=manten&limite=8` recibe texto y límite. El servicio elimina espacios exteriores y devuelve una lista vacía para textos de menos de dos caracteres o de más de cincuenta. El límite predeterminado es ocho; se acota entre uno y veinte. Un límite no numérico recibe HTTP 400.

```json
[
  {"descripcion":"Mantenimiento web","precioUnitario":120.00,"descuento":5.00,"porcentajeIva":21.00}
]
```

Una única consulta sobre `conceptos` y `facturas` busca coincidencias en cualquier posición, escapando los comodines de LIKE. Ordena por `fecha_emision`, `idfactura` e `idconcepto`, todos descendentes. Después deduplica por descripción sin espacios exteriores y sin distinguir mayúsculas, conservando la primera aparición. No modifica el histórico ni añade tablas. Precio, descuento e IVA proceden juntos de esa aparición, no de medias ni de máximos independientes.

Cada línea mantiene su temporizador, consulta y selección. Espera 250 ms tras la última entrada y muestra hasta ocho sugerencias. Las versiones de consulta impiden que una respuesta obsoleta reabra o sustituya resultados, incluso cuando la cancelación del transporte no es efectiva.

Ratón o flechas y Enter permiten seleccionar. Escape, pérdida de foco y click fuera cierran la lista. Eliminar la línea, cerrar el modal, reiniciar el formulario o iniciar el guardado invalidan también las operaciones pendientes. Se utiliza el patrón combobox/listbox, con selección activa y foco conservado en Descripción; el desplegable queda debajo del campo, dentro del flujo del formulario.

Solo seleccionar rellena descripción, precio, descuento e IVA. La cantidad se conserva y todos los campos continúan editables. Los valores históricos nulos dejan campos vacíos para completarlos, sin inventar ceros ni recuperar una tarifa antigua. Un fallo de sugerencias no bloquea el alta manual ni muestra errores técnicos intrusivos.

El contrato POST del bloque 3A permanece intacto: Spring sigue calculando numeración e importes y guardando factura y conceptos conjuntamente.

### VERIFICADO

- 48 pruebas Java seleccionadas: cero fallos, errores u omisiones, salida 0. Incluyen las 40 anteriores y ocho focales nuevas: tres HTTP y cinco SQL con MySQL real aislado.
- Las nuevas pruebas cubren contrato de cuatro campos, validación y límites, coincidencia parcial, ausencia de resultados, comodines literales, deduplicación, orden por fecha y ambos desempates, límite después de deduplicar, nulos históricos y comparación del histórico completo antes/después de consultar.
- `facturas-alta.spec.cjs`: ejecución final conjunta de 22 pruebas superadas, salida 0; conserva las doce anteriores y añade diez. Son diecinueve pruebas de interfaz y tres E2E con Spring/MySQL reales.
- Se verificaron debounce, selección explícita, teclado, Escape y click fuera, cantidad intacta, campos editables, respuestas/errores obsoletos incluso sin cancelación efectiva, cierre y eliminación con consulta pendiente, independencia entre líneas, fallo secundario sin bloquear el alta, nulos históricos y móvil sin desbordamiento. Se inspeccionaron las capturas de escritorio y móvil.
- El E2E creó histórico sintético con precio antiguo 90 y reciente 120, insertando el uso antiguo después del nuevo. El navegador seleccionó `Mantenimiento web`, mantuvo cantidad 3 y copió precio 120, descuento 5 e IVA 21. Una sola POST guardó base `342.00`, IVA `71.82` y total `413.82`; se comprobaron los valores en MySQL y que los conceptos históricos permanecían intactos.
- Sintaxis de JavaScript y `git diff --check`: salida 0.

La validación de este bloque utiliza una instancia nueva de MySQL 8.4.11, con directorio `/tmp/facturas-mysql-FsVt3W/datos/`, socket propio y puerto 19369, y Spring en `127.0.0.1:18082`. Se verificaron UUID, directorio y esquema `facturas_pruebas` antes de escribir fixtures. Spring se inició con ubicación de configuración temporal, datasource explícito e inicialización SQL deshabilitada; sus conexiones TCP apuntaban al puerto 19369. No se accedió a `bd_facturacion` ni se ejecutó `Facturacion360ApplicationTests`.

Para repetir se aplican los comandos parametrizados de las secciones anteriores con esta nueva instancia: `PUERTO_MYSQL_PRUEBAS=19369`, `DIRECTORIO_MYSQL_PRUEBAS=/tmp/facturas-mysql-FsVt3W/datos/`, su UUID verificado y una clave temporal solo para el proceso. El arranque de Spring y `FACTURAS_URL_PRUEBAS` usan el puerto 18082. El ejecutor de navegador sigue siendo Chrome con Playwright y Node 22 ya instalados, sin nuevas dependencias. Los resultados finales se destinan a `target/playwright-3a1-cierre` y no se incluyen en Git.

Durante la validación hubo un timeout inicial de carga de página; la carga posterior funcionó sin cambiar configuración ni tiempos de espera. Se corrigieron los selectores de las pruebas nuevas para no contar las opciones nativas de los selectores. También se sincronizó la preparación común y la reapertura del modal con el foco final de Bootstrap: escribir antes de terminar su animación podía perder el foco y cancelar correctamente una consulta. Se conservaron las pruebas anteriores y sus aserciones.

Al terminar se cerraron únicamente el navegador diagnóstico, Spring en 18082 y MySQL en 19369; los archivos temporales permanecen locales. El apagado deliberado de Spring con SIGTERM produjo salida 143 y salida 1 del comando de arranque, después del cierre ordenado; no corresponde al resultado de pruebas, que fue 0. La aplicación preexistente en 8080 no se utilizó ni se detuvo.

### RESERVA

La deduplicación limita los resultados devueltos, pero la consulta ordena las coincidencias históricas y el driver JDBC puede almacenarlas en memoria. No se acredita rendimiento con históricos voluminosos. No se añade optimización preventiva a este bloque.

### PENDIENTE

Bloque 3B y edición de facturas existentes sin iniciar. Este bloque no publica cambios ni integra trabajo en `master`. La extracción local previa de estilos de filtros en `style.css` y `facturas.css` se conserva fuera del commit 3A.1.
