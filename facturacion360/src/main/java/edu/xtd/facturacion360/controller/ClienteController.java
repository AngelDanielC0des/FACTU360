package edu.xtd.facturacion360.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

import edu.xtd.facturacion360.dto.ApiResponseDto;
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
    public ResponseEntity<?> crear(
            @Valid @RequestBody ClienteRequest clienteRequest,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {

            log.warn(
                "POST /cliente -> 400, datos no válidos: {}",
                bindingResult.getFieldErrors()
                    .stream()
                    .map(error ->
                        error.getField() + ": " + error.getDefaultMessage()
                    )
                    .toList()
            );

            Map<String, String> errores = new HashMap<>();

            bindingResult.getFieldErrors().forEach(error ->
                errores.put(
                    error.getField(),
                    error.getDefaultMessage()
                )
            );

            return ResponseEntity
                    .badRequest()
                    .body(errores);
        }

        try {

            log.debug("Cliente sin errores de validación");

            Cliente cliente =
                    clienteMapper.toDomain(clienteRequest);

            Cliente clienteNuevo =
                    clienteService.crear(cliente);

            log.debug(
                "Cliente creado correctamente {}",
                clienteNuevo
            );

            ClienteResponse clienteResponse =
                    clienteMapper.toResponse(clienteNuevo);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(clienteResponse);

        } catch (DuplicateKeyException e) {

            log.error("NIF duplicado", e);

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();

        } catch (Exception e) {

            log.error(
                "Excepción creando cliente",
                e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
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
            @Valid @ModelAttribute CriteriosCliente criterios,
            BindingResult bindingResult) {

        ResponseEntity<PaginaClienteResponse> respuestaHttp = null;

        log.info(
            "GET /cliente/listar-pagina -> {}",
            criterios
        );

        if (bindingResult.hasErrors()) {

            log.warn(
                "GET /cliente/listar-pagina -> 400, criterios no válidos: {}",
                bindingResult.getFieldErrors()
                    .stream()
                    .map(error ->
                        error.getField() + ": " + error.getDefaultMessage()
                    )
                    .toList()
            );

            respuestaHttp =
                    ResponseEntity.badRequest().build();

        } else {

            try {

                PaginaClienteResponse pagina =
                        clienteService.listarPagina(criterios);

                log.info(
                    "GET /cliente/listar-pagina -> 200 (pagina {} de {})",
                    pagina.paginaActual() + 1,
                    pagina.totalPaginas()
                );

                respuestaHttp =
                        ResponseEntity.ok(pagina);

            } catch (
                DataAccessException |
                TransactionException e
            ) {

                log.error(
                    "Error al listar la pagina de clientes",
                    e
                );

                respuestaHttp =
                        ResponseEntity.internalServerError().build();
            }
        }

        return respuestaHttp;
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

        ResponseEntity<List<String>> respuestaHttp = null;

        log.info("GET /cliente/provincias");

        try {

            List<String> provincias =
                    clienteService.listarProvincias();

            log.info(
                "GET /cliente/provincias -> 200 ({} provincias)",
                provincias.size()
            );

            respuestaHttp =
                    ResponseEntity.ok(provincias);

        } catch (DataAccessException e) {

            log.error(
                "Error al listar las provincias",
                e
            );

            respuestaHttp =
                    ResponseEntity.internalServerError().build();
        }

        return respuestaHttp;
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

        ResponseEntity<List<String>> respuestaHttp = null;

        log.info(
            "GET /cliente/poblaciones?provincia={}",
            provincia
        );

        try {

            List<String> poblaciones =
                    clienteService.listarPoblaciones(provincia);

            log.info(
                "GET /cliente/poblaciones -> 200 ({} poblaciones)",
                poblaciones.size()
            );

            respuestaHttp =
                    ResponseEntity.ok(poblaciones);

        } catch (DataAccessException e) {

            log.error(
                "Error al listar las poblaciones",
                e
            );

            respuestaHttp =
                    ResponseEntity.internalServerError().build();
        }

        return respuestaHttp;
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

        ResponseEntity<ClienteResponse> respuestaHttp = null;

        log.info("GET /cliente/{}", id);

        try {

            Optional<Cliente> cliente =
                    clienteService.obtenerPorId(id);

            if (cliente.isPresent()) {

                ClienteResponse respuesta =
                        clienteMapper.toResponse(cliente.get());

                log.info(
                    "GET /cliente/{} -> 200",
                    id
                );

                respuestaHttp =
                        ResponseEntity.ok(respuesta);

            } else {

                log.warn(
                    "GET /cliente/{} -> 404, no existe",
                    id
                );

                respuestaHttp =
                        ResponseEntity.notFound().build();
            }

        } catch (DataAccessException e) {

            log.error(
                "Error al obtener el cliente {}",
                id,
                e
            );

            respuestaHttp =
                    ResponseEntity.internalServerError().build();
        }

        return respuestaHttp;
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
    public ResponseEntity<?> actualizar(
            @Parameter(
                description = "Identificador del cliente",
                example = "1"
            )
            @PathVariable int id,

            @Valid @RequestBody ClienteRequest clienteRequest,

            BindingResult bindingResult) {

        log.info(
            "PUT /cliente/{}",
            id
        );

        if (bindingResult.hasErrors()) {

            log.warn(
                "PUT /cliente/{} -> 400, datos no válidos: {}",
                id,
                bindingResult.getFieldErrors()
                    .stream()
                    .map(error ->
                        error.getField() + ": " + error.getDefaultMessage()
                    )
                    .toList()
            );

            Map<String, String> errores =
                    new HashMap<>();

            bindingResult.getFieldErrors().forEach(error ->
                errores.put(
                    error.getField(),
                    error.getDefaultMessage()
                )
            );

            return ResponseEntity
                    .badRequest()
                    .body(errores);
        }

        try {

            Cliente cliente =
                    clienteMapper.toDomain(clienteRequest);

            Optional<Cliente> actualizado =
                    clienteService.actualizar(
                        id,
                        cliente
                    );

            if (actualizado.isPresent()) {

                ClienteResponse respuesta =
                        clienteMapper.toResponse(
                            actualizado.get()
                        );

                log.info(
                    "PUT /cliente/{} -> 200",
                    id
                );

                return ResponseEntity.ok(respuesta);

            } else {

                log.warn(
                    "PUT /cliente/{} -> 404, no existe",
                    id
                );

                return ResponseEntity.notFound().build();
            }

        } catch (DuplicateKeyException e) {

            log.warn(
                "PUT /cliente/{} -> 409, NIF/CIF duplicado",
                id
            );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();

        } catch (
            DataAccessException |
            TransactionException e
        ) {

            log.error(
                "Error al actualizar el cliente {}",
                id,
                e
            );

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
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
    public ResponseEntity<ApiResponseDto> eliminar(
            @Parameter(
                description = "Identificador del cliente",
                example = "1"
            )
            @PathVariable int id) {

        log.info(
            "Petición DELETE recibida para eliminar el cliente con ID {}",
            id
        );

        try {

            clienteService.eliminar(id);

            log.info(
                "Cliente con ID {} eliminado correctamente.",
                id
            );

            return ResponseEntity.ok(
                new ApiResponseDto(
                    true,
                    "Cliente eliminado correctamente"
                )
            );

        } catch (DataIntegrityViolationException e) {

            log.error(
                "No se puede eliminar el cliente {} porque tiene datos relacionados.",
                id,
                e
            );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                        new ApiResponseDto(
                            false,
                            "No se puede eliminar el cliente porque tiene facturas asociadas."
                        )
                    );

        } catch (ResponseStatusException e) {

            log.warn(
                "No existe el cliente con ID {}.",
                id
            );

            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(
                        new ApiResponseDto(
                            false,
                            e.getReason()
                        )
                    );

        } catch (Exception e) {

            log.error(
                "Error inesperado eliminando el cliente {}.",
                id,
                e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        new ApiResponseDto(
                            false,
                            "No se pudo eliminar el cliente."
                        )
                    );
        }
    }
}
