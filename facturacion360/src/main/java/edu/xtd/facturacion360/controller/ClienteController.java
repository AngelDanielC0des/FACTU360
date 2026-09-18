package edu.xtd.facturacion360.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Recibe las peticiones HTTP relativas a los clientes y devuelve su respuesta.
 *
 * MÉTODO HTTP - OPERACIÓN LÓGICA - OPERACIÓN SQL
 *
 * GET    - LEER      - SELECT
 * POST   - CREAR     - INSERT
 * PUT    - MODIFICAR - UPDATE
 * DELETE - BORRAR    - DELETE
 */
@Tag(
    name = "Clientes",
    description = "Operaciones para consultar, crear, actualizar y eliminar clientes"
)
@RestController
@RequestMapping("/cliente")
public class ClienteController {

    private static final Logger log =
            LoggerFactory.getLogger(ClienteController.class);

    private static final int LIMITE_MIN = 1;
    private static final int LIMITE_MAX = 100;

    @Autowired
    ClienteService clienteService;

    @Autowired
    ClienteMapper clienteMapper;

    /**
     * Crea un cliente.
     *
     * Si los datos no cumplen las validaciones de ClienteRequest,
     * devuelve un 400 con los errores concretos de cada campo.
     */
    @Operation(
        summary = "Crea un cliente",
        description = "Registra un cliente a partir de los datos recibidos"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Cliente creado correctamente"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada no válidos"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ya existe un cliente con ese NIF/CIF"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno al crear el cliente"
        )
    })
    @PostMapping
    public ResponseEntity<ClienteResponse> crear(
            @Valid @RequestBody ClienteRequest clienteRequest) {

        // Sin BindingResult al lado del @Valid a proposito: con el, Spring mete los errores
        // ahi dentro y no lanza nada, asi que este mismo bloque habia que repetirlo en crear
        // y en actualizar. Sin el lanza MethodArgumentNotValidException, y el manejador
        // global la devuelve como un 400 con el motivo de CADA campo.
        //
        // El NIF repetido tampoco se atrapa aqui: el DuplicateKeyException sube y el mismo
        // manejador lo convierte en un 409. Antes ese 409 se devolvia con el cuerpo vacio.
        Cliente clienteNuevo =
                clienteService.crear(clienteMapper.toDomain(clienteRequest));

        log.info(
            "POST /cliente -> 201, cliente {}",
            clienteNuevo.idCliente()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(clienteMapper.toResponse(clienteNuevo));
    }

    /**
     * Devuelve una página de clientes con búsqueda,
     * filtros y ordenación opcionales.
     */
    @Operation(
        summary = "Lista una página de clientes",
        description = "Devuelve una página de clientes con búsqueda, filtros y ordenación"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Página recuperada correctamente"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Algún criterio supera la longitud permitida"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno al consultar los clientes"
        )
    })
    @GetMapping("/listar-pagina")
    public ResponseEntity<PaginaClienteResponse> listarPagina(
            @Valid @ModelAttribute CriteriosCliente criterios) {

        log.info(
            "GET /cliente/listar-pagina -> {}",
            criterios
        );

        PaginaClienteResponse pagina =
                clienteService.listarPagina(criterios);

        log.info(
            "GET /cliente/listar-pagina -> 200 (pagina {} de {})",
            pagina.paginaActual() + 1,
            pagina.totalPaginas()
        );

        return ResponseEntity.ok(pagina);
    }

    /**
     * Devuelve los ultimos clientes dados de alta, para rellenar un desplegable.
     *
     * <p>Sigue marcado como obsoleto, pero obsoleto no es lo mismo que roto: es el endpoint
     * con el que se monto el selector de clientes del alta de facturas, y ese selector lo
     * sigue llamando. Se perdio en la resolucion de un merge (documentado en
     * {@code docu/fallos_master.txt}) y con el se quedo sin poder crearse ninguna factura,
     * porque el desplegable salia vacio.</p>
     *
     * <p>El sintoma engañaba: al no existir la ruta, la peticion encajaba en
     * {@code @GetMapping("/{id}")} y fallaba al convertir "listar-ultimos" a int, asi que
     * devolvia 400 y no 404. Eso es justo lo que ManejadorExcepciones corrige ahora.</p>
     *
     * @param limite cuantos se piden; se acota en silencio entre LIMITE_MIN y LIMITE_MAX
     * @return los clientes mas recientes
     */
    @Deprecated
    @Operation(
        summary = "Lista los ultimos clientes",
        description = "Devuelve los clientes mas recientes, para rellenar un desplegable."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Lista de clientes"
        )
    })
    @GetMapping("/listar-ultimos")
    public ResponseEntity<List<ClienteResponse>> listarUltimos(
            @Parameter(
                description = "Cuantos clientes se piden",
                example = "100"
            )
            @RequestParam(defaultValue = "10") int limite) {

        // Pedir cero o un numero negativo no es un error del que haya que avisar: se acota y
        // se sigue. El log deja constancia del valor pedido y del que se ha usado de verdad.
        int limiteSeguro = Math.max(LIMITE_MIN, Math.min(LIMITE_MAX, limite));

        log.info(
            "GET /cliente/listar-ultimos?limite={} (acotado a {})",
            limite,
            limiteSeguro
        );

        List<ClienteResponse> respuesta =
                clienteService.listarUltimos(limiteSeguro)
                    .stream()
                    .map(clienteMapper::toResponse)
                    .toList();

        log.info(
            "GET /cliente/listar-ultimos -> 200 ({} clientes)",
            respuesta.size()
        );

        return ResponseEntity.ok(respuesta);
    }

    /**
     * Devuelve las provincias distintas que existen en la tabla.
     */
    @Operation(
        summary = "Lista las provincias",
        description = "Devuelve las provincias distintas de la tabla clientes"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Provincias recuperadas correctamente"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno al consultar las provincias"
        )
    })
    @GetMapping("/provincias")
    public ResponseEntity<List<String>> listarProvincias() {

        log.info("GET /cliente/provincias");

        List<String> provincias =
                clienteService.listarProvincias();

        log.info(
            "GET /cliente/provincias -> 200 ({} provincias)",
            provincias.size()
        );

        return ResponseEntity.ok(provincias);
    }

    /**
     * Devuelve las poblaciones distintas.
     */
    @Operation(
        summary = "Lista las poblaciones",
        description = "Devuelve las poblaciones distintas de la tabla clientes"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Poblaciones recuperadas correctamente"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno al consultar las poblaciones"
        )
    })
    @GetMapping("/poblaciones")
    public ResponseEntity<List<String>> listarPoblaciones(
            @Parameter(
                description = "Provincia por la que filtrar",
                example = "Valencia"
            )
            @RequestParam(required = false) String provincia) {

        log.info(
            "GET /cliente/poblaciones?provincia={}",
            provincia
        );

        List<String> poblaciones =
                clienteService.listarPoblaciones(provincia);

        log.info(
            "GET /cliente/poblaciones -> 200 ({} poblaciones)",
            poblaciones.size()
        );

        return ResponseEntity.ok(poblaciones);
    }

    /**
     * Obtiene un cliente por su ID.
     */
    @Operation(
        summary = "Obtiene un cliente por su id",
        description = "Devuelve el detalle completo del cliente"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Cliente encontrado"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No existe ningún cliente con ese id"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno al consultar el cliente"
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> obtenerPorId(
            @Parameter(
                description = "Identificador del cliente",
                example = "1"
            )
            @PathVariable int id) {

        log.info("GET /cliente/{}", id);

        // orElseThrow en vez de un if con notFound().build(): el 404 sale con su motivo
        // dentro en lugar de con el cuerpo vacio, y de darle formato se encarga
        // ManejadorExcepciones. El DataAccessException, si lo hubiera, sube igual.
        Cliente cliente = clienteService.obtenerPorId(id)
                .orElseThrow(() -> {
                    log.warn("GET /cliente/{} -> 404, no existe", id);
                    return new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe el cliente " + id
                    );
                });

        log.info("GET /cliente/{} -> 200", id);

        return ResponseEntity.ok(clienteMapper.toResponse(cliente));
    }

    /**
     * Actualiza un cliente existente.
     *
     * Si los datos no son válidos, devuelve un 400
     * con los mensajes concretos de validación.
     */
    @Operation(
        summary = "Actualiza un cliente",
        description = "Modifica los datos del cliente indicado"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Cliente actualizado correctamente"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada no válidos"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No existe ningún cliente con ese id"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ya existe otro cliente con ese NIF/CIF"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno al actualizar el cliente"
        )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> actualizar(
            @Parameter(
                description = "Identificador del cliente",
                example = "1"
            )
            @PathVariable int id,

            @Valid @RequestBody ClienteRequest clienteRequest) {

        log.info(
            "PUT /cliente/{}",
            id
        );

        // Igual que en crear: sin BindingResult, los errores de validacion los formatea el
        // manejador global, y el NIF/CIF duplicado sube como DuplicateKeyException y sale
        // como 409. Antes ese 409 iba sin cuerpo y este bloque estaba duplicado.
        Cliente actualizado = clienteService
                .actualizar(id, clienteMapper.toDomain(clienteRequest))
                .orElseThrow(() -> {
                    log.warn("PUT /cliente/{} -> 404, no existe", id);
                    return new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe el cliente " + id
                    );
                });

        log.info("PUT /cliente/{} -> 200", id);

        return ResponseEntity.ok(clienteMapper.toResponse(actualizado));
    }

    /**
     * Elimina un cliente identificado por su ID.
     */
    @Operation(
        summary = "Elimina un cliente",
        description = "Elimina el cliente identificado por su ID."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Cliente eliminado correctamente"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Cliente no encontrado"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "El cliente tiene facturas asociadas"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error interno del servidor"
        )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(
                description = "Identificador del cliente",
                example = "1"
            )
            @PathVariable int id) {

        log.info(
            "Peticion DELETE recibida para eliminar el cliente con ID {}",
            id
        );

        // Sin try/catch, que era el ultimo del controlador. Si el cliente tiene facturas,
        // el repositorio lanza ClienteConFacturasException y el manejador global la
        // convierte en un 409 que dice justamente eso. Traducirlo aqui obligaba a que el
        // controlador supiera de claves ajenas.
        clienteService.eliminar(id);

        log.info(
            "Cliente con ID {} eliminado correctamente.",
            id
        );

        return ResponseEntity.noContent().build();
    }
}
