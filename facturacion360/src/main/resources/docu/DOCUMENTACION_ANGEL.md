# Clientes — Listado, buscador, filtros, ordenación, alta, detalle y edición

Muestra en la tabla de `clientes.html` **los clientes** dados de alta, con **buscador**,
**filtros** por provincia y población y **ordenación**, pidiéndolos al backend por `fetch`.
Cada fila se **despliega** para ver el detalle completo o para **editarlo** allí mismo, y el
**alta** se rellena en un formulario que se abre arriba de la propia tabla.
Está hecha con **JDBC Template + MySQL**,
siguiendo la arquitectura por capas que subió Val a `master`.

## Qué hace

- **Listar paginado, buscar, filtrar y ordenar** — todo en el mismo endpoint:
  `GET /cliente/listar-pagina?pagina=0&tamano=10&busqueda=&provincia=&poblacion=&ordenarPor=&direccion=`
  → devuelve una **página** de clientes + metadatos de paginación, como JSON.
- **Desplegables de filtro**: `GET /cliente/provincias` y `GET /cliente/poblaciones?provincia=`
  → las provincias y poblaciones que existen en la tabla, para rellenarlos.
- **Listar últimos** (⚠️ **obsoleto**, ver abajo): `GET /cliente/listar-ultimos` → los **10
  más recientes** en una lista simple.
- **Ver el detalle**: `GET /cliente/{id}` → todos los datos de un cliente, incluidos los que la
  tabla no enseña (dirección, código postal, población y provincia).
- **Editar**: `PUT /cliente/{id}` → guarda los cambios del formulario que se abre en la fila.
- **Dar de alta**: `POST /cliente` → crea el cliente que se rellena en el formulario que se
  abre arriba de la tabla.
- **Frontend**: `clientes.js` pide una página y pinta la tabla; arriba hay una barra con el
  buscador, los dos desplegables y el control de orden, y debajo los botones
  **"Anterior" / "Siguiente"** para moverse entre páginas. Al pulsar una fila (o el botón del
  ojo) se despliega **debajo** una fila con el detalle; con el lápiz, con los campos
  editables. Y "Añadir Cliente" abre ese mismo formulario en blanco, en la primera posición
  de la tabla.

**¿Por qué buscar NO tiene endpoint propio?** Porque buscar es "listar con un filtro de texto
más". Si `/cliente/buscar` fuera aparte, habría que darle su propia paginación, sus propios
metadatos y su propio manejo de errores —y el frontend tendría dos caminos distintos según
hubiera texto escrito o no—. Compartiendo endpoint, la búsqueda hereda todo eso gratis.

---

## Qué entrega cada rama

Este documento cubre **tres ramas apiladas**, y conviene saber qué toca cada una antes de
leerlo, porque se integran en este orden y cada una da por hecha la anterior:

| Rama | Qué añade | Se apoya en |
|---|---|---|
| `feature/verDetallesYEditar_Angel` | Ver el detalle y editar en la fila; `GET /cliente/{id}` y los arreglos del `PUT` | `master` |
| `feature/modulosES_Angel` | Parte `clientes.js` en 16 módulos ES por capas. **No cambia ningún comportamiento** | la anterior |
| `feature/añadirclientes_Angel` | El alta en la tabla y los colores de estado | `verDetallesYEditar` |

Todo lo que se cuenta más abajo describe el **comportamiento**, que es el mismo en las tres.
Cuando algo sea propio de una, se dice.

> **Los nombres de fichero de este documento son los de `verDetallesYEditar_Angel`**, donde
> todo vive en `clientes.js`. En `modulosES_Angel` ese fichero está partido: el mapa de qué
> función vive en qué módulo está en la cabecera de `js/main.js`.

### Lo que entrega `feature/verDetallesYEditar_Angel`

Resumen de todo lo implementado, para no tener que reconstruirlo leyendo los commits uno a uno.
Cada punto tiene su explicación completa más abajo; aquí está el **qué** y el enlace al
**porqué**.

#### Backend

| Endpoint | Estado | Códigos que devuelve |
|---|---|---|
| `GET /cliente/{id}` | **nuevo** — antes era un esbozo que devolvía `null` | 200 · 404 · 500 |
| `PUT /cliente/{id}` | **arreglado** — tres fallos, ver abajo | 200 · 400 · 404 · 409 · 500 |
| `GET /cliente/listar-pagina` | el `400` sale ya con cuerpo vacío, como el resto | 200 · 400 · 500 |
| `GET /cliente/listar-ultimos` | marcado `@Deprecated`; sigue funcionando | 200 · 500 |
| `GET /cliente/provincias` · `/poblaciones` | registran su `200`, que era lo único que faltaba | 200 · 500 |

**Los tres fallos del `UPDATE`**, cada uno con su prueba de regresión:

1. **Guardar sin cambiar nada respondía `404`.** MySQL informa de 0 filas afectadas cuando la
   sentencia no altera ningún valor, y ese `false` se traducía a "no existe": pulsar Guardar sin
   tocar un campo decía que el cliente no estaba. Ahora la existencia se comprueba con
   `findById` **en la misma transacción** y el booleano del `update` se ignora a propósito.
   → `guardarSinCambiosNoEsUnFallo`
2. **La fecha de alta se borraba.** `ClienteRequest` no la trae, así que el mapper la dejaba a
   `null` y la respuesta vaciaba la columna "Alta" en cuanto la fila se repintaba. Ahora se
   conserva la que hay en la base de datos. → `laFechaDeAltaSeConserva`
3. **El NIF repetido soltaba la traza de Spring al navegador.** `nif_cif` tiene índice `UNIQUE`
   y la `DuplicateKeyException` no se capturaba. Ahora responde **409**. →
   `guardarConNifDuplicado`

Además: `actualizar` pasa a devolver `Optional<Cliente>` en vez de `null`, como `obtenerPorId`
([por qué](#actualizar-también-devuelve-optional)); el escape de comodines del `LIKE` cambia a
`!` para no depender del `sql_mode` del servidor
([por qué](#por-qué-el-carácter-de-escape-es--y-no-la-barra-invertida)); y los criterios sanean
los saltos de línea antes de llegar al log ([por qué](#qué-registra-cada-endpoint)).

#### Frontend

**El despliegue de la fila** ([detalle](#ver-el-detalle-y-editar-en-la-propia-fila)):

- Un único panel por cliente con **dos modos**, detalle o edición, en vez de dos filas que
  pudieran contradecirse. Varios abiertos a la vez.
- **El estado vive fuera del DOM**, así que los paneles abiertos y lo que estés escribiendo
  sobreviven a buscar, paginar y refrescar.
- **Se pinta al instante** con los datos que ya trajo el listado y se revalida contra el
  servidor en paralelo; si algo cambió, se concilia **campo a campo sin pisar lo escrito**
  ([detalle](#cuando-los-datos-frescos-no-coinciden-conciliar)).
- Descartar cambios pregunta con un **diálogo de Bootstrap**, no con el `confirm()` del
  navegador ([por qué](#descartar-cambios-un-diálogo-de-bootstrap-no-confirm)).
- Tras guardar: **el foco vuelve** al lápiz de esa fila, se anuncia el resultado y la tabla se
  refresca sola ([detalle](#qué-pasa-exactamente-al-pulsar-guardar)).
- **Recuperación de página fuera de rango**: si la página en la que estabas deja de existir, se
  aterriza en la última que sí ([detalle](#coordinación-con-listarpagina-la-página-que-ya-no-existe)).

**Semántica y accesibilidad**
([detalle](#accesibilidad-y-semántica-de-la-tabla-y-la-barra-de-filtros)):

- **Se acabaron las tablas anidadas**: el detalle es una lista de descripción y la edición un
  formulario con `<label for>` y `<fieldset>`. De paso se corrigen **dos incumplimientos del
  criterio 2.5.3** ([por qué](#por-qué-los-paneles-no-son-tablas)).
- Cada panel **dice de qué cliente es**, y en edición cada campo estrena su **lápiz**.
- El nombre del cliente es `<th scope="row">`, la barra de filtros un `<search>`, la página
  tiene `<h1>` y la tabla `<caption>`.
- **Contrastes medidos, no elegidos a ojo**: cuatro textos no llegaban al mínimo
  ([tabla](#contraste-los-grises-se-han-medido-no-elegido-a-ojo)).
- Región de anuncios invisible + franja visible, para que nada se cuente dos veces en pantalla
  ([por qué](#dos-sitios-para-los-avisos-uno-que-se-ve-y-otro-que-se-lee)).

**Rendimiento y robustez**:

- `AbortController` **por canal** y espera de 300 ms en el buscador, para que dos respuestas no
  se crucen.
- Reabrir los paneles tras repintar **no cuesta ninguna petición**: los datos acaban de llegar
  con el listado.
- **Regla del `await`**: después de esperar al servidor, nada de lo que se capturó antes se da
  por vivo. Sin ella, un error de guardado se escribía en un formulario que ya no estaba y el
  usuario creía que había guardado.
- `integrity` + `crossorigin` en los tres CDN, fecha formateada en UTC y `colspan` calculado
  según las columnas visibles.

#### Calidad

- **44 pruebas** en cuatro clases, **ninguna necesita MySQL**
  ([qué cubre cada una](#tests-automáticos)).
- Nomenclatura en español y descriptiva en todo, y la regla de "variable antes del `return`"
  **sin excepciones** ([por qué](#a-decisiones-de-estilo-inyección-de-dependencias-y-forma-del-return)).
- Un `.mailmap` en la raíz que agrupa las tres firmas de git con las que se había ido firmando
  (`angel`, `Angel` y una con emojis, las tres con el mismo correo), para que el recuento de
  commits no salga partido en tres. No reescribe ningún commit: es una tabla de traducción que
  git aplica al mostrar.

> **Sobre cómo se ha construido.** Buena parte de esta rama se ha desarrollado con asistencia
> de IA, y los commits lo reflejan con su firma `Co-authored-by`. Lo que **no** ha sido
> automático es la revisión: cada decisión de las que se explican aquí se ha contrastado contra
> el código y contra los datos reales de la base de datos, y varias recomendaciones que parecían
> razonables se han descartado precisamente por hacerlo —el `@Pattern` del NIF/CIF es el ejemplo
> más claro y está contado en [Limitaciones conocidas](#limitaciones-conocidas)—.

### Lo que entrega `feature/modulosES_Angel`

**Ningún cambio de comportamiento**: parte `clientes.js` —que había llegado a 2.000 líneas— en
16 módulos ES organizados por capas, con una sola regla que los mantiene ordenados: *un módulo
solo importa de capas estrictamente inferiores*. El mapa completo está en la cabecera de
`js/main.js`, que es el único fichero que carga el HTML.

Los dos sitios que rompen la línea recta lo hacen con un evento y no con un `import`, porque un
módulo de abajo necesita provocar algo de arriba: `clientes:cambiaron` y
`clientes:limpiar-filtros`.

### Lo que entrega `feature/añadirclientes_Angel`

- **Alta de clientes en la propia tabla** (`POST /cliente`), reutilizando el formulario de la
  edición. Sustituye al modal, que se borra: no funcionaba y le faltaban tres campos
  obligatorios. Ver [El alta de clientes](#el-alta-de-clientes).
- **Colores de estado repartidos en dos juegos**, uno para el borde y otro para el texto, que
  es lo que permite que el recuadro se vea vivo sin bajar el contraste del cartel. El detalle
  pasa de verde a **morado** y el alta se queda el verde.

---

## Arquitectura por capas

Cada capa tiene una única responsabilidad. El flujo de una petición es:

```
Navegador ─GET /cliente/listar-pagina→ Controller → Service → Repository(JDBC) → MySQL
Navegador ←──── JSON ─── PaginaClienteResponse ←(Mapper)─ Cliente ←(RowMapper)─ fila
```

| Capa | Fichero | Responsabilidad |
|------|---------|-----------------|
| **Controller** | `controller/ClienteController.java` | Recibe la petición HTTP, comprueba que los datos que llegan son válidos, orquesta la llamada y traduce el resultado a un código: `200`, `400` si los datos no valen, `404` si no existe, `409` si el NIF está repetido y `500` si falla la BD. No calcula nada. |
| **Service** | `service/ClienteService` (interfaz) + `ClienteServiceImpl` | La lógica de negocio y las transacciones. Es donde se calculan los metadatos de paginación y donde vive lo delicado de `actualizar`: conservar la fecha de alta, distinguir "no existe" de "guardado sin cambios" e ignorar el booleano del `update`. |
| **Repository** | `repository/ClienteRepository` (interfaz) + `ClienteRepositoryJdbcImpl` | La **única** capa que habla SQL: monta las consultas y las ejecuta con `JdbcTemplate`. Aquí viven las dos defensas del buscador (lista blanca del `ORDER BY` y escape de comodines). |
| **RowMapper** | `repository/ClienteRowMapper.java` | Convierte cada fila del `ResultSet` en un objeto `Cliente`. |
| **DTOs de entrada** | `dto/ClienteRequest` (cuerpo del `POST`/`PUT`) · `dto/CriteriosCliente` (parámetros del listado) | Lo que llega del navegador, con sus validaciones. `CriteriosCliente` además se normaliza a sí mismo. |
| **DTOs de salida** | `dto/ClienteResponse` (un cliente) · `dto/PaginaClienteResponse` (una página + metadatos) | Lo que viaja como JSON. Separarlos del dominio deja cambiar el modelo por dentro sin romper el contrato. |
| **Dominio** | `dto/Cliente` | Lo que se maneja entre capas, ajeno a HTTP y a SQL. |
| **Mapper** | `dto/ClienteMapper.java` | Traduce `ClienteRequest` → `Cliente` → `ClienteResponse`. |

## Cómo lo hemos hecho

> ⚠️ **`listar-ultimos` está marcado como obsoleto (`@Deprecated`).** Lo que sigue explica cómo
> funciona, porque es el endpoint con el que se montó el listado y sirve de ejemplo del caso
> simple, pero **el camino bueno es `listar-pagina`**: hace lo mismo y además admite búsqueda,
> filtros y ordenación, y devuelve los metadatos de paginación.
>
> **Obsoleto no es lo mismo que roto**: sigue funcionando y con sus dos pruebas. Se mantiene
> porque es el endpoint con el que se montó el listado y sirve de ejemplo del caso simple —el
> que solo trae N filas, sin criterios ni metadatos—, y porque borrarlo sería romper el
> contrato a cualquiera que ya lo estuviera llamando. Lo que se ha añadido es
> `deprecated = true` en su `@Operation`, para que Swagger lo pinte tachado: hasta ahora,
> quien lo encontrara ahí no tenía ninguna forma de saber que no era el camino a seguir.
>
> Hasta hace poco lo consumía además `cors/inicio.js`, la demostración de CORS y JSONP del
> profesor. Esa demo ya no está en el proyecto, así que **hoy no lo llama nadie**.

1. **`ClienteRepositoryJdbcImpl.findUltimos(limite)`** — ejecuta con `JdbcTemplate`:
   ```sql
   SELECT idcliente, nombre, nif_cif, direccion, codigopostal,
          poblacion, provincia, telefono, email, fecha_alta
   FROM clientes ORDER BY idcliente DESC LIMIT ?
   ```
   - **`ORDER BY idcliente DESC`**: `idcliente` es **autoincremental** (la BD asigna un número
     mayor a cada alta nueva), así que ordenar de mayor a menor equivale a ir **del más reciente
     al más antiguo**, sin necesitar una columna de fecha.
   - **`LIMIT ?`**: de esa lista ya ordenada, nos quedamos solo con los primeros `limite`. El
     troceado lo hace **MySQL, no Java**, así que es eficiente aunque la tabla tenga miles de filas.
   - **El `?` evita la inyección SQL**: `JdbcTemplate` usa un *PreparedStatement*, donde el valor
     de `limite` viaja a la BD **aparte** del texto SQL y **nunca se interpreta como código**. Si
     en cambio pegáramos el valor dentro del `String` (`... LIMIT " + limite`), un valor malicioso
     podría "colar" instrucciones SQL extra; con `?` el dato y la instrucción van por separado y
     eso es imposible.

   El resultado se guarda en una variable, se **loguea** (SLF4J) y se devuelve.
2. **`ClienteRowMapper.mapRow`** — construye un `Cliente` leyendo cada columna del `ResultSet`.
3. **`ClienteServiceImpl.listarUltimos(limite)`** — delega en el repositorio (aquí viviría la
   regla de negocio si se complicara).
4. **`ClienteMapper.toResponse(cliente)`** — traduce el `Cliente` de dominio al
   `ClienteResponse` que viaja como JSON.
5. **`ClienteController.listarUltimos(limite)`** — valida el `limite` (acotado 1–100), pide al
   service, mapea a `ClienteResponse`, **loguea** el resultado y lo devuelve. Va envuelto en
   `try/catch (DataAccessException)`: si la BD falla, lo registra en el log y devuelve `500`.
6. **`clientes.js`** — pide una página, y por cada cliente clona el `<template>` de la tabla
   rellenándolo con `textContent` (seguro frente a `<`/`&`).

### Paginación (de N en N)

Además de `listar-ultimos`, añadimos un **endpoint nuevo** `GET /cliente/listar-pagina?pagina=&tamano=`
(deja `listar-ultimos` intacto). **Idea**: `LIMIT n` = "dame n filas"; `OFFSET m` = "sáltate m". Con
páginas de 10: página 0 → `OFFSET 0`, página 1 → `OFFSET 10`… (`offset = pagina * tamano`); el
troceado lo hace MySQL. Las piezas:

- **Repositorio**: `findPagina(criterios)` (`... WHERE ... ORDER BY ... LIMIT ? OFFSET ?`) y
  `contarTotal(criterios)` (`SELECT COUNT(*)` con `queryForObject`, que devuelve un único
  valor). Los dos reciben el mismo `CriteriosCliente`, que agrupa los siete datos de la
  consulta; empezaron llevándolos sueltos y se agruparon después
  ([por qué](#los-criterios-viajan-juntos-el-record-criterioscliente)).
- **DTO `PaginaClienteResponse`**: la lista de la página + metadatos (`paginaActual`, `totalPaginas`,
  `totalElementos`, `hayAnterior`, `haySiguiente`) para que el frontend sepa dónde está.
- **Service `listarPagina(...)`**: calcula `offset`, el total de páginas con
  `Math.ceil((double) total / tamano)`, los flags `hayAnterior/haySiguiente`, y mapea a `ClienteResponse`.
- **Controller `listarPagina`**: mismo patrón (validación, logs, `try/catch`); devuelve el `PaginaClienteResponse`.
- **`clientes.js`**: guarda la `paginaActual`, pide `/cliente/listar-pagina?...`, pinta
  `datos.contenido` y **activa/desactiva** los botones "Anterior"/"Siguiente" según los flags.

> **Ojo con el `offset`**: se calcula como `(long) pagina * tamano`, con el `(long)` en el **primer**
> operando. Si se multiplicara en `int` y se ampliara después, una página muy alta (`?pagina=30000000`)
> **desbordaría** el `int` antes de convertirse, daría un offset **negativo** y MySQL fallaría con un
> error de sintaxis. Un `500` que cualquiera puede provocar desde la barra de direcciones.

### Buscador, filtros y ordenación

Los tres se resuelven en la **misma consulta**: se monta un `WHERE` con lo que llegue informado y
se le pega el `ORDER BY` y el `LIMIT/OFFSET`.

#### Un solo constructor de `WHERE` (`anadirFiltros`)

`findPagina` (las filas) y `contarTotal` (cuántas hay) llaman **al mismo método**. Es importante:
si cada uno montara su propio `WHERE`, bastaría con que alguien tocara uno y se olvidara del otro
para que el número de páginas dejara de cuadrar con lo que se ve en pantalla.

Las condiciones se van metiendo en una lista y al final se unen con `AND`. Así no hay que ir
preguntando en cada `if` "¿soy la primera, pongo `WHERE` o `AND`?".

#### Búsqueda: los comodines de `LIKE` hay que escaparlos

La búsqueda usa `LIKE` con comodines alrededor del término:

```sql
WHERE (nombre LIKE ? ESCAPE '!' OR nif_cif LIKE ? ESCAPE '!')
```

- **Los paréntesis del `OR` son obligatorios**: sin ellos, los `AND` de los filtros solo se
  aplicarían a la parte del `nif_cif`, porque `AND` tiene **prioridad** sobre `OR`.
- **`%` y `_` son metacaracteres de `LIKE`** (`%` = "cualquier cosa", `_` = "un carácter
  cualquiera"). Si el término del usuario se mete tal cual, buscar `%` produce el patrón `%%%`,
  que casa con **TODAS** las filas: el buscador deja de filtrar y devuelve la tabla entera. Por eso
  `escaparComodines()` los neutraliza antes de envolver el término. **No es inyección SQL** (el
  valor sigue viajando como `?`), pero sí una forma de saltarse el filtro.
- **El orden de escapado importa**: el propio carácter de escape va **primero**. Si se
  sustituyera el último, volvería a escapar los que acabamos de introducir para `%` y `_`.

#### Por qué el carácter de escape es `!` y no la barra invertida

Casi todos los ejemplos que se encuentran usan `\`, y así empezó esto. Se cambió por dos
motivos, y el segundo es un fallo de verdad:

**1. Con la barra, el mismo carácter se escribía de cuatro maneras.** Dentro de una cadena de
MySQL la barra invertida escapa al carácter siguiente, así que `ESCAPE '\'` no compila: la
comilla de cierre quedaría escapada. Hay que poner `'\\'`, y como además hay que escaparlas
para el `String` de Java, en el código se leían **cuatro**:

```java
// Antes: cuatro barras en Java para decir una en la base de datos
condiciones.add("(nombre LIKE ? ESCAPE '\\\\' OR nif_cif LIKE ? ESCAPE '\\\\')");
```

Era el sitio del repositorio donde más fácil resultaba equivocarse: quitar o añadir una barra
compila igual y solo se nota consultando.

**2. Con el `sql_mode` `NO_BACKSLASH_ESCAPES`, la consulta entera se rompía.** Ese modo hace que
MySQL deje de tratar la barra invertida como carácter de escape dentro de las cadenas, así que
`'\\'` pasa a ser una cadena de **dos** caracteres y `ESCAPE` solo admite uno: la respuesta es
`Incorrect arguments to ESCAPE` y el listado —la pantalla principal— devolvía un **500**. No es
el modo por defecto, pero lo activa `mysqldump` y hay instalaciones que lo dejan puesto, así que
la aplicación funcionaba o no según cómo estuviera configurado el servidor de cada uno.

El `!` no significa nada en ninguno de los dos modos. La consulta se lee igual en el código y en
la base de datos, y el término que contenga un `!` se escapa como cualquier otro carácter
especial. Por eso el test compara el fragmento `ESCAPE` **entero** y no solo que aparezca la
palabra: cambiarlo sin querer no rompe la compilación, se nota en la base de datos.

- **Sin `LOWER()`**: la tabla es `utf8mb4_0900_ai_ci`, y ese `ai_ci` significa *accent insensitive,
  case insensitive*: ya compara ignorando mayúsculas **y** acentos, así que `garcia` encuentra
  `García`. Envolver la columna en `LOWER()` no cambiaría el resultado y además la volvería
  **no-sargable** (con una función encima, ningún índice puede usarse).

#### Ordenación: por qué hace falta una lista blanca

El `ORDER BY` **no admite `?`**. No es un capricho: un `?` es un *valor*, y el nombre de una
columna es *parte de la instrucción*. Así que, o se traduce, o habría que concatenar el texto del
usuario dentro del SQL — y eso **sí** sería inyección SQL de manual.

La solución son dos parámetros y dos traducciones:

```java
ordenarPor  →  se busca como CLAVE en el mapa COLUMNAS_ORDEN → nombre real de columna
direccion   →  "asc".equalsIgnoreCase(direccion) ? "ASC" : "DESC"
```

Fíjate en que el texto del usuario **nunca se concatena**: solo se usa como clave de búsqueda. Si
pide una columna que no está en el mapa (o intenta colar `nombre; DROP TABLE clientes`), no se
encuentra y cae en el valor por defecto. La dirección solo puede acabar valiendo `ASC` o `DESC`.

Ventaja de tenerlo en dos parámetros en vez de un único string tipo `nombre_az`: **permitir ordenar
por una columna más es añadir una línea al mapa**, y funciona en ambos sentidos automáticamente.
Con el string opaco harían falta dos valores nuevos por cada columna.

El `ORDER BY` que sale es:

```sql
ORDER BY fecha_alta IS NULL, fecha_alta DESC, idcliente DESC
```

- **`columna IS NULL` primero**: `fecha_alta` admite `NULL`, y MySQL coloca los nulos al principio
  en `ASC` y al final en `DESC`. Sin esto, los clientes sin fecha **saltarían de un extremo al otro**
  de la lista al invertir el orden. Con esto quedan siempre al final.
- **`idcliente` de desempate**: sin él, dos clientes con la misma fecha (o el mismo nombre) pueden
  salir en orden distinto en cada consulta y, al paginar, verse **repetidos** en una página y
  **desaparecer** de la siguiente.

**La ordenación se aplica sobre lo ya filtrado sin trabajo extra**: en SQL el `ORDER BY` se evalúa
*después* del `WHERE`. Si filtras por Madrid y ordenas alfabéticamente, ordena los de Madrid.

#### La página y el total, en la misma transacción

`listarPagina` hace **dos** consultas: una trae las filas de la página y otra cuenta el total para
saber cuántas páginas hay. Si cada una va por su cuenta y alguien da de alta o borra un cliente
justo entre las dos, verían estados distintos de la tabla: el total diría "12 clientes" mientras se
muestran 10 filas, y la paginación no cuadraría.

Por eso el método lleva:

```java
@Transactional(readOnly = true)
```

Con la transacción abierta, MySQL (InnoDB, aislamiento `REPEATABLE READ`) sirve **las dos consultas
desde la misma foto** de la tabla. El `readOnly = true` avisa además al driver de que no vamos a
escribir, con lo que puede optimizar.

**Los demás métodos de lectura NO la llevan, y es intencionado**: `listarUltimos`,
`listarProvincias` y `listarPoblaciones` hacen **una sola** consulta, así que no hay dos lecturas
que puedan discrepar entre sí y una transacción solo añadiría un `BEGIN`/`COMMIT` sin ganar nada.
Anotar por costumbre allí donde no hace falta es ruido.

#### Los criterios viajan juntos: el record `CriteriosCliente`

Los siete datos que definen una consulta (`pagina`, `tamano`, `busqueda`, `provincia`, `poblacion`,
`ordenarPor`, `direccion`) **no se pasan sueltos**, sino agrupados en un `record`.

El motivo: esa firma de siete parámetros aparecía en **cinco sitios** (controller, interfaz e
implementación del service, e interfaz e implementación del repository). Añadir un filtro nuevo
obligaba a tocar los cinco y a no equivocarse de orden en ninguno. Con el record se añade un
componente y ya está. Es el *code smell* clásico de "lista larga de parámetros".

Spring lo rellena solo desde la URL con `@ModelAttribute`, emparejando cada parámetro con el
componente que se llama igual:

```java
@GetMapping("/listar-pagina")
public ResponseEntity<PaginaClienteResponse> listarPagina(
        @Valid @ModelAttribute CriteriosCliente criterios,
        BindingResult bindingResult) { ... }
```

El `BindingResult` que va detrás no es decorativo: es lo que hace que un criterio demasiado
largo se responda con un `400` **de cuerpo vacío**, como el resto de endpoints, en vez de con la
página de error de Spring ([el porqué](#logs-y-manejo-de-errores)).

**Toda la normalización vive en su constructor compacto**, y eso es lo importante: antes estaba
repartida —el acotado de la página en el controller, la limpieza del término en el service—, así que
había dos sitios donde se decidía qué es un valor válido. Ahora hay uno solo, y da igual si el
record lo construye Spring desde la URL o alguien a mano.

```java
public CriteriosCliente {
    pagina = (pagina == null || pagina < 0) ? 0 : pagina;
    tamano = (tamano == null || tamano <= 0) ? TAMANO_DEFECTO : Math.min(TAMANO_MAX, tamano);
    busqueda = normalizar(busqueda);   // "" y "   " -> null
    ...
}
```

**¿Por qué `Integer` y no `int` en `pagina` y `tamano`?** Es la parte menos evidente y conviene no
tocarla sin saber por qué está. Cuando un parámetro **no viene en la URL**, Spring intenta enlazar
`null` — y `null` no se puede convertir a un primitivo. Con `int` la aplicación respondía `400` a
**todas** las peticiones, incluidas las correctas:

```
Failed to convert value of type 'null' to required type 'int'
```

Con `Integer`, el `null` llega sin problema y es el constructor compacto quien decide el valor por
defecto. A partir de ahí ya nunca son `null`, así que se usan como si fueran `int`.

> Detalle a recordar: esto **compilaba perfectamente**. Solo se ve arrancando la aplicación y
> haciendo una petición. Que un refactor compile no significa que funcione.

#### Frontend: un único estado

`clientes.js` guarda **un solo objeto** `{ busqueda, provincia, poblacion, ordenarPor, direccion }`.
Es lo que impide que los controles se contradigan: se puede cambiar el orden desde el selector de
la barra **o** pulsando las cabeceras "Nombre" y "Alta" de la tabla, y como los dos caminos escriben
en el mismo sitio y luego se repinta todo desde ahí, no pueden acabar mostrando cosas distintas.

- **Al cambiar cualquier criterio se vuelve a la página 0.** Si estás en la página 7 y filtras por
  una provincia con 12 clientes, esa página ya no existe y verías una tabla vacía sin saber por qué.
- **Debounce de 300 ms** en el buscador: se reinicia un temporizador en cada tecla y solo se consulta
  cuando el usuario para de escribir. Sin él, teclear "garcia" lanzaría 6 consultas a la BD.
- **`AbortController`**: el debounce reduce las peticiones, pero no evita que **dos respuestas se
  crucen**. Si escribes "gar", sale la petición, sigues hasta "garcia" y la respuesta de "gar" llega
  la última, pintaría la tabla con resultados que no son los del texto del buscador. El helper
  `pedirJson(canal, url)` cancela la petición anterior **del mismo canal** antes de lanzar la nueva.
  Se usan canales separados (`listado`, `provincias`, `poblaciones`) porque al cambiar de provincia
  se piden a la vez las poblaciones y el listado: con un único controlador compartido, cada una
  abortaría a la otra.
- **Cascada de desplegables**: al elegir provincia se recargan sus poblaciones y se limpia la que
  hubiera elegida (si no, quedaría un filtro "Valencia + Madrid" que no devuelve nada).
- **Columna "Alta"** en la tabla: ordenar por fecha de alta sin ver la fecha no hay forma de
  comprobarlo.

### Cómo está documentada esta parte (Javadoc)

Los contratos se documentan **en las interfaces**, `ClienteRepository` y `ClienteService`, con
`@param`, `@return` y `@throws`. Las implementaciones no repiten ese Javadoc: duplicarlo obliga a
mantener dos textos que acaban diciendo cosas distintas. Lo que sí llevan las implementaciones son
comentarios `//` que explican **cómo** y **por qué** se hace algo, no **qué** hace.

Se documenta también `@throws DataAccessException`, porque forma parte real del contrato: el
controller la captura para responder un `500` en vez de dejar escapar el error.

#### Las etiquetas `@author` y `@autor`

Conviven dos, y no es una errata:

| Etiqueta | Dónde | Por qué |
|---|---|---|
| `@author` | Clases y records | Es la estándar de Javadoc |
| `@autor` | Métodos sueltos | La estándar **no se admite** en métodos |

El `@author` estándar solo vale en *overview*, *package* y *class/interface*. Ponerlo sobre un
método no es que se ignore: con doclint activo **falla la generación** del Javadoc. Para poder
firmar métodos concretos se declara una etiqueta propia en el `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <configuration>
        <tags>
            <tag>
                <name>autor</name>
                <placement>tm</placement>
                <head>Autor:</head>
            </tag>
        </tags>
    </configuration>
</plugin>
```

`placement` indica dónde vale la etiqueta: `t` tipos, `m` métodos, `f` campos, `c` constructores,
`p` paquetes, `a` todas. Con `tm` se admite en clases y en métodos.

> **Importante:** esa configuración y las etiquetas `@autor` van juntas. Si se quita del `pom.xml`,
> javadoc pasa a considerarlas desconocidas y la generación falla. Para retirarla hay que borrar
> antes las etiquetas del código.

Generar la documentación:

```bash
./mvnw javadoc:javadoc      # sale en target/reports/apidocs/index.html
```

> **Ahora mismo ese comando falla, y no por nuestro código.** Son tres líneas de Javadoc en
> métodos de compañeros:
>
> - `ClienteController.crear`: dos líneas empiezan por `@Valid` y `@RequestBody`. Javadoc lee
>   como **etiqueta** cualquier `@algo` al principio de línea, no las conoce y da error. Se
>   arregla escribiéndolas como `{@code @Valid}` y `{@code @RequestBody}`, o metiéndolas dentro
>   de la frase en vez de al principio del renglón.
> - `ClienteRepositoryJdbcImpl.insert`: documenta `@throws SQLException` y el método no la
>   lanza; javadoc lo comprueba y también es error. Se arregla borrando esa línea.
>
> No lo tocamos porque es código de otra persona y cambiarlo por nuestra cuenta sería
> apropiarnos de su parte. Queda anotado aquí para quien lo escribió: con esas tres líneas
> arregladas, el comando termina bien (lo demás son solo avisos).

### Refresco automático tras cambios

Cuando se crea, edita o elimina un cliente, la tabla debe reflejarlo. Lo resolvemos con un **evento
personalizado**, para **desacoplar** nuestra parte de la de los compañeros: `clientes.js` **escucha**
el evento `clientes:cambiaron` y, al recibirlo, **recarga la página actual** (`cargarClientes(paginaActual)`),
volviendo a pedir los datos a la BD. Así nuestra tabla siempre está al día sin conocer el código de
quien hace el cambio (ellos solo tienen que **disparar** el evento — ver la última sección).

Optamos por **re-fetch** (volver a pedir) en vez de tocar el DOM a mano: es más simple y garantiza que
la tabla coincide con la BD. Y descartamos **caché** a propósito: con datos que cambian, un caché
serviría datos viejos (lo contrario de lo que queremos) y habría que invalidarlo en cada cambio.

### Ver el detalle y editar en la propia fila

La tabla enseña cinco columnas, pero un cliente tiene diez campos. Al pulsar una fila —o el
botón del ojo— se inserta **debajo una fila hermana** que ocupa todas las columnas
(`colspan`) y muestra el resto de datos con su rótulo cada uno, en una lista de descripción
(el porqué está en ["Por qué los paneles NO son tablas"](#por-qué-los-paneles-no-son-tablas)).
Con el lápiz se abre esa misma fila, pero con los campos editables. Se pueden tener **varias
abiertas a la vez** y cada una se cierra volviendo a pulsarla.

**Un solo panel por cliente, con un modo** (`detalle` o `edicion`), en vez de dos filas
independientes. Con dos filas se podría acabar viendo a la vez el detalle y el formulario del
mismo cliente diciendo cosas distintas, y habría que sincronizarlos; con una sola, cambiar de
modo es cambiar lo de dentro.

#### El backend: `GET /cliente/{id}`

- **`ClienteRepositoryJdbcImpl.findById(id)`** usa `query(...).stream().findFirst()` y **no**
  `queryForObject`. Este último lanza `EmptyResultDataAccessException` cuando no hay fila, y
  "ese cliente no existe" es una respuesta prevista (acaba en un `404`), no un fallo de acceso
  a datos: montar un `try/catch` para un caso normal es tratar como excepción lo que no lo es.
- **El service devuelve `Optional<Cliente>`** por lo mismo: con `null` es fácil olvidarse de
  comprobarlo, y el `Optional` obliga.
- **El controller** traduce ese `Optional` a `200` o `404`, y captura `DataAccessException`
  para el `500`, igual que el resto de endpoints.
- **No choca con `/cliente/listar-pagina`**: Spring da prioridad a las rutas literales sobre
  las que llevan variable. Y `/cliente/abc` devuelve `400` él solo, sin que nosotros hagamos
  nada, porque no puede convertir el `{id}` a `int` — con el matiz de que ese `400` concreto no
  sale con el cuerpo vacío de los demás
  ([explicado en "Logs y manejo de errores"](#logs-y-manejo-de-errores)).

#### El frontend: estado fuera del DOM

La tabla **se repinta entera** cada vez que se pagina, se busca o se refresca, así que los
paneles abiertos se perderían. Por eso el estado vive en un `Map` (`filasDesplegadas`):
`id del cliente -> { modo, borrador }`. Después de pintar las filas, `reabrirDespliegues()`
vuelve a abrir los que sigan en la página.

- **Reabrirlo tras repintar la tabla no cuesta ninguna petición.** `listar-pagina` devuelve
  `ClienteResponse` completos, exactamente los mismos campos que `GET /cliente/{id}`, así que
  los datos acaban de llegar: se guardan en `clientesEnPagina` (id → cliente) y
  `reabrirDespliegues()` pinta desde ahí. Antes se volvían a pedir uno por uno, y con tres
  paneles abiertos **cada tecla del buscador eran cuatro peticiones** en vez de una, todas para
  dibujar lo que la primera acababa de traer. Ese mapa **no es una caché**: se vacía y se rehace
  en cada repintado, así que nunca contiene nada más viejo que la tabla que se está viendo, que
  es justo lo que se le criticaba a un caché.
- **Abrir un panel a mano pinta ya, y comprueba después.** Ver el detalle es la acción más
  repetida de la pantalla, y esperar a la respuesta para enseñar algo era medio segundo de
  "cargando" para acabar dibujando, casi siempre, lo que ya estaba en memoria. Ahora se pinta
  con lo del listado y **la petición sale igual**, en paralelo: si lo que llega es idéntico
  —el caso normal— no pasa nada de nada; si difiere, se ponen al día la fila y el panel.

  La comprobación no sobra. La tabla puede llevar cargada un buen rato, y abrir un cliente es
  un acto deliberado; abrirlo **para editarlo**, más: el formulario devuelve los ocho campos, y
  partir de una foto vieja revierte sin querer lo que haya cambiado otra persona.

  Que no ahorre peticiones es a propósito: lo que se gana es que el panel aparezca lleno.

#### Cuando los datos frescos no coinciden: `conciliar()`

Enseñar algo y cambiarlo un instante después puede ser peor que la espera, así que hay reglas:

- **Si no ha cambiado nada, no se toca el DOM.** Se comparan los nueve campos que pueden venir
  distintos (los ocho editables más la fecha de alta; el id no, porque es la clave con la que se
  ha buscado el cliente) y se sale. Es el
  caso habitual y tiene que ser invisible.
- **Se actualiza también la fila**, no solo el panel: si cambió el nombre, dejar el viejo en la
  tabla y el nuevo justo debajo es peor que no haber comprobado nada. Como los nombres
  accesibles de los botones llevan dentro el del cliente, se vuelven a escribir con
  `marcarFila()`.
- **En el formulario se va campo a campo, y manda lo que el usuario haya escrito.** Un campo se
  considera intacto si sigue teniendo el valor que se pintó desde la BD; ese se actualiza sin
  más. Si lo ha tocado, se respeta su texto y se le dice cuál era, con un aviso en la alerta del
  propio formulario: *"Otra persona ha cambiado este cliente mientras lo editabas. Se ha
  actualizado: Teléfono. Se ha conservado lo que escribiste en: Nombre o empresa."* Los nombres
  de los campos salen de su `aria-label`, para no tener una segunda lista que se desactualice.
  Y la referencia de "hay cambios sin guardar" pasa a ser el dato nuevo, o al cerrar preguntaría
  por unos cambios que ya no existen.
- **Un `404` significa que lo han borrado**: se avisa en la franja de fuera —lo único que
  sobrevive al refresco—, se olvida el panel y se dispara `clientes:cambiaron`. Es el mismo
  camino que ya tenía ese caso al pulsar Guardar.
- **Cualquier otro fallo se calla** (solo un `console.warn`). En pantalla hay datos buenos, los
  del listado: sustituirlos por un mensaje de error porque una comprobación de fondo no ha ido
  sería empeorar lo que el usuario ya está viendo. El panel de error con su botón de reintentar
  se queda para el único caso en que no hay nada que enseñar: que el cliente no estuviera en el
  mapa.
- **`borrador`**: si la tabla se repinta mientras hay un formulario abierto (basta con teclear
  en el buscador), lo escrito se guarda antes de vaciar el `<tbody>` y se restaura al
  reabrirlo. Sin eso, se perdería sin avisar.
- **Delegación de eventos**: un único listener de `click` y otro de `submit` en el `<tbody>`.
  Los botones se crean y se destruyen en cada repintado, así que enlazarlos uno a uno
  obligaría a volver a hacerlo cada vez. *(Los `addEventListener` que había sobre `.btn-ver` y
  `.btn-editar` no llegaban a enlazar nada: se registraban al cargar la página, cuando esos
  botones todavía vivían dentro del `<template>`.)*
- **Un canal de `AbortController` por fila** (`detalle-7`, `guardar-7`): abrir dos filas a la
  vez lanza dos peticiones que no deben cancelarse entre ellas, que es justo por lo que ya
  había canales separados para el listado y los desplegables.

#### El aviso de "Ver detalles" al segundo de ratón encima

Es un *tooltip* de Bootstrap con `delay: { show: 1000 }`, **delegado** en el `<tbody>`
(opción `selector`): así vale también para las filas que aún no existen y no hay que crearlo y
destruirlo en cada repintado.

- **`container: 'body'`**: la tabla vive dentro de `.table-responsive`, que tiene `overflow`,
  y el globo se veía recortado por arriba.
- El texto cambia según el estado ("Ver detalles" / "Ocultar detalles"), pero el globo se crea
  en el **primer** hover y se queda con el texto que hubiera entonces. Por eso, al alternar, se
  reescribe `data-bs-title` y se **destruye** la instancia: el hover siguiente la vuelve a
  crear ya con el texto nuevo.
- El aviso de la fila lo lleva **una sola celda, la del nombre** (`th.cliente-nombre`). Ni la
  celda de acciones —cada botón de allí explica lo suyo— ni las demás columnas.

> **Los cuatro motivos por los que salían varios globos a la vez y no se iban.** Esto costó
> encontrarlo porque no era un fallo, eran cuatro independientes:
>
> 1. **El aviso lo llevaban las cinco celdas de la fila.** Recorrerla con el ratón encendía
>    cinco instancias con sus cinco temporizadores para decir todas lo mismo. Que el resto de
>    la fila también despliega ya lo cuenta el cursor de mano de `.fila-cliente`.
> 2. **`hide()` no cierra el globo mientras quede un disparador activo.** En Bootstrap 5.3,
>    tanto `_leave()` como el `complete()` de `hide()` se salen antes de tiempo si alguno lo
>    está, y al pulsar un botón **ese botón se queda con el foco**: el globo sobrevivía al
>    `hide()` y también al `mouseleave`. Se pasó a `dispose()`, que derriba el globo pase lo
>    que pase; la raíz delegada vuelve a crear la instancia en el siguiente hover, así que no
>    se pierde nada. Hecho eso, `ocultarPistas` y `limpiarPistas` hacían lo mismo sobre
>    ámbitos distintos y **`ocultarPistas` desapareció**.
> 3. **Con `container: 'body'` el globo cuelga del `<body>`, no del elemento.** Si su dueño se
>    iba sin pasar por `limpiarPistas`, no lo cerraba nadie nunca. Ahora `limpiarPistas` barre
>    además los `.tooltip` que no apunte ningún `aria-describedby`, que es la marca de que un
>    globo sigue vivo. Es lo que cierra el problema de verdad, funcione o no el desmontaje de
>    Bootstrap.
> 4. **`escribirPista` destruía la instancia aunque el texto fuera el mismo**, en cada
>    repintado, peleándose con los temporizadores de apertura en vuelo.
>
> Lo que **no** había que tocar, aunque lo pareciera: `trigger: "hover focus"`. En la `th` el
> foco es inerte, pero los botones de acción lo necesitan para quien navega con teclado.

> **Ojo: son DOS los sitios que vacían el `<tbody>`**, `pintarFilas` y `mostrarMensaje`, y lo
> que haya que rescatar antes de vaciarlo hay que rescatarlo en los dos. A `mostrarMensaje` se
> llega también desde `mostrarError`, que no pasa por `pintarFilas`, y ahí se escapaban dos
> cosas: los globos —quedaba uno flotando sobre el mensaje de error, y sin filas detrás que
> pulsar no había forma de provocar la limpieza— y los borradores de los formularios abiertos.

#### Editar: dos arreglos en el `PUT` que ya existía

1. **La fecha de alta se perdía en la respuesta.** El `ClienteMapper.toDomain` deja `fechaAlta`
   a `null` (no viene en el `ClienteRequest`) y el service copiaba ese `null` al objeto que
   devolvía. La BD conservaba el dato —el `UPDATE` no toca esa columna—, pero el JSON de
   respuesta lo traía vacío, así que la fila se repintaba con un guion en la columna "Alta".
   Ahora se lee el cliente antes de actualizar y se conserva su fecha.
2. **No se distinguía "no existe" de "no cambió nada".** El resultado salía del número de filas
   que devuelve el `UPDATE`, y ese número no separa los dos casos: con la configuración por
   defecto del driver funciona de casualidad (MySQL informa de filas *encontradas*), pero basta
   con activar `useAffectedRows=true` para que guardar sin tocar ningún campo empiece a
   responder `404`. Ahora se comprueba antes con `findById`, y la lectura y la escritura van en
   la **misma transacción** para que entre las dos no se cuele un borrado.

Además, el controller no capturaba nada: cambiar el NIF por el de otro cliente viola el índice
`UNIQUE` de `nif_cif`, la `DuplicateKeyException` se escapaba y el navegador recibía la página
de error de Spring **con la traza dentro**. Ahora devuelve `409`, igual que hace `crear`.

#### Validación: la misma en los dos lados

Los `input` del formulario llevan **exactamente** las restricciones de `ClienteRequest`
(`required` donde hay `@NotBlank`, `maxlength` con el mismo número que el `@Size`,
`type="email"`). Así el navegador corta lo que el servidor rechazaría y no se gasta una
petición para recibir un `400`. Del servidor solo puede llegar un error que el navegador no
puede prever: el **`409` del NIF repetido**, y se señala en *su* campo y no en un aviso
general, porque si no el usuario tendría que adivinar cuál de los ocho campos falla.

> El modal de "Añadir Cliente" que hubo en su día solo tenía cuatro campos, y dirección,
> población y provincia son obligatorias en el backend: por eso el formulario de edición los
> lleva **todos**, y por eso el alta pudo reutilizarlo tal cual. La función de guardar se llama
> `guardarEdicion` y no `guardarCliente` porque ese nombre estaba reservado para el alta, que
> es quien lo ocupa ahora.

#### Qué pasa exactamente al pulsar "Guardar"

Un guardado no termina cuando el `PUT` responde `200`: la fila que se estaba editando puede
haber cambiado de sitio (si se ordena por nombre y se le cambia el nombre) o de página, y el
botón que el usuario tenía pulsado deja de existir. Esta es la secuencia y por qué cada paso:

1. **Se olvida el panel abierto por su `id`**, no por la referencia al `<tr>`. Si la tabla se
   ha repintado mientras viajaba la petición, esa referencia apunta a una fila que ya no está
   en el documento y el `Map` de paneles abiertos se quedaría con una entrada fantasma: al
   refrescar volvería a abrirse el formulario de un cliente que el usuario ya había guardado.
2. **Se anota a quién hay que devolverle el foco** (`focoPendiente`) y, cuando `pintarFilas()`
   termina de repintar, `devolverFoco()` lo lleva al botón de editar de esa misma fila. Sin
   eso, el foco se va al `<body>` y quien navega con teclado vuelve al principio de la página
   después de cada guardado. Si el cliente ya no está en la página (se ha ido a otra, o ya no
   cumple el filtro), no se fuerza el foco a ningún sitio raro.
3. **Se anuncia "Cliente guardado"** en la franja que hay entre los filtros y la tabla. Está
   **fuera** de la tabla a propósito: dentro del panel, el refresco del punto siguiente la
   borraría en el mismo instante en que se escribe. Guardar cambia la pantalla sin cambiar de
   contexto y hay que poder enterarse sin verla (criterio **4.1.3**). El mensaje se borra solo a
   los cinco segundos, porque un "Cliente guardado" fijo acaba pareciendo el resultado de la
   última acción aunque sea de hace diez minutos.
4. **Se dispara `clientes:cambiaron`**, el mismo evento que usan los compañeros. La tabla se
   recarga sola con `cargarClientes(paginaActual)` y así enseña lo que hay en la BD, incluido
   lo que haya cambiado otro usuario mientras tanto.

**El `404` de "alguien lo ha borrado mientras lo editabas"** va a esa franja y **no** a la
alerta del formulario: ese caso dispara un refresco y el mensaje del panel duraría lo que tarda
en llegar la respuesta del listado. Los demás errores del guardado (`400`, `500`, red) sí se
quedan en la alerta del formulario, que es donde se va a corregir el problema.

#### Dos sitios para los avisos: uno que se ve y otro que se lee

Hay **dos elementos** para contar lo que pasa, y no es una duplicación:

| Elemento | Qué es | Para qué |
|---|---|---|
| `#anuncios` | `role="status"` y `visually-hidden` | Lo único que anuncia. Siempre en el documento y vacío |
| `#aviso-clientes` | franja verde/roja, sin `role` | Lo que se ve, cuando no hay otro sitio donde verlo |

Son dos por dos motivos concretos:

- **Una región que aparece con el mensaje ya dentro no se anuncia.** Lo que vigila el lector de
  pantalla es el *cambio de contenido* de algo que ya estaba en el árbol de accesibilidad, así
  que crear el elemento (o quitarle el `hidden`) con el texto puesto no dispara nada. Por eso
  `#anuncios` nace vacío y solo se le cambia el texto, y por eso la franja y la alerta del
  formulario se esconden con `:empty` en el CSS en lugar de con `hidden`.
- **Hay mensajes que ya se ven en su sitio** —el de la tabla cuando falla la carga, el del panel
  cuando no se puede abrir el detalle— y escribirlos además en la franja los pondría dos veces
  en pantalla. Al ser `#anuncios` invisible, puede repetirlos sin que se note.

Con eso, `anunciar(texto, { visible })` escribe siempre en la región invisible y, solo si se le
pide, también en la franja. El **"cargando" del panel no se anuncia** a propósito: es un mensaje
de paso que se sustituye en cuanto llega la respuesta, y anunciarlo dejaría al lector leyendo
algo que ya no está. Que el panel se ha abierto lo dice el `aria-expanded` del botón pulsado.

**Y el `409` del NIF repetido no se marca solo en rojo.** El campo recibe `aria-invalid="true"`
y un `aria-describedby` que apunta al mensaje —que lleva el id del cliente dentro, porque puede
haber varios formularios abiertos y dos elementos no pueden compartir id—. El rojo de Bootstrap
es solo color: sin esos dos atributos, quien no ve la pantalla se encuentra el foco en un campo
que aparentemente no tiene nada. Al volver a teclear en el campo se limpian los tres.

#### Coordinación con `listarPagina`: la página que ya no existe

`listarPagina` **no reencamina** cuando le piden una página fuera de rango: devuelve esa página
vacía con el total real. Es lo correcto para una API —el paginado no puede saber si eso es un
error de quien llama o una tabla que ha encogido—, pero deja un caso feo en pantalla: si se
está en la página 4, se borran clientes hasta que solo quedan tres páginas y se refresca, la
tabla sale vacía diciendo *"Página 4 de 3"* y sin ninguna salida obvia.

La corrección va en el frontend, en `cargarClientes()`: si la respuesta viene vacía **pero hay
páginas**, se pide la última que sí existe. Es una sola llamada más y solo en ese caso. El
contador de peticiones en vuelo aguanta bien la recursión (la de dentro suma y resta antes que
la de fuera, así que el "cargando" no se apaga a medias), y el canal de `AbortController` del
listado tampoco se pisa: la petición anterior ya ha terminado cuando arranca la segunda.

#### `actualizar` también devuelve `Optional`

Igual que `obtenerPorId`, y por lo mismo: "ese cliente no existe" es un resultado normal que
acaba en un `404`, no un error, y con un `null` el controller puede olvidarse de comprobarlo.
Que los dos métodos del service que buscan un cliente concreto respondan igual quita una
excepción que recordar.

Relacionado, el `update` del repositorio quedó **documentado con su trampa**: devuelve si el
`UPDATE` modificó alguna fila, y MySQL cuenta 0 cuando la sentencia no cambia ningún valor. Un
`false` significa "no se ha modificado nada", que puede ser tanto que el cliente no exista como
que se haya guardado igual que estaba, y **no sirve para distinguirlos**. De ahí el `findById`
previo en la misma transacción.

#### Descartar cambios: un diálogo de Bootstrap, no `confirm()`

Cerrar un formulario con algo escrito pregunta antes. Empezó siendo el `confirm()` del
navegador, que funciona y es accesible de serie, pero tiene tres pegas: **bloquea la página**
entera mientras está abierto, no se puede dar estilo (en medio de una pantalla de Bootstrap
canta) y algunos navegadores ofrecen **silenciarlo** tras el segundo o tercero; silenciado
devuelve `false`, o sea "no descartes", y la fila no se cerraría nunca sin que el usuario
entienda por qué. Ahora es el mismo tipo de diálogo que ya usa "Añadir Cliente".

Dos detalles que no son obvios:

- **La promesa se resuelve en `hidden.bs.modal`**, cuando el diálogo ya ha terminado de
  cerrarse, y no al pulsar el botón. Al cerrarse hay que devolver el foco a donde estaba, y si
  se respondiera antes, quien preguntó colocaría el foco (en el lápiz de la fila) y esa vuelta
  se lo llevaría de allí.
- **Ese retorno del foco lo hacemos nosotros.** Bootstrap solo lo hace con los diálogos que se
  abren mediante `data-bs-toggle`, y este se abre desde el código: sin guardarnos el elemento
  activo, quien contesta "Seguir editando" acaba con el foco en el `<body>`, al principio de la
  página, justo después de decir que quería seguir donde estaba.
- **Después de preguntar se comprueba que la fila siga en el documento.** Preguntar lleva su
  tiempo, y en ese rato la tabla ha podido repintarse: la fila de antes ya no está en pantalla y
  cerrarla no se vería. Con `confirm()` esto no pasaba, porque bloqueaba también los
  temporizadores.

### El alta de clientes

Rama `feature/añadirclientes_Angel`. "Añadir Cliente" abre **arriba del todo de la tabla** el
mismo formulario de la edición, en blanco, y al guardar manda un `POST /cliente`.

**Sustituye al modal, que se ha borrado.** No funcionaba: su botón llamaba a una
`guardarCliente()` que no existía en ningún sitio y solo dejaba un `ReferenceError` en la
consola, y además el formulario recogía cuatro de los ocho campos que `ClienteRequest` exige
—le faltaban dirección, código postal, población y provincia—, así que el `POST` habría
devuelto `400` aunque hubiera llegado a enviarse.

**No lleva fila de cliente encima**, a diferencia del detalle y de la edición: un cliente que
todavía no existe no tiene nombre ni CIF que enseñar, y los tres botones de acción no tendrían
a qué apuntar. Solo se inserta el panel.

#### Por qué el alta NO pasa por `abrirDespliegue`

Ese camino gira entero alrededor de una `tr.fila-cliente` con su `data-cliente-id`: `filaViva`,
`panelDe`, `modoDe` y `marcarFila` la buscan. El alta no tiene ni fila ni id, así que son cuatro
funciones cortas propias —`abrirAlta`, `cerrarAlta`, `reabrirAlta` y `guardarCliente`— en vez de
un puñado de condiciones repartidas por el despliegue preguntando *"¿y si es el alta?"*.

Por lo mismo, el estado va en una variable suelta (`altaAbierta`) y **no** en
`filasDesplegadas`, que es un `Map` indexado por id de cliente. Meter un `0` centinela parecía
más corto y era peor: se colaría en esas cuatro funciones, que lo darían por un cliente de
verdad y fallarían en silencio.

#### El formulario se comparte con la edición

`pintarPanelEdicion` hacía ya el 90 % de lo que necesita el alta, así que se partió en dos:
`pintarFormularioCliente` clona la plantilla, sustituye los sufijos `PLANTILLA` de los `id` y
rellena los campos, y encima quedan `pintarPanelEdicion` y `pintarPanelAlta`, que solo se
diferencian en el sufijo, el cartel y con qué valores arrancan. El sufijo del alta es `nuevo`,
que no puede chocar con ningún id de cliente porque esos son números.

Los dos formularios comparten la clase `.formulario-edicion`, así que los dos *listeners*
delegados del `<tbody>` recogen a los dos; los distingue `.formulario-alta`, que solo lleva el
del alta. Y como el alta no tiene `dataset.clienteId`, `formularioVivo()` nunca la encuentra,
que es justo lo que se quiere.

#### "Lo he creado, ¿dónde está?"

Es la pregunta incómoda del alta, y tiene respuesta porque se comprobó en el código:
`fecha_alta` es un `date` **sin hora**, y el orden por defecto es
`ORDER BY fecha_alta DESC, idcliente DESC`. Todos los clientes creados hoy empatan en la fecha
y **desempata el `idcliente` más alto, que es el recién creado**: sale el primero. Lo que era
la fila 10 pasa a la página 2 y el total sube uno, de lo que ya se encarga la paginación.

Pero hay cuatro situaciones en las que lo creas y no lo ves:

| Situación | Dónde acaba el cliente nuevo |
|---|---|
| Estabas en la página 2 o posterior | En la 1 |
| Orden "Más antiguos" (`fecha_alta ASC`) | El desempate también invierte: al final de la última página |
| Orden por nombre | Donde le toque alfabéticamente, en cualquier página |
| Un filtro de provincia o población que no cumple | En ninguna página de la vista actual |

Por eso, tras el `201` se pide la **página 0** conservando los criterios y se mira si el cliente
ha salido de verdad. Si está, el foco va a su fila; si no, se dice **por qué** en vez de dejar
al usuario buscándolo.

> Se localiza por el **NIF/CIF** y no por el id porque `enviarJson` solo devuelve el código de
> la respuesta, y ensancharlo para que a veces devuelva también el cuerpo le dejaría dos formas
> de retorno según un parámetro. El NIF vale igual: es único —por eso el backend contesta `409`
> cuando se repite— y es un dato que el usuario acaba de escribir.

#### Detalles que costaron

- **`focus()` sobre un elemento que no está en el documento no hace nada.** En la edición no se
  plantea, porque allí el panel se inserta antes de pintarlo; aquí la fila se construía entera y
  se insertaba después, así que el cursor nunca llegaba al campo Nombre. Por eso
  `insertarFilaAlta` inserta ella misma: el orden *pintar → insertar → enfocar* tiene que estar
  garantizado en un solo sitio.
- **Pulsar "Añadir Cliente" dos veces no puede abrir dos formularios.** El segundo se llevaría
  el mismo sufijo de `id` que el primero y los `<label for>` apuntarían al que no es. Si ya está
  abierta, el botón solo lleva el foco al primer campo.
- **El alta sobrevive a los repintados con lo tecleado**, igual que los paneles abiertos: el
  borrador se guarda en `guardarBorradores()` y la fila se vuelve a insertar arriba, sin animar
  y sin robar el foco, porque el repintado suele venir de que el usuario está escribiendo en el
  buscador.
- **El `404` de `mostrarErrorGuardado` no aplica al alta**, y por eso `contarErrorAlta` no lo
  contempla: no hay ningún cliente que alguien haya podido borrar mientras tanto. Los códigos
  del alta son `201`, `400`, `409` y el resto.

### La pantalla de carga (viene de `master`)

No es de estas ramas —entró con la PR #19 de Sergio y llegó por el `merge`—, pero conviene saber
por qué al integrarla quedaron **dos contadores de peticiones en vuelo** y no hay que unificarlos:

- `listadosEnVuelo` marca el `aria-busy` de la tabla, así que cuenta **solo** los listados: es
  lo único que de verdad la deja a medias.
- `peticionesActivas` tapa la pantalla entera, así que cuenta **todas** las llamadas a
  `pedirJson`, incluidas las de los desplegables y las de los paneles.

Con uno solo, rellenar un desplegable dejaría la tabla anunciada como ocupada sin estarlo. Son
contadores y no booleanos por el mismo motivo en los dos casos: una petición cancelada termina
**después** de que arranque la que la sustituye, así que con un booleano la que muere apagaría
el indicador de la que sigue viva.

### El buscador se solapaba con el filtro de al lado

Estando cerrado, el círculo de la lupa pisaba el desplegable de provincia. No era el icono: era
el `<input>`. Con `box-sizing: border-box`, **cuando el relleno supera al ancho, el ancho se
ignora**: los `3.2rem` del hueco de la lupa más `1rem` a la derecha más los bordes suman unos
69 px, que no caben en los 50 px del círculo, así que el campo se dibujaba 19 px más ancho que
su contenedor y se metía por debajo del control siguiente (el `gap` de la barra es de 8 px).

El arreglo es que **el ancho cerrado supere al relleno**. Al pasar el círculo a una píldora de
`9rem` (ver abajo) ya caben de sobra los `3.2rem` de la lupa más el `1rem` de la derecha, así
que el problema desaparece por construcción y el relleno puede ser el mismo abierto y cerrado.
La posición de la lupa se calcula desde la misma variable (`--hueco-lupa`) que usa el relleno,
para que no puedan volver a descuadrarse por separado.

**Y abierto tampoco puede partir la barra en dos líneas.** Sumando el buscador abierto
(`20rem`), los tres desplegables (210 + 210 + 290 px), el botón de limpiar y los huecos, no se
cabe en el ancho útil de un escritorio de 1140 px: al pulsar la lupa, "Limpiar" se caía a la
línea de abajo y la barra daba un salto. Dentro de la barra el tamaño lo decide el *flex*, así
que el buscador se dimensiona con **`flex-basis`** (y la transición va sobre él, o abrirse
sería un salto en vez de un crecimiento) y se le deja **encoger** (`flex-shrink: 1`) con un
suelo de `min-width: var(--ancho-cerrado)`: cuando falta sitio cede él, que es el único con
espacio de sobra, pero nunca queda más estrecho que cerrado. Con menos de 768 px la barra pasa
a un control por línea y el `flex-basis` vuelve al `100%`.

> **El ancho se decide en un solo bloque.** Al añadir lo del *flex* quedaron dos reglas
> hablando del mismo tamaño: `.buscador-clientes` con `flex: 0 0 auto`, `width` y una transición
> sobre `width`, y `.barra-filtros .buscador-clientes` con `flex: 0 1 basis`. La segunda es más
> específica, así que ganaba entera y la primera era código muerto —con un comentario que
> además defendía justo lo contrario de lo que acababa pasando—. Ahora el bloque general solo
> lleva la posición y el alto, y todo lo que tenga que ver con el ancho vive en el de la barra.

### La lupa no parecía pulsable (y no se abría con el teclado)

Dos problemas distintos en el mismo control.

**El de teclado era un fallo, no una mejora**: el buscador solo se abría con un listener de
`click` en todo el documento. Quien llegaba con el tabulador se quedaba dentro de una píldora
sin ver el campo ni lo que escribía (criterio **2.1.1** de WCAG, todo lo que funciona con ratón
tiene que funcionar con teclado). Ahora se abre con **`focusin`** y se recoge con **`focusout`**
si está vacío: el mismo código cubre ratón y teclado, porque un clic acaba enfocando el input
igual, y ya no hace falta vigilar cada clic de la página para saber cuándo cerrarlo.

**El de descubribilidad**: un icono suelto no comunica que se pueda pulsar, y con el campo
plegado tampoco hay placeholder que lo insinúe. Cerrado ya no es un círculo con la lupa, sino
una **píldora con la lupa y la palabra "Buscar"**. El texto es la única señal que nadie tiene
que adivinar; al enfocarla se estira y aparece el placeholder completo. La palabra es
`aria-hidden`, porque el `<input>` ya tiene su `aria-label` y si no se leería dos veces.

### Accesibilidad y semántica de la tabla y la barra de filtros

Lo que se ha corregido, y por qué cada cosa:

- **El nombre del cliente es `<th scope="row">`**, no un `<td>`. Es la cabecera de su fila: con
  eso un lector de pantalla dice *"García S.L., email: ..."* al recorrer las celdas, en vez de
  leer valores sueltos sin saber de quién son.
- **La tabla tiene `<caption>`** (oculto visualmente) y el contenedor con scroll horizontal es
  un `role="region"` con `tabindex="0"`: una zona que se desplaza tiene que poder recorrerse
  con el teclado, o las columnas de la derecha son inalcanzables sin ratón. Ese `tabindex`
  viene puesto en el HTML y lo **quita `clientes.js` cuando la tabla cabe entera**, vigilando
  el tamaño con un `ResizeObserver`: una parada del tabulador donde no hay nada que desplazar
  es una molestia para quien navega así. Se hace en ese sentido —puesto de serie y quitado
  después— para que un fallo del JS deje la versión accesible y no la contraria.
- **Los botones de acción llevan el nombre del cliente** en su `aria-label`. Sin eso, pedir la
  lista de botones de la página devuelve diez veces *"Editar cliente"* sin saber a cuál
  pertenece cada uno. El texto visible no cambia: son botones de icono y ese nombre solo existe
  para la tecnología asistiva.
- **El nombre y el aviso emergente de cada botón se escriben en el mismo sitio**
  (`marcarFila()` → `nombrarAccion()`) y el nombre **empieza por el texto del aviso**. Son dos
  textos que tienen que decir lo mismo —quien maneja el ordenador por voz dicta lo que ve
  (criterio **2.5.3**, *label in name*)— y repartidos en dos funciones se acaban
  desincronizando en cuanto cambia uno.
- **El estado abierto/cerrado lo dice `aria-expanded`**, no el nombre del botón: el nombre se
  mantiene estable ("Ver detalles de X") y es el atributo el que cambia. Es el patrón estándar
  de *disclosure*.
- **La barra de filtros es un `<search>`** (con `role="search"` explícito para los navegadores
  anteriores a 2023), el título de la página es un `<h1>` —antes empezaba en `<h2>`— y el enlace
  activo del menú lleva `aria-current="page"`, que es lo que hace que se anuncie como la página
  actual; la clase `active` solo pinta.
- **La etiqueta del botón de orden es `aria-live="polite"`**: cambia sola al pulsarlo ("Más
  recientes" → "A → Z") y sin eso quien no ve la pantalla pulsa y no se entera del resultado.
- **Los iconos decorativos llevan `aria-hidden="true"`** y el botón de eliminar ha dejado el
  `title` nativo (que además duplicaba su `aria-label`) para usar el mismo aviso emergente que
  los demás.
- **El nombre accesible del botón "Limpiar" empieza por "Limpiar"**, aunque siga con la cuenta
  de filtros. Si se sustituyera por otra frase, quien maneja el ordenador por voz diría *"pulsa
  Limpiar"* y no pasaría nada (criterio **2.5.3**, *label in name*).

### Por qué los paneles NO son tablas

Es la decisión de semántica más importante de esta pantalla, y la que menos se ve mirándola.

Los dos paneles empezaron siendo una `<table>` con sus `<th>` de cabecera. Encajaba
visualmente —rótulos arriba, valores debajo— pero traía tres problemas, y el tercero era un
incumplimiento de verdad.

**1. Una tabla dentro de otra tabla.** El panel vive dentro de un `<td>` de la tabla de
clientes. Con una `<table>` ahí, un lector de pantalla anuncia *"tabla de 5 columnas"* dentro
de *"tabla de 6 columnas"*: quien navega por celdas con los atajos de tabla entra y sale de
dos rejillas distintas sin saber en cuál está. Con tres paneles abiertos, cuatro tablas.

**2. En edición, la tabla no era una tabla.** Una `<table>` describe datos con relación de
filas y columnas. Ahí había **ocho campos de un formulario** en una única fila de datos: era
maquetación disfrazada de tabla, que es el uso que las tablas dejaron de tener hace veinte
años.

**3. Dos campos incumplían el criterio 2.5.3** (*label in name*). Como el `<th>` solo se
asocia al campo *a través* de la tabla, cada `<input>` llevaba además un `aria-label`, y en
dos casos ese nombre **no contenía el texto visible**:

| Rótulo visible | `aria-label` que había | Consecuencia |
|---|---|---|
| `NIF/CIF` | `NIF o CIF` | decir "NIF/CIF" por voz no hacía nada |
| `Email` | `Correo electrónico` | decir "Email" por voz no hacía nada |

Es el mismo criterio que ya se había arreglado en el botón "Limpiar", así que era además una
incoherencia con el propio estándar de esta pantalla.

**Qué hay ahora:**

- El **detalle** es una **lista de descripción** (`<dl>` → `<div>` → `<dt>`+`<dd>`). Son los
  datos de UN cliente, pares rótulo/valor, que es exactamente para lo que existe `<dl>`. El
  lector dice *"Dirección: Calle Mayor 1"* y se acabó. El `<div>` intermedio lo permite el
  estándar y es lo que deja emparejar cada par en la rejilla de CSS.
- La **edición** es un formulario de verdad: `<label for>` + `<input id>` dentro de
  `<fieldset>` con `<legend>` oculto (que ocupa el sitio que tenían los `<caption>`). El
  rótulo pasa a estar asociado al campo **directamente**, que es lo que pide el criterio
  **1.3.1**, y el `aria-label` desaparece: **lo visible ES el nombre accesible**, así que los
  dos ya no pueden divergir nunca más.
- Los dos se colocan con la **misma rejilla CSS**, así que el aspecto es el de antes y pasar
  de un modo al otro no mueve nada de sitio.

> **¿No estropea esto el sistema de `<template>`?** No, y la duda es razonable: dentro de una
> `<table>` el analizador del navegador solo admite `<tr>`, `<td>`… y se come cualquier otra
> cosa. Pero de los cuatro templates de la página, los dos que han cambiado
> (`panel-detalle-template` y `panel-edicion-template`) **no empiezan por `<tr>`**: empiezan
> por `<div>` y por `<form>`, así que su contenido se analiza como el de cualquier otra parte
> del documento y un `<dl>` o un `<fieldset>` son perfectamente válidos ahí. Los dos que sí
> empiezan por `<tr>` (la fila del cliente y la del despliegue) no se han tocado.
>
> Y en eficiencia se gana un poco: el panel de detalle pasa de unos 24 nodos a unos 16, porque
> una tabla necesita `thead`, `tbody` y un `tr` por nivel que la lista no necesita. El clonado
> es el mismo `cloneNode(true)` de siempre.

**Los `id` de los campos se generan al pintar.** Un `<label for>` necesita que su campo tenga
`id`, y puede haber varios formularios abiertos a la vez: dos elementos no pueden compartirlo.
`pintarPanelEdicion` recorre `CAMPOS_EDITABLES` y le pega el id del cliente a cada uno. En el
`<template>` los `id` llevan el sufijo `PLANTILLA` a propósito, para que canten a la vista en
el inspector si alguno se quedara sin sustituir.

**Cada panel dice de quién es.** La etiqueta de estado pasó de decir "Editando" a decir
*"Editando García S.L."*, y el panel la apunta con `aria-labelledby`. Con varios paneles
abiertos, y con la fila del cliente ya por encima, antes no había forma de saber de quién eran
los datos que se estaban leyendo. El nombre sale del texto **que ya se ve**, así que no hay
dos cadenas que puedan acabar diciendo cosas distintas.

### Contraste: los grises se han medido, no elegido a ojo

Un gris "que se ve bien" en un monitor bueno puede estar por debajo de lo que exige el criterio
**1.4.3** de WCAG (4.5:1 para texto normal, 3:1 para texto grande o para un elemento gráfico
que transmite información). Se han medido todos los textos de esta pantalla contra el fondo
sobre el que se pintan de verdad, y **cuatro no llegaban**:

| Texto | Antes | Ahora | Mínimo |
|---|---|---|---|
| Rótulos de los paneles (`dt` y `label`, 12 px en negrita) | `#64748b` → 4.12:1 | `#475569` → **6.56:1** | 4.5:1 |
| Aviso de "Cargando…" del panel | `#64748b` → 4.12:1 | `#475569` → **6.56:1** | 4.5:1 |
| Placeholder del buscador | `#9ca3af` → **2.54:1** | `#6b7280` → **4.83:1** | 4.5:1 |
| Cartel "Editando" | `#b45309` → 4.35:1 | `#92400e` → **6.14:1** | 4.5:1 |

Dos detalles que explican por qué se escaparon:

- **12 px en negrita NO es "texto grande".** El umbral está en 18 pt (24 px), o en 14 pt
  (18,66 px) si va en negrita. Un rótulo pequeño y en negrita parece que debería contar como
  grande y no cuenta, así que se le aplica el 4.5:1 completo.
- **Al placeholder le aplica el criterio igual que a cualquier otro texto.** Es fácil pensar que
  por ser una pista pasajera no cuenta. El gris nuevo es además el mismo que el de la palabra
  "Buscar" que hay al lado, así que los dos textos del mismo control se ven ya iguales.

### Estilo: lo que se ve y por qué

- **Rayado alterno azul/blanco, pintado por cliente y no por fila del DOM.** El
  `table-striped` de Bootstrap alterna con `nth-of-type`, así que **cada panel de detalle
  insertado invierte el rayado de todo lo que viene detrás** y la tabla acaba con dos filas
  blancas seguidas. Como las filas las pinta `pintarFilas()`, la paridad la marca él con la
  clase `fila-par` y los paneles ya no cuentan.

  > **Los tres tonos de fila son una escala, y viven juntos en `:root` por eso.** Al principio
  > estaban repartidos por la hoja, y pasó lo previsible: se bajó el rayado por su cuenta *"para
  > que el resaltado de la fila abierta se siguiera distinguiendo"*, con buena intención pero
  > apretando los tres en un margen de luminancia del 15 %. El resultado era que **ni el rayado
  > ni el resaltado llegaban a verse**: abrir una fila **par** apenas le cambiaba el fondo
  > (1,09:1 respecto al rayado) y todo el peso lo llevaba el borde de color.
  >
  > | Paso | Antes | Ahora |
  > |---|---|---|
  > | fila normal → rayada | 1,055:1 | **1,111:1** |
  > | rayada → abierta | 1,095:1 | **1,127:1** |
  > | abierta → con el ratón encima | 1,077:1 | **1,122:1** |
  >
  > No era un incumplimiento —el contraste entre fondos alternos no lo regula WCAG, y el texto
  > se queda entre 10,5:1 y 14,7:1 en los cuatro—, era que los avisos de fondo no hacían su
  > trabajo. De referencia, el rayado que trae Bootstrap de serie ronda 1,12, así que esto no
  > es más marcado de lo normal. Si hay que retocarlos, **se mueven los tres**.
- **Recuadro de color en el bloque abierto**: **morado** si se está viendo, **ámbar** si se
  está editando y **verde** si es un alta. Envuelve el bloque entero —la fila del cliente
  **más** las filas del panel, que en edición son dos de rótulos y dos de campos— de modo que
  los dos `<tr>` se leen como una sola tarjeta. La fila pone el borde de arriba y los
  laterales, el panel los laterales y el de abajo, y el color viaja en una variable
  (`--color-modo`) declarada en las dos filas. Y como el color por sí solo no vale (criterio
  **1.4.1**), el panel lo dice además con letras: "Viendo detalles de García S.L." /
  "Editando García S.L." / "Nuevo cliente".

  > **El alta cierra su recuadro por arriba ella sola.** En los otros dos modos ese lado lo
  > pone la fila del cliente, pero el alta no tiene fila encima —el cliente todavía no
  > existe— y sin una regla propia el recuadro se quedaba abierto, como una U.

  > **Son DOS juegos de color y no uno, y ese es el truco.** El borde y el texto no piden lo
  > mismo: al borde le basta 3:1 (criterio **1.4.11**) y el texto del cartel necesita 4.5:1
  > (criterio **1.4.3**). Mientras los dos papeles compartieron color mandaba el más exigente,
  > y por eso los bordes salían apagados y oscuros. Separándolos, el borde se va al tono vivo y
  > el cartel conserva el contraste:
  >
  > | Modo | Borde (`--borde-*`) | Texto del cartel (`--estado-*`) | Contraste del texto |
  > |---|---|---|---|
  > | Detalle | `#a855f7` | `#6b21a8` | 6,91:1 |
  > | Edición | `#fb923c` | `#92400e` | 5,66:1 |
  > | Alta | `#22c55e` | `#157347` | 4,69:1 |
  >
  > Medidos sobre `--fila-abierta` (`#dbe6ff`), que es el fondo del panel abierto. De
  > referencia de lo justo que va esto: el ámbar que había antes de todo (`#b45309`) se
  > quedaba en **4,35:1**, o sea que valía como borde y **no** como texto, que es el tipo de
  > fallo que no se ve mirando la pantalla. Y el `#ffc107` de Bootstrap ni siquiera valía como
  > borde: 1,6:1 sobre blanco.
  >
  > **Si algún día se vuelven a fundir en una sola variable, el que se rompe es el texto.**
  >
  > El brillo "neón" no es el color, es un halo (`box-shadow: 0 0 14px -4px`) del tono del
  > borde sobre el `<td>` del panel. Va ahí y no en las celdas de la fila: aquellas llevan un
  > `box-shadow: none` que no es decorativo, mata el resaltado de `table-hover` de Bootstrap,
  > que es un `inset` gigante, y el halo lo reviviría partiendo el bloque en dos tonos.
  >
  > Y sobre daltonismo: los tres siguen distinguiéndose entre sí en deuteranopia. Aunque no lo
  > hicieran, el criterio 1.4.1 está cubierto por el cartel con letras y por los iconos
  > distintos (ojo, lápiz y `+`).

  > **El borde del panel va condicionado al modo, no puesto siempre**, y esto costó un fallo
  > entender por qué. Al cerrar, `marcarFila()` quita las clases `modo-detalle` y
  > `modo-edicion`, pero el panel se queda **250 ms en el documento plegándose**. Durante esos
  > 250 ms la variable `--color-modo` ya no existe, el `var()` no resuelve, la propiedad se
  > vuelve inválida y `border-color` cae a su valor inicial, que es `currentColor`: se veía una
  > **raya oscura parpadear en cada cierre**. Con el selector condicionado al modo, el borde
  > desaparece junto con las clases en vez de quedarse con un color heredado.

- **Un lápiz en cada campo editable**, y solo ahí. Es la señal de que ese dato se puede
  escribir, y va dentro del `<label>` con `aria-hidden="true"`, porque es decorativo: que el
  campo sea editable ya lo dice el propio `<input>`. La diferencia con el panel de detalle está
  garantizada **por el marcado** —el detalle es una `<dl>` y no tiene ningún `<label>`— y no
  por una clase que alguien pueda olvidarse de quitar al cambiar de modo.

  > **El color del lápiz sigue al formulario en el que está**, ámbar al editar y verde al dar
  > de alta, porque los dos paneles clonan la **misma** plantilla y si no saldrían lápices
  > ámbar dentro del recuadro verde del alta. Se distingue por la clase del `<form>` y no por
  > el modo de la fila: las clases de modo se quitan al cerrar y el panel se queda 250 ms
  > plegándose, así que el icono cambiaría de color a media animación. Y va en el tono
  > **oscuro**, no en el del borde: es un glifo de 0,7 em y en el tono vivo se lee mucho peor
  > a ese tamaño.
- **Esquinas redondeadas**: van en el contenedor, no en la `<table>`. Con
  `border-collapse: collapse` el radio de la tabla no recorta el fondo de las celdas y la
  cabecera azul seguiría saliendo en pico; el `.table-responsive` ya tiene `overflow-x: auto`,
  así que recorta sin tocar el scroll.
- **Iconos de los filtros en azul de marca** sobre un fondo apenas teñido, y **el control se
  marca cuando tiene filtro puesto** (icono en sólido, borde azul, texto en negrita), con un
  contador junto a "Limpiar". Es el único sitio de la barra donde el color aporta información
  en vez de decorar: con tres controles idénticos estén o no usados, la pregunta "¿por qué solo
  salen 4 clientes?" obligaba a abrir los desplegables uno a uno. Cambian dos iconos: `fa-map`
  para provincia (`fa-map-location-dot` es una mancha a 14 px) y `fa-filter-circle-xmark` para
  Limpiar, que dice "quitar filtros" y no "borrar".
- **Todos los controles de la barra miden lo mismo** (`--alto-control`): el buscador medía 50 px
  y los desplegables `input-group-sm` 31 px, y la fila se veía escalonada.

### Otros detalles de uso

- **Email y teléfono son enlaces** `mailto:` y `tel:`. El esquema lo construye siempre el
  código y nunca el dato del servidor, así que un valor manipulado no puede colar un `href`
  de tipo `javascript:`. Pulsar el enlace **no** despliega además la fila, y su aviso emergente
  dice lo que hace ("Escribir a este correo") en vez de heredar el "Ver detalles" de la celda.
- **En móvil se ocultan email y teléfono** (`d-none d-md-table-cell`): seis columnas obligan a
  desplazar en horizontal y esos dos datos siguen estando a un clic, en el detalle.
- **Mientras carga una página, la tabla se atenúa** y lleva `aria-busy="true"`; si no, se ven
  los datos de la página anterior como si fueran los nuevos. El "cargando" se apaga con un
  **contador** de peticiones en vuelo, no con un booleano: una petición cancelada termina
  *después* de que arranque la que la sustituye y apagaría el estado de la que sigue viva.
- **Si la tabla sale vacía por un filtro, el botón de quitarlo está ahí mismo**, en el propio
  mensaje: la salida tiene que estar donde se ve el problema.
- En el formulario de edición, `autocomplete="off"` (son datos de otra persona, no de quien
  rellena) e `inputmode` numérico/telefónico para que en móvil salga el teclado correcto.
- **Los asteriscos de campo obligatorio son `aria-hidden`**: se leerían como "asterisco", que no
  dice nada, y los campos ya llevan `required`, que el lector anuncia como "obligatorio".
- **Ningún número de columnas escrito a mano.** El `colspan` de la fila desplegada y el de la
  fila de mensajes salen de contar los `<th>` de la tabla, y el texto por defecto del error del
  NIF se lee del propio `<template>` al arrancar. Son los dos sitios donde una copia a mano se
  queda vieja sin que nadie se entere. Y se cuentan **las cabeceras visibles**, no todas: en
  móvil hay dos ocultas con `d-none`, y un `colspan` de 6 en una tabla de 4 columnas inventa dos
  columnas más, con lo que el mensaje de "no hay clientes" se centra respecto a un ancho mayor
  que el de la tabla y se sale por la derecha. El `ResizeObserver` que ya vigila el tamaño
  reajusta ese `colspan` al estrechar la ventana con un panel abierto.
- **La fecha se formatea en UTC.** El backend manda un día suelto, sin hora, y el navegador lo
  interpreta como medianoche **UTC**; al pasarlo a la hora local de un huso negativo esa
  medianoche cae en el día anterior y el alta se vería con un día menos. Se formatea con
  `timeZone: "UTC"`, que es el huso en el que está expresado el dato.
- **El buscador acota a 60 caracteres** (`maxlength`), que es el `@Size` de `CriteriosCliente` y,
  a su vez, lo que mide la columna `nombre`: pasarse solo sirve para gastar una petición que el
  servidor va a rechazar con un `400`. Mismo criterio que en el formulario de edición. Y
  **`Enter` busca sin esperar** los 300 ms del *debounce*: pulsar `Enter` es decir "ya he
  terminado de escribir", y que no pasara nada durante un tercio de segundo se sentía como que
  la tecla no hacía nada.
- **Las tres etiquetas de CDN llevan `integrity`** (Bootstrap CSS y JS, Font Awesome): el
  navegador comprueba que lo que descarga es exactamente el fichero esperado y, si no, no lo
  usa. Las huellas se calcularon descargando esas mismas URL, no copiándolas de ningún sitio.
  **Google Fonts se queda fuera** a propósito: devuelve un CSS distinto según el navegador, así
  que ninguna huella fija valdría para todos y la fuente dejaría de cargar. Solo está puesto en
  `clientes.html`, que es nuestra página; las demás siguen igual.
- **Se ha quitado el `querySelectorAll('.btn-eliminar')` del final de `clientes.js`**: no
  enganchaba nada, porque al ejecutarse ese código los botones todavía viven dentro del
  `<template>`. En su sitio queda un comentario que explica que las acciones de la fila van por
  delegación en el `<tbody>`, que es donde quien implemente el borrado tiene que añadir su caso.

## Logs y manejo de errores

Usamos **SLF4J** (`private static final Logger log = LoggerFactory.getLogger(...)`), que escribe
al log configurado en `logback-spring.xml` y permite **niveles**. Ponemos **un log por capa**, con
la granularidad adecuada:

- **Repositorio** (`findUltimos`): `log.debug("findUltimos({}) -> {} filas", ...)` — nivel `DEBUG`
  porque es detalle técnico de la consulta.
- **Servicio** (`listarUltimos`): `log.info("listarUltimos({}) -> {} clientes", ...)` — el hecho
  de negocio: qué se ha encontrado, qué se ha guardado, qué no existía.
- **Controller** (`listarUltimos`): `log.info(...)` al recibir la petición (con sus parámetros) y
  al responder, y **lo que registra al responder es el código HTTP** (`GET /cliente/7 -> 200`).
- **RowMapper**: **sin log a propósito** — se ejecuta una vez por CADA fila y llenaría la consola.

**Cada capa cuenta lo suyo, y no lo mismo.** El controller y el service escribían dos líneas
seguidas diciendo lo mismo con otras palabras (*"cliente actualizado"* dos veces), que es ruido
que además engaña: al leer el log parecen dos cosas distintas. Ahora el service dice **qué ha
pasado** y el controller **con qué se responde**, que es una información que el service no
tiene. Y el nivel va por quién tiene el problema: `warn` para lo que arregla quien llama (un
`400` por datos mal formados, un `404`, el `409` del NIF repetido, **sin traza**: no hay nada
roto que ir a mirar) y `error`, con la excepción entera, solo para los fallos del servidor.

Los `log.debug` del repositorio **no se veían**: `application.properties` solo subía a `DEBUG`
el paquete de Spring que imprime el SQL, así que el nuestro se quedaba en `INFO`. Con
`logging.level.edu.xtd.facturacion360=DEBUG` la capa de log que describe este apartado existe
de verdad.

Fíjate que para poder loguear el valor **primero lo guardamos en una variable y luego lo
devolvemos** (ver ["Decisiones de estilo"](#a-decisiones-de-estilo-inyección-de-dependencias-y-forma-del-return)).

### Qué registra cada endpoint

La regla es **dos líneas por petición en el controller**: una al entrar, con lo que ha pedido
quien llama, y otra al salir, con el código HTTP. Ni una más (el hecho de negocio lo cuenta el
service) ni una menos. Esta es la tabla completa, que sirve para comprobarlo de un vistazo:

| Endpoint | Al entrar | Al responder | Nivel de los errores |
|---|---|---|---|
| `GET /cliente/listar-ultimos` | `?limite=` y el valor acotado | `-> 200 (N clientes)` | `error` (500) |
| `GET /cliente/listar-pagina` | los criterios ya normalizados | `-> 200 (pagina X de Y)` | `warn` (400) · `error` (500) |
| `GET /cliente/provincias` | la ruta | `-> 200 (N provincias)` | `error` (500) |
| `GET /cliente/poblaciones` | `?provincia=` | `-> 200 (N poblaciones)` | `error` (500) |
| `GET /cliente/{id}` | el id | `-> 200` | `warn` (404) · `error` (500) |
| `PUT /cliente/{id}` | el id | `-> 200` | `warn` (400, 404, 409) · `error` (500) |

> **Lo que faltaba.** `listar-pagina`, `provincias` y `poblaciones` registraban su entrada y sus
> errores, pero **no su 200**: eran los tres únicos endpoints cuyo camino bueno no dejaba
> rastro. Y `listar-pagina` es el más usado de toda la pantalla, así que el log solo hablaba de
> él cuando algo iba mal, lo que da una idea muy engañosa de lo que está pasando. Ahora los
> seis siguen la misma regla.

> **Los criterios se limpian de saltos de línea antes de guardarse.** El log del listado vuelca
> el `toString()` del record, y `busqueda` acepta hasta 60 caracteres, saltos de línea
> incluidos. Buscar `garcia\n2026-08-02 INFO Cliente 7 eliminado` partía la traza en dos y
> dejaba la segunda mitad con el aspecto de una línea escrita por la propia aplicación: quien
> leyera el log vería un borrado que nunca ocurrió. Es el ataque conocido como *log forging*
> (CWE-117). Se neutraliza en `CriteriosCliente.normalizar`, que ya es el único sitio donde se
> decide qué es un criterio válido, y para buscar en la base de datos un salto de línea no
> aporta nada.

**Errores**: el controller envuelve la operación en `try/catch (DataAccessException)`. Si la BD
falla, `log.error("...", e)` deja el fallo (con su traza) **en el log**, y al navegador le
respondemos un **`500`** limpio (cuerpo vacío) en vez de soltarle una traza interna.

**Y el `400` del listado también sale limpio.** `listarPagina` valida las longitudes con
`@Valid`, pero sin un `BindingResult` detrás del `@ModelAttribute` esa validación la resuelve
Spring **antes** de entrar en el método: lanza `BindException` y el cuerpo de la respuesta pasa
a ser la página de error por defecto —con la traza dentro mientras `devtools` esté en el
*classpath*—, justo lo contrario de lo que hacen los demás endpoints. Con el `BindingResult`,
el `400` se devuelve desde aquí, con cuerpo vacío, igual que el del `PUT`.

> Queda un `400` que no pasa por nosotros: `/cliente/abc`, cuando el `{id}` no es un número.
> Como el parámetro se declara `int`, Spring tiene que convertirlo **antes** de invocar el
> método; falla, lanza `MethodArgumentTypeMismatchException` y la resuelve el
> `DefaultHandlerExceptionResolver`. Nuestro código no llega a ejecutarse, así que en el log no
> aparece la línea del controller, sino la de ese resolutor.
>
> El **código** es correcto (`400`); lo que no encaja con el resto es el **cuerpo**, que lo
> genera el `/error` por defecto. En desarrollo sale con la traza entera, porque `devtools`
> activa por su cuenta `server.error.include-stacktrace=always` (es la línea *"Devtools property
> defaults active!"* del arranque). Empaquetado sin `devtools`, el valor por defecto es `never`
> y solo salen cuatro campos: `timestamp`, `status`, `error` y `path`. O sea: la parte fea se
> queda en desarrollo, y lo que llegaría a producción es una incoherencia de formato.
>
> **No se arregla aquí** porque ninguna de las formas posibles se queda dentro de lo nuestro:
> le corresponde a la **gestión centralizada de excepciones**, que es una feature aparte del
> proyecto. Lo que sigue es el análisis ya hecho, para que quien la implemente no tenga que
> repetirlo.
>
> El frontend no genera nunca esas URL: los identificadores salen de `dataset.clienteId`, que se
> rellenó con lo que devolvió el backend. Para llegar a este `400` hay que escribir la dirección
> a mano o llamar a la API desde fuera.

> ### 📌 Para la gestión centralizada de excepciones
>
> **Lo que hace falta**: un `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` en el
> `@RestControllerAdvice` que devuelva `400` con **cuerpo vacío**, o con `ProblemDetail` si el
> equipo decide adoptar ese formato para toda la API. Si se opta por `ProblemDetail`, conviene
> cambiar también los `build()` de nuestros endpoints para que no convivan dos formatos de error.
>
> **A qué afecta**: a `/cliente/abc`, a `?limite=abc` y a `?pagina=abc` por igual. Son todas
> conversiones de tipo que fallan **antes** de entrar al método, así que se arreglan de una vez
> o ninguna; parchear una sola dejaría las demás igual y encima parecería resuelto.
>
> **Dos caminos que ya se han descartado, para no perder el tiempo con ellos:**
>
> - Meter el `@ExceptionHandler` dentro de `ClienteController` **no acota nada**: `crear` y
>   `eliminar` también reciben `@PathVariable int id` y cambiarían igual, solo que de forma
>   menos visible que con un advice global.
> - Restringir la ruta con `@GetMapping("/{id:\\d+}")` parece lo más inocente y es lo peor: al
>   no casar el patrón, Spring deja de encontrar el método y responde **`404`**. Decir "no
>   existe" cuando lo que pasa es "eso no es un identificador" informa peor que ahora.
>
> **Lo que ya está probado y no hay que tocar**: `?pagina=abc` **sí** sale con cuerpo vacío,
> porque los criterios se enlazan por el constructor del record y el fallo de conversión acaba
> en el `BindingResult`. Está fijado en `ClienteControllerTest.listadoConPaginaNoNumerica`.

## Tests automáticos

Cuatro clases en `src/test/java`, **ninguna necesita base de datos**, que es la condición para
que se ejecuten siempre y no solo cuando alguien se acuerda de arrancar MySQL. Son **44
pruebas**: 18 del controller, 13 del repositorio, 9 de los criterios y 4 del service.

Al lanzarlas con `./mvnw test` verás **45**, no 44. La que sobra es
`Facturacion360ApplicationTests.contextLoads`, que vino con el esqueleto de Spring Initializr y
no es de esta feature. Se anota la diferencia para que nadie tenga que averiguar por qué los dos
números no cuadran.

| Clase | Qué asegura |
|---|---|
| `CriteriosClienteTest` | La normalización del record: la página negativa, el tamaño acotado, los textos en blanco a `null`, los saltos de línea neutralizados y que el `offset` no desborde. Y que el mensaje de longitud **saque el número de la constante** y no de un literal: es lo único que no se nota por ningún otro sitio, porque el `400` sale con el cuerpo vacío y ese texto solo llega al log |
| `ClienteRepositoryJdbcImplTest` | El SQL que se construye: comodines de `LIKE` escapados, lista blanca del `ORDER BY`, filtros acumulados y el recuento con los mismos filtros que la página. Y el **filtro en cascada**: que el `AND provincia = ?` aparezca al elegir provincia y desaparezca sin ella, que una provincia en blanco cuente como ninguna, y que las provincias salgan sin repetir y sin huecos |
| `ClienteServiceImplTest` | La lógica de `actualizar`: no tocar la BD si el cliente no existe, conservar la fecha de alta, usar el id de la ruta y que guardar sin cambios siga siendo un guardado correcto |
| `ClienteControllerTest` | Los códigos HTTP del detalle, el guardado y el listado: `200`, `400`, `404`, `409` y `500`, y que todos los errores salgan con el cuerpo vacío. También los **dos endpoints de los desplegables**, incluido que al *service* le llegue `null` —y no una cadena vacía— cuando la petición no trae provincia, que es de lo que depende la cascada |

Por qué esas cuatro y no otras:

- **`CriteriosCliente`** es donde se decide qué es un criterio válido para toda la aplicación:
  el controller, el service y el repositorio dan por hecho que lo que reciben viene normalizado
  y no lo vuelven a comprobar. Si eso se rompe, se rompe en silencio y el fallo aparece tres
  capas más abajo, con un `OFFSET` negativo o un filtro por la cadena vacía.
- **El repositorio** guarda dos defensas que no se ven en pantalla y solo se notan cuando
  fallan. Se prueban con un `JdbcTemplate` **simulado**, porque lo que importa es la consulta
  que se entrega, no lo que devolvería MySQL. Los parámetros se recogen desde la propia
  respuesta simulada y no con un `ArgumentCaptor`: el último argumento de `query` es variable y
  un captor ahí solo recoge un valor, mientras que estas consultas llevan hasta seis.

  Y guarda además **el único método cuya consulta cambia de forma según lo que llegue**:
  `findPoblaciones`, el del filtro en cascada. Invertir ese `if` compila, deja el resto de
  pruebas en verde y no produce ningún error en pantalla —el desplegable se sigue llenando—,
  solo que deja de recortarse al elegir provincia. Para verlo hay que abrir la aplicación y
  acordarse de qué poblaciones debería haber, que es justo lo que una revisión de código no
  hace. **Comprobado invirtiéndolo a propósito antes de dar las pruebas por buenas: tres de las
  cuatro se ponen en rojo**, y el código se restauró después. Una prueba que no falla cuando
  rompes lo que vigila no está vigilando nada.
- **El service** es donde vive la única lógica de negocio de verdad del módulo, y sus tres
  decisiones se romperían **en silencio**: conservar la fecha de alta, distinguir "no existe" de
  "guardado sin cambios" e ignorar a propósito el `boolean` del `update`. Hacía falta probarlo
  ahí y no desde el controller, porque el test del controller **sustituye el service entero por
  un doble**: lo que comprobaba era el contrato del doble, no el de la clase. Sin
  `ClienteServiceImplTest`, alguien podía "simplificar" el método usando el booleano del
  repositorio y la pantalla empezaba a responder `404` al guardar sin tocar ningún campo, con
  todas las demás pruebas en verde.
- **El controller** no calcula nada; lo suyo es traducir un `Optional` vacío o una excepción de
  clave duplicada al código que le toca. Justo eso es lo que el frontend da por supuesto para
  decidir si pinta el formulario en rojo, si avisa de que el cliente ya no existe o si refresca
  la tabla: cambiarlo sin querer rompe la pantalla sin romper ninguna compilación. Con
  `@WebMvcTest` se levanta **solo la capa web** y el service se sustituye por un doble
  (`@MockitoBean`), así que casos casi imposibles de provocar a mano —el `409`, o un fallo de
  base de datos para comprobar que el `500` sale sin traza— se piden y ya está.

> **Dos cosas de Spring Boot 4 que despistan al copiar ejemplos de internet.** `@WebMvcTest` ha
> cambiado de paquete (ahora es `org.springframework.boot.webmvc.test.autoconfigure`), y
> `@MockBean` ya no existe: su sustituta es `@MockitoBean`, en
> `org.springframework.test.context.bean.override.mockito`. Los tutoriales todavía usan los
> nombres antiguos y el error que sale es un "no se encuentra el símbolo" que no explica nada.

> Al ejecutar los tests, Mockito avisa de que se está *auto-adjuntando* como agente. Es un aviso
> suyo de cara a futuras versiones de la JDK, no un fallo; se quita configurando el `argLine` de
> surefire, que es tocar el `pom.xml` compartido.

Lo que **no** cubren: nada que hable de verdad con MySQL. Un `@JdbcTest` necesita una BD de
test (H2 con su script, o Testcontainers) y H2 no se comporta igual que MySQL en lo que aquí
importa —la *collation* que ignora mayúsculas y acentos, el `ESCAPE` del `LIKE`—, así que un
test verde ahí no probaría lo que parece. Queda para cuando se decida esa infraestructura.

## Limitaciones conocidas

Seis cosas que se han mirado y se dejan como están, a propósito:

- **El NIF/CIF y el código postal solo se validan por longitud, no por formato.** Un `????` de
  diez caracteres pasa como NIF, y `ABCDE` como código postal. Lo suyo sería un `@Pattern` en
  `ClienteRequest`, y no se ha puesto por dos motivos concretos:

  El primero es que **rompería la aplicación con los datos que hay**, y no en un caso aislado:
  al repasar `backupFacturacion360.sql` uno por uno, **tres de los siete identificadores
  fiscales semilla son inválidos**.

  | Cliente | Valor | Letra que le tocaría | |
  |---|---|---|---|
  | 2 | `12345678Z` | `Z` | ✅ |
  | 5 | `48765432P` | **`G`** | ❌ letra de control equivocada |
  | 16 | `12345678Q` | **`Z`** | ❌ letra de control equivocada |
  | 6 | `G6975841` | — | ❌ siete dígitos tras la letra; un CIF lleva ocho |

  La letra de un DNI sale del resto de dividir el número entre 23, buscado en la tabla
  `TRWAGMYFPDXBNJZSQVHLCKE`: `48765432 mod 23 = 4` → `G`, y `12345678 mod 23 = 14` → `Z`.

  Con un `@Pattern`, esos tres clientes no se podrían volver a guardar nunca desde el formulario
  de edición —el `PUT` devolvería `400` para siempre—, y el cliente 6 es además uno de los dos
  que esta misma documentación recomienda para probar el borrado, porque no tiene facturas. Que
  fallen tres de siete es el dato importante: no es un registro que se coló, es que **los datos
  semilla nunca se validaron**, así que la restricción tiene que ir *detrás* del saneado y no
  delante. Habría que limpiarlos antes, y ese fichero es de quien montó el alta.

  El segundo es que `ClienteRequest` no es nuestro: lo escribió quien hizo el alta, y una
  validación nueva ahí afecta también a su `POST`. Es una conversación, no un *commit*.

  Para que quede el trabajo hecho, el patrón sería
  `[0-9]{8}[A-HJ-NP-TV-Z]` para el NIF y `[A-HJ-NP-SUVW][0-9]{7}[0-9A-J]` para el CIF; y para
  hacerlo bien de verdad haría falta comprobar además la **letra de control**, que es un
  cálculo y no un patrón, así que su sitio sería un validador propio y no una anotación.

- **La última edición gana, sin avisar.** Si dos personas abren el mismo cliente y guardan, la
  segunda pisa lo de la primera y nadie se entera. Resolverlo bien pide una columna de versión
  en la tabla (bloqueo optimista): el `UPDATE` llevaría `WHERE version = ?`, 0 filas afectadas
  significaría "alguien ha guardado antes que tú" y eso sería un `409`. Es un cambio de esquema
  y afecta a todo el equipo, así que no entra aquí. Con un usuario a la vez —que es como se
  usa— no se nota.

  Lo que sí se ha hecho es **estrechar la ventana**: al abrir un panel se comprueba el cliente
  contra el servidor, así que se edita sobre lo que hay ahora y no sobre lo que había cuando se
  cargó la tabla. Sigue sin cubrir lo que cambie **mientras** el formulario está abierto; eso ya
  es el bloqueo optimista.
- **`filasDesplegadas` conserva la entrada de un cliente borrado** durante lo que dure la
  sesión. Es el precio de que un panel abierto sobreviva a buscar y a paginar: no hay forma de
  distinguir "este cliente ya no está en la página" de "ya no está en la base de datos" sin
  preguntar por él. Son unos pocos objetos pequeños y se van al recargar la página.

- **`clientes.js` es un único fichero de 2.133 líneas, sin módulos ES.** Dentro conviven al
  menos cinco responsabilidades que se podrían separar: la capa de red (`pedirJson`, los canales
  con `AbortController`), el pintado desde los `<template>`, el despliegue de la fila, la
  edición con su conciliación, y los anuncios de accesibilidad.

  Partirlo es viable y limpio: **no hay ni un manejador `onclick` en el HTML** y el `<script>` ya
  va con `defer`, así que nada de la página depende de que estas funciones estén en el ámbito
  global; bastaría `type="module"` con `import`/`export`, sin *bundler* ni paso de compilación.
  No entra aquí por una cuestión de orden: mover 2.133 líneas dentro de una rama que ya trae
  decenas de *commits* de funcionalidad hace la revisión **más** difícil, no menos. Su sitio es
  una rama propia, después de que esta se integre.

- **La conciliación del formulario no tiene pruebas.** `conciliarFormulario` resuelve un
  *merge* a tres bandas —lo que se pintó, lo que hay ahora en la base de datos y lo que el
  usuario lleva escrito— campo a campo, y es la lógica más delicada del frontend.

  No necesita navegador: es una función sobre tres objetos y tres pruebas cubrirían sus tres
  ramas (el campo que nadie tocó, el que el usuario cambió, y el que escribió igual que el
  servidor). Lo que sí necesita es un ejecutor de JavaScript dentro de un proyecto Maven, y eso
  cambia el *build* de todo el equipo. Es la misma categoría que los índices y que el
  `@JdbcTest`: una decisión de infraestructura que se habla con Val, no algo que se mete desde
  una *feature*.

## Base de datos

La app se conecta a `jdbc:mysql://localhost:3306/bd_facturacion` (root/root, en
`application.properties`). La tabla real es **`clientes`**, con columnas: `idcliente`,
`nombre`, `nif_cif`, `direccion`, `codigopostal`, `poblacion`, `provincia`, `telefono`,
`email`, `fecha_alta`. Necesitas esa BD arrancada y con datos. *(El script de ejemplo que
tuvimos, `bd_facturacion.sql`, se retiró porque usaba nombres antiguos.)*

## Cómo testear

1. Tener **MySQL** arrancado con la BD `bd_facturacion` y la tabla `clientes` con datos.
2. Desde `facturacion360/`: `./mvnw spring-boot:run`.
3. **Listar últimos**: `GET http://localhost:8080/cliente/listar-ultimos` → `200` + lista JSON.
   Probar los límites: `?limite=3` → 3; `?limite=500` → 100 (acotado); `?limite=0` → 1.
4. **Paginación**: `GET http://localhost:8080/cliente/listar-pagina?pagina=0&tamano=10` → `200` con
   `contenido` + metadatos (`paginaActual`, `totalPaginas`, `hayAnterior`, `haySiguiente`).
5. **Buscador**: `?busqueda=garcia` → encuentra "María López García" y "Juanita Pérez García";
   `?busqueda=GARCIA` → lo mismo (la collation ignora mayúsculas); `?busqueda=12345678` → encuentra
   por NIF; `?busqueda=zzzznoexiste` → `200` con `contenido: []` (**no** un 204: un 204 no lleva
   cuerpo y rompería el `respuesta.json()` del frontend).
6. **Comodines escapados** (la comprobación importante): `?busqueda=%25` (que es `%` codificado) y
   `?busqueda=_` → deben devolver `contenido: []`. Si devolvieran la tabla entera, el escapado
   estaría roto.
7. **Filtros**: `?provincia=Madrid`; `?provincia=Madrid&poblacion=Parla`. Y los desplegables:
   `GET /cliente/provincias`, `GET /cliente/poblaciones?provincia=Madrid` → `["Madrid","Parla"]`.
8. **Ordenación**: `?ordenarPor=nombre&direccion=asc` → alfabético; `...&direccion=desc` → el orden
   exactamente inverso; `?ordenarPor=fecha_alta&direccion=asc` → empieza por la fecha más antigua.
9. **Lista blanca**: `?ordenarPor=;DROP TABLE&direccion=x` → cae al orden por defecto, **sin error**.
10. **Offset desbordado**: `?pagina=30000000&tamano=100` → página vacía, **no** un `500`.
11. **Frontend**: abrir `http://localhost:8080/clientes.html` → la tabla se rellena y los botones
    **"Anterior" / "Siguiente"** cambian de página; el texto muestra "Página X de Y". Escribir en el
    buscador filtra tras la pausa; elegir provincia recarga las poblaciones en cascada; pulsar la
    cabecera "Nombre" ordena alfabéticamente y actualiza también el selector y el botón de la barra.
12. **AbortController**: con la pestaña Network abierta, teclear "garcia" deprisa → las peticiones
    intermedias aparecen como `canceled` y la tabla acaba mostrando lo que pone el input.
13. **Refresco automático**: en la consola del navegador ejecutar
    `document.dispatchEvent(new CustomEvent('clientes:cambiaron'))` → la tabla se recarga sola.
14. **Logs y errores**: mira la consola (`GET /cliente/...`, `listarUltimos(...) -> N`); si paras
    MySQL y repites, la respuesta es `500` y aparece un `log.error`.
15. **Detalle (API)**: `GET http://localhost:8080/cliente/1` → `200` con los diez campos;
    `GET /cliente/99999` → `404` **con el cuerpo vacío** (si trae una traza dentro, es que la
    excepción se ha escapado); `GET /cliente/abc` → `400`.
16. **Detalle (pantalla)**: pulsar una fila la despliega y vuelve a pulsarla la cierra; abrir
    **tres a la vez** y cerrar la del medio; con dos abiertas, paginar y volver → siguen
    abiertas; buscar algo que las deje fuera → desaparecen, y al limpiar el buscador vuelven.
17. **Aviso a 1 segundo**: dejar el ratón quieto sobre una fila cerrada → "Ver detalles"; sobre
    una abierta → "Ocultar detalles"; sobre la celda de los botones → no sale el de la fila,
    sale el del botón que se esté señalando.
18. **Teclado**: con `Tab` hasta el botón del ojo y `Enter` se despliega igual, y el botón
    anuncia `aria-expanded="true"`.
19. **Editar**: el lápiz abre el formulario con los datos de la BD. Guardar **sin cambiar
    nada** → `200` y la fila se queda igual (si diera `404`, la comprobación de existencia
    está mal). Cambiar el nombre con la tabla ordenada por nombre → al guardar, la fila se
    recoloca sola.
20. **La fecha de alta sobrevive**: tras guardar, la columna "Alta" sigue con su fecha y no con
    un guion. En Swagger, la respuesta del `PUT` trae `fechaAlta` informada.
21. **Errores del formulario**: dejar el nombre vacío → lo corta el navegador y no sale
    petición; poner el NIF de otro cliente → `409` señalado en ese campo; borrar el cliente
    desde otra pestaña y guardar → `404` con su aviso y la tabla se refresca.
22. **Cambios sin guardar**: escribir en un campo y pulsar la fila para cerrarla → pregunta
    antes de descartar. Escribir y **teclear en el buscador** (que repinta la tabla) → al
    volver, lo escrito sigue ahí.
23. **Solapamiento del buscador**: el ancho calculado de `#buscador-clientes` cerrado tiene que
    coincidir con el de su contenedor (**144 px**, `9rem`) y no pisar al filtro de provincia.
    Comprobarlo a 1440, 1200, 992, 768 y 360 px de ancho de ventana.
24. **Buscador con teclado**: desde la barra de navegación, pulsar `Tab` hasta el buscador → se
    abre solo, se ve el placeholder y lo que se escribe. Salir con `Tab` estando vacío → se
    recoge; salir con texto escrito → se queda abierto y con el borde azul de "filtro puesto".
25. **Filtros marcados**: elegir provincia y población → los dos iconos pasan a azul sólido y
    junto a "Limpiar" aparece un `2`. Pulsar "Limpiar" → vuelven a su gris y el contador
    desaparece.
26. **Rayado con paneles abiertos**: abrir el detalle de la 1.ª y la 3.ª fila y comprobar que
    las filas de cliente **siguen alternando** color (con `table-striped` no lo hacían).
27. **Colores de estado**: abrir el detalle → recuadro **morado** y el cartel "Viendo
    detalles"; pulsar el lápiz de esa misma fila → el recuadro pasa a **ámbar** y el cartel a
    "Editando"; pulsar "Añadir Cliente" → el de arriba sale en **verde** con "Nuevo cliente".
    Los tres carteles tienen que leerse cómodamente: el borde va vivo, el texto no.
28. **Enlaces de la tabla**: pulsar el email → abre el cliente de correo y **la fila no se
    despliega**; pulsar en el hueco de esa misma celda, al lado del enlace → sí se despliega.
29. **Lector de pantalla** (NVDA o el Narrador de Windows): en la lista de botones, cada uno
    dice el nombre de su cliente ("Editar cliente García S.L."). Al recorrer las celdas con las
    flechas de tabla, cada valor va precedido del nombre del cliente.
30. **Zona desplazable**: a 360 px de ancho, `Tab` tiene que parar en la tabla y las flechas
    moverla en horizontal. A pantalla completa, donde la tabla cabe entera, `Tab` **no** para
    ahí (el `tabindex` se quita solo); estrechar la ventana y volver a probar.
31. **Cargando**: con la pestaña Network en "Slow 3G", paginar → la tabla se atenúa mientras
    llega la respuesta y vuelve a su opacidad al pintarse.
32. **Aviso de guardado**: guardar un cliente → sale la franja verde "Cliente guardado" encima
    de la tabla, **sobrevive al refresco** y se va sola a los cinco segundos. Con un lector de
    pantalla, se anuncia sin que haya que ir a buscarla.
33. **Foco tras guardar**: abrir el lápiz con el teclado, cambiar algo y guardar con `Enter` →
    el foco vuelve al lápiz **de esa misma fila**, no al principio de la página. Repetirlo
    ordenando por nombre y cambiando el nombre (la fila se mueve de sitio): el foco la sigue.
34. **NIF repetido, en el inspector**: poner el NIF de otro cliente y guardar → el `<input>`
    tiene que quedar con `aria-invalid="true"` y un `aria-describedby` que apunte al id del
    mensaje visible. Teclear en el campo → los dos atributos desaparecen.
35. **Cliente borrado mientras se edita**: abrir el lápiz, borrarlo desde otra pestaña (o con
    un `DELETE` en Swagger) y guardar → mensaje de "ya no existe" en la franja de avisos, que
    **sigue ahí** después de que la tabla se refresque. *Elige un cliente **sin facturas** (con
    los datos del backup, el 6 o el 16, o uno que crees tú): `facturas` tiene una clave ajena a
    `clientes`, así que borrar a los demás falla por integridad referencial y no llegarías a
    probar lo que quieres.*
36. **Página que se queda sin clientes**: dejar 31 clientes (4 páginas), ir a la página 4,
    borrar los que sobran hasta dejar 30 desde otra pestaña y refrescar con
    `document.dispatchEvent(new CustomEvent('clientes:cambiaron'))` → tiene que aterrizar en la
    **página 3 con datos**, no en una página vacía que diga "Página 4 de 3".
37. **La barra de filtros no se parte**: a 1200 y a 1440 px, pulsar la lupa → el buscador se
    abre encogiendo lo que haga falta y "Limpiar" **se queda en la misma línea**. A 768 px o
    menos, cada control ocupa su propia línea completa.
38. **Recuadro del último cliente**: abrir el detalle del **último** cliente de la página → el
    recuadro de color tiene que cerrar por abajo (antes le faltaba esa raya).
39. **Sin peticiones de más al repintar** (pestaña Network, filtro `cliente/`): abrir tres
    paneles, borrar el filtro de Network y **teclear una letra en el buscador** → tiene que
    salir **una sola** petición, la de `listar-pagina`. Ninguna a `/cliente/{id}`. Abrir un
    panel a mano sí pide su detalle, para comprobarlo.
40. **Los datos del panel reabierto son los buenos**: con un detalle abierto, cambiar ese mismo
    cliente desde otra pestaña y refrescar → el panel enseña los datos nuevos, no los de antes.
41. **Búsqueda demasiado larga**: `GET /cliente/listar-pagina?busqueda=` + 61 caracteres → `400`
    con **cuerpo vacío** (en las herramientas del navegador, `Content-Length: 0`), no la página
    de error de Spring. Desde la pantalla no se puede llegar: el campo corta en 60.
42. **Enter en el buscador**: escribir y pulsar `Enter` → la búsqueda sale al momento, sin la
    espera de 300 ms, y **una sola vez** (no dos).
43. **Descartar cambios**: escribir en un campo y pulsar la fila → sale el diálogo de Bootstrap,
    no el del navegador. "Seguir editando" → el foco vuelve al control desde el que se preguntó
    y no se pierde nada. "Descartar" → se cierra la fila y el foco acaba en su lápiz. Probarlo
    también con `Escape` y pulsando el fondo oscuro: las dos cosas equivalen a "seguir
    editando".
44. **Fallo de red anunciado**: parar la aplicación y paginar → el mensaje sale en la tabla y,
    con un lector de pantalla, **se anuncia** (antes se escribía en el `<tbody>`, que no anuncia
    nada). Lo mismo con el error del panel de detalle.
45. **`integrity` de los CDN**: recargar con la caché desactivada → la página tiene que verse
    con sus estilos y con los avisos emergentes funcionando. Si algo fuera mal con las huellas,
    se vería al instante: la página saldría sin estilo y sin Bootstrap.
46. **Mensaje centrado en móvil**: a 360 px de ancho, buscar algo que no exista → el mensaje de
    "no hay clientes" tiene que quedar centrado **dentro** de la tabla. Con un panel abierto,
    estrechar la ventana de escritorio a móvil → el panel sigue ocupando el ancho justo.
47. **Tests**: `./mvnw test` desde `facturacion360/` → 45 pruebas en verde **sin MySQL
    arrancado**. (Son 44 las de esta feature; la 45 es el `contextLoads` que trae el esqueleto
    de Spring.)
48. **El panel se abre lleno**: en Network, poner "Slow 3G" y pulsar una fila → los datos
    aparecen **al instante**, sin pasar por "Cargando", y la petición a `/cliente/{id}` sigue
    saliendo (es la comprobación). Cuando llega, nada se mueve.
49. **El detalle se pone al día solo**: abrir el detalle de un cliente, cambiarle la población
    desde otra pestaña (o con un `PUT` en Swagger) y **volver a abrir** ese detalle → sale el
    valor de antes durante un instante y se corrige al llegar la respuesta. Comprobar que la
    **fila** también se actualiza si lo que cambia es el nombre, el CIF, el email, el teléfono
    o la fecha.
50. **Lo que estás escribiendo no se pisa**: abrir el lápiz, escribir un nombre nuevo **sin
    guardar**, cambiar el teléfono de ese cliente desde otra pestaña y volver a pulsar el lápiz
    (cerrar y abrir) → el teléfono se actualiza, tu nombre a medio escribir **sigue ahí**, y
    encima del formulario aparece el aviso diciendo exactamente eso.
51. **Borrado durante la comprobación**: abrir un panel de un cliente que otra pestaña acaba de
    borrar → aviso de "ya no existe" en la franja y la tabla se refresca sin la fila.
52. **Un fallo de la comprobación no rompe nada**: parar la aplicación y abrir un panel → los
    datos del listado se quedan en pantalla y el fallo solo se ve en la consola. La tabla no se
    queda a medias ni sale el panel de error.
53. **El escape del `LIKE` aguanta cualquier `sql_mode`**: en MySQL, `SET SESSION sql_mode =
    'NO_BACKSLASH_ESCAPES';` y repetir el punto 6 (`?busqueda=%25` y `?busqueda=_`) → siguen
    devolviendo `contenido: []`. Con el escape anterior, esta misma prueba daba un `500` con
    *"Incorrect arguments to ESCAPE"*. Devolver el modo a su valor con `SET SESSION sql_mode =
    DEFAULT;`.
54. **El error de guardado no se pierde aunque la tabla se repinte**: abrir el lápiz, poner el
    NIF de **otro** cliente y, justo antes de pulsar Guardar, **teclear una letra en el
    buscador** (eso repinta la tabla mientras viaja el `PUT`) → el aviso del `409` tiene que
    verse igualmente. Si el panel ha desaparecido del todo, sale en la franja de arriba. Antes
    no salía en ningún sitio y parecía que se había guardado.
55. **La tabla vacía se anuncia**: con NVDA o el Narrador, buscar algo que no exista → tiene que
    leerse *"No hay clientes que coincidan con la búsqueda. Puedes quitar los filtros."*, no
    solo el "Sin resultados" del pie.
56. **Dos avisos iguales seguidos se leen los dos**: con lector de pantalla, guardar un cliente
    y guardar otro a continuación → "Cliente guardado" tiene que sonar **las dos veces**.
57. **Los logs no repiten ni se saltan nada**: paginar, filtrar por provincia y abrir un detalle
    → en la consola, cada endpoint deja **exactamente dos líneas** del controller (la de entrada
    y la del código HTTP) más la del service. Ninguna duplicada, ninguna ausente. Comprobar en
    particular que `listar-pagina` deja su `-> 200 (pagina X de Y)`.
58. **Un salto de línea en el buscador no ensucia el log**: pegar en el buscador un texto con un
    salto de línea dentro → en la consola tiene que salir en **una sola línea**, con el salto
    convertido en espacio.
59. **El lápiz solo en edición**: abrir el lápiz de una fila → los ocho rótulos llevan su lápiz
    ámbar delante. Pulsar el ojo de esa misma fila → el panel cambia a detalle y **no queda ni
    un lápiz**.
60. **El recuadro no parpadea al cerrar**: abrir un detalle y cerrarlo mirando el borde mientras
    se pliega → tiene que irse en morado, **sin ningún destello oscuro**, y el cartel tiene que
    conservar su color hasta el final. Repetirlo en edición (ámbar). Antes se veía una raya
    negra durante el plegado.
61. **El recuadro envuelve el bloque entero**: con el panel de edición abierto, el rectángulo
    ámbar tiene que rodear la fila del cliente **y las cuatro filas del panel** (dos de rótulos
    y dos de campos), sin cortarse por dentro. Probarlo también con el **último** cliente de la
    página, que es donde antes faltaba el lado de abajo.
62. **Control por voz** (o, si no lo tienes a mano, buscando el `id` en el inspector): decir
    **"NIF/CIF"** y **"Email"** tiene que llevar el foco a esos campos. Antes no pasaba nada,
    porque el nombre accesible decía "NIF o CIF" y "Correo electrónico".
63. **Lector de pantalla en el panel** (NVDA o el Narrador de Windows): al abrir un detalle
    debe oírse *"Viendo detalles de García S.L."* y luego *"Dirección: Calle Mayor 1"*, y
    **no** debe anunciarse ninguna tabla dentro de la tabla. En edición, al llegar a un campo
    debe decir el rótulo que se ve ("NIF/CIF") y "obligatorio" donde toque.
64. **Dos paneles de edición a la vez**: abrir el lápiz de dos clientes distintos y comprobar
    en el inspector que los `id` de sus campos **no se repiten** (llevan el id del cliente
    dentro) y que no queda ninguno con el sufijo `PLANTILLA`. Pulsar el rótulo de un campo
    tiene que llevar el foco a **su** campo y no al del otro panel.
65. **La validación sigue avisando**: en el panel de edición, borrar el nombre y pulsar Guardar
    → el mensaje rojo tiene que aparecer justo debajo de ese campo. (Depende de que el
    `<input>` y su `.invalid-feedback` sigan siendo hermanos, que es lo que mira el selector de
    Bootstrap.)

### El alta

66. **Abrir el alta**: pulsar "Añadir Cliente" → arriba del todo aparece un formulario en
    blanco con el recuadro **verde**, el cartel "Nuevo cliente" y el cursor ya en Nombre.
67. **No se abre dos veces**: volver a pulsar el botón → **no** sale un segundo formulario; el
    foco vuelve al campo Nombre del que ya está.
68. **Validación antes de salir a la red**: pulsar Guardar con todo vacío → el navegador marca
    los cinco obligatorios y **no se manda ninguna petición** (mirarlo en la pestaña Red).
69. **Crear**: rellenar los ocho campos y Guardar → la tabla salta a la primera página y el
    cliente sale **el primero**, con el foco en su lápiz.
70. **NIF repetido**: crear un cliente con un NIF/CIF que ya exista → `409`, el campo NIF en
    rojo con "Ya existe otro cliente con este NIF/CIF" y el formulario **sigue abierto** para
    corregirlo.
71. **Sobrevive a la búsqueda**: con el alta a medio rellenar, escribir algo en el buscador →
    el formulario sigue arriba **con lo tecleado**, y el cursor no se va del buscador.
72. **Sobrevive a "sin coincidencias"**: con el alta a medio rellenar, buscar algo que no
    exista → sale el mensaje de la tabla vacía y el formulario **sigue ahí**, encima.
73. **Cancelar con algo escrito**: pulsar Cancelar con un campo relleno → sale el diálogo de
    "Hay cambios sin guardar". Con todo vacío, se cierra sin preguntar.
74. **Creado pero fuera de la vista**: filtrar por una provincia y crear un cliente de otra →
    avisa de que no se ve por los filtros. Lo mismo poniendo el orden "Más antiguos": avisa de
    que con ese orden no está en la primera página.
75. **El recuadro cierra por arriba**: mirar el borde verde del alta → tiene que rodear el
    formulario por los **cuatro** lados, no quedarse en forma de U.

## ⚠️ Si en la BD real la tabla o las columnas se llaman distinto

Los nombres de tabla/columnas están escritos "a mano" en el SQL, así que **deben coincidir en
varios sitios a la vez**. Si el esquema cambiara, hay que ajustarlos todos:

1. La constante **`COLUMNAS_CLIENTE`** de `ClienteRepositoryJdbcImpl` (la lista de columnas que
   comparten `findPagina` y las demás consultas; por eso está extraída, para no repetirla).
2. El `SELECT ... FROM clientes ...` de **`findUltimos`**.
3. Las condiciones de **`anadirFiltros`** (`nombre`, `nif_cif`, `provincia`, `poblacion`) y las
   consultas de **`findProvincias`** / **`findPoblaciones`**.
4. El mapa **`COLUMNAS_ORDEN`**, cuyos *valores* son nombres reales de columna (las *claves* son lo
   que manda el frontend y pueden quedarse como están).
5. Los `rs.getXxx("nombre_columna")` de **`ClienteRowMapper.mapRow`**.

Ejemplo: si la columna pasara a llamarse `id` en vez de `idcliente`, habría que ajustar
`COLUMNAS_CLIENTE`, el `ORDER BY ... idcliente` de `sqlOrden` y el `rs.getInt("idcliente")`.

> **Nota:** al principio seguimos el `ESQUEMA ER.png` (que usa `nombre_razon_social` y `pais`),
> pero el profe fijó como buenos los nombres **reales de la BD**: tabla `clientes`, columnas
> `idcliente`, `nombre`, `codigopostal`… El código ya usa esos nombres reales.

---


## Qué señaló la auditoría y cómo ha quedado

Seis puntos. **Cinco están cerrados** y se resumen en una línea cada uno; el sexto es el único
que sigue abierto y va al final, con su análisis entero.

| | Punto | Estado |
|---|---|---|
| **A** | Inyección de dependencias y forma del `return` | Decisión razonada, abajo |
| **B** | Manejo de errores centralizado | 🤝 Es una feature de otro |
| **C** | Tests automáticos | ✅ Todo salvo lo que necesita BD |
| **D** | `limite` como `@RequestParam` | ✅ Implementado |
| **E** | Nombres de rama descriptivos | ✅ Resuelto |
| **F** | Índices de base de datos | ⏳ **Pendiente** |


### A. Decisiones de estilo: inyección de dependencias y forma del `return`

Dos elecciones de estilo de nuestra parte. **No hay una única "correcta"**: cada una tiene pros y
contras, así que las dejamos razonadas.

**1) Inyección de dependencias: `@Autowired` en el campo (lo que usamos) vs. por constructor.**
La "inyección" es que Spring te da los objetos ya creados en vez de hacer tú `new`. Se puede
recibir en el **atributo** o en el **constructor**:

| | `@Autowired` en campo *(actual, patrón del profe)* | Por constructor *(`private final` + constructor)* |
|---|---|---|
| **Pros** | Menos código, muy directo. | Campos `final` (**inmutables**); dependencias **a la vista**; **testeable sin Spring** (le pasas dobles). |
| **Contras** | El campo **no puede ser `final`** (es mutable); las dependencias quedan "**ocultas**" (hay que leer toda la clase); testear necesita Spring o reflexión. | Un poco **más de código**; **cambia el patrón del profe** (hay que acordarlo). |

```java
// Ejemplo por constructor:
private final ClienteService clienteService;
public ClienteController(ClienteService s) { this.clienteService = s; }
```
> **Decisión**: mantenemos **inyección por campo** para seguir el patrón del profe. Migrar a
> constructor sería trivial si se acuerda con él.

Al escribir los tests se ha visto en la práctica lo que dice la columna de contras:
`ClienteRepositoryJdbcImplTest` no puede pasarle el `JdbcTemplate` simulado al repositorio, así
que se lo mete Mockito por **reflexión** con `@InjectMocks`. Funciona, pero es la herramienta la
que rellena un campo privado desde fuera; con un constructor sería un `new` normal y corriente.


**2) Forma del `return`: guardar en variable vs. `return` directo.**

| | Variable → log → `return` *(actual)* | `return` directo *(como lo teníamos antes)* |
|---|---|---|
| **Pros** | Puedes **inspeccionar y loguear** el valor antes de devolverlo; en el depurador pones un breakpoint en el `return` y **ves la variable**; encaja con logs y `try/catch`. | Más corto y conciso. |
| **Contras** | Un poco **más verboso** (una línea extra). | **No puedes ver ni loguear** el valor sin partir la expresión; **depurar es más incómodo** (no hay variable donde poner el ojo). |

```java
// Antes: no hay dónde mirar el valor
return clienteRepository.findUltimos(limite);
```

```java
// Ahora: la variable existe, así que se puede loguear y poner un breakpoint en el return
List<Cliente> clientes = clienteRepository.findUltimos(limite);
log.debug("findUltimos({}) -> {} filas", limite, clientes.size());
return clientes;
```
> **Decisión**: usamos el patrón variable sobre todo porque **facilita el log y el
> depurado** (poder mirar el valor justo antes de devolverlo).
>
> Y se aplica **sin excepciones**, también en los métodos privados que no loguean. Los cuatro
> que quedaban devolviendo la expresión directa (`sqlOrden` y `escaparComodines` del
> repositorio, `offset()` y `normalizar()` del record de criterios) se han igualado al resto.
> En `sqlOrden` es donde más se nota que no era un capricho: ese método **es** la defensa
> contra la inyección SQL, así que es justo el sitio donde uno quiere poder poner un
> *breakpoint* y ver qué fragmento se ha montado con lo que llegó por la URL.



### B. Manejo de errores centralizado (`@RestControllerAdvice`) — 🤝 ASIGNADO A OTRA FEATURE

> **Esto ya no es un pendiente nuestro**: la *gestión centralizada de excepciones* es una feature
> propia dentro del reparto del equipo. Lo que queda aquí es el análisis hecho, para que quien la
> implemente lo aproveche en vez de repetirlo; el caso concreto que la justifica, con los dos
> caminos ya descartados, está en
> ["Logs y manejo de errores"](#logs-y-manejo-de-errores).
>
> **No lo hemos metido en esta rama a propósito.** Un `@RestControllerAdvice` cambia por
> definición el contrato de respuesta de **todos** los endpoints del proyecto, incluidos el alta,
> el borrado y los de facturas. Meterlo desde una rama que se llama "ver detalles y editar" es
> colar en una PR un cambio que su revisor no espera encontrar ahí.

**Concepto**: cuando un endpoint lanza una excepción, Spring puede **desviarla** a una clase
"guardiana" que decide qué responder, en vez de soltar el error por defecto. Es como un `catch`
global para todos los controllers.
- **Cómo**: una clase con métodos `@ExceptionHandler`, uno por tipo de error:
  ```java
  @RestControllerAdvice
  public class ManejadorErrores {
      @ExceptionHandler(DataAccessException.class)   // errores de BD
      public ResponseEntity<Void> bd(DataAccessException e) {
          log.error("Error de base de datos", e);     // el detalle, al log
          return ResponseEntity.internalServerError().build();   // al navegador, un 500 pelado
      }
  }
  ```
- **Por qué es relevante**: hoy cada endpoint repite su propio `try/catch` para no soltar una
  traza al navegador. Funciona —nuestros `500`, `404`, `409` y `400` salen ya con el cuerpo
  vacío—, pero es el mismo bloque copiado una y otra vez, y lo que **no** pasa por ningún
  método nuestro se sigue escapando: las conversiones de tipo que fallan antes de entrar
  (`/cliente/abc`, `?limite=abc`), explicadas en
  ["Logs y manejo de errores"](#logs-y-manejo-de-errores). El manejador central arregla las dos
  cosas: quita la repetición y cubre también lo que ocurre fuera del método.

### C, D y E — cerrados

- **C. Tests automáticos.** Escritas las **44 pruebas** que no necesitan base de datos, en cuatro
  clases: qué cubre cada una está en ["Tests automáticos"](#tests-automáticos). Sigue abierto
  **solo** el `@JdbcTest`, y no por falta de ganas: hay que decidir antes qué base de datos de
  test se usa, porque H2 no imita ni la *collation* ni el `ESCAPE` de MySQL —que es justo lo que
  habría que probar—, así que haría falta Testcontainers. Es infraestructura de todo el equipo.
- **D. `limite` como `@RequestParam`.** Hecho: el `10` que estaba escrito en el código llega
  ahora por la URL con `defaultValue`, y se acota entre las constantes `LIMITE_MIN` y
  `LIMITE_MAX` para que nadie pida `?limite=999999`. *(Lo más "REST" sería `@Min`/`@Max`
  devolviendo un `400`, pero eso necesita el manejador de errores del punto B, que es de otro.)*
- **E. Nombres de rama descriptivos.** Venía de que la primera rama se llamaba `Angel` a secas,
  que dice quién trabaja pero no en qué. La convención en uso es `feature/<loQueHace>_Angel`.

### F. Índices de base de datos para los filtros y la ordenación — ⏳ el único pendiente

La tabla `clientes` solo tiene `PRIMARY KEY (idcliente)` y `UNIQUE KEY (nif_cif)`. Los filtros que
añadimos comparan por **igualdad** (`WHERE provincia = ?`, `WHERE poblacion = ?`) y la ordenación
usa `nombre` y `fecha_alta`: **ninguna de esas cuatro columnas tiene índice**, así que MySQL recorre
la tabla entera para resolverlas.

Un índice es como el índice alfabético de un libro: permite saltar a lo que buscas sin leer todas
las páginas. Pero solo sirve si la condición se puede "buscar por orden".

**Aquí está el matiz importante**: al **buscador** un índice **no** le serviría. Usa
`LIKE '%texto%'`, y ese `%` **inicial** significa "que contenga", no "que empiece por" — como no
sabes por qué letra empieza, el orden alfabético no te ayuda y hay que mirar fila por fila igual.
Por eso el índice **no** arregla la búsqueda; arregla los **filtros** y la **ordenación**, que sí
son condiciones ordenables.

```sql
ALTER TABLE clientes ADD INDEX idx_provincia_poblacion (provincia, poblacion);
ALTER TABLE clientes ADD INDEX idx_nombre (nombre);
```

El índice compuesto `(provincia, poblacion)` sirve para las dos combinaciones que usa el frontend:
filtrar solo por provincia, y filtrar por provincia + población. El orden de las columnas importa:
un índice compuesto se puede usar "de izquierda a derecha", así que `(provincia, poblacion)` vale
para `WHERE provincia = ?` pero **no** para `WHERE poblacion = ?` a solas.

**Por qué está pendiente y no hecho:** con las 7 filas actuales no se nota absolutamente nada —es
una mejora pensada para cuando la tabla crezca—, y sobre todo **toca `backupFacturacion360.sql`,
que es un script compartido por todo el equipo**. Conviene comentarlo con Val antes de meterlo en
ninguna rama, para no pisar el esquema de los demás.

---

## Para los compañeros: avisar cuando cambie un cliente

**¿Tenéis que tocar vuestro código? Sí, una sola línea.** Nuestra tabla se refresca sola, pero
necesita que le **aviséis** cuando un cliente cambie. **Justo después de que vuestro `fetch` de
crear, editar o eliminar responda con éxito (2xx)**, disparad este evento:

```js
document.dispatchEvent(new CustomEvent('clientes:cambiaron'));
```

Ejemplo (eliminar):
```js
async function eliminarCliente(id) {
    const respuesta = await fetch(`/cliente/${id}`, { method: 'DELETE' });
    if (respuesta.ok) {
        document.dispatchEvent(new CustomEvent('clientes:cambiaron')); // <-- la tabla se recarga sola
    }
}
```

Notas:
- **No hay que pasar datos** en el evento ni conocer nuestras funciones: basta con dispararlo.
- Nosotros recargamos la **página actual**. Si al **crear** queréis que el cliente nuevo se vea al
  instante (aparece el primero, en la página 0), llevad además al usuario a esa primera página.

### Si vais a tocar la pantalla de clientes

Dos cosas que ahorran un rato de depuración.

**1) Los botones de la fila no se enganchan uno a uno.** Las filas se clonan de un `<template>`
y se crean y se destruyen en cada repintado, así que un `document.querySelectorAll('.btn-...')`
al cargar la página **no encuentra ningún botón** y no engancha nada. (Había uno así para
eliminar, y por eso no hacía nada.) Todo va por **delegación** en un único listener sobre el
`<tbody>`; el sitio para añadir una acción nueva es este `if`, en `clientes.js`:

```js
const boton = evento.target.closest(".celda-acciones .btn");
if (boton) {
    if (boton.classList.contains("btn-ver")) alternarDespliegue(fila, "detalle");
    else if (boton.classList.contains("btn-editar")) alternarDespliegue(fila, "edicion");
    // ← aquí va el caso de .btn-eliminar
    return;
}
```

El id del cliente está en `fila.dataset.clienteId`. Después de un `await` (una confirmación, un
`fetch`), **no reutilicéis esa variable `fila`**: la tabla puede haberse repintado y ese `<tr>`
ya no estar en el documento. Para eso están `filaViva(id)` y `formularioVivo(id)`.

**2) `clientes.js` ocupa el ámbito global de la página.** Se carga con `<script defer>` y no con
`type="module"`, así que todo lo que declara vive en `window`. No es una decisión, es cómo se
quedó: hoy **el HTML no llama a ninguna función desde un `onclick`** —no queda ninguno en
`clientes.html`—, así que pasarlo a módulos es viable y está anotado en
[Limitaciones conocidas](#limitaciones-conocidas). Mientras siga siendo global, si añadís otro
`<script>` a `clientes.html`, evitad estos nombres o se pisarán en silencio:

| Tipo | Nombres ocupados |
|---|---|
| Estado | `criterios`, `paginaActual`, `filasDesplegadas`, `clientesEnPagina`, `peticionesEnVuelo`, `focoPendiente`, `listadosEnVuelo`, `peticionesActivas`, `altaAbierta` |
| Peticiones | `pedirJson`, `enviarJson`, `cerrarCanal`, `cargarClientes`, `cargarProvincias`, `cargarPoblaciones` |
| Pintado | `pintarFilas`, `pintarCeldasFila`, `pintarPaginacion`, `pintarEnlace`, `mostrarMensaje`, `mostrarError`, `pintarFormularioCliente`, `pintarPanelEdicion`, `pintarPanelAlta` |
| Despliegue | `abrirDespliegue`, `cerrarDespliegue`, `alternarDespliegue`, `marcarFila`, `nombrarPanel`, `filaViva`, `formularioVivo` |
| Alta | `abrirAlta`, `cerrarAlta`, `reabrirAlta`, `insertarFilaAlta`, `filaAlta`, `formularioAlta`, `guardarCliente`, `contarErrorAlta`, `avisarClienteCreado`, `confirmarDescarteAlta` |
| Avisos | `anunciar`, `escribirPista`, `limpiarPistas` |
| Pantalla de carga | `mostrarCarga`, `ocultarCarga` |

`guardarEdicion` y `guardarCliente` son **dos funciones distintas y ninguna es la otra**: la
primera manda el `PUT` de una fila que se está editando y la segunda el `POST` del alta. El
nombre estuvo reservado a propósito hasta que la feature del alta llegó a ocuparlo.

