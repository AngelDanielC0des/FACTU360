package edu.xtd.facturacion360.dto;

import java.util.regex.Pattern;

import jakarta.validation.constraints.Size;

/**
 * Los criterios con los que se pide una página de clientes: qué página, de qué tamaño,
 * qué buscar, por qué filtrar y cómo ordenar.
 *
 * <p>Van juntos en un objeto y no como siete parámetros sueltos porque esa firma se
 * repetía en cinco sitios (controller, service y repository, con sus interfaces): añadir
 * un filtro obligaba a tocar los cinco.</p>
 *
 * <p>Toda la normalización vive en el constructor compacto, así que hay un único lugar
 * donde se decide qué es un valor válido, tanto si lo construye Spring desde la URL
 * (con {@code @ModelAttribute}, emparejando por nombre) como si se crea a mano.</p>
 *
 * @param pagina     índice de la página empezando en 0; se fuerza a no ser negativo
 * @param tamano     clientes por página; máximo {@value #TAMANO_MAX}, y
 *                   {@value #TAMANO_DEFECTO} si no llega
 * @param busqueda   texto a buscar en nombre y nif_cif (coincidencia parcial);
 *                   {@code null} = no buscar
 * @param provincia  filtro por provincia; {@code null} = no filtrar
 * @param poblacion  filtro por población; {@code null} = no filtrar
 * @param ordenarPor columna por la que ordenar: {@code nombre} o {@code fecha_alta}
 * @param direccion  sentido de la ordenación: {@code asc} o {@code desc}
 *
 * @author AngelDanielC0des
 * @see PaginaClienteResponse
 * @see edu.xtd.facturacion360.repository.ClienteRepository#findPagina(CriteriosCliente)
 */
public record CriteriosCliente(

		// Integer y no int, a propósito: si el parámetro no viene en la URL, Spring enlaza
		// null, y null no convierte a primitivo (con int, la aplicación respondía 400 hasta
		// a las peticiones correctas). Tras el constructor compacto nunca son null.
		Integer pagina,

		Integer tamano,

		// Las longitudes máximas son las de las columnas reales de la tabla: un término
		// más largo no puede coincidir con nada, así que se rechaza antes de ir a la BD.
		//
		// El {max} del mensaje NO es una plantilla nuestra: Bean Validation sustituye entre
		// llaves los atributos de la propia anotación (es como están escritos sus mensajes de
		// serie: "size must be between {min} and {max}"). Así la constante es el único sitio
		// donde vive el número; escribiéndolo a mano, cambiar la constante dejaba el mensaje
		// diciendo otra cosa y nada en la línea lo delataba.
		@Size(max = LONGITUD_MAX_BUSQUEDA, message = "La búsqueda no puede superar los {max} caracteres")
		String busqueda,

		@Size(max = LONGITUD_MAX_PROVINCIA, message = "La provincia no puede superar los {max} caracteres")
		String provincia,

		@Size(max = LONGITUD_MAX_POBLACION, message = "La población no puede superar los {max} caracteres")
		String poblacion,

		String ordenarPor,

		String direccion) {

	/**
	 * Clientes por página si no se pide otra cosa. Es el mismo valor que la constante
	 * {@code TAMANO_PAGINA} de {@code clientes.js}, que es quien lo manda de verdad desde la
	 * pantalla; no hay forma de compartirlo entre Java y el JavaScript, así que está anotado
	 * en los dos sitios.
	 */
	public static final int TAMANO_DEFECTO = 10;

	/** Tope de clientes por página, para que nadie pida la tabla entera de una vez. */
	public static final int TAMANO_MAX = 100;

	/** Longitud de la columna {@code nombre} en la tabla clientes. */
	public static final int LONGITUD_MAX_BUSQUEDA = 60;

	/** Longitud de la columna {@code provincia} en la tabla clientes. */
	public static final int LONGITUD_MAX_PROVINCIA = 15;

	/** Longitud de la columna {@code poblacion} en la tabla clientes. */
	public static final int LONGITUD_MAX_POBLACION = 30;

	/** Columna por la que se ordena si no se pide otra cosa. */
	public static final String ORDEN_DEFECTO = "fecha_alta";

	/** Sentido de ordenación por defecto: lo más reciente primero. */
	public static final String DIRECCION_DEFECTO = "desc";

	/**
	 * Saltos de línea, uno o varios seguidos. Los neutraliza {@link #normalizar(String)} para
	 * que un criterio no pueda partir una línea del log en dos.
	 *
	 * <p>Se compila <strong>una sola vez</strong> y no en cada llamada: {@code String.replaceAll}
	 * hace un {@code Pattern.compile} completo cada vez que se invoca, y {@code normalizar} se
	 * llama cinco veces por cada {@code CriteriosCliente}, o sea cinco por petición del listado,
	 * que es la pantalla más usada.</p>
	 */
	private static final Pattern SALTOS_DE_LINEA = Pattern.compile("[\\r\\n]+");

	/**
	 * Constructor compacto: deja todos los componentes ya normalizados, de modo que
	 * quien reciba un {@code CriteriosCliente} no tiene que volver a comprobar nada.
	 */
	public CriteriosCliente {
		// null (no lo han mandado) o fuera de rango: se usa el valor por defecto.
		pagina = (pagina == null || pagina < 0) ? 0 : pagina;
		tamano = (tamano == null || tamano <= 0) ? TAMANO_DEFECTO : Math.min(TAMANO_MAX, tamano);

		busqueda = normalizar(busqueda);
		provincia = normalizar(provincia);
		poblacion = normalizar(poblacion);

		// Estos dos nunca quedan a null: siempre se ordena de alguna manera. Se guarda lo
		// que devuelve normalizar en vez de llamarlo y volver a hacer trim() sobre el
		// original, que era recorrer dos veces la misma cadena para obtener lo mismo.
		String ordenNormalizado = normalizar(ordenarPor);
		String direccionNormalizada = normalizar(direccion);

		ordenarPor = (ordenNormalizado == null) ? ORDEN_DEFECTO : ordenNormalizado;
		direccion = (direccionNormalizada == null) ? DIRECCION_DEFECTO : direccionNormalizada;
	}

	/**
	 * Cuántas filas hay que saltar para llegar a esta página (el {@code OFFSET} del SQL).
	 *
	 * <p>El {@code (long)} va en el primer operando a propósito: multiplicar en {@code int}
	 * y ampliar después haría que una página muy alta desbordase, dando un offset negativo
	 * que MySQL rechaza.</p>
	 *
	 * @return el número de filas a saltar; nunca negativo
	 */
	public long offset() {
		// Variable y un solo return, como el resto del proyecto: un offset mal calculado es de
		// las cosas que solo se ven mirándolo, porque la consulta no falla, simplemente
		// devuelve la página equivocada.
		long desplazamiento = (long) pagina.intValue() * tamano.intValue();

		return desplazamiento;
	}

	/**
	 * Deja en {@code null} lo que no aporta filtro y quita los espacios sobrantes del resto,
	 * para que el repositorio maneje un único convenio ("null = sin filtro") en vez de
	 * distinguir entre {@code null}, {@code ""} y {@code "   "}.
	 *
	 * <p>Los saltos de línea se cambian por un espacio. No es cosmética: el controller
	 * escribe estos criterios en el log ({@code log.info("... -> {}", criterios)}), así que un
	 * término de búsqueda que llevara un {@code \n} dentro partiría la traza en dos y dejaría
	 * la segunda mitad con el aspecto de una línea escrita por la propia aplicación. Es el
	 * ataque conocido como <em>log forging</em>. Para buscar en la base de datos un salto de
	 * línea no aporta nada, así que se neutraliza aquí, en el único sitio donde se decide qué
	 * es un criterio válido.</p>
	 *
	 * @param valor el texto tal cual llega de la URL
	 * @return el texto sin espacios alrededor ni saltos de línea, o {@code null} si no había
	 *         contenido
	 */
	private static String normalizar(String valor) {
		String valorNormalizado = (valor == null || valor.isBlank())
				? null
				: SALTOS_DE_LINEA.matcher(valor.trim()).replaceAll(" ");

		return valorNormalizado;
	}
}
