# H0 · Suelo firme — Plan de implementación

> **Para trabajadores agénticos:** SUB-HABILIDAD OBLIGATORIA: usa
> `superpowers:subagent-driven-development` (recomendado) o
> `superpowers:executing-plans` para ejecutar este plan tarea a tarea. Los pasos usan
> casillas (`- [ ]`) para poder marcarlos.

**Objetivo:** dejar `./mvnw verify` en verde y bajo integración continua, de modo que a
partir de aquí cualquier regresión se detecte sola.

**Arquitectura:** H0 no toca una sola línea de código de producción. Corrige una aserción
de prueba obsoleta, sustituye el arranque manual de MySQL por Testcontainers, y añade
integración continua y tres plugins de análisis en modo informe. La restricción de «ni un
cambio de comportamiento» se cumple por construcción: ningún fichero de `src/main/java` se
modifica.

**Pila:** Java 21, Spring Boot 4.1.0, Maven wrapper, JUnit 5, Mockito, Testcontainers 2.0.5,
MySQL 8.4, GitHub Actions.

**Especificación:** `docs/superpowers/specs/2026-09-19-facturacion360-design.md`

## Restricciones globales

- **Directorio del proyecto Maven:** `facturacion360/`. El `pom.xml` **no** está en la raíz
  del repositorio. Todos los comandos de Maven se ejecutan desde `facturacion360/`.
- **Java 21.** `java.version` es 21 en el `pom.xml`; no se cambia.
- **Spring Boot 4.1.0.** Importa `testcontainers-bom:2.0.5`, así que los artefactos de
  Testcontainers se declaran **sin `<version>`**.
- **Coordenadas de Testcontainers 2.x:** los módulos llevan prefijo. Es
  `org.testcontainers:testcontainers-mysql`, **no** `org.testcontainers:mysql` (ese artefacto
  se quedó en 1.21.4 y no existe en 2.0.5). Verificado con `dependency:get` y contra
  `testcontainers-bom-2.0.5.pom`.
- **Clase del contenedor:** `org.testcontainers.mysql.MySQLContainer` (el paquete nuevo de
  2.x). La ruta antigua `org.testcontainers.containers.MySQLContainer` sigue en el jar por
  compatibilidad; **no se usa**.
- **`MySQLContainer` NO lleva parámetro de tipo en 2.x.** Se declara `MySQLContainer` y se
  construye `new MySQLContainer("mysql:8.4")`, sin diamante. La forma de 1.x
  —`MySQLContainer<?>` y `new MySQLContainer<>(...)`— es un error de compilación:
  `error: type MySQLContainer does not take parameters`. Comprobado compilando las dos
  versiones contra `testcontainers-mysql-2.0.5.jar`. Si un editor o un asistente sugiere
  añadir el diamante, **está equivocado**.
- **Línea base de no regresión: 160 pruebas, 0 fallos.** Ningún commit puede dejar menos.
- **Estilo del proyecto:** nombres en español, «variable + un solo `return`», comentarios que
  explican el *porqué* y no el *qué*. Se respeta en todo lo que se escriba.
- **No se ejecutan `git commit` ni `git push`.** Los comandos se dejan preparados para que
  los revise y ejecute la persona. Cada commit termina con
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

---

## Estructura de ficheros

| Fichero | Responsabilidad | Tarea |
|---|---|---|
| `facturacion360/src/test/java/.../controller/FacturaControllerTests.java` | Modificar: una aserción obsoleta | 1 |
| `facturacion360/pom.xml` | Modificar: dependencias de Testcontainers y tres plugins | 2, 5 |
| `facturacion360/src/test/java/.../repository/FacturaGuardadoIntegracionTests.java` | Modificar: arranque por contenedor en vez de por propiedades del sistema | 2 |
| `.github/workflows/ci.yml` | Crear: la única puerta automática antes de integrar | 3 |
| `.gitignore` (raíz) | Modificar: ignorar los logs | 4 |
| `docs/guia-desarrollo.md` | Crear: arquitectura, convenciones y lo que no se debe hacer | 6 |
| `docs/flujo-trabajo.md` | Crear: ramas, commits y qué comprueba la integración | 6 |

---

### Tarea 1: Poner verde la suite

**Ficheros:**
- Modificar: `facturacion360/src/test/java/edu/xtd/facturacion360/controller/FacturaControllerTests.java:130-134`

**Interfaces:**
- Consume: nada.
- Produce: una suite en verde. Todas las tareas siguientes dependen de ello, porque sin verde
  no se distingue una regresión nueva de la heredada.

**Contexto para quien lo ejecute.** La aserción que falla comprueba que la edición de un
borrador rechaza un estado inválido *antes* de tocar el repositorio. Cuando se escribió
(`07d752e`), el único estado admitido era `BORRADOR`, así que el test usaba `EMITIDA` como
ejemplo de estado inválido. Horas después, `9c28908` («permitir emitir al guardar un
borrador») pasó la regla a admitir `BORRADOR` **y** `EMITIDA`, y no actualizó el test. Desde
entonces `EMITIDA` es válido, la petición llega al repositorio, el doble devuelve `null` y
sale 404 donde el test espera 400.

La regla nueva es la buena: el frontend depende de ella en `facturas.js:504`, `:527` y `:865`.
Así que lo que se corrige es el test, y se corrige **conservando su intención**: se sustituye
`EMITIDA` por `ANULADA`, que sí es un estado que la regla rechaza hoy.

- [ ] **Paso 1: Reproducir el fallo y guardar la evidencia**

```bash
cd facturacion360
./mvnw test -Dtest=FacturaControllerTests
```

Esperado: FALLO.
```
FacturaControllerTests.edicionValidaIdEstadoYConceptosAntesDeEscribir:134
  Status expected:<400> but was:<404>
```

- [ ] **Paso 2: Sustituir la aserción obsoleta**

En `FacturaControllerTests.java`, dentro de
`edicionValidaIdEstadoYConceptosAntesDeEscribir()`, sustituir estas dos líneas:

```java
		clienteHttp.perform(put("/factura/7/borrador").contentType(MediaType.APPLICATION_JSON).content(PETICION))
				.andExpect(status().isBadRequest());
```

por estas:

```java
		// ANULADA y no EMITIDA: el estado que aqui hace falta es uno que la regla rechace
		// ANTES de leer la factura, que es lo que este metodo comprueba. EMITIDA lo era
		// hasta 9c28908 ("permitir emitir al guardar un borrador"), que lo paso a valido;
		// desde entonces la peticion llega al repositorio y responde 404, no 400. ANULADA
		// pasa el @Pattern del FacturaRequest y la rechaza FacturaServiceImpl, que es
		// exactamente el orden que se quiere fijar.
		String anulada = PETICION.replace("EMITIDA", "ANULADA");
		clienteHttp.perform(put("/factura/7/borrador").contentType(MediaType.APPLICATION_JSON).content(anulada))
				.andExpect(status().isBadRequest());
```

- [ ] **Paso 3: Verificar que pasa**

```bash
./mvnw test -Dtest=FacturaControllerTests
```

Esperado: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Paso 4: Comprobar que la prueba prueba algo (rompiendo el código a propósito)**

Una prueba que pasa con el código bien y con el código mal no prueba nada. En
`facturacion360/src/main/java/edu/xtd/facturacion360/service/FacturaServiceImpl.java:175`,
ampliar temporalmente la lista de estados admitidos:

```java
		if (!List.of("BORRADOR", "EMITIDA", "ANULADA").contains(facturaRequest.estado())) {
```

Ejecutar:
```bash
./mvnw test -Dtest=FacturaControllerTests
```

Esperado: **FALLO**, `expected:<400> but was:<404>`. Si pasa, la aserción no está fijando
nada y hay que rehacerla antes de seguir.

- [ ] **Paso 5: Deshacer la rotura deliberada**

```bash
cd ..
git checkout -- facturacion360/src/main/java/edu/xtd/facturacion360/service/FacturaServiceImpl.java
git diff --stat   # debe listar SOLO FacturaControllerTests.java
```

- [ ] **Paso 6: Verificar la suite entera**

```bash
cd facturacion360 && ./mvnw test
```

Esperado: `Tests run: 160, Failures: 0, Errors: 0, Skipped: 20` y `BUILD SUCCESS`.
Las 20 saltadas siguen siéndolo: las resuelve la tarea 2.

- [ ] **Paso 7: Commit (preparar, no ejecutar)**

```bash
git add facturacion360/src/test/java/edu/xtd/facturacion360/controller/FacturaControllerTests.java
git commit -m "test(facturas): el estado invalido del caso vuelve a ser invalido

La asercion usaba EMITIDA como ejemplo de estado que la edicion rechaza.
Dejo de serlo en 9c28908, que lo hizo valido a proposito para poder emitir
al guardar un borrador, y el test se quedo sin actualizar: master lleva en
rojo desde entonces.

Se cambia por ANULADA, que es lo que el metodo queria comprobar —un estado
rechazado antes de leer la factura— y hoy si lo es. La regla no se toca:
el frontend depende de emitir-al-guardar en facturas.js:504, :527 y :865.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Tarea 2: Las 20 pruebas de integración pasan a ejecutarse siempre

**Ficheros:**
- Modificar: `facturacion360/pom.xml` (bloque `<dependencies>`)
- Modificar: `facturacion360/src/test/java/edu/xtd/facturacion360/repository/FacturaGuardadoIntegracionTests.java:27-84`

**Interfaces:**
- Consume: la suite en verde de la tarea 1.
- Produce: `FacturaGuardadoIntegracionTests` ejecutándose contra un MySQL real y desechable.
  Las tareas 3 (integración continua) y 5 (cobertura) cuentan con que estas 20 pruebas corren.

**Contexto para quien lo ejecute.** Hoy la clase se salta entera porque exige cuatro
propiedades del sistema (`facturas.mysql.puerto`, `.servidor`, `.directorio`, `.clave`) que
apuntan a una instancia de MySQL que alguien tiene que levantar a mano. El `@BeforeAll`
comprueba `@@server_uuid` y `@@datadir` **antes de escribir nada**: es una salvaguarda
deliberada para no arrasar una base real por un puerto mal escrito. Esa intención hay que
conservarla; lo que cambia es el mecanismo, porque un contenedor es desechable por
construcción.

- [ ] **Paso 1: Añadir las dependencias**

En `facturacion360/pom.xml`, dentro de `<dependencies>`, junto a las demás de `test`:

```xml
		<!--
			Testcontainers 2.x cambio los identificadores de artefacto: los modulos llevan
			prefijo. Es testcontainers-mysql, NO mysql, que se quedo en 1.21.4 y no existe
			en la 2.0.5 que gestiona Spring Boot 4.1.0. Por eso van sin <version>: el
			spring-boot-dependencies importa el testcontainers-bom.
		-->
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-mysql</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
```

- [ ] **Paso 2: Comprobar que resuelven y con qué versión**

```bash
cd facturacion360
./mvnw -q dependency:tree -Dincludes=org.testcontainers
```

Esperado: aparecen `testcontainers-mysql:2.0.5` y `testcontainers-junit-jupiter:2.0.5`.
Si sale `Could not resolve`, el identificador está mal escrito: revisar el prefijo.

- [ ] **Paso 3: Cambiar el arranque de la clase de prueba**

En `FacturaGuardadoIntegracionTests.java`, sustituir el import y la anotación:

```java
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
```
```java
@EnabledIfSystemProperty(named = "facturas.mysql.puerto", matches = "[0-9]+")
class FacturaGuardadoIntegracionTests {
```

por:

```java
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
```
```java
/** Solo para una instancia temporal identificada expresamente; no carga application.properties. */
@Testcontainers
class FacturaGuardadoIntegracionTests {

	/**
	 * La misma version que produccion. Con "mysql:latest" una subida de version de la
	 * imagen cambiaria el comportamiento de las pruebas sin que nadie tocara el codigo.
	 *
	 * SIN diamante: en Testcontainers 2.x MySQLContainer dejo de llevar parametro de tipo.
	 * Escribir MySQLContainer<> es "type MySQLContainer does not take parameters".
	 */
	@Container
	static final MySQLContainer CONTENEDOR = new MySQLContainer("mysql:8.4")
			.withDatabaseName("facturas_pruebas")
			.withUsername("pruebas_facturas")
			.withPassword("pruebas_facturas");
```

- [ ] **Paso 4: Cambiar el `@BeforeAll`**

Sustituir el cuerpo de `prepararBaseAislada()` desde la primera línea hasta
`assertEquals("facturas_pruebas", ...)` incluida, es decir este bloque:

```java
		int puerto = Integer.parseInt(System.getProperty("facturas.mysql.puerto"));
		assertTrue(puerto >= 10000 && puerto <= 65535);
		String servidor = System.getProperty("facturas.mysql.servidor");
		String directorio = System.getProperty("facturas.mysql.directorio");
		String clave = System.getProperty("facturas.mysql.clave");
		assertNotNull(servidor);
		assertNotNull(directorio);
		assertTrue(directorio.startsWith("/tmp/facturas-mysql-"));
		assertNotNull(clave);
		datos = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + puerto
				+ "/facturas_pruebas?sslMode=DISABLED&allowPublicKeyRetrieval=true", "pruebas_facturas", clave);
		jdbc = new JdbcTemplate(datos);
		// Ninguna escritura antes de comprobar la identidad y ubicación del servidor temporal.
		assertEquals(servidor, jdbc.queryForObject("SELECT @@server_uuid", String.class));
		assertEquals(directorio, jdbc.queryForObject("SELECT @@datadir", String.class));
		assertEquals("facturas_pruebas", jdbc.queryForObject("SELECT DATABASE()", String.class));
```

por este:

```java
		datos = new DriverManagerDataSource(CONTENEDOR.getJdbcUrl(),
				CONTENEDOR.getUsername(), CONTENEDOR.getPassword());
		jdbc = new JdbcTemplate(datos);

		// Ninguna escritura antes de comprobar que se esta hablando con el contenedor y no
		// con otra cosa. Antes esto se comprobaba con @@server_uuid y @@datadir porque el
		// servidor lo levantaba una persona y un puerto mal escrito podia apuntar a una base
		// real. Con el contenedor esa clase de error ya no cabe —la URL la da el propio
		// contenedor—, pero la comprobacion se mantiene: es barata y es la que convierte un
		// "apunta a otro sitio" en un fallo de prueba en lugar de en un DELETE.
		assertEquals(CONTENEDOR.getDatabaseName(),
				jdbc.queryForObject("SELECT DATABASE()", String.class));
```

El resto del método —la carga del esquema desde `backupFacturacion360v1.sql` y las tres
comprobaciones de `information_schema`— **se deja tal cual**. Sigue siendo lo que garantiza
que el esquema de prueba es el del proyecto y no uno inventado.

- [ ] **Paso 5: Quitar los imports que sobran**

Borrar `import org.junit.jupiter.api.condition.EnabledIfSystemProperty;`. Es el único import
que sobra.

Las tres llamadas a `assertNotNull` del fichero eran justamente las tres que desaparecen en el
paso 4, así que tras el cambio ya no se usa ninguna. **No hay que tocar nada por eso**: los
asertos entran por el comodín `import static org.junit.jupiter.api.Assertions.*;`, que no
genera aviso de import sin usar. `assertTrue` y `assertEquals` sí siguen usándose, en la carga
del esquema y en las comprobaciones de `information_schema`.

Comprobar que compila antes de seguir:

```bash
./mvnw -q test-compile
```

Esperado: sin errores. Si sale `cannot find symbol` sobre `MySQLContainer`, revisar el
prefijo del artefacto y el paquete: es `org.testcontainers.mysql`, no
`org.testcontainers.containers`.

- [ ] **Paso 6: Ejecutar y verificar que dejan de saltarse**

```bash
./mvnw test -Dtest=FacturaGuardadoIntegracionTests
```

Esperado: `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`. La primera vez tarda más
porque descarga la imagen `mysql:8.4`.

Si sale `Could not find a valid Docker environment`, Docker no está arrancado. Arrancarlo y
repetir; el plan asume Docker disponible.

- [ ] **Paso 7: Verificar la suite entera**

```bash
./mvnw test
```

Esperado: `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0` y `BUILD SUCCESS`.
**Ninguna saltada.** Ese 0 es el resultado de esta tarea.

- [ ] **Paso 8: Commit (preparar, no ejecutar)**

```bash
git add facturacion360/pom.xml \
        facturacion360/src/test/java/edu/xtd/facturacion360/repository/FacturaGuardadoIntegracionTests.java
git commit -m "test(facturas): la integracion levanta su propio MySQL y deja de saltarse

Las 20 pruebas exigian cuatro propiedades del sistema apuntando a una
instancia que alguien tenia que levantar a mano, asi que no las ejecutaba
nadie: 20 de 160 saltadas en cada pasada.

Con Testcontainers el servidor lo levanta la propia prueba y se tira al
acabar. La salvaguarda de no escribir antes de saber con quien se habla se
conserva; lo que cambia es el mecanismo, porque un contenedor ya es
desechable por construccion.

La imagen se fija en mysql:8.4, la de produccion: con :latest una subida de
la imagen cambiaria el resultado sin tocar el codigo.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Tarea 3: Integración continua

**Ficheros:**
- Crear: `.github/workflows/ci.yml` (en la **raíz** del repositorio, no en `facturacion360/`)

**Interfaces:**
- Consume: la suite en verde y sin saltadas de las tareas 1 y 2.
- Produce: la puerta automática que exige el flujo de trabajo de la tarea 6.

**Contexto para quien lo ejecute.** Los dos fallos que documenta `docu/fallos_master.txt` se
colaron porque un merge resuelto «tomando un lado entero» borró código sin que nada lo
avisara. El propio documento concluye que un par de pruebas de rutas lo habrían cazado. Esas
pruebas ya existen; lo que faltaba es algo que las ejecute sin depender de que alguien se
acuerde.

- [ ] **Paso 1: Crear el flujo**

Crear `.github/workflows/ci.yml`:

```yaml
# La puerta automatica antes de integrar. Existe por un motivo concreto: los dos endpoints
# que documenta docu/fallos_master.txt desaparecieron en sendos merges resueltos tomando un
# lado entero, y nadie se entero porque el proyecto seguia compilando. Git no avisa de eso;
# una suite ejecutada en cada push, si.
name: CI

on:
  push:
    branches: [master]
  pull_request:

jobs:
  pruebas:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Preparar Java 21
        uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: temurin
          # Cachea ~/.m2 a partir de los pom.xml. Sin esto cada ejecucion vuelve a
          # descargarse Spring Boot entero.
          cache: maven

      # El pom.xml NO esta en la raiz del repositorio: cuelga de facturacion360/.
      # -B quita las barras de progreso, que en un registro de CI son ruido ilegible.
      - name: Pruebas
        working-directory: facturacion360
        run: ./mvnw -B verify

      # Se suben siempre, tambien cuando las pruebas fallan: es justo cuando hacen falta.
      - name: Informes de Surefire
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: facturacion360/target/surefire-reports/
```

- [ ] **Paso 2: Comprobar que el YAML es válido antes de subirlo**

```bash
python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML valido')"
```

Esperado: `YAML valido`.

- [ ] **Paso 3: Comprobar en local lo mismo que hará la integración**

```bash
cd facturacion360 && ./mvnw -B verify
```

Esperado: `BUILD SUCCESS`, `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0`.
Se usa `verify` y no `test` porque es lo que ejecutará la integración: si `verify` hace algo
más que `test` en este proyecto, mejor descubrirlo aquí que en el primer push.

- [ ] **Paso 4: Commit (preparar, no ejecutar)**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: las pruebas se ejecutan en cada push y en cada pull request

Los dos endpoints perdidos que documenta fallos_master.txt desaparecieron
en merges resueltos tomando un lado entero, y pasaron desapercibidos porque
el proyecto seguia compilando y las pruebas que quedaban pasaban. El propio
documento concluye que un par de pruebas de rutas lo habrian cazado: existen,
lo que faltaba era algo que las ejecutara sin depender de la memoria de nadie.

El working-directory apunta a facturacion360 porque el pom.xml no esta en la
raiz. Los informes de Surefire se suben tambien cuando falla, que es cuando
sirven de algo.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Tarea 4: Los logs salen del repositorio

**Ficheros:**
- Borrar: `facturacion360/carpeta1/` (9 ficheros)
- Modificar: `.gitignore` (raíz)

**Interfaces:**
- Consume: nada.
- Produce: nada que otras tareas usen. Es independiente y puede ejecutarse en cualquier orden.

**Contexto para quien lo ejecute.** `facturacion360/carpeta1/` contiene los logs de la
aplicación: `f.log` (290 KB) y ocho rotaciones comprimidas. La raíz ya ignora `logs/` y
`mislogs/`, pero no `carpeta1`. Revisado su contenido: solo trazas de `JdbcTemplate` y
criterios de búsqueda vacíos, sin datos personales. El riesgo hoy es bajo; el hábito, con
`logging.level.org.springframework.jdbc.core=DEBUG` activo, no lo es.

- [ ] **Paso 1: Confirmar qué hay y que no lleva datos personales**

```bash
git ls-files facturacion360/carpeta1/
grep -ocE "[0-9]{8}[A-Z]" facturacion360/carpeta1/f.log
```

Esperado: nueve ficheros listados, y un recuento de NIF de 0 o 1. Si sale un número alto,
**parar**: hay datos personales en el historial y eso cambia el tratamiento; consultarlo
antes de seguir.

- [ ] **Paso 2: Sacarlos del árbol**

```bash
git rm -r --cached facturacion360/carpeta1
rm -rf facturacion360/carpeta1
```

- [ ] **Paso 3: Ignorarlos**

Añadir al final de `.gitignore` de la raíz:

```gitignore
# Los logs de la aplicacion no se versionan. Estuvieron en facturacion360/carpeta1 durante
# meses porque el nombre no encajaba con ninguna regla de las de arriba, asi que aqui se
# ignora el fichero por su nombre y no por el de la carpeta que lo contenga.
f.log
f.log.*
carpeta1/
```

- [ ] **Paso 4: Comprobar que ya no los ve**

```bash
git status --short
git check-ignore -v facturacion360/carpeta1/f.log 2>/dev/null || echo "(la carpeta ya no existe, correcto)"
```

Esperado: el `status` muestra los nueve borrados y el cambio de `.gitignore`, y nada más.

- [ ] **Paso 5: Verificar que no se ha roto nada**

```bash
cd facturacion360 && ./mvnw -B verify
```

Esperado: `BUILD SUCCESS`, 160 pruebas. Los logs no los lee nadie, pero se comprueba igual.

- [ ] **Paso 6: Commit (preparar, no ejecutar)**

```bash
git add -A .gitignore facturacion360/carpeta1
git commit -m "chore: los logs de la aplicacion salen del repositorio

facturacion360/carpeta1 traia f.log (290 KB) y ocho rotaciones comprimidas.
La raiz ya ignoraba logs/ y mislogs/, pero no este nombre, asi que llevaban
meses versionandose. Revisado el contenido: trazas de JdbcTemplate y criterios
vacios, sin datos personales.

Se ignoran por el nombre del fichero ademas de por el de la carpeta, para que
la proxima carpeta que alguien invente tampoco los cuele.

AVISO: esto los quita del arbol, NO del historial. Sacarlos de ahi exige
reescribirlo, y esa decision no se toma en un commit de limpieza.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

- [ ] **Paso 7: Dejar anotado el procedimiento de reescritura, sin ejecutarlo**

No se ejecuta. Se entrega para que lo decida la persona, junto con el aviso de que reescribir
el historial obliga a todo el que tenga un clon a volver a clonar:

```bash
# git filter-repo --path facturacion360/carpeta1 --invert-paths
# git push --force-with-lease origin master
```

---

### Tarea 5: Cobertura y análisis estático, en modo informe

**Ficheros:**
- Modificar: `facturacion360/pom.xml` (bloque `<build><plugins>`)

**Interfaces:**
- Consume: la suite verde y sin saltadas de las tareas 1 y 2 (la cobertura se mide ejecutando
  las pruebas; con 20 saltadas la cifra mentiría).
- Produce: la cifra de cobertura de partida, que la tarea 6 escribe en el flujo de trabajo.

**Contexto para quien lo ejecute.** Los tres plugins entran **sin umbral que rompa la
compilación**. No es tibieza: un umbral puesto el mismo día que el plugin, sobre un código
que nunca lo ha tenido, o es tan bajo que no dice nada o deja el proyecto en rojo por algo que
no se acaba de romper. Primero se mide, y el umbral se pone cuando haya una cifra real que
defender. Spotless va con `ratchetFrom` por el mismo motivo: sin él, la primera ejecución
reformatea el proyecto entero y destroza `git blame` en un commit que se suponía de
infraestructura.

- [ ] **Paso 1: Añadir los tres plugins**

En `facturacion360/pom.xml`, dentro de `<build><plugins>`, después del
`maven-javadoc-plugin` (no antes: ese lleva un comentario de NO BORRAR que conviene no
separar de su etiqueta):

```xml
			<!--
				Cobertura. SIN umbral que rompa la compilacion, a proposito: un umbral
				puesto el mismo dia que el plugin o es tan bajo que no dice nada, o deja
				el proyecto en rojo por algo que no se acaba de romper. Primero se mide;
				el umbral se pone cuando haya una cifra real que defender.
				Informe en target/site/jacoco/index.html
			-->
			<plugin>
				<groupId>org.jacoco</groupId>
				<artifactId>jacoco-maven-plugin</artifactId>
				<version>0.8.15</version>
				<executions>
					<execution>
						<id>preparar-agente</id>
						<goals><goal>prepare-agent</goal></goals>
					</execution>
					<execution>
						<id>informe</id>
						<phase>verify</phase>
						<goals><goal>report</goal></goals>
					</execution>
				</executions>
			</plugin>

			<!--
				Analisis estatico. failOnError a false por la misma razon que JaCoCo no
				lleva umbral: primero se ve que dice sobre el codigo que ya hay.
			-->
			<plugin>
				<groupId>com.github.spotbugs</groupId>
				<artifactId>spotbugs-maven-plugin</artifactId>
				<version>4.10.4.1</version>
				<configuration>
					<failOnError>false</failOnError>
					<effort>Max</effort>
					<threshold>Default</threshold>
				</configuration>
			</plugin>

			<!--
				Formato. ratchetFrom es lo importante: sin el, la primera ejecucion
				reformatea los 58 ficheros del proyecto y destroza git blame en un commit
				de infraestructura. Con el, solo mira lo que la rama haya tocado.
				Y solo tres reglas, ninguna que mueva codigo: quitar espacios al final,
				dejar salto de linea final y ordenar los imports.
			-->
			<plugin>
				<groupId>com.diffplug.spotless</groupId>
				<artifactId>spotless-maven-plugin</artifactId>
				<version>3.10.2</version>
				<configuration>
					<ratchetFrom>origin/master</ratchetFrom>
					<java>
						<removeUnusedImports />
						<trimTrailingWhitespace />
						<endWithNewline />
					</java>
				</configuration>
			</plugin>
```

- [ ] **Paso 2: Comprobar que el `pom.xml` sigue siendo válido**

```bash
cd facturacion360
./mvnw -q validate
```

Esperado: sin errores. Si sale `Non-parseable POM`, el XML está mal cerrado.

- [ ] **Paso 3: Medir la cobertura de partida**

```bash
./mvnw -B verify
```

Esperado: `BUILD SUCCESS`, 160 pruebas, 0 saltadas, y el informe generado en
`target/site/jacoco/index.html`.

- [ ] **Paso 4: Anotar la cifra**

```bash
grep -oE "Total[^%]*%" target/site/jacoco/index.html | head -1
```

Apuntar el porcentaje: es el que la tarea 6 escribe en `docs/flujo-trabajo.md` como línea de
partida. **Ese número sale de ejecutar la herramienta, que es lo correcto para una medida de
cobertura** (a diferencia de un valor de referencia funcional, que tiene que venir de fuera).

- [ ] **Paso 5: Ver qué dice SpotBugs, sin arreglar nada todavía**

```bash
./mvnw -B spotbugs:check
```

Esperado: termina con `BUILD SUCCESS` aunque encuentre avisos, porque `failOnError` está a
`false`. Apuntar cuántos y de qué categoría; **no se corrige ninguno en H0**: corregir un
aviso de SpotBugs es un cambio de comportamiento y este hito no tiene ninguno.

- [ ] **Paso 6: Commit (preparar, no ejecutar)**

```bash
git add facturacion360/pom.xml
git commit -m "build: cobertura y analisis estatico, de momento solo midiendo

JaCoCo, SpotBugs y Spotless entran sin umbral que rompa la compilacion. Un
umbral puesto el mismo dia que el plugin, sobre codigo que nunca lo ha tenido,
o es tan bajo que no dice nada o deja el proyecto en rojo por algo que no se
acaba de romper. Primero se mide.

Spotless lleva ratchetFrom: sin el, la primera ejecucion reformatea los 58
ficheros y destroza git blame. Y solo tres reglas, ninguna que mueva codigo.

Ningun aviso de SpotBugs se corrige aqui: corregirlo seria un cambio de
comportamiento, y este hito no tiene ninguno.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Tarea 6: La guía de desarrollo y el flujo de trabajo

**Ficheros:**
- Crear: `docs/guia-desarrollo.md`
- Crear: `docs/flujo-trabajo.md`

**Interfaces:**
- Consume: la cifra de cobertura de la tarea 5 y el flujo de integración de la tarea 3.
- Produce: los documentos vivos que cada hito posterior actualiza con lo que haya decidido.

**Contexto para quien lo ejecute.** Son los entregables 2 y 3 del encargo. La parte que de
verdad importa es la última sección de la guía —**lo que no se debe hacer y por qué**—,
porque es la que evita que dentro de seis meses alguien deshaga este trabajo creyendo que
mejora algo. Su contenido sale de la sección 5 de la especificación, que ya está contrastada
contra el código.

- [ ] **Paso 1: Escribir `docs/guia-desarrollo.md`**

Con estas secciones, en este orden:

1. **Dónde está cada cosa** — el `pom.xml` cuelga de `facturacion360/`, no de la raíz; los
   cinco paquetes; los dos frontends que conviven y cuál es el bueno para código nuevo.
2. **Las capas y por qué** — `controller → service → repository → RowMapper`, interfaz por
   capa, DTO de entrada y salida separados del dominio, todos los DTO `record`.
3. **El mapa de diez capas del JavaScript** — copiado de la cabecera de `main.js`, con su
   excepción conocida documentada, y la regla: quien añada un `import` mira el mapa primero.
4. **Convenciones** — nombres en español; «variable + un solo `return`»; SLF4J parametrizado;
   el contrato en la interfaz y `{@inheritDoc}` en la implementación; la etiqueta `@autor` y
   por qué el `maven-javadoc-plugin` no se puede borrar.
5. **Inyección de dependencias: por constructor, campos `private final`** — con
   `EmisorServiceImpl` como ejemplo. Anotar que el resto se convierte en H1.
6. **Errores** — todo error sale como `ProblemDetail` (RFC 9457) desde `ManejadorExcepciones`.
7. **Transacciones** — `TransactionTemplate` en el servicio; el registro fiscal irá dentro de
   la transacción de emisión y el envío fuera (H4 y H7).
8. **Pruebas** — unitarias con dobles; integración con Testcontainers y MySQL 8.4; los valores
   de referencia funcionales vienen de documentos externos, nunca de ejecutar el programa.
9. **Lo que NO se debe hacer, y por qué** — la tabla de la sección 5 de la especificación,
   entera, con sus motivos.

- [ ] **Paso 2: Escribir `docs/flujo-trabajo.md`**

Con estas secciones:

1. **Ramas** — `feature/<nombre>` desde `master`; nunca commit directo sobre `master`.
2. **Actualizar la rama con `master` ANTES de integrarla** — con su motivo explícito: es lo
   que habría evitado los dos endpoints perdidos, porque hace que el conflicto salga en la
   rama, donde se ve, y no en el merge, donde no.
3. **Commits** — un cambio conceptual por commit; el mensaje explica **el porqué**, no el qué;
   en español y sin tildes en el asunto, como el resto del historial.
4. **Qué tiene que pasar antes de integrar** — `./mvnw -B verify` en verde, sin saltadas; la
   integración continua en verde; y ninguna prueba menos que la línea base de 160.
5. **Qué comprueba la integración continua** — lo que hace `ci.yml`, y la cobertura de partida
   medida en la tarea 5.
6. **Lo que todavía no está automatizado** — la spec de Playwright, que exige la aplicación
   arrancada y una variable de entorno, y por eso no está en `ci.yml` todavía. Queda para H10.

- [ ] **Paso 3: Comprobar que los enlaces internos funcionan**

```bash
grep -oE "\]\([^)]+\)" docs/guia-desarrollo.md docs/flujo-trabajo.md \
  | sed 's/.*(\(.*\))/\1/' | grep -v "^http" | sort -u \
  | while read f; do [ -e "$f" ] || echo "ROTO: $f"; done
echo "(sin lineas ROTO = todos los enlaces resuelven)"
```

- [ ] **Paso 4: Verificación final del hito completo**

```bash
cd facturacion360 && ./mvnw -B verify
```

Esperado: `BUILD SUCCESS`, `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Paso 5: Commit (preparar, no ejecutar)**

```bash
git add docs/guia-desarrollo.md docs/flujo-trabajo.md
git commit -m "docs: la guia de desarrollo y el flujo de trabajo

La seccion que importa es la ultima de la guia, la de lo que no se debe
hacer: el proyecto tiene bastante codigo cuyas razones no son obvias y que
parece mejorable —el orden de los catch, el xxx minuscula del formateador,
el MessageDigest que se pide en cada llamada— y sin el porque escrito es
cuestion de tiempo que alguien lo simplifique y rompa algo que no se nota.

El flujo recoge tambien la leccion de fallos_master.txt: actualizar la rama
con master ANTES de integrarla, para que el conflicto salga donde se ve.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Definición de terminado para H0

- [ ] `./mvnw -B verify` → `BUILD SUCCESS`, `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0`
- [ ] `git diff master --stat` no toca **ningún** fichero de `src/main/java`
- [ ] La aserción corregida se ha comprobado rompiendo el código a propósito (tarea 1, paso 4)
- [ ] `.github/workflows/ci.yml` en verde en el primer push
- [ ] Cero `TODO` nuevos, cero `catch` vacíos nuevos, cero `System.out` nuevos
- [ ] `docs/guia-desarrollo.md` y `docs/flujo-trabajo.md` escritos
- [ ] Seis commits preparados y entregados, ninguno ejecutado

**Pendiente de verificación externa en H0:** nada. Este hito se verifica entero en local.

**Anotado para hitos posteriores, no resuelto aquí:**
- El `System.out.println` de `ClienteMapper:37` → H1.
- Los avisos de SpotBugs que salgan en la tarea 5 → se trian en H1 y H2; corregirlos es
  cambio de comportamiento.
- Los logs y las credenciales **siguen en el historial** de Git → decisión pendiente de la
  persona, con el procedimiento ya entregado.
