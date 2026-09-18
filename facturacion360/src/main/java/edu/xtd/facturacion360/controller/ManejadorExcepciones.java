package edu.xtd.facturacion360.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import edu.xtd.facturacion360.repository.ClienteRepository;
import edu.xtd.facturacion360.repository.FacturaRepository;

/**
 * El único sitio donde se decide qué responde la API cuando algo sale mal.
 *
 * Extiende {@link ResponseEntityExceptionHandler} a propósito, y no es un detalle: esa clase
 * ya resuelve las excepciones que lanza el propio Spring —JSON malformado, método no
 * soportado, ruta que no existe, parámetro que falta— y las devuelve ya como
 * {@link ProblemDetail}. Escribirlas a mano habría sido repetir lo que el framework hace
 * mejor. Aquí abajo solo van las que Spring no puede conocer porque son de este proyecto.
 *
 * <p>El cuerpo de TODOS los errores es un {@code ProblemDetail} (RFC 9457): el mismo formato
 * que Spring Boot devuelve por defecto, el que springdoc documenta solo en Swagger, y el que
 * el frontend lee en su campo {@code detail}. Antes convivían cuatro formas distintas —texto
 * plano, un mapa, un ApiResponseDto y, en quince sitios, un cuerpo vacío—, así que quien
 * consumía la API tenía que adivinar cuál le tocaba en cada endpoint.</p>
 *
 * <p>Regla que conviene no romper: el cliente recibe un motivo en su idioma y NUNCA una
 * traza. La traza va al log, que es donde sirve para algo.</p>
 */
@RestControllerAdvice
public class ManejadorExcepciones extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ManejadorExcepciones.class);

	/**
	 * Arma el cuerpo de un error.
	 *
	 * @param estado  el código HTTP que se devuelve
	 * @param detalle el motivo, redactado para que lo lea una persona
	 * @return el cuerpo, listo para devolver
	 */
	private static ProblemDetail problema(HttpStatusCode estado, String detalle) {
		ProblemDetail cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);

		// resolve() y no valueOf(): valueOf lanza IllegalArgumentException con un codigo que
		// no sea de los estandar, y lanzar DESDE AQUI es lo peor que puede pasar, porque se
		// pierde el error original y el contenedor acaba respondiendo con su pagina por
		// defecto. resolve() devuelve null y nos quedamos sin titulo, que no es grave.
		HttpStatus conocido = HttpStatus.resolve(estado.value());

		if (conocido != null) {
			cuerpo.setTitle(conocido.getReasonPhrase());
		}

		return cuerpo;
	}

	/**
	 * El NIF/CIF de un cliente que ya está en la base de datos.
	 *
	 * <p>Va antes que el manejador genérico de duplicados a propósito: los dos responden 409,
	 * pero este puede decir QUÉ campo ha chocado. El repositorio ya ha mirado el nombre del
	 * índice para poder lanzarlo, así que aquí solo hay que dar su motivo.</p>
	 *
	 * @param excepcion la colisión, ya traducida por el repositorio
	 * @return 409 nombrando el campo
	 */
	@ExceptionHandler(ClienteRepository.NifCifDuplicadoException.class)
	public ProblemDetail gestionarNifDuplicado(ClienteRepository.NifCifDuplicadoException excepcion) {
		log.warn("NIF/CIF repetido al guardar un cliente");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * El cliente que no se puede borrar porque tiene facturas.
	 *
	 * @param excepcion el borrado bloqueado, ya traducido por el repositorio
	 * @return 409 diciendo que son facturas
	 */
	@ExceptionHandler(ClienteRepository.ClienteConFacturasException.class)
	public ProblemDetail gestionarClienteConFacturas(
			ClienteRepository.ClienteConFacturasException excepcion) {
		log.warn("Se ha intentado borrar un cliente que tiene facturas");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * Se factura a un cliente que ya no está.
	 *
	 * <p>409 y no 400, y conviene dejar escrito por qué. Se puede argumentar que el problema
	 * está en lo enviado —el identificador no corresponde a nada— y responder 400. Pero quien
	 * factura no escribió ese número: lo eligió de una lista donde estaba. Lo que ha pasado es
	 * que alguien borró el cliente por debajo, y eso es un <em>conflicto con el estado
	 * actual</em>, que es exactamente lo que significa un 409.</p>
	 *
	 * <p>Y hay una razón práctica que pesa más: este caso <strong>ya salía como 409</strong>
	 * antes, por el manejador genérico de integridad, y el formulario de facturas lo trata como
	 * tal («…o el cliente ya no estar disponible»). Lo que esta clase arregla es el mensaje, que
	 * decía que sobraban datos relacionados cuando lo que falta es el cliente. Cambiar además
	 * el código rompería a quien ya lo consume sin ganar nada.</p>
	 *
	 * @param excepcion la clave ajena huérfana, ya traducida por el repositorio
	 * @return 409 diciendo que el cliente ya no existe
	 */
	@ExceptionHandler(FacturaRepository.ClienteInexistenteException.class)
	public ProblemDetail gestionarClienteInexistente(
			FacturaRepository.ClienteInexistenteException excepcion) {
		log.warn("Se ha intentado facturar a un cliente que ya no existe");
		return problema(HttpStatus.CONFLICT, excepcion.getMessage());
	}

	/**
	 * Gestiona el error que se produce cuando intentamos guardar un dato que ya existe.
	 *
	 * @param excepcion excepción producida por tener un dato duplicado
	 * @return respuesta 409 indicando que el registro ya existe
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	public ProblemDetail gestionarDatoDuplicado(DuplicateKeyException excepcion) {
		log.error("Se ha intentado guardar un dato que ya existe", excepcion);
		return problema(HttpStatus.CONFLICT, "Ya existe un registro con ese dato");
	}

	/**
	 * Gestiona el error que se produce cuando no se puede borrar un registro porque tiene
	 * otros datos relacionados.
	 *
	 * @param excepcion excepción producida por la relación entre los datos
	 * @return respuesta 409 indicando que no se puede realizar la operación
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail gestionarIntegridadDatos(DataIntegrityViolationException excepcion) {
		log.error("La operación incumple una relación de la base de datos", excepcion);
		return problema(HttpStatus.CONFLICT,
				"No se puede realizar la operación porque hay datos relacionados");
	}

	/**
	 * Gestiona los errores generales al consultar o modificar la base de datos.
	 *
	 * @param excepcion excepción producida al acceder a la base de datos
	 * @return respuesta 500 indicando que existe un error de base de datos
	 */
	@ExceptionHandler(DataAccessException.class)
	public ProblemDetail gestionarBaseDatos(DataAccessException excepcion) {
		log.error("Error al acceder a la base de datos", excepcion);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error al acceder a la base de datos");
	}

	/**
	 * Gestiona los errores que se pueden producir al completar una transacción.
	 *
	 * @param excepcion excepción producida durante la transacción
	 * @return respuesta 500 indicando que no se pudo completar la operación
	 */
	@ExceptionHandler(TransactionException.class)
	public ProblemDetail gestionarTransaccion(TransactionException excepcion) {
		log.error("Error al completar la operación en la base de datos", excepcion);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "No se ha podido completar la operación");
	}

	/**
	 * Gestiona las excepciones que ya traen dentro su código de estado, que es como los
	 * servicios de este proyecto cuentan un 404 o un 409 de negocio.
	 *
	 * @param excepcion excepción que contiene el estado y el mensaje del error
	 * @return respuesta con el código y el mensaje de la excepción
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ProblemDetail> gestionarEstadoHttp(ResponseStatusException excepcion) {
		log.warn("No se ha podido realizar la operación solicitada: {}", excepcion.getReason());

		HttpStatusCode estado = excepcion.getStatusCode();
		String motivo = excepcion.getReason() != null
				? excepcion.getReason()
				: "No se ha podido completar la operación";

		return ResponseEntity.status(estado).body(problema(estado, motivo));
	}

	/**
	 * Gestiona un valor de la URL que no encaja con el tipo que espera el método.
	 *
	 * <p>Aquí está el matiz que costó semanas de despiste y que quedó documentado en
	 * {@code docu/fallos_master.txt}: cuando el que no encaja es un {@link PathVariable}, la
	 * respuesta honesta es un <strong>404</strong> y no un 400. Pedir
	 * {@code /cliente/listar-ultimos} cuando solo existe {@code /cliente/&#123;id&#125;} no es
	 * mandar mal un parámetro: es pedir una ruta que no está. Un 400 manda a quien depura a
	 * revisar el JavaScript, donde no hay nada que arreglar; un 404 le manda al backend, que
	 * es donde está el problema de verdad.</p>
	 *
	 * <p>Si el que falla es un parámetro de consulta ({@code ?limite=abc}), entonces sí es
	 * culpa de lo que se ha enviado, y el 400 es lo correcto.</p>
	 *
	 * @param excepcion el valor que no se pudo convertir
	 * @return 404 si venía en la ruta, 400 si venía en la consulta
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> gestionarTipoQueNoEncaja(
			MethodArgumentTypeMismatchException excepcion) {

		boolean vieneEnLaRuta = excepcion.getParameter().hasParameterAnnotation(PathVariable.class);

		if (vieneEnLaRuta) {
			log.warn("Ruta inexistente: {} no es un valor válido para {}",
					excepcion.getValue(), excepcion.getName());

			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(problema(HttpStatus.NOT_FOUND, "No existe el recurso solicitado"));
		}

		log.warn("Parámetro {} con un valor no válido: {}", excepcion.getName(), excepcion.getValue());

		// Se dice QUE se esperaba, no solo que lo enviado no vale: "limite no tiene un valor
		// valido" deja igual de perdido que no decir nada. El tipo se saca del propio metodo,
		// asi que no hay que mantener una lista a mano.
		Class<?> esperado = excepcion.getRequiredType();
		String tipo = esperado != null ? esperado.getSimpleName() : "otro tipo";

		return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST,
				"El parámetro " + excepcion.getName() + " esperaba un valor de tipo " + tipo
						+ " y se ha recibido " + excepcion.getValue()));
	}

	/**
	 * La red final: cualquier cosa que no hayan recogido ni Spring ni los manejadores de
	 * arriba acaba aquí.
	 *
	 * <p>Devuelve un mensaje genérico a propósito. Un fallo imprevisto no se le explica a
	 * quien usa la aplicación con el nombre de una clase de Java, y una traza en la respuesta
	 * cuenta de más a quien no debería saberlo. El detalle entero va al log.</p>
	 *
	 * @param excepcion lo que nadie esperaba
	 * @return respuesta 500 con un motivo que se pueda leer
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail gestionarImprevisto(Exception excepcion) {
		log.error("Error no contemplado", excepcion);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR,
				"Se ha producido un error inesperado. Vuelve a intentarlo en unos segundos");
	}

	/**
	 * Los errores de validación de un {@code @Valid @RequestBody}.
	 *
	 * <p>Se sobrescribe el de la clase base solo para añadir el mapa de campo a motivo en la
	 * propiedad {@code errores}: con el cuerpo que viene de serie, el frontend sabe QUE algo
	 * está mal pero no CUÁL de los ocho campos, y tendría que adivinarlo.</p>
	 *
	 * @param excepcion los campos que no han pasado la validación
	 * @param cabeceras las cabeceras que propone la clase base
	 * @param estado    el código que propone la clase base
	 * @param peticion  la petición que falló
	 * @return respuesta 400 con el motivo de cada campo
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException excepcion, HttpHeaders cabeceras,
			HttpStatusCode estado, WebRequest peticion) {

		// LinkedHashMap y putIfAbsent: se conserva el orden de los campos del formulario y, si
		// un campo incumple dos reglas a la vez, se cuenta la primera y no una al azar.
		Map<String, String> errores = new LinkedHashMap<>();
		for (FieldError error : excepcion.getBindingResult().getFieldErrors()) {
			errores.putIfAbsent(error.getField(), error.getDefaultMessage());
		}

		log.warn("Datos no válidos: {}", errores);

		ProblemDetail cuerpo = problema(HttpStatus.BAD_REQUEST, "Revisa los campos marcados");
		cuerpo.setProperty("errores", errores);

		return ResponseEntity.badRequest().body(cuerpo);
	}

}
