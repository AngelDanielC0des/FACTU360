# 📄 FACTURACION360

Sistema de gestión de clientes y facturación desarrollado con **Java 21**, **Spring Boot**, **Spring JDBC**, **MySQL** y tecnologías web estándar (**HTML, CSS y JavaScript**).

El objetivo del proyecto es proporcionar una aplicación sencilla, escalable y mantenible para la administración de clientes y facturas siguiendo una arquitectura profesional basada en capas.

---

# 🚀 Tecnologías utilizadas

### Backend

- Java 21
- Spring Boot 4.1.0
- Spring MVC
- Spring JDBC (`JdbcTemplate`)
- Bean Validation
- springdoc-openapi (Swagger UI)
- SLF4J + Logback
- Maven

### Base de datos

- MySQL 8

### Frontend

- HTML5
- CSS3
- Bootstrap 5.3
- JavaScript (Fetch API)

---

# 📂 Arquitectura

El proyecto sigue una arquitectura por capas:

```
Navegador ──GET /cliente/listar-pagina──►  Controller ──► Service ──► Repository ──► MySQL
Navegador ◄──── JSON ── ClienteResponse ◄──(Mapper)── Cliente ◄──(RowMapper)── fila
```

Cada capa tiene una única responsabilidad:

| Capa | Ficheros | Responsabilidad |
|------|----------|-----------------|
| **Controller** | `controller/ClienteController` | Recibe la petición HTTP, valida la entrada y traduce el resultado a códigos de estado REST. |
| **Service** | `service/ClienteService` + `ClienteServiceImpl` | La lógica de negocio: metadatos de paginación, reglas de dominio y coordinación de repositorios. |
| **Repository** | `repository/ClienteRepository` + `ClienteRepositoryJdbcImpl` | La única capa que habla SQL, con `JdbcTemplate`. |
| **RowMapper** | `repository/ClienteRowMapper` | Convierte cada fila del `ResultSet` en un objeto `Cliente`. |
| **DTO** | `dto/ClienteRequest` · `dto/ClienteResponse` · `dto/Cliente` | Entrada, salida y dominio van separados, de modo que el modelo interno puede cambiar sin romper el contrato con el frontend. |
| **Mapper** | `dto/ClienteMapper` | Traduce entre los DTO y el dominio. |

---

# 📁 Estructura del proyecto

```
FACTURACION360
 ├── LICENSE
 ├── README.md
 └── facturacion360              <- aquí está el pom.xml
      └── src
           ├── main
           │    ├── java/edu/xtd/facturacion360
           │    │    ├── controller
           │    │    ├── service
           │    │    ├── repository
           │    │    └── dto
           │    └── resources
           │         ├── static          <- HTML, CSS, JS e imágenes
           │         ├── docu            <- backup SQL, diagramas y protocolo de Git
           │         ├── application.properties
           │         └── logback-spring.xml
           └── test
```

---

# ⚙️ Instalación

## 1. Clonar el repositorio

```bash
git clone https://github.com/Valexx55/FACTURACION360.git
```

---

## 2. Crear la base de datos

En el repositorio está el volcado completo, que crea la base de datos `bd_facturacion` con las tablas `clientes`, `conceptos` y `facturas`:

```bash
mysql -u root -p < facturacion360/src/main/resources/docu/backupFacturacion360.sql
```

También puede importarse desde MySQL Workbench con *Server → Data Import*.

---

## 3. Configurar la conexión

Ajusta tus credenciales en `facturacion360/src/main/resources/application.properties`:

```properties
spring.application.name=facturacion360

spring.datasource.url=jdbc:mysql://localhost:3306/bd_facturacion
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000

logging.level.org.springframework.jdbc.core=DEBUG
```

---

## 4. Ejecutar la aplicación

Con el wrapper incluido en el repositorio, sin necesidad de tener Maven instalado:

```bash
cd FACTURACION360/facturacion360
./mvnw spring-boot:run
```

En Windows, `mvnw.cmd spring-boot:run`. También puede importarse la carpeta `facturacion360` en Eclipse o IntelliJ IDEA como proyecto Maven.

Una vez arrancada:

| | |
|---|---|
| Panel de clientes | http://localhost:8080/clientes.html |
| Documentación interactiva de la API | http://localhost:8080/swagger-ui.html |
| Esquema OpenAPI en JSON | http://localhost:8080/v3/api-docs |

---

# ✨ Funcionalidades

Actualmente el sistema incluye:

- Listado de clientes paginado
- Búsqueda por nombre y por NIF/CIF
- Filtros por provincia y por población, en cascada
- Ordenación por nombre y por fecha de alta, en ambos sentidos
- API REST documentada con OpenAPI / Swagger UI
- Persistencia con Spring JDBC sobre MySQL
- Interfaz web con Bootstrap

Próximamente:

- Alta de clientes desde la interfaz 
- Detalle y edición de clientes desde la interfaz
- Eliminación de clientes
- Facturas
---

# 📡 API REST

Todos los endpoints de clientes cuelgan de `/cliente`

| Método | Endpoint | Descripción |
|---------|----------|-------------|
| GET | `/cliente/listar-pagina` | Página de clientes. Admite `?pagina=`, `?tamano=`, `?busqueda=`, `?provincia=`, `?poblacion=`, `?ordenarPor=`, `?direccion=` |
| GET | `/cliente/listar-ultimos` | Los últimos clientes dados de alta. Admite `?limite=` (1–100, por defecto 10) |
| GET | `/cliente/provincias` | Provincias distintas, para el desplegable de filtro |
| GET | `/cliente/poblaciones` | Poblaciones distintas. Admite `?provincia=` para el filtro en cascada |
| GET | `/cliente/{id}` | Un cliente por su identificador |
| POST | `/cliente` | Crear cliente |
| PUT | `/cliente/{id}` | Actualizar cliente |
| DELETE | `/cliente/{id}` | Eliminar cliente |

Buscar, filtrar y ordenar comparten endpoint con el listado en lugar de tener uno propio: buscar es «listar con un filtro de texto más», así que de este modo hereda la paginación, los metadatos y el manejo de errores, y el frontend usa un único camino de código haya término escrito o no.

---

# 🛠 Buenas prácticas y convenios de código

**Diseño**

- Arquitectura en capas con separación estricta de responsabilidades.
- DTO de entrada y de salida separados del modelo de dominio.
- Cada capa se programa contra una interfaz (`ClienteService`, `ClienteRepository`).

**Seguridad**

- Consultas parametrizadas (`PreparedStatement`) en todo el acceso a datos.
- Lista blanca de columnas para el `ORDER BY`, que no admite parámetros.
- Los comodines de `LIKE` se escapan para que no se pueda saltar el filtro de búsqueda.
- Validación de entrada con Bean Validation (`@Valid`).
- El frontend pinta los datos con `textContent`, nunca interpolando en `innerHTML`.

**Estilo**

- Nomenclatura en español, coherente con el dominio.
- Regla de **variable + un solo `return`**: el resultado se guarda en una variable, se registra en el log y se devuelve al final. Así siempre hay un punto donde loguear lo que se devuelve.
- Trazas con SLF4J parametrizado (`log.info("... {}", valor)`).
- Nivel `DEBUG` para el paquete `edu.xtd` y `INFO` para el resto, con salida a consola y a fichero (`logback-spring.xml`).

**Documentación**

- El contrato se documenta en la interfaz y las implementaciones lo heredan con `{@inheritDoc}`.
- El `maven-javadoc-plugin` declara la etiqueta personalizada **`@autor`** (con `placement` de tipos y métodos), que permite atribuir métodos concretos dentro de clases compartidas: el `@author` estándar solo admite clases e interfaces.

```bash
./mvnw javadoc:javadoc     # genera la documentación en target/reports/apidocs
```

---

# 🔀 Flujo de trabajo

Trabajamos con **Feature Branch**: cada mejora vive en su propia rama y se integra en `master` mediante Pull Request. El protocolo completo está en `docu/protocoloGit.txt`.

```bash
git checkout master
git pull --ff-only origin master
git checkout -b feature/<nombreMejora>
# ... trabajo, y demo antes de subir ...
git push -u origin feature/<nombreMejora>
```

En GitHub se abre la Pull Request describiendo los cambios y cómo probarlos, y se asigna a **Val** como revisor. Nunca se hace *commit* directamente sobre `master`.



# 📸 Capturas

<img width="1565" height="883" alt="image" src="https://github.com/user-attachments/assets/c840e5e0-9b52-440c-b7cd-1be25552417d" />

<hr>

<img width="1565" height="959" alt="image" src="https://github.com/user-attachments/assets/a656b2fe-6fe5-42ea-9ac4-7fe93e00dc01" />

---


# 👨‍💻 Autores

**Val, Jaime, Gonzalo, Manu, Sergio, Fran y Ángel**

GitHub:

https://github.com/Valexx55
