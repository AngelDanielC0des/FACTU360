package edu.xtd.facturacion360.controller;

import java.util.List;
import java.util.Optional;
import java.sql.SQLIntegrityConstraintViolationException;
import edu.xtd.facturacion360.dto.ApiResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.Cliente;
import edu.xtd.facturacion360.dto.ClienteMapper;
import edu.xtd.facturacion360.dto.ClienteRequest;
import edu.xtd.facturacion360.dto.ClienteResponse;
import edu.xtd.facturacion360.dto.CriteriosCliente;
import edu.xtd.facturacion360.dto.PaginaClienteResponse;
import edu.xtd.facturacion360.service.ClienteService;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * 
 * 
 * Recibe las peticiones HTTP relativas a los clientes y devuelve su respuesta.
 * 
 * 
 * MÉTODO HTTP - OPERACIÓN LÓGICA - OPERACIÓN SQL
 * 
 * GET - LEER - SELECT POST - CREAR - INSERT PUT - MODIFICAR - UPDATE DELETE -
 * BORRAR - DELETE
 * 
 * 
 */
@Tag(name = "Clientes", description = "Operaciones para consultar, crear, actualizar y eliminar clientes")
@RestController
@RequestMapping("/cliente")
public class ClienteController {

	private static final Logger log = LoggerFactory.getLogger(ClienteController.class);

	/**
	 * Mínimo de clientes que puede pedir {@link #listarUltimos(int)}. Pedir cero o un número
	 * negativo no es un error del que haya que avisar: se sube hasta aquí y se responde con
	 * un cliente.
	 *
	 * <p>El listado paginado NO usa estas dos constantes: sus topes viven en
	 * {@link CriteriosCliente}, junto a los datos que acotan, para que quien añada un
	 * criterio nuevo los encuentre sin salir del record.</p>
	 */
	private static final int LIMITE_MIN = 1;

	/**
	 * Tope de clientes que puede pedir {@link #listarUltimos(int)}, para que nadie se traiga
	 * la tabla entera con un {@code ?limite=999999}. Lo que pase de aquí se recorta en
	 * silencio, y el log deja constancia del valor pedido y del que se ha usado de verdad.
	 */
	private static final int LIMITE_MAX = 100;

	@Autowired
	ClienteService clienteService;

	@Autowired
	ClienteMapper clienteMapper;




	


	



	
	/**
	 * Crea un cliente a partir de los datos recibidos. ClienteResponse contiene los
	 * datos que se devuelven en la respuesta HTTP.
	 *
	 * @Valid indica que se debe validar el objeto recibido según las anotaciones de
	 *        validación definidas en la clase ClienteRequest.
	 * @RequestBody indica que el objeto ClienteRequest se debe obtener del cuerpo
	 *              de la petición HTTP. ClienteRequest contiene los datos recibidos
	 *              en la petición HTTP. BindingResult contiene el resultado de la
	 *              validación, incluyendo errores si los hubiera.
	 *
	 *              Devuelve 201 si se crea el cliente, 400 si hay errores de
	 *              validación y 500 si no se consigue guardar.
	 */
	@Operation(summary = "Crea un cliente", description = "Registra un cliente a partir de los datos recibidos")
	@ApiResponses({ @ApiResponse(responseCode = "201", description = "Cliente creado correctamente"),
			@ApiResponse(responseCode = "400", description = "Datos de entrada no válidos"),
			@ApiResponse(responseCode = "409", description = "Ya existe un cliente con ese NIF/CIF"),
			@ApiResponse(responseCode = "500", description = "Error interno al crear el cliente") })
	@PostMapping
	public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest clienteRequest,
			BindingResult bindingResult) {
		ResponseEntity<ClienteResponse> respuesta;
		ClienteResponse clienteResponse = null;

		if (bindingResult.hasErrors()) {
			log.error("Cliente recibido con errores");
			respuesta = ResponseEntity.badRequest().build();
		} else {
			try {
				log.debug("Cliente sin errores de validación");
				Cliente cliente = clienteMapper.toDomain(clienteRequest);
				Cliente clienteNuevo = clienteService.crear(cliente);

				log.debug("Cliente creado correctamente " + clienteNuevo);
				clienteResponse = clienteMapper.toResponse(clienteNuevo);
				respuesta = ResponseEntity.status(HttpStatus.CREATED).body(clienteResponse);

			} catch (DuplicateKeyException e) {
				log.error("NIF duplicado", e);
				respuesta = ResponseEntity.status(HttpStatus.CONFLICT).build();
			} catch (Exception e) {
				log.error("Excepción creando cliente", e);
				respuesta = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
		}

		return respuesta;
	}

	
	/**
	 * Devuelve una PÁGINA de clientes (para la paginación de la tabla), con búsqueda,
	 * filtros y ordenación opcionales. No toca a {@link #listarUltimos(int)}; es un
	 * endpoint aparte. Ejemplo de uso:
	 * {@code GET /cliente/listar-pagina?pagina=0&tamano=10&busqueda=garcia&provincia=Valencia&ordenarPor=nombre&direccion=asc}
	 *
	 * <p>Buscar es "listar con un filtro de texto más", por eso comparte endpoint con el
	 * listado: así la búsqueda hereda la paginación y los metadatos, y el frontend usa
	 * un único camino de código tanto si hay término de búsqueda como si no.</p>
	 *
	 * <p>El {@code BindingResult} detrás del {@code @ModelAttribute} cumple aquí el mismo
	 * papel que en {@link #actualizar(int, ClienteRequest, BindingResult)}: sin él, pasar
	 * una búsqueda más larga de la cuenta hace que Spring lance la excepción por su cuenta
	 * y el 400 salga con la página de error por defecto en vez del cuerpo vacío que
	 * devuelven los demás endpoints.</p>
	 *
	 * @param criterios     los parámetros de la URL (?pagina=, ?tamano=, ?busqueda=,
	 *                      ?provincia=, ?poblacion=, ?ordenarPor=, ?direccion=) que Spring
	 *                      agrupa en un {@link CriteriosCliente}. El propio record se
	 *                      encarga de acotarlos y limpiarlos, así que aquí llegan ya
	 *                      normalizados.
	 * @param bindingResult resultado de validar las longitudes de esos criterios
	 * @return {@code 200 OK} con un {@link PaginaClienteResponse} (los clientes de
	 *         la página + metadatos de paginación); {@code 400} si algún criterio
	 *         supera la longitud permitida; o {@code 500} si falla la BD.
	 * @autor AngelDanielC0des
	 * @see CriteriosCliente
	 * @see PaginaClienteResponse
	 */
	@Operation(summary = "Lista una página de clientes", description = "Devuelve una página de clientes con búsqueda por nombre o NIF/CIF, filtros por provincia y población, y ordenación por nombre o fecha de alta en ambos sentidos")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Página recuperada correctamente"),
			@ApiResponse(responseCode = "400", description = "Algún criterio supera la longitud permitida"),
			@ApiResponse(responseCode = "500", description = "Error interno al consultar los clientes") })
	@GetMapping("/listar-pagina")
	public ResponseEntity<PaginaClienteResponse> listarPagina(@Valid @ModelAttribute CriteriosCliente criterios,
			BindingResult bindingResult) {

		ResponseEntity<PaginaClienteResponse> respuestaHttp = null;

		// Ya no hace falta acotar nada aquí: el constructor compacto de CriteriosCliente
		// deja la página, el tamaño y los textos listos para usar. Por eso el log muestra
		// los valores YA normalizados, que son los que de verdad se van a consultar.
		log.info("GET /cliente/listar-pagina -> {}", criterios);

		if (bindingResult.hasErrors()) {
			// warn y solo el campo con su mensaje, igual que en actualizar: una búsqueda
			// demasiado larga es cosa de quien llama, no algo que haya que ir a mirar.
			log.warn("GET /cliente/listar-pagina -> 400, criterios no válidos: {}",
					bindingResult.getFieldErrors().stream()
							.map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
			respuestaHttp = ResponseEntity.badRequest().build();
		} else {
			try {
				// El service trae la página y ya calcula los metadatos (total, hayAnterior,
				// etc.).
				PaginaClienteResponse pagina = clienteService.listarPagina(criterios);

				// El código HTTP, igual que en el resto de endpoints: el service ya cuenta
				// cuántos clientes hay. Sin esta línea, el endpoint más usado de la pantalla
				// era el único cuyo camino bueno no dejaba rastro, y el log solo hablaba de
				// listar-pagina cuando algo iba mal.
				log.info("GET /cliente/listar-pagina -> 200 (pagina {} de {})",
						pagina.paginaActual() + 1, pagina.totalPaginas());
				respuestaHttp = ResponseEntity.ok(pagina);
			} catch (DataAccessException | TransactionException e) {
				// TransactionException aparte de DataAccessException porque NO son la misma
				// familia: al ser listarPagina transaccional, si la BD no responde el fallo
				// salta al ABRIR la transacción (CannotCreateTransactionException), antes de
				// lanzar ninguna consulta. Sin este segundo tipo, esa excepción se escaparía y
				// el cliente recibiría la página de error de Spring con la traza dentro, en vez
				// del 500 limpio que devuelven los demás endpoints.
				log.error("Error al listar la pagina de clientes", e);
				respuestaHttp = ResponseEntity.internalServerError().build();
			}
		}

		return respuestaHttp;
	}
	
	/**
	 * Devuelve las provincias distintas que existen en la tabla, para rellenar el
	 * desplegable de filtro del frontend. Ejemplo de uso:
	 * {@code GET /cliente/provincias}
	 *
	 * @return {@code 200 OK} con la lista de provincias; o {@code 500} si falla la BD.
	 * @autor AngelDanielC0des
	 * @see #listarPoblaciones(String)
	 */
	@Operation(summary = "Lista las provincias", description = "Devuelve las provincias distintas de la tabla clientes, ordenadas alfabéticamente")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Provincias recuperadas correctamente"),
			@ApiResponse(responseCode = "500", description = "Error interno al consultar las provincias") })
	@GetMapping("/provincias")
	public ResponseEntity<List<String>> listarProvincias() {

		ResponseEntity<List<String>> respuestaHttp = null;
		log.info("GET /cliente/provincias");

		try {
			List<String> provincias = clienteService.listarProvincias();
			log.info("GET /cliente/provincias -> 200 ({} provincias)", provincias.size());
			respuestaHttp = ResponseEntity.ok(provincias);
		} catch (DataAccessException e) {
			log.error("Error al listar las provincias", e);
			respuestaHttp = ResponseEntity.internalServerError().build();
		}

		return respuestaHttp;
	}

	/**
	 * Devuelve las poblaciones distintas que existen en la tabla, para el desplegable
	 * de filtro en cascada. Ejemplo de uso:
	 * {@code GET /cliente/poblaciones?provincia=Valencia}
	 *
	 * @param provincia si llega informada, solo las poblaciones de esa provincia;
	 *                  si no, todas. Opcional.
	 * @return {@code 200 OK} con la lista de poblaciones; o {@code 500} si falla la BD.
	 * @autor AngelDanielC0des
	 * @see #listarProvincias()
	 */
	@Operation(summary = "Lista las poblaciones", description = "Devuelve las poblaciones distintas de la tabla clientes; si se indica provincia, solo las de esa provincia")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Poblaciones recuperadas correctamente"),
			@ApiResponse(responseCode = "500", description = "Error interno al consultar las poblaciones") })
	@GetMapping("/poblaciones")
	public ResponseEntity<List<String>> listarPoblaciones(
			@Parameter(description = "Provincia por la que filtrar", example = "Valencia") @RequestParam(required = false) String provincia) {

		ResponseEntity<List<String>> respuestaHttp = null;
		log.info("GET /cliente/poblaciones?provincia={}", provincia);

		try {
			List<String> poblaciones = clienteService.listarPoblaciones(provincia);
			log.info("GET /cliente/poblaciones -> 200 ({} poblaciones)", poblaciones.size());
			respuestaHttp = ResponseEntity.ok(poblaciones);
		} catch (DataAccessException e) {
			log.error("Error al listar las poblaciones", e);
			respuestaHttp = ResponseEntity.internalServerError().build();
		}

		return respuestaHttp;
	}

	
	/**
	 * Devuelve el detalle completo de un cliente: los mismos datos que el listado más los que
	 * la tabla no muestra (dirección, código postal, población y provincia). Lo pide el
	 * frontend al desplegar una fila y antes de editarla. Ejemplo de uso:
	 * {@code GET /cliente/7}
	 *
	 * <p>No choca con {@code /cliente/listar-pagina} ni con {@code /cliente/provincias}: Spring
	 * da prioridad a las rutas literales sobre las que llevan variable.</p>
	 *
	 * @param id identificador del cliente; llega en la propia ruta. Si no es un número, Spring
	 *           responde {@code 400} antes de entrar en el método.
	 * @return {@code 200 OK} con el {@link ClienteResponse}; {@code 404} si no existe ningún
	 *         cliente con ese id; o {@code 500} si falla la BD.
	 * @autor AngelDanielC0des
	 * @see #listarPagina(CriteriosCliente, BindingResult)
	 */
	@Operation(summary = "Obtiene un cliente por su id", description = "Devuelve el detalle completo del cliente, incluidos los campos que el listado no muestra")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
			@ApiResponse(responseCode = "404", description = "No existe ningún cliente con ese id"),
			@ApiResponse(responseCode = "500", description = "Error interno al consultar el cliente") })
	@GetMapping("/{id}")
	public ResponseEntity<ClienteResponse> obtenerPorId(
			@Parameter(description = "Identificador del cliente", example = "1") @PathVariable int id) {

		ResponseEntity<ClienteResponse> respuestaHttp = null;
		log.info("GET /cliente/{}", id);

		try {
			// El service devuelve un Optional: aquí es donde "no existe" se convierte en el
			// código HTTP que le corresponde, un 404 con cuerpo vacío.
			Optional<Cliente> cliente = clienteService.obtenerPorId(id);

			if (cliente.isPresent()) {
				ClienteResponse respuesta = clienteMapper.toResponse(cliente.get());
				log.info("GET /cliente/{} -> 200", id);
				respuestaHttp = ResponseEntity.ok(respuesta);
			} else {
				log.warn("GET /cliente/{} -> 404, no existe", id);
				respuestaHttp = ResponseEntity.notFound().build();
			}
		} catch (DataAccessException e) {
			log.error("Error al obtener el cliente {}", id, e);
			respuestaHttp = ResponseEntity.internalServerError().build();
		}

		return respuestaHttp;
	}
	
	/**
	 * Modifica un cliente existente con los datos recibidos. Lo usa el formulario que se abre
	 * en la propia fila de la tabla. Ejemplo de uso: {@code PUT /cliente/7}
	 *
	 * <p>El {@code BindingResult} justo detrás del {@code @RequestBody} no es decorativo: con él,
	 * Spring deja los errores de validación en el objeto en vez de lanzar una excepción, y así
	 * el 400 se devuelve desde aquí igual que en {@link #crear(ClienteRequest, BindingResult)}.</p>
	 *
	 * @param id            identificador del cliente que se modifica, en la ruta
	 * @param clienteRequest datos nuevos del cliente, validados con las restricciones de
	 *                       {@link ClienteRequest}
	 * @param bindingResult  resultado de esa validación
	 * @return {@code 200 OK} con el cliente ya actualizado; {@code 400} si los datos no son
	 *         válidos; {@code 404} si no existe ese cliente; {@code 409} si el NIF/CIF ya es de
	 *         otro cliente; o {@code 500} si falla la BD.
	 * @autor AngelDanielC0des
	 * @see #obtenerPorId(int)
	 * @see #crear(ClienteRequest, BindingResult)
	 */
	@Operation(summary = "Actualiza un cliente", description = "Modifica los datos del cliente indicado; la fecha de alta se conserva")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Cliente actualizado correctamente"),
			@ApiResponse(responseCode = "400", description = "Datos de entrada no válidos"),
			@ApiResponse(responseCode = "404", description = "No existe ningún cliente con ese id"),
			@ApiResponse(responseCode = "409", description = "Ya existe otro cliente con ese NIF/CIF"),
			@ApiResponse(responseCode = "500", description = "Error interno al actualizar el cliente") })
	@PutMapping("/{id}")
	public ResponseEntity<ClienteResponse> actualizar(
			@Parameter(description = "Identificador del cliente", example = "1") @PathVariable int id,
			@Valid @RequestBody ClienteRequest clienteRequest, BindingResult bindingResult) {

		ResponseEntity<ClienteResponse> respuestaHttp = null;
		log.info("PUT /cliente/{}", id);

		if (bindingResult.hasErrors()) {
			// warn y no error: los datos mal formados son cosa de quien llama, no un fallo del
			// servidor, y en el log conviene que 'error' sea siempre algo que hay que mirar.
			// Solo el campo y su mensaje: getAllErrors() vuelca el objeto de error entero y deja
			// veinte líneas de códigos de Spring en el log por cada campo que falle.
			log.warn("PUT /cliente/{} -> 400, datos no válidos: {}", id, bindingResult.getFieldErrors().stream()
					.map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
			respuestaHttp = ResponseEntity.badRequest().build();
		} else {
			try {
				Cliente cliente = clienteMapper.toDomain(clienteRequest);

				// Optional igual que en obtenerPorId: aquí es donde "no existe" se convierte en
				// el 404 que le corresponde.
				Optional<Cliente> actualizado = clienteService.actualizar(id, cliente);

				if (actualizado.isPresent()) {
					ClienteResponse respuesta = clienteMapper.toResponse(actualizado.get());
					log.info("PUT /cliente/{} -> 200", id);
					respuestaHttp = ResponseEntity.ok(respuesta);
				} else {
					log.warn("PUT /cliente/{} -> 404, no existe", id);
					respuestaHttp = ResponseEntity.notFound().build();
				}
			} catch (DuplicateKeyException e) {
				// nif_cif tiene índice UNIQUE: cambiarlo por el de otro cliente lo viola. Sin este
				// catch, la excepción se escapaba y el navegador recibía la página de error de
				// Spring con la traza dentro, en vez del 409 que ya devuelve crear().
				//
				// warn y sin la traza, por lo mismo que el 400 de arriba: el NIF repetido lo
				// arregla quien edita cambiándolo, no hay nada roto en el servidor que mirar.
				log.warn("PUT /cliente/{} -> 409, NIF/CIF duplicado", id);
				respuestaHttp = ResponseEntity.status(HttpStatus.CONFLICT).build();
			} catch (DataAccessException | TransactionException e) {
				// TransactionException aparte: al ser actualizar() transaccional, si la BD no
				// responde el fallo salta al ABRIR la transacción, antes de lanzar ninguna
				// consulta, y no sería una DataAccessException.
				log.error("Error al actualizar el cliente {}", id, e);
				respuestaHttp = ResponseEntity.internalServerError().build();
			}
		}

		return respuestaHttp;
	}


	/**
	 * Elimina un cliente identificado por su ID.
	 *
	 * Si el cliente existe y no tiene restricciones de integridad (por ejemplo,
	 * facturas asociadas), se elimina correctamente y se devuelve un HTTP 200.
	 *
	 * Si el cliente no existe se devuelve un HTTP 404.
	 *
	 * Si el cliente tiene registros asociados que impiden su eliminación,
	 * se devuelve un HTTP 409 (Conflict).
	 *
	 * Si ocurre cualquier otro error inesperado,
	 * se devuelve un HTTP 500.
	 *
	 * @param id identificador del cliente a eliminar.
	 * @return respuesta HTTP con el resultado de la operación.
	 */
	@Operation(
	        summary = "Elimina un cliente",
	        description = "Elimina el cliente identificado por su ID."
	)
	@ApiResponses({
	        @ApiResponse(responseCode = "200", description = "Cliente eliminado correctamente"),
	        @ApiResponse(responseCode = "404", description = "Cliente no encontrado"),
	        @ApiResponse(responseCode = "409", description = "El cliente tiene facturas asociadas"),
	        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
	})
	
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponseDto> eliminar(
	        @Parameter(description = "Identificador del cliente", example = "1")
	        @PathVariable int id) {


	    log.info("Petición DELETE recibida para eliminar el cliente con ID {}", id);

	    try {


	        // Delegamos la lógica de negocio al Service
	        clienteService.eliminar(id);


	        log.info("Cliente con ID {} eliminado correctamente.", id);

	        return ResponseEntity.ok(
	                new ApiResponseDto(
	                        true,
	                        "Cliente eliminado correctamente"
	                )
	        );

	    } catch (DataIntegrityViolationException e) {

	        log.error("No se puede eliminar el cliente {} porque tiene datos relacionados.", id, e);

	        return ResponseEntity.status(HttpStatus.CONFLICT)
	                .body(
	                        new ApiResponseDto(
	                                false,
	                                "No se puede eliminar el cliente porque tiene facturas asociadas."
	                        )
	                );

	    } catch (ResponseStatusException e) {

	        log.warn("No existe el cliente con ID {}.", id);

	        return ResponseEntity.status(e.getStatusCode())
	                .body(
	                        new ApiResponseDto(
	                                false,
	                                e.getReason()
	                        )
	                );

	    } catch (Exception e) {

	        log.error("Error inesperado eliminando el cliente {}.", id, e);

	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(
	                        new ApiResponseDto(
	                                false,
	                                "No se pudo eliminar el cliente."
	                        )
	                );
	    }
	}

}
