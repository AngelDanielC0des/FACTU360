# Flujo de trabajo

Documento vivo. Acompaña a la [guía de desarrollo](guia-desarrollo.md).

---

## 1 · Ramas

**Feature Branch.** Cada mejora en su rama, integrada en `master` por Pull Request.
**Nunca se hace commit directamente sobre `master`.**

```bash
git checkout master
git pull --ff-only origin master
git checkout -b feature/<NombreMejora>
```

## 2 · Actualiza tu rama con `master` ANTES de integrarla

Este paso tiene nombre propio porque es el que evita el fallo más caro que ha tenido el
proyecto.

```bash
git fetch origin
git merge origin/master        # en TU rama
# resuelves aqui, donde se ve lo que estas resolviendo
```

Los dos endpoints que documenta `docu/fallos_master.txt` —`/cliente/listar-ultimos` y
`/factura/{id}/detalle`— desaparecieron en merges resueltos **tomando un lado entero**. No
salen como línea borrada en ningún commit: solo se ven comparando los dos padres del merge. En
el diff de la Pull Request no había nada raro, el proyecto compilaba y las pruebas que quedaban
pasaban.

Resolver el conflicto en la rama lo pone delante de quien lo está resolviendo. Resolverlo en el
merge lo esconde.

## 3 · Commits

- **Un cambio conceptual por commit.** Un refactor y una corrección de comportamiento no van
  juntos, aunque toquen el mismo fichero.
- **El mensaje explica el porqué, no el qué.** El *qué* ya está en el diff. Lo que se pierde es
  la razón.
- Asunto en español, sin tildes, en minúscula tras el prefijo, como el resto del historial:
  `feat(...)`, `fix(...)`, `test(...)`, `refactor(...)`, `docs(...)`, `chore(...)`, `ci`,
  `build`.
- Si el commit corrige algo que otro commit introdujo, **nómbralo por su hash**. Es lo que
  permite reconstruir por qué el código es como es.

## 4 · Qué tiene que pasar antes de integrar

Todo esto, sin excepciones:

- [ ] `./mvnw -B verify` en verde desde `facturacion360/`
- [ ] **160 pruebas como mínimo, 0 fallos y 0 saltadas**
- [ ] La integración continua en verde en la Pull Request
- [ ] Cada cambio de comportamiento con una prueba que **falla con el código anterior**
- [ ] Cero `TODO` nuevos, cero `catch` vacíos nuevos, cero `System.out`
- [ ] Si has añadido un `import` en el JavaScript de clientes, has mirado el mapa de capas
- [ ] Si has decidido algo no obvio, está en la guía de desarrollo

**«0 saltadas» es un criterio, no una casualidad.** Una prueba que se salta no protege nada y
se pudre sin que nadie lo vea: las 20 de integración estuvieron saltándose desde que se
escribieron y, al ejecutarlas por primera vez, tenían tres roturas acumuladas.

## 5 · Qué comprueba la integración continua

`.github/workflows/ci.yml`, en cada `push` a `master` y en cada Pull Request:

| Paso | Qué hace |
|---|---|
| `actions/setup-java@v4` | JDK 21 Temurin, con caché de `~/.m2` |
| `./mvnw -B verify` | Compila, ejecuta las 160 pruebas y genera el informe de cobertura |
| `upload-artifact` | Sube los informes de Surefire **también cuando falla** |

Se ejecuta con `working-directory: facturacion360` porque el `pom.xml` no está en la raíz.

Las pruebas de integración levantan MySQL con Testcontainers y **necesitan Docker**. Los
ejecutores `ubuntu-latest` lo traen arrancado; si algún día se cambia de ejecutor, es lo primero
que hay que comprobar.

### Cifras de partida (2026-09-19)

Medidas sobre las 160 pruebas en verde. No son umbrales todavía:

| Medida | Valor |
|---|---|
| Cobertura de instrucciones | 56 % |
| Cobertura de ramas | 53 % |
| Clases analizadas | 44 |
| Avisos de SpotBugs | 19 (15 de ellos `EI_EXPOSE_REP`/`REP2`) |

```bash
./mvnw -B verify            # informe en target/site/jacoco/index.html
./mvnw -B spotbugs:check    # no rompe la compilacion
./mvnw -B spotless:check    # solo mira lo que la rama haya tocado
```

**Ningún plugin rompe la compilación todavía, a propósito.** Un umbral puesto el mismo día que
el plugin, sobre código que nunca lo ha tenido, o es tan bajo que no dice nada, o deja el
proyecto en rojo por algo que no se acaba de romper. Primero se mide. El umbral se pone cuando
haya una cifra que defender, y se sube desde ahí.

Spotless lleva `ratchetFrom origin/master`: sin él, la primera ejecución reformatearía los 58
ficheros del proyecto y destrozaría `git blame`.

## 6 · Lo que todavía NO está automatizado

| Qué | Por qué | Cuándo |
|---|---|---|
| `src/test/js/facturas-alta.spec.cjs` | Playwright; exige la aplicación arrancada y `FACTURAS_URL_PRUEBAS`. No hay `package.json` en el repositorio, así que hoy no lo ejecuta nadie | H10, que lo necesita como red antes de trocear `facturas.js` |
| Umbral de cobertura | Falta acordar la cifra | Cuando haya varias medidas seguidas |
| SpotBugs rompiendo la compilación | Hay 19 avisos que triar primero | H1 y H2 |
| Comprobación del mapa de capas del JavaScript | No existe herramienta en el proyecto | Sin fecha; hoy se mantiene a mano |

## 7 · Pull Request

Se describe qué cambia y **cómo probarlo**, y se asigna revisor. La rama se borra tras integrar:

```bash
git push origin --delete feature/<NombreMejora>
git branch -d feature/<NombreMejora>
```
