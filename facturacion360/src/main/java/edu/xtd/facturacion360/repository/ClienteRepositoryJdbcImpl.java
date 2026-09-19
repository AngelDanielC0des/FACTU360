package edu.xtd.facturacion360.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.Cliente;
import edu.xtd.facturacion360.dto.CriteriosCliente;

/**
 * Implementación de {@link ClienteRepository} sobre MySQL con {@code JdbcTemplate}.
 *
 * <p>Es la única capa que habla SQL: traduce cada operación del contrato a una consulta y
 * convierte las filas en objetos {@link Cliente} mediante {@link ClienteRowMapper}. Todos los
 * valores viajan como parámetros ({@code ?}) para evitar la inyección SQL; lo que no admite
 * parámetros, como el {@code ORDER BY}, se resuelve con listas blancas.</p>
 *
 * <p>El detalle de cada método está documentado en la interfaz.</p>
 *
 * @see ClienteRepository
 */
@Repository
public class ClienteRepositoryJdbcImpl implements ClienteRepository {

	private static final Logger log = LoggerFactory.getLogger(ClienteRepositoryJdbcImpl.class);

	// con este objeto, accedemos a base de datos (ejecuta el SQL contra MySQL)
	@Autowired
	JdbcTemplate jdbcTemplate;


	// convierte cada fila del ResultSet en un objeto Cliente (lo usa jdbcTemplate.query)



	private static final String INSERTAR_CLIENTE = """
			INSERT INTO clientes (nombre, nif_cif, direccion, codigopostal, poblacion, provincia, telefono, email, fecha_alta)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
			""";

	/**
	 * Columnas que seleccionan TODAS las consultas de clientes de esta clase. Está en una
	 * constante para que el detalle y el listado no puedan acabar devolviendo campos
	 * distintos: si mañana se añade una columna, se añade una sola vez y la traen las tres.
	 */
	private static final String COLUMNAS_CLIENTE = "idcliente, nombre, nif_cif, direccion, codigopostal, "
			+ "poblacion, provincia, telefono, email, fecha_alta";

	/**
	 * <strong>Lista blanca</strong> de ordenaciones permitidas. La CLAVE es lo que puede
	 * mandar el cliente por la URL; el VALOR es el nombre real de la columna, escrito por
	 * nosotros.
	 *
	 * <p>Como el {@code ORDER BY} no admite '?', esta traducción es lo que impide que el
	 * texto del usuario llegue nunca al SQL: si la clave no está en el mapa, se usa
	 * {@link #COLUMNA_ORDEN_DEFECTO}. Es la defensa contra la inyección SQL en el único
	 * punto de la consulta donde no puede haber un parámetro.</p>
	 *
	 * <p>Para permitir ordenar por una columna más basta con añadir aquí una línea:
	 * funciona automáticamente en ambos sentidos (ver {@link #sqlOrden(String, String)}).</p>
	 */
	private static final Map<String, String> COLUMNAS_ORDEN = Map.of(
			"nombre", "nombre",
			"fecha_alta", "fecha_alta");

	/**
	 * Columna por la que se ordena si no piden otra cosa, o si piden una que no está en
	 * {@link #COLUMNAS_ORDEN}. Lo más reciente primero es lo que espera ver quien abre la
	 * pantalla.
	 */
	private static final String COLUMNA_ORDEN_DEFECTO = "fecha_alta";

	/**
	 * Carácter con el que se neutralizan los comodines de LIKE.
	 *
	 * <p>No es la barra invertida <strong>a propósito</strong>, y ese "a propósito" arregla
	 * un fallo real: {@link #escaparComodines(String)} lo explica entero.</p>
	 */
	private static final String ESCAPE_LIKE = "!";

	/**
	 * El fragmento de SQL que declara ese carácter, montado con la constante.
	 *
	 * <p>Se compone aquí y no se escribe a mano en la consulta porque si no el {@code !}
	 * acaba en dos sitios y solo uno de ellos es la constante. Cambiarla —que es justo lo
	 * que invita a hacer el javadoc de arriba, que dedica un párrafo a por qué ese carácter
	 * concreto— dejaría {@link #escaparComodines(String)} generando {@code #%} mientras la
	 * consulta sigue diciendo {@code ESCAPE '!'}: el escape pasaría a ser un carácter
	 * literal, el {@code %} volvería a ser comodín, y buscar «50%» devolvería la tabla
	 * entera. Sin error y sin excepción: el buscador simplemente dejaría de filtrar.</p>
	 *
	 * <p>No abre la puerta a inyección: {@code ESCAPE_LIKE} es un literal de esta clase y
	 * por aquí no pasa nada que venga del usuario.</p>
	 */
	private static final String LIKE_CON_ESCAPE = " LIKE ? ESCAPE '" + ESCAPE_LIKE + "'";

	@Autowired
	ClienteRowMapper clienteRowMapper;


	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<Cliente> findUltimos(int limite) {
		// ORDER BY idcliente DESC: al ser autoincremental, el id más alto es el alta más
		// reciente, así que no hace falta mirar la fecha. El troceado con LIMIT lo hace MySQL,
		// no Java, de modo que es eficiente aunque la tabla tenga miles de filas.
		// El '?' es un parámetro: JdbcTemplate usa un PreparedStatement, así que el valor viaja
		// aparte del texto SQL y nunca se interpreta como código (sin inyección SQL).
		String sql = "SELECT " + COLUMNAS_CLIENTE + " FROM clientes ORDER BY idcliente DESC LIMIT ?";

		// query(sql, rowMapper, args...) es la sobrecarga para SELECT de varias filas: sustituye
		// los '?', aplica el rowMapper a cada fila y devuelve la lista (vacía si no hay, nunca null).
		List<Cliente> clientes = jdbcTemplate.query(sql, clienteRowMapper, limite);
		log.debug("findUltimos({}) -> {} filas", limite, clientes.size());
		return clientes;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<Cliente> findPagina(CriteriosCliente criterios) {
		// Igual que findUltimos pero con dos '?': LIMIT (cuántas filas) y OFFSET (cuántas
		// saltar). Se sustituyen en orden -> primero 'tamano', luego 'offset'. Así traemos solo
		// la página pedida, no todos los clientes.
		// El WHERE se construye solo con los criterios que llegan informados (ver anadirFiltros).
		StringBuilder sql = new StringBuilder("SELECT " + COLUMNAS_CLIENTE + " FROM clientes");
		List<Object> args = new ArrayList<>();
		anadirFiltros(sql, args, criterios);

		// El ORDER BY NO admite '?': el criterio se traduce con la lista blanca sqlOrden().
		sql.append(sqlOrden(criterios.ordenarPor(), criterios.direccion())).append(" LIMIT ? OFFSET ?");
		args.add(criterios.tamano());
		args.add(criterios.offset());

		List<Cliente> clientes = jdbcTemplate.query(sql.toString(), clienteRowMapper, args.toArray());
		log.debug("findPagina({}) -> {} filas", criterios, clientes.size());
		return clientes;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public long contarTotal(CriteriosCliente criterios) {
		// queryForObject: para un SELECT que devuelve UN SOLO valor (aquí el nº total de filas).
		// Le decimos el tipo esperado (Long.class) para que lo convierta por nosotros.
		// Lleva los MISMOS criterios que findPagina (misma llamada a anadirFiltros): el total
		// debe cuadrar con lo que se ve, o el nº de páginas mentiría. Aquí no se ponen LIMIT
		// ni ORDER BY: no cambian CUÁNTOS hay, solo cuáles se ven y en qué orden.
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM clientes");
		List<Object> args = new ArrayList<>();
		anadirFiltros(sql, args, criterios);

		Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
		long totalSeguro = total != null ? total : 0L;

		log.debug("contarTotal({}) -> {} clientes", criterios, totalSeguro);
		return totalSeguro;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<String> findProvincias() {
		// DISTINCT: una sola fila por provincia aunque haya mil clientes en ella. Se
		// descartan nulls y cadenas vacías para no ensuciar el desplegable.
		String sql = "SELECT DISTINCT provincia FROM clientes "
				+ "WHERE provincia IS NOT NULL AND provincia <> '' ORDER BY provincia";
		List<String> provincias = jdbcTemplate.queryForList(sql, String.class);
		log.debug("findProvincias() -> {} provincias", provincias.size());
		return provincias;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<String> findPoblaciones(String provincia) {
		// Si llega provincia, solo las poblaciones de esa provincia (para el desplegable
		// en cascada); si llega vacía, todas las poblaciones.
		StringBuilder sql = new StringBuilder("SELECT DISTINCT poblacion FROM clientes "
				+ "WHERE poblacion IS NOT NULL AND poblacion <> ''");
		List<Object> args = new ArrayList<>();
		if (provincia != null && !provincia.isBlank()) {
			sql.append(" AND provincia = ?");
			args.add(provincia);
		}
		sql.append(" ORDER BY poblacion");

		List<String> poblaciones = jdbcTemplate.queryForList(sql.toString(), String.class, args.toArray());
		log.debug("findPoblaciones(provincia={}) -> {} poblaciones", provincia, poblaciones.size());
		return poblaciones;
	}

	/**
	 * Construye el WHERE con los criterios que lleguen informados (null o vacío = sin
	 * filtrar). Lo usan TANTO findPagina COMO contarTotal, y por eso está aquí fuera: si
	 * cada uno montara su propio WHERE, el total y las filas mostradas podrían dejar de
	 * cuadrar en cuanto alguien tocara uno y se olvidara del otro.
	 *
	 * <p>Cada valor se añade como '?' a {@code args}, en orden, para que viaje aparte del
	 * texto SQL (PreparedStatement): así el dato NUNCA se interpreta como instrucción y
	 * no hay inyección SQL.</p>
	 *
	 * @param sql       consulta en construcción; se le concatenan las condiciones
	 * @param args      valores de los '?', en el mismo orden en que aparecen
	 * @param criterios de aquí se usan la búsqueda, la provincia y la población; la
	 *                  paginación y la ordenación no forman parte del WHERE
	 * @autor AngelDanielC0des
	 * @see #escaparComodines(String)
	 */
	private void anadirFiltros(StringBuilder sql, List<Object> args, CriteriosCliente criterios) {
		// Vamos juntando condiciones y al final las unimos con AND. Así no hace falta ir
		// preguntando "¿soy la primera condición, pongo WHERE o AND?" en cada if.
		List<String> condiciones = new ArrayList<>();

		String busqueda = criterios.busqueda();
		String provincia = criterios.provincia();
		String poblacion = criterios.poblacion();

		if (busqueda != null && !busqueda.isBlank()) {
			// Coincidencia parcial en AMBOS campos: los comodines %...% envuelven al término.
			// Los paréntesis alrededor del OR son OBLIGATORIOS: sin ellos, los AND de los
			// filtros solo se aplicarían al nif_cif (AND tiene prioridad sobre OR).
			// Sin LOWER(): la tabla es utf8mb4_0900_ai_ci, que YA compara ignorando
			// mayúsculas y acentos ('garcia' encuentra 'García'). Envolver la columna en
			// LOWER() no cambiaría el resultado y además impediría usar índices.
			condiciones.add("(nombre" + LIKE_CON_ESCAPE + " OR nif_cif" + LIKE_CON_ESCAPE + ")");
			String patron = "%" + escaparComodines(busqueda) + "%";
			args.add(patron);
			args.add(patron);
		}
		if (provincia != null && !provincia.isBlank()) {
			condiciones.add("provincia = ?");
			args.add(provincia);
		}
		if (poblacion != null && !poblacion.isBlank()) {
			condiciones.add("poblacion = ?");
			args.add(poblacion);
		}

		if (!condiciones.isEmpty()) {
			sql.append(" WHERE ").append(String.join(" AND ", condiciones));
		}
	}

	/**
	 * Neutraliza los comodines de LIKE dentro del texto que escribe el usuario.
	 *
	 * <p>En LIKE, '%' significa "cualquier cosa" y '_' "un carácter cualquiera". Si el
	 * término llega sin tratar, buscar "%" genera el patrón '%%%', que casa con TODAS
	 * las filas: el buscador dejaría de filtrar y devolvería la tabla entera. No es
	 * inyección SQL (el valor sigue viajando como '?'), pero sí un modo de saltarse
	 * el filtro, así que los escapamos para que se busquen como caracteres normales.</p>
	 *
	 * <p><strong>Por qué el carácter de escape es '!' y no la barra invertida</strong>, que es
	 * lo que se ve en la mayoría de los ejemplos. Por dos motivos, y el segundo es un fallo
	 * real:</p>
	 *
	 * <ol>
	 * <li>La barra invertida tiene significado <em>dentro de las cadenas</em> de MySQL, así que
	 * escribir {@code ESCAPE '\'} no compila: hay que poner dos barras, y como además hay que
	 * escaparlas para el {@code String} de Java, en el código se leían cuatro. Era el sitio de
	 * este repositorio donde más fácil resultaba equivocarse al tocar la consulta.</li>
	 * <li>Con el {@code sql_mode} <b>NO_BACKSLASH_ESCAPES</b> activado, MySQL deja de
	 * interpretar {@code '\\'} como una barra y rechaza la sentencia entera con
	 * <em>"Incorrect arguments to ESCAPE"</em>: el listado, que es la pantalla principal,
	 * respondía 500 según cómo estuviera configurado el servidor. Ese modo no es el de por
	 * defecto, pero lo activa {@code mysqldump} y hay instalaciones que lo dejan puesto.</li>
	 * </ol>
	 *
	 * <p>El '!' no significa nada en ninguno de los dos modos, así que la consulta se lee igual
	 * en el código y en la base de datos. No hace falta que el carácter sea "raro": solo que
	 * los pocos términos que lo contengan se escapen, y de eso se encarga la primera
	 * sustitución.</p>
	 *
	 * <p>El orden importa: el propio carácter de escape va PRIMERO. Si se sustituyera el
	 * último, volvería a escapar los que acabamos de introducir para '%' y '_'.</p>
	 *
	 * @param termino texto tal cual lo escribió el usuario
	 * @return el mismo texto con '!', '%' y '_' escapados; se usa junto a ESCAPE '!'
	 * @autor AngelDanielC0des
	 * @see #anadirFiltros(StringBuilder, List, CriteriosCliente)
	 */
	private String escaparComodines(String termino) {
		// Variable y un solo return, como el resto del proyecto: aquí no hay log, pero es el
		// punto donde uno quiere poner un breakpoint si el buscador deja de filtrar.
		String terminoEscapado = termino.replace(ESCAPE_LIKE, ESCAPE_LIKE + ESCAPE_LIKE)
				.replace("%", ESCAPE_LIKE + "%")
				.replace("_", ESCAPE_LIKE + "_");

		return terminoEscapado;
	}

	/**
	 * Lista blanca de ordenaciones: traduce lo que pide el cliente a un fragmento
	 * ORDER BY escrito por nosotros. Es imprescindible porque el ORDER BY no admite
	 * '?': sin esta traducción habría que concatenar el texto del cliente en el SQL y
	 * eso sí abriría la puerta a la INYECCIÓN SQL.
	 *
	 * <p>Fíjate en que el texto del usuario nunca se concatena: solo se usa como CLAVE
	 * para buscar en el mapa. Si pide una columna que no está en la lista, se ignora y
	 * cae en el valor por defecto. La dirección se resuelve con una comparación, así
	 * que solo puede acabar valiendo "ASC" o "DESC".</p>
	 *
	 * @param ordenarPor columna pedida; ver {@link #COLUMNAS_ORDEN}
	 * @param direccion  "asc" o "desc"; cualquier otra cosa se trata como "desc"
	 * @return el fragmento " ORDER BY ..." correspondiente, nunca {@code null}
	 * @autor AngelDanielC0des
	 * @see #findPagina(CriteriosCliente)
	 */
	private String sqlOrden(String ordenarPor, String direccion) {
		// El null se filtra ANTES de consultar el mapa: Map.of() crea un mapa inmutable
		// que no admite claves nulas y lanzaría NullPointerException en vez de devolver
		// el valor por defecto. Por la URL no debería llegar nunca (el @RequestParam
		// tiene defaultValue), pero el repositorio no puede depender de eso.
		String columna = ordenarPor == null
				? COLUMNA_ORDEN_DEFECTO
				: COLUMNAS_ORDEN.getOrDefault(ordenarPor, COLUMNA_ORDEN_DEFECTO);
		String sentido = "asc".equalsIgnoreCase(direccion) ? "ASC" : "DESC";

		// 'columna IS NULL' va primero para que las filas sin valor (fecha_alta admite
		// NULL) queden SIEMPRE al final, se ordene ascendente o descendentemente. Si no,
		// MySQL las pondría al principio en ASC y al final en DESC, y al invertir el
		// orden saltarían de un extremo al otro de la lista.
		// 'idcliente' es el desempate: sin él, dos clientes con la misma fecha (o el mismo
		// nombre) podrían intercambiarse entre consultas y, al paginar, verse repetidos en
		// una página y desaparecer de la siguiente.
		//
		// El fragmento se guarda en una variable antes de devolverlo, como el resto del
		// proyecto. Aquí importa más que en ningún otro sitio: este método ES la defensa
		// contra la inyección SQL, así que es justo donde uno quiere poder mirar en el
		// depurador qué se ha montado de verdad con lo que llegó por la URL.
		String fragmentoOrden = " ORDER BY " + columna + " IS NULL, " + columna + " " + sentido
				+ ", idcliente " + sentido;

		return fragmentoOrden;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public Optional<Cliente> findById(int id) {
		// Mismas columnas que el resto de consultas (COLUMNAS_CLIENTE) para que el detalle
		// y el listado no puedan acabar devolviendo campos distintos.
		String sql = "SELECT " + COLUMNAS_CLIENTE + " FROM clientes WHERE idcliente = ?";

		// query(...).findFirst() en vez de queryForObject(): este último lanza
		// EmptyResultDataAccessException cuando no hay fila, y "ese cliente no existe" es una
		// respuesta prevista (un 404), no un fallo de acceso a datos. Devolviendo un Optional,
		// quien llama lo resuelve con un if en vez de con un try/catch.
		Optional<Cliente> cliente = jdbcTemplate.query(sql, clienteRowMapper, id).stream().findFirst();

		log.debug("findById({}) -> {}", id, cliente.isPresent() ? "encontrado" : "no existe");
		return cliente;
	}

	
	/**
	 * Inserta un cliente en base de datos.
	 * @throws SQLException 
	 */
	@Override
	public Cliente insert(Cliente cliente)  {
		
		Cliente clienteInsertado = null;

	    KeyHolder keyHolder = new GeneratedKeyHolder();
	    LocalDate fechaAlta = LocalDate.now();

	    int numFilasAfectadas;

	    try {
	    numFilasAfectadas = jdbcTemplate.update(connection -> {

	       PreparedStatement ps = connection.prepareStatement(
	                INSERTAR_CLIENTE,
	                Statement.RETURN_GENERATED_KEYS
	        );

	        ps.setString(1, cliente.nombre());
	        ps.setString(2, cliente.nifCif());
	        ps.setString(3, cliente.direccion());
	        ps.setString(4, cliente.codigoPostal());
	        ps.setString(5, cliente.poblacion());
	        ps.setString(6, cliente.provincia());
	        ps.setString(7, cliente.telefono());
	        ps.setString(8, cliente.email());
	        ps.setDate(9, Date.valueOf(fechaAlta));

	        return ps;

	    }, keyHolder);

	    } catch (DuplicateKeyException error) {
	        // Se traduce aqui, que es donde se sabe que indice es cual. Mas arriba solo
	        // llegaria un DuplicateKeyException identico al del numero de factura.
	        if (RestriccionSql.duplicado(error, "nif_cif_UNIQUE")) {
	            throw new NifCifDuplicadoException(error);
	        }

	        // Si no es la que conocemos, sube tal cual: tragarse lo que no se entiende es
	        // peor que no haberlo mirado.
	        throw error;
	    }

	    
	    if (numFilasAfectadas == 1) 
	    {
	    		
	    	int idCliente = keyHolder.getKey().intValue();
		    clienteInsertado = new Cliente(
		            idCliente,
		            cliente.nombre(),
		            cliente.nifCif(),
		            cliente.direccion(),
		            cliente.codigoPostal(),
		            cliente.poblacion(),
		            cliente.provincia(),
		            cliente.telefono(),
		            cliente.email(),
		            fechaAlta
		    );
	    }

	    
	    return clienteInsertado;
	}

	@Override
	public boolean update(Cliente cliente) {

	    // Sentencia SQL que actualiza los datos de un cliente.
	    // Solo se modifican los campos editables; la fecha de alta se mantiene.
	    String sql = """
	        UPDATE clientes
	        SET nombre = ?,
	            nif_cif = ?,
	            direccion = ?,
	            codigopostal = ?,
	            poblacion = ?,
	            provincia = ?,
	            telefono = ?,
	            email = ?
	        WHERE idcliente = ?
	        """;

	    // Ejecutamos la sentencia SQL utilizando JdbcTemplate.
	    // Cada '?' de la consulta se sustituye por el valor correspondiente
	    // del objeto Cliente.
	    int filas;

	    try {
	        filas = jdbcTemplate.update(
	            sql,
	            cliente.nombre(),
	            cliente.nifCif(),
	            cliente.direccion(),
	            cliente.codigoPostal(),
	            cliente.poblacion(),
	            cliente.provincia(),
	            cliente.telefono(),
	            cliente.email(),
	            cliente.idCliente()
	        );
	    } catch (DuplicateKeyException error) {
	        if (RestriccionSql.duplicado(error, "nif_cif_UNIQUE")) {
	            throw new NifCifDuplicadoException(error);
	        }

	        throw error;
	    }

	    // Si se ha modificado al menos una fila, devolvemos true.
	    // Si no se ha modificado ninguna, devolvemos false.
	    return filas > 0;
	}
	/**
	 * Ejecuta la sentencia SQL para borrar un cliente de la base de datos según su ID.
	 *
	 * @param id El identificador del cliente a eliminar.
	 * @return true si se eliminó exactamente una fila (borrado exitoso),
	 *         false si no se eliminó ninguna fila (el cliente no existía).
	 */
	@Override
	public boolean deleteById(int id) {
		boolean borrarOk = false;
		String instruccionBorrar = "DELETE FROM clientes where idcliente = ?;";

		int filasborradas;

		try {
			filasborradas = jdbcTemplate.update(instruccionBorrar, id);
		} catch (DataIntegrityViolationException error) {
			// El manejador global sabe que hay datos relacionados, pero no que son facturas.
			// Eso lo sabe quien escribio la clave ajena, o sea este fichero.
			if (RestriccionSql.padreConHijos(error, "FK_CLIENTE")) {
				throw new ClienteConFacturasException(error);
			}

			throw error;
		}
		if (filasborradas == 1) {
			borrarOk = true;
		} 
		
		return borrarOk;
	}
}
