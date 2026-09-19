# FACTURACION360 · Diseño para completar el desarrollo

Fecha: 2026-09-19 · Base: `master` = `4aaec9b`, árbol limpio.

Este documento es la especificación de la que cuelga el plan de implementación. No es la
guía de desarrollo (esa se escribe en `docs/guia-desarrollo.md` a medida que cada hito
decide algo) ni el flujo de trabajo (`docs/flujo-trabajo.md`).

---

## 1 · Línea base, medida

Tomada sobre `master` = `4aaec9b`, Java 21.0.11, Spring Boot 4.1.0, MySQL 8.4.9.

```
./mvnw test  →  Tests run: 160, Failures: 1, Errors: 0, Skipped: 20
                BUILD FAILURE (exit 1)
```

| Clase | Pruebas | Estado |
|---|---|---|
| `Facturacion360ApplicationTests` | 1 | ok |
| `FacturaControllerTests` | 14 | **1 fallo** |
| `ManejadorExcepcionesTests` | 14 | ok |
| `ConceptoRequestTests` | 23 | ok |
| `FacturaGuardadoIntegracionTests` | 20 | 20 saltadas |
| `FacturaRepositoryNumeracionTests` | 3 | ok |
| `RestriccionSqlTests` | 9 | ok |
| `CalculadoraDesgloseTests` | 8 | ok |
| `FacturaGuardadoTests` | 22 | ok |
| `FacturaServiceImplTests` | 1 | ok |
| `NifCifValidadorTests` | 32 | ok |
| `CalculadorHuellaTests` | 13 | ok |

**Objetivo de no regresión: 160 pruebas como mínimo, 0 fallos.** Un hito que deje menos
pruebas que la línea base no está terminado, aunque esté verde.

Las 20 saltadas lo están por `@EnabledIfSystemProperty(named = "facturas.mysql.puerto")`.
Fuera de Maven hay además `src/test/js/facturas-alta.spec.cjs` (Playwright, 54 KB) que hoy
**no ejecuta nada**: no hay `package.json` en el repositorio.

### El fallo, datado

```
FacturaControllerTests.edicionValidaIdEstadoYConceptosAntesDeEscribir:134
  Status expected:<400> but was:<404>
```

Miente el test, no el código. Cronología:

- `07d752e` (15/09, ftauro333) escribe el test fijando «editar exige estado BORRADOR».
- `9c28908` (15/09, mismo autor, «permitir emitir al guardar un borrador») cambia la regla
  a `List.of("BORRADOR", "EMITIDA")` en `FacturaServiceImpl` y no toca el test.

Evidencia de que la regla nueva es la buena — el frontend depende de ella:

- `facturas.js:504` envía `estado: campoEstado.value` en el PUT del borrador.
- `facturas.js:527` muestra «el estado debe ser BORRADOR o EMITIDA».
- `facturas.js:865` exige al menos un concepto cuando el estado es EMITIDA.

Comandos que lo demuestran:

```bash
git show 9c28908 -- .../service/FacturaServiceImpl.java
git blame -L 133,134 -- .../controller/FacturaControllerTests.java
```

---

## 2 · Lo que el encargo daba por cierto y no lo es

Verificado contra el código. Cuando el encargo y el código discrepan, manda el código.

| Punto | Lo que decía | Lo que hay |
|---|---|---|
| 10 | `ClienteMapper.limpiarTexto` con un `replaceAll` de espacios | El método **no existe**. `grep -rn "limpiarTexto" src/` devuelve 0 resultados. Lo análogo es `CriteriosCliente:97`, un `Pattern` precompilado y correcto. Nada que hacer. |
| 11 | 10 `catch` vacíos, 6 `TODO`/`FIXME` | **0 `catch` vacíos** y **3 `TODO`**. Los otros tres eran la palabra «TODOS» dentro de comentarios. |
| 12 | «ocho parámetros del mismo tipo» en `CadenaCanonica.alta` | Ocho parámetros de **cuatro tipos**: 4 `String`, 1 `LocalDate`, 2 `BigDecimal`, 1 `OffsetDateTime`. El riesgo es real pero acotado a `cuotaTotal` contra `importeTotal` y a los cuatro `String`. |
| 9 (Fase 2) | «el hueco del QR ya está en `factura-imprimir.css` con su zona de silencio» | **No existe.** Ni QR, ni zona de silencio, ni leyenda. El único `mm` del fichero es `margin: 14mm` de la página. Hay que maquetarlo entero. |
| — | `docu/fallos_master.txt` | **Desfasado por completo.** Los dos endpoints que da por perdidos existen: `ClienteController:191` y `FacturaController:58`, con pruebas en `ManejadorExcepcionesTests`. Incluso `FacturaServiceImplTests`, que daba por perdido, está recuperado. |
| — | `PAGADA` como estado a separar (doc. VERI\*FACTU 2.4) | **`PAGADA` no existe en el proyecto.** El patrón real admite BORRADOR, EMITIDA y ANULADA, y el enum de la base es el mismo. Los dos ejes ya están separados; lo único pendiente es que no hay forma de registrar un cobro, y de ahí sale H11. |

Confirmados tal cual: los puntos **1, 2, 3, 4, 5, 6, 7, 8, 9 y 13**.

Del punto 1, el detalle medido: 16 `@Autowired` sobre campo, en 7 clases, **ninguna con
constructor**, y **15 de los 16 campos sin modificador de acceso**. El único `private` es
`EmisorRepositoryImpl.jdbcTemplate`. `EmisorServiceImpl` no está en la lista porque ya usa
constructor con `private final`: es el modelo a seguir.

### Hallazgos nuevos

- **`facturacion360/carpeta1/` son logs de la aplicación versionados**: `f.log` (290 KB) y
  ocho `.gz`. La raíz ignora `logs/` y `mislogs/`, pero no `carpeta1`. Revisados: solo
  trazas de `JdbcTemplate` y criterios con filtros vacíos, sin datos personales. El daño hoy
  es bajo; el hábito, con el `DEBUG` de JDBC activo, no lo es.
- **`clientes` solo tiene `PRIMARY KEY` y `UNIQUE (nif_cif)`.** Se filtra por `provincia` y
  `poblacion` y se ordena por `nombre` y `fecha_alta`: cuatro columnas sin índice en la
  pantalla más usada.
- **Bloque de JavaScript comentado dentro de `EmisorController.java`** (~líneas 140-215),
  con una URL absoluta a `localhost:8080` escrita a mano.
- Sobre el riesgo de descuadre entre marcadores y argumentos: el patrón real es
  `anadirFiltros(StringBuilder sql, List<Object> args, ...)`, que añade fragmento y
  argumento **juntos**. Es el patrón seguro. **No hay ningún descuadre hoy**; se fija con
  una prueba en H2 para que siga así.

---

## 3 · Entorno confirmado

```
MySQL 8.4.9 · bd_facturacion · 5 tablas · 1 emisor · 0 clientes · 0 facturas
Docker disponible
```

El volcado `backupFacturacion360v3.sql` contiene un único `INSERT` (el emisor), así que la
base cargada coincide exactamente con el volcado. Consecuencia para las migraciones: el
patrón de cuatro pasos se escribe igual —porque en destino sí habrá datos— pero **aquí no
se puede verificar el relleno con datos reales**. Cada migración se prueba con Testcontainers
sobre una base sembrada a propósito, no sobre la de desarrollo.

---

## 4 · Decisiones tomadas, con su motivo

| Decisión | Motivo |
|---|---|
| Testcontainers para la integración | Hay Docker. Convierte 20 pruebas saltadas en pruebas que corren siempre, en local y en CI, sin depender de un puerto pactado ni de la base de desarrollo. |
| El test obsoleto se corrige, no el código | El frontend depende de emitir-al-guardar en tres sitios. Revertir la regla rompe la pantalla de facturas. |
| Refactor de inyección en 7 commits, uno por clase | Revisable de uno en uno y reversible sin arrastrar. La alternativa era un commit de 14 ficheros. |
| Autenticación con Spring Security y usuarios en base de datos | Es lo mínimo defendible en algo que toca datos fiscales. Un usuario en configuración deja sin trazabilidad quién emitió qué. |
| `facturas.js` se trocea siguiendo el mapa de capas existente | No se crea un segundo mapa paralelo. La spec de Playwright, una vez ejecutable, es la red contra regresiones. |
| `fecha_cobro` se añade como funcionalidad nueva, al final | No es una separación de ejes (ya están separados) ni un requisito de VERI\*FACTU. Va fuera del camino crítico. |
| Java se alinea al esquema, no al revés, en los tamaños del emisor | Ensanchar la tabla es cambio de esquema, y `emisor` ya se toca en H4 por otro motivo. Dos migraciones sobre la misma tabla en hitos distintos es pedir un `Duplicate column name`. |
| El paquete `verifactu/huella` no depende de Spring | Ya es así, y es lo que permite probar contra los vectores oficiales sin levantar nada. |

---

## 5 · Lo que NO se toca

Contrastado con el código: todo lo de la lista del encargo sigue siendo cierto hoy, salvo
donde se indica.

| Qué | Por qué |
|---|---|
| `CadenaCanonica` y `CalculadorHuella`, por dentro | Comprobados contra los tres vectores oficiales de la AEAT, literales en `CalculadorHuellaTests`. Es lo único que falla en silencio. En H4 se les **añade una sobrecarga** que recibe `RegistroFacturacion`; la firma actual se conserva y las 13 pruebas siguen llamándola. |
| `xxx` minúscula en el formateador de fecha | La forma en mayúsculas colapsa el huso cero a `Z`. Ya costó el fallo `b00a8d2`. |
| `MessageDigest` pedido en cada llamada | Tiene estado; compartirlo da huellas corruptas con dos hilos. |
| Lista blanca `COLUMNAS_ORDEN` en el `ORDER BY` | Es la defensa contra inyección. Un marcador de parámetro no vale para un nombre de columna. |
| Orden de los `catch` en `FacturaRepositoryJdbcImpl` | `DuplicateKeyException` hereda de `DataIntegrityViolationException`. Invertirlo mata el reintento de numeración. Está comentado en el propio fichero, líneas 74-75. |
| Constructores de compatibilidad en `ConceptoFactura` y `ConceptoRequest` | Permiten que el código anterior compile tras añadir campos. |
| `conciliarFormulario` frente a `pintarPanelEdicion` | Uno reemplaza y el otro concilia conservando lo tecleado. Unificarlos borra lo que el usuario escribe. Ya se rechazó una vez. |
| El mapa de diez capas del JavaScript | Es lo que impide los ciclos. H10 lo **extiende**, no lo sustituye. |
| El escape del `LIKE` y su constante | El orden de los tres reemplazos es el único correcto. |
| Ausencia de `idemisor` y `fecha_hora_expedicion` | Deliberada **hasta H4**. En H4 dejan de estar ausentes, y es H4 quien trae el Java que las rellena, en el mismo commit. Es exactamente la condición que pone `migracion-verifactu.sql`. |

---

## 6 · Los doce hitos

Cada uno deja el proyecto coherente. Se puede parar en cualquiera.

### H0 · Suelo firme

- **De qué depende:** nada.
- **Qué cambia:** la aserción obsoleta de `FacturaControllerTests:133-134` pasa a usar un
  estado que de verdad se rechaza antes de tocar el repositorio, de forma que el test siga
  probando lo que su nombre dice y además sea cierto. Se añade `.github/workflows/ci.yml`,
  JaCoCo, Spotless y SpotBugs. `carpeta1/` sale del árbol y entra en `.gitignore`.
  Testcontainers sustituye a `@EnabledIfSystemProperty`.
- **Riesgo:** bajo. No cambia una línea de producción.
- **Cómo se verifica:** `./mvnw test` da 160 pruebas, 0 fallos y **0 saltadas**.
- **Qué pruebas lo cubren:** la propia corregida. Se comprueba rompiendo el código a
  propósito: ampliando la lista de estados admitidos en `FacturaServiceImpl:175`, el test
  tiene que ponerse rojo. Si no, la prueba no prueba nada.
- **Aviso:** borrar `carpeta1/` **no lo saca del historial**. Se entrega el procedimiento por
  separado; reescribir historia es decisión del dueño del repositorio.

### H1 · Refactor sin cambio de comportamiento

- **De qué depende:** H0 (sin verde no se distingue una regresión propia de la heredada).
- **Qué cambia:** siete commits, uno por clase —`ClienteRepositoryJdbcImpl`,
  `FacturaRepositoryJdbcImpl`, `EmisorRepositoryImpl`, `ClienteServiceImpl`,
  `FacturaServiceImpl`, `ClienteController`, `FacturaController`— en ese orden, de abajo
  arriba. Cada commit: constructor, campos `private final`, y **retirada del
  `ReflectionTestUtils` de las pruebas de esa clase**. Más: el `System.out.println` de
  `ClienteMapper:37`, las credenciales a variable de entorno con un `.properties.ejemplo`
  versionado, el `DEBUG` de JDBC solo en perfil de desarrollo, y el bloque de JavaScript
  comentado dentro de `EmisorController`.
- **Riesgo:** medio, concentrado en `FacturaControllerTests`, que monta el servicio a mano
  con tres asignaciones por reflexión, y en `FacturaGuardadoIntegracionTests`, que inyecta en
  el repositorio.
- **Cómo se verifica:** mismas 160 pruebas con las mismas aserciones; la aplicación arranca;
  y `grep -rn "ReflectionTestUtils" src/test/` devuelve **0 resultados**. Ese grep es el que
  demuestra que el beneficio se ha cobrado: si queda uno, el refactor no ha servido.
- **Qué pruebas lo cubren:** las existentes, sin añadir ninguna. Es refactor: si hiciera falta
  una prueba nueva, es que ha cambiado el comportamiento y el commit está mal.
- **Nota sobre las credenciales:** sacarlas del fichero **no las quita del historial**. Mismo
  tratamiento que `carpeta1/`.

### H2 · Correcciones acotadas de comportamiento

- **De qué depende:** H1.
- **Qué cambia:** un commit por corrección.
  1. `@Valid` en los dos `save` de `EmisorController` **junto con** el alineamiento de los
     tamaños al esquema (nombre 150 a 60, dirección 250 a 200, correo 150 a 50). Van juntos a
     propósito: poner `@Valid` sin arreglar los tamaños deja un 500 de MySQL disfrazado de
     dato validado.
  2. Cota en `FacturaRepositoryJdbcImpl.buscar(String)`, replicando el patrón de `findPagina`
     y su constante.
  3. `spring.servlet.multipart.max-file-size` coherente con `mediumblob`, y
     `MaxUploadSizeExceededException` en `ManejadorExcepciones`.
  4. Cuatro índices en `clientes`: `provincia`, `poblacion`, `nombre`, `fecha_alta`.
  5. Collation esperanto a `utf8mb4_0900_ai_ci` en las cinco columnas de texto de `emisor`.
- **Riesgo:** los puntos 4 y 5 **cambian el esquema**; se piden aparte con el DDL delante.
  El punto 2 cambia lo que ve el usuario al abrir `facturas.html`, porque la carga se ejecuta
  sola: hay que decidir el límite y que el frontend lo diga.
- **Cómo se verifica:** cada corrección con una prueba que **falla contra el código de hoy**.
  Para el punto 5, además: `SHOW FULL COLUMNS FROM emisor` no debe devolver ninguna collation
  de esperanto.
- **Qué pruebas lo cubren:** un CIF inventado da 400 y no se guarda; un nombre de 61
  caracteres da 400 y no 500; una búsqueda vacía con 500 facturas sembradas devuelve el
  límite y no 500 filas; una subida de 2 MB da 413 con `ProblemDetail` y no 500. Más una
  prueba que cuenta los marcadores del SQL y los compara con el tamaño de la lista de
  argumentos en las cuatro consultas que se montan a trozos, para fijar el patrón seguro que
  hoy ya se cumple.

### H3 · Autenticación

- **De qué depende:** H2. Independiente de H4 a H9: puede intercambiarse con ellos.
- **Qué cambia:** `spring-boot-starter-security`, tabla `usuarios` con contraseñas BCrypt,
  formulario de acceso, CSRF activo, y `/cliente`, `/factura` y `/emisor` cerrados. Los
  ficheros estáticos y la documentación OpenAPI quedan según se decida en su momento.
- **Riesgo:** alto de regresión en el frontend. CSRF rompe todos los `fetch` de escritura
  —POST, PUT y DELETE— de `facturas.js`, `perfil.js` y los módulos de clientes, y el síntoma
  es un 403 que parece de permisos.
- **Cómo se verifica:** sin sesión, cada endpoint devuelve 401 o redirige; con sesión,
  responde como antes. Las pantallas siguen funcionando: alta y edición de cliente, alta y
  edición de factura, guardado del emisor con logo.
- **Qué pruebas lo cubren:** una prueba MockMvc por familia de endpoints, sin autenticar y
  autenticada. Y la spec de Playwright, que a partir de H0 sí se ejecuta, recorre el alta de
  factura completa contra la aplicación real con CSRF activo.

### H4 · Registro inmutable encadenado (cumplimiento mínimo, 1 de 2)

- **De qué depende:** H2 (H3 si va antes).
- **Qué cambia:** el hito grande.
  - Migración con el patrón de cuatro pasos: `idemisor` y `fecha_hora_expedicion` a `NOT NULL`
    tras rellenarlas (desde `fecha_creacion`, y a falta de ella el mediodía de
    `fecha_emision`), más `serie`, `tipo_factura`, `tipo_rectificativa`,
    `idfactura_rectificada`, y `num_factura` de 15 a 60 caracteres porque el número pasa a
    llevar la serie delante.
  - Tabla `registro_facturacion` con clave única sobre la huella y disparadores que rechazan
    `UPDATE` y `DELETE`.
  - `RegistroFacturacion`, `TipoRegistro` y `EstadoEnvio` en `verifactu/dto/`.
  - Numeración por serie, no solo por año.
  - Encadenado con `SELECT ... FOR UPDATE` sobre el último registro del emisor.
  - Enganche en `FacturaServiceImpl` **dentro de la transacción de emisión**.
  - `CadenaCanonica.alta(RegistroFacturacion)` como sobrecarga que delega en la firma de ocho
    parámetros. Resuelve el punto 12 sin tocar lo verificado.
- **Riesgo: el más alto del plan.** Es donde se rompe en silencio. Mitigación explícita:
  `CadenaCanonica` y `CalculadorHuella` no se modifican por dentro, y las 13 pruebas de los
  vectores oficiales siguen invocando la firma antigua. Si una de ellas se mueve un carácter,
  el refactor está mal y se revierte.
- **Cómo se verifica:** con Testcontainers sobre una base sembrada.
- **Qué pruebas lo cubren:** 20 facturas seguidas producen una cadena sin huecos ni
  bifurcaciones; dos emisiones simultáneas nunca comparten huella anterior; la primera factura
  del sistema lleva la huella anterior vacía y la marca de primer registro; facturas de dos
  series distintas comparten cadena, en orden de generación; `UPDATE` y `DELETE` sobre
  `registro_facturacion` fallan con el mensaje del disparador; la migración aplicada sobre una
  base con facturas previas no deja ninguna fila sin emisor ni sin fecha y hora.

### H5 · QR y leyenda (cumplimiento mínimo, 2 de 2)

- **De qué depende:** H4.
- **Qué cambia:** `GeneradorQr` con ZXing —nivel de corrección M, margen 4, 472 píxeles de
  lado para 40 mm a 300 ppp— y la maquetación completa en la hoja impresa: rótulo «QR
  tributario:», imagen entre 30 y 40 mm, zona de silencio y leyenda. **Hay que escribirla
  entera**: no existe ningún hueco previo.
- **Riesgo:** bajo en código, medio en maquetación. La hoja impresa es la única pantalla sin
  pruebas automáticas.
- **Cómo se verifica:** la URL generada se compara **contra la que fija el PDF
  *Características del QR y servicio de cotejo*, versión 0.5.0 del 10/12/2025**, no contra la
  que produzca el programa. El QR impreso se escanea con un móvil y tiene que abrir la página
  de cotejo de la AEAT.
- **Qué pruebas lo cubren:** una URL con un número de factura que contenga un ampersand sigue
  teniendo cuatro parámetros (es donde está el riesgo real, y por eso se codifica en UTF-8);
  el importe se serializa con dos decimales y sin notación científica; el PNG generado se
  vuelve a decodificar con ZXing y devuelve la URL de partida.

> **H4 + H5 es el hito de «cumplimiento mínimo verificable».** Al cerrarlo, cada factura
> emitida genera un registro correcto, encadenado, inmutable por disparador, con su QR
> impreso que abre la sede de la AEAT. El envío es un doble de prueba. Todo esto es
> verificable entero **sin certificado y sin red**.

### H6 · XML validado contra el XSD

- **De qué depende:** H4.
- **Qué cambia:** los XSD oficiales se descargan y **se guardan dentro del repositorio** con
  la fecha de descarga, y de ahí se generan las clases con `jaxb2-maven-plugin`.
  `ConstructorSuministro` pasa del registro al XML.
- **Riesgo:** bajo. Si el XSD cambia, se regenera.
- **Cómo se verifica:** el XML de un registro real valida contra `SuministroInformacion.xsd`
  y `SuministroLR.xsd` con un validador de JAXP.
- **Qué pruebas lo cubren:** validación contra el XSD de un alta y de una anulación; y que los
  campos del XML coinciden con los que entraron en la huella, que es el punto donde XML y
  huella se pueden desalinear sin avisar.

### H7 · Envío: interfaz, máquina de estados y cola

- **De qué depende:** H6.
- **Qué cambia:** interfaz `ClienteAeat`, implementación de prueba que devuelve respuestas
  guardadas, máquina de estados de pendiente a aceptado, aceptado con errores o rechazado, y
  cola con reintentos acotados y espera creciente. El envío va **fuera** de la transacción de
  emisión.
- **Riesgo:** medio. Los códigos de error concretos salen del PDF *Validaciones y errores*
  v1.2.2; cuáles llegan de verdad no se sabrá hasta H8.
- **Cómo se verifica:** con respuestas de prueba construidas a partir de ese PDF.
- **Qué pruebas lo cubren:** un corte de red deja el registro pendiente y **la factura se
  emite igual**; un aceptado-con-errores no se reenvía; un rechazo por NIF inválido no entra
  en bucle; el tiempo de espera que indica la respuesta retrasa efectivamente el siguiente
  envío; un lote de más de 1.000 registros se trocea.

### H8 · Certificado y SOAP real

- **De qué depende:** H7.
- **Qué cambia:** WSDL oficial guardado en el repositorio, cliente generado con
  `cxf-codegen-plugin`, fábrica de contexto SSL desde un PKCS#12, TLS mutuo, y el extremo
  (preproducción o producción) como **configuración, nunca constante compilada**.
- **Riesgo:** **este hito no se puede terminar.** Ver la sección 8.
- **Cómo se verifica:** hasta donde se puede, que el cliente generado compile contra el WSDL
  guardado y que el contexto SSL se construya desde un PKCS#12 de prueba autofirmado.
- **Qué pruebas lo cubren:** construcción del contexto SSL, y selección de extremo por perfil.
  **Nada que dependa del servicio real.**

### H9 · Anulación y rectificativas R1 a R5

- **De qué depende:** H7.
- **Qué cambia:** registro de anulación —la cadena canónica ya existe y está verificada—,
  árbol de decisión anular o rectificar, tipos R1 a R5 apuntando a la factura rectificada, y
  retirada de cualquier camino que borre una factura emitida.
- **Riesgo:** medio. Toca `facturas.js`, que en este punto ya estará troceado si H10 va antes.
- **Cómo se verifica:** anular crea un registro nuevo y **no modifica el alta original**.
- **Qué pruebas lo cubren:** la anulación encadena correctamente; una rectificativa genera
  registro de **alta**, no de anulación; no existe ningún endpoint que borre una factura
  emitida, ni siquiera protegido.

### H10 · `facturas.js` en módulos, y paginación del listado

- **De qué depende:** H0 (para que la spec de Playwright se ejecute y haga de red).
- **Qué cambia:** las 1.014 líneas se reparten en módulos ES integrados **en el mapa de capas
  existente de `main.js`**, no en un mapa paralelo. Más paginación del listado de facturas,
  que es lo que hace innecesario el límite provisional de H2.
- **Riesgo:** alto de regresión, en la pantalla más complicada. Por eso depende de que la
  spec de Playwright corra primero.
- **Cómo se verifica:** la spec de Playwright pasa antes y después, sin tocarla.
- **Qué pruebas lo cubren:** `facturas-alta.spec.cjs`, ya escrita, más las que haga falta
  añadir para la paginación.

### H11 · `fecha_cobro`

- **De qué depende:** H2. Fuera del camino de VERI\*FACTU.
- **Qué cambia:** columna `fecha_cobro date NULL`, campo en el detalle, y una forma de marcar
  una factura como cobrada. Hoy no existe ninguna.
- **Riesgo:** bajo. No toca el registro ni la cadena: el cobro no es un dato fiscal que viaje
  a la AEAT.
- **Cómo se verifica:** se marca una factura como cobrada, se recarga, y sigue marcada y en
  estado EMITIDA. Ese último detalle es el que demuestra que los dos ejes están separados.
- **Qué pruebas lo cubren:** marcar y desmarcar el cobro no altera el estado; una factura en
  BORRADOR no admite cobro.

---

## 7 · Dependencias nuevas

| Hito | Artefacto | Ámbito | Para qué |
|---|---|---|---|
| H0 | `spring-boot-testcontainers`, `testcontainers:mysql`, `testcontainers:junit-jupiter` | test | Ejecutar siempre las 20 pruebas de integración |
| H0 | `jacoco-maven-plugin`, `spotless-maven-plugin`, `spotbugs-maven-plugin` | build | Cobertura, formato, análisis estático |
| H3 | `spring-boot-starter-security` | runtime | Autenticación |
| H5 | `com.google.zxing:core`, `com.google.zxing:javase` | runtime | PNG del QR |
| H6 | `jaxb2-maven-plugin` | build | Clases desde el XSD oficial |
| H8 | `cxf-codegen-plugin` | build | Cliente desde el WSDL oficial |

Los plugins de build no llegan al artefacto desplegado.

---

## 8 · Lo que quedará sin verificación externa

Se repite en el resumen de cada fase afectada.

- **H8 entero.** Se construye contra el WSDL publicado, con el extremo detrás de
  `ClienteAeat` y una implementación de prueba, de forma que pasar al real sea cambiar
  configuración. Pero **no habrá prueba de que un envío real devuelva un CSV**: hace falta un
  certificado de la FNMT dado de alta y válido en preproducción.
- **De H7, los códigos de error reales.** Las respuestas de prueba salen del PDF
  *Validaciones y errores* v1.2.2, no del servicio.
- **De H6, la versión del XSD.** Valida contra el XSD que se descargue el día que se escriba,
  guardado en el repositorio. La AEAT publica revisiones.

Cuando haya certificado, hay que confirmar tres cosas antes de nada: **de qué tipo es**
(determina el extremo), **con qué NIF está asociado** (ese NIF va en `IDEmisorFactura` y en
`ObligadoEmision`; si no coinciden, la AEAT rechaza) y **cuál es su contraseña**.

---

## 9 · Fuentes externas

Los valores de referencia vienen de aquí, nunca de ejecutar el programa.

| Documento | Versión verificada | Qué aporta |
|---|---|---|
| Algoritmo de cálculo de la huella (PDF) | 0.1.2 · 27/08/2024 | Cadena canónica, normalización y los tres vectores |
| Características del QR y servicio de cotejo (PDF) | 0.5.0 · 10/12/2025 | Tamaño, nivel M, rótulo, leyenda y URL de validación |
| Diseños de registro | página viva | Campos del registro de alta, anulación y evento |
| Esquemas XSD | página viva | `SuministroInformacion.xsd`, `SuministroLR.xsd`, límite de 1.000 |
| WSDL de los servicios web | página viva | Contrato y direcciones de cada entorno |
| Validaciones y errores (PDF) | 1.2.2 | Errores admisibles y no admisibles |

Normativa: RD 1007/2023, Orden HAC/1177/2024, RDL 15/2025.

---

## 10 · Riesgos, ordenados

1. **Romper la huella sin enterarse (H4).** Mitigado: no se toca el interior de las clases
   verificadas, solo se añade una sobrecarga, y las 13 pruebas de los vectores siguen usando
   la firma antigua.
2. **CSRF rompiendo el frontend (H3).** Mitigado: la spec de Playwright corre contra la
   aplicación real desde H0.
3. **Migración a medias (H4).** El DDL de MySQL hace COMMIT implícito: no hay ROLLBACK. Copia
   de seguridad obligatoria y comprobaciones entre bloques, como ya documenta
   `migracion-verifactu.sql`.
4. **Regresión en `facturas.js` (H10).** Mitigado por la dependencia de H0.
5. **Credenciales y logs ya en el historial (H1).** No se resuelve borrando el fichero. Se
   entrega el procedimiento; reescribir historia lo decide el dueño del repositorio.
