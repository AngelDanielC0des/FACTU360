# Guía de desarrollo de FACTURACION360

Documento vivo. Cada hito que decide algo lo escribe aquí.

La sección que más importa es la última, **[Lo que NO se debe hacer](#9--lo-que-no-se-debe-hacer-y-por-qué)**:
este proyecto tiene bastante código cuyas razones no son obvias y que parece mejorable. Sin el
porqué escrito, es cuestión de tiempo que alguien lo «simplifique» y rompa algo que no se nota.

---

## 1 · Dónde está cada cosa

**El `pom.xml` no está en la raíz del repositorio**, cuelga de `facturacion360/`. Todos los
comandos de Maven se ejecutan desde ahí. Es el primer tropiezo de quien llega.

```
FACTURACION360
 ├── README.md
 ├── .github/workflows/ci.yml        <- la puerta automatica
 ├── docs/                            <- esta guia, el flujo y las especificaciones
 └── facturacion360/                  <- aqui esta el pom.xml
      └── src
           ├── main
           │    ├── java/edu/xtd/facturacion360
           │    │    ├── controller   service   repository   dto
           │    │    ├── validacion   <- el validador de NIF/CIF
           │    │    └── verifactu    <- huella y cadena canonica
           │    └── resources
           │         ├── static       <- HTML, CSS, JS
           │         │    └── js      <- los modulos ES de clientes
           │         └── docu         <- volcados SQL, diagramas, documentacion
           └── test
                ├── java              <- lo que ejecuta ./mvnw test
                └── js                <- Playwright; hoy NO lo ejecuta nadie
```

**Conviven dos estilos de frontend, y no por accidente sin más:**

| Pantalla | Cómo está | Para código nuevo |
|---|---|---|
| Clientes | 21 módulos ES repartidos en diez capas, bajo `static/js/` | **Este es el bueno** |
| Facturas | `facturas.js`, 1.014 líneas en un fichero suelto | Se trocea en H10 |

## 2 · Las capas, y por qué

```
Navegador ──GET──► Controller ──► Service ──► Repository ──► MySQL
Navegador ◄─JSON── Response ◄─(Mapper)── Dominio ◄─(RowMapper)── fila
```

- **Controller**: recibe HTTP, valida la entrada, traduce a códigos de estado. No sabe SQL.
- **Service**: la lógica de negocio, las transacciones y la coordinación de repositorios.
- **Repository**: la **única** capa que habla SQL, con `JdbcTemplate`.
- **RowMapper**: de `ResultSet` a objeto de dominio.
- **DTO**: entrada (`…Request`), salida (`…Response`) y dominio, **separados**. Así el modelo
  interno cambia sin romper el contrato con el frontend. Todos son `record`.

Cada capa se programa **contra una interfaz** (`ClienteService`, `ClienteRepository`), y la
implementación hereda el contrato con `{@inheritDoc}`.

**No se usa JPA ni Hibernate, y no se va a usar.** Todo el acceso a datos es `JdbcTemplate`.
Mezclar dos formas de acceder a la base sería peor que cualquiera de las dos. Además hay dos
puntos donde conviene bajar a SQL igualmente: el `SELECT … FOR UPDATE` que impedirá bifurcar la
cadena de VERI\*FACTU, y la asignación del número de serie.

## 3 · El mapa de diez capas del JavaScript

Está en la cabecera de `static/js/main.js`, que es el único fichero que carga el HTML.

```
capa 0   config   dom   estado                        sin dependencias
capa 1   api   avisos   foco   fila   notificaciones   problema   validacion
capa 2   formulario
capa 3   dialogo   paneles
capa 4   despliegue
capa 5   edicion   borrado
capa 6   tabla
capa 7   listado
capa 8   alta   filtros
capa 9   main
```

**Un módulo solo importa de las capas de arriba.** No es decorativo: es lo que garantiza que no
haya ciclos. **Se mantiene a mano**: no hay ninguna comprobación automática, así que **quien
añada un `import` es quien tiene que mirar el mapa**.

Hay una excepción conocida y documentada: `avisos` importa de `notificaciones` y las dos están
en la capa 1. Se documenta en vez de taparla, que es lo único peor que tenerla.

## 4 · Convenciones

- **Nombres en español**, coherentes con el dominio.
- **Variable y un solo `return`**: el resultado se guarda, se registra en el log y se devuelve
  al final. Así siempre hay un punto donde loguear lo que se devuelve.
- **SLF4J parametrizado**: `log.info("... {}", valor)`. Nunca concatenando.
- **El contrato se documenta en la interfaz**; la implementación lo hereda con `{@inheritDoc}`.
- **Los comentarios explican el porqué, no el qué.** Si un comentario dice lo que hace la línea
  siguiente, sobra; si dice por qué se hizo así y no de la forma evidente, es el que salva el
  código dentro de seis meses.
- **La etiqueta `@autor`** (sin `h`) la declara el `maven-javadoc-plugin` en el `pom.xml` y
  permite atribuir métodos concretos dentro de clases compartidas, cosa que el `@author`
  estándar no admite. **No borres ese plugin** sin quitar antes todas las etiquetas `@autor`
  del código: javadoc las trataría como desconocidas y fallaría la generación.

## 5 · Inyección de dependencias

**Por constructor, con campos `private final`.** El modelo es `EmisorServiceImpl`:

```java
@Service
public class EmisorServiceImpl implements EmisorService {

    private final EmisorRepository emisorRepository;

    public EmisorServiceImpl(EmisorRepository emisorRepository) {
        this.emisorRepository = emisorRepository;
    }
```

Por qué, y no `@Autowired` sobre el campo:

- Una dependencia que falte se descubre **al arrancar**, no al usarla.
- El campo puede ser `final`: nada lo reasigna a mitad de ejecución.
- Las pruebas pasan un doble por el constructor, **sin `ReflectionTestUtils`**.

> **Estado a 2026-09-19:** siete clases siguen con inyección por campo y 15 de sus 16 campos
> son *package-private*. Se convierten en **H1**, un commit por clase. Mientras tanto,
> `EmisorServiceImpl` es la referencia. **Código nuevo: siempre por constructor.**

## 6 · Errores

Todos los errores salen como **`ProblemDetail` (RFC 9457)** desde `ManejadorExcepciones`, que
es un `@RestControllerAdvice`. Los controllers **no** llevan `try/catch` de fontanería.

El repositorio **traduce qué restricción de SQL ha saltado** antes de dejarla subir:
`NifCifDuplicadoException`, `ClienteConFacturasException`, `ClienteInexistenteException`,
`NumeroFacturaDuplicadoException`. Se hace ahí porque es el único sitio que sabe qué índice es
cuál. Estas excepciones extienden `RuntimeException`, **no** las de Spring: una prueba que
espere `DataIntegrityViolationException` genérica está fijando un contrato antiguo.

El frontend pinta con `textContent`, **nunca** interpolando en `innerHTML`.

## 7 · Transacciones

`TransactionTemplate` en el servicio, no `@Transactional` sobre el método, cuando hace falta
controlar la propagación o reintentar. El alta de factura usa `PROPAGATION_REQUIRES_NEW` y
reintenta hasta tres veces ante una colisión de número.

**Regla para VERI\*FACTU, cuando llegue (H4 y H7):**
- La generación del registro va **dentro** de la transacción de emisión. Una factura emitida
  sin registro sería una infracción.
- El envío a la AEAT va **fuera**. Es asíncrono y puede fallar sin arrastrar a la factura.

## 8 · Pruebas

```bash
cd facturacion360
./mvnw -B verify        # 160 pruebas, 0 fallos, 0 saltadas
```

- **Unitarias**: con dobles de Mockito, sin levantar la aplicación.
- **Integración**: `FacturaGuardadoIntegracionTests` levanta **MySQL 8.4 con Testcontainers**.
  La imagen se fija a `mysql:8.4`, la de producción: con `:latest`, una subida de la imagen
  cambiaría el resultado sin que nadie tocara el código.
- El esquema de prueba se carga del volcado **de número más alto** (`backupFacturacion360v3.sql`).
  Cargar uno viejo da errores de columna que parecen del código y son del volcado.

> **Hace falta Docker.** Sin el motor arrancado, esas 20 pruebas **fallan**, no se saltan. Es
> deliberado: `@EnabledIfDockerAvailable` las haría saltarse con elegancia y reabriría justo el
> agujero que se tapó — estuvieron saltándose desde que se escribieron y se pudrieron sin que
> nadie lo viera.

**Tres reglas sobre las pruebas:**

1. **Una prueba que pasa con el código bien y con el código mal no prueba nada.** Compruébala
   rompiendo el código a propósito y viéndola roja.
2. **Los valores de referencia funcionales vienen de fuera**, nunca de ejecutar el programa.
   Las huellas de VERI\*FACTU salen de los tres vectores del PDF de la AEAT; la URL del QR, de
   su especificación. Una medida como la cobertura sí sale de la herramienta: es otra cosa.
3. **Cada cambio de comportamiento lleva una prueba que falla con el código anterior.**

Hay además `src/test/js/facturas-alta.spec.cjs` (Playwright). Hoy **no lo ejecuta nadie**: no
hay `package.json` y exige la aplicación arrancada y `FACTURAS_URL_PRUEBAS`. Se integra en H10.

## 9 · Lo que NO se debe hacer, y por qué

Esta tabla está contrastada contra el código. Si crees que algo de aquí está mal, **discútelo
antes de cambiarlo**.

| Qué | Por qué está así |
|---|---|
| **No toques por dentro `CadenaCanonica` ni `CalculadorHuella`** | Comprobados contra los tres vectores oficiales de la AEAT. Es lo único del proyecto que **falla en silencio**: si se rompe, las huellas salen mal en todos los registros de forma consistente y la aplicación parece funcionar. Para añadir capacidades, añade una sobrecarga; no reescribas las que están verificadas. |
| **`xxx` minúscula en el formateador de fecha** | `XXX` en mayúsculas colapsa el huso cero a `Z`, y la AEAT espera `+00:00`. Ya costó el fallo `b00a8d2`. |
| **`MessageDigest` se pide en cada llamada** | Tiene estado. Compartir la instancia da huellas corruptas con dos hilos. No lo conviertas en constante. |
| **La lista blanca `COLUMNAS_ORDEN` del `ORDER BY`** | Es **la** defensa contra inyección SQL en la ordenación. Un `?` no vale para un nombre de columna. No la sustituyas por interpolación «porque ya se valida antes». |
| **El orden de los `catch` en `FacturaRepositoryJdbcImpl`** | `DuplicateKeyException` **hereda de** `DataIntegrityViolationException`. Ponerla después se tragaría las colisiones de número y mataría el reintento de numeración. Está comentado en el propio fichero. |
| **Los constructores de compatibilidad de `ConceptoFactura` y `ConceptoRequest`** | Permiten que el código anterior compile tras añadir campos. No son duplicación. |
| **`conciliarFormulario` frente a `pintarPanelEdicion`** | Uno **reemplaza** el formulario; el otro **concilia** conservando lo que el usuario está tecleando. Unificarlos borra lo que está escribiendo. Ya se rechazó esa propuesta una vez. |
| **El escape del `LIKE` y su constante** | El orden de los tres `replace` es el único correcto. Cambiarlo deja escapar comodines. |
| **El mapa de diez capas del JavaScript** | Es lo que impide los ciclos. Se extiende, no se sustituye. |
| **`estado` no incluye `PAGADA`** | No es un descuido: los dos ejes —documento y cobro— ya están separados. El enum real es `BORRADOR`, `EMITIDA`, `ANULADA`. Documentación antigua dice lo contrario; manda el código. |
| **`facturas` no tiene `idemisor` ni `fecha_hora_expedicion`** | Omisión **deliberada hasta H4**. Ponerlas `NOT NULL` sin el Java que las rellena rompe el alta entera: `Field 'idemisor' doesn't have a default value`. Entran en el mismo commit que su `INSERT`. |
| **La edición de un borrador admite `EMITIDA`** | Es emitir-al-guardar, una funcionalidad viva de la que depende `facturas.js`. No la revuelvas a «solo BORRADOR»: rompe la pantalla. |

### Y dos cosas más

**No resuelvas un merge tomando un lado entero.** Los dos endpoints que documenta
`docu/fallos_master.txt` desaparecieron así, en silencio, sin salir como línea borrada en
ningún commit. Actualiza tu rama con `master` **antes** de integrarla: el conflicto sale
entonces en la rama, donde se ve.

**No des por cierto lo que dice la documentación sin mirar el código.** `fallos_master.txt`
describe dos fallos que ya están corregidos; el documento de VERI\*FACTU describe un
`@Pattern` que no es el real y coloca clases en paquetes donde no acabaron. Los propios
documentos avisan de ello. Manda el código.
