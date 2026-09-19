package edu.xtd.facturacion360.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import edu.xtd.facturacion360.dto.Cliente;
import edu.xtd.facturacion360.dto.ClienteMapper;
import edu.xtd.facturacion360.repository.ClienteRepository.ClienteConFacturasException;
import edu.xtd.facturacion360.repository.ClienteRepository.NifCifDuplicadoException;
import edu.xtd.facturacion360.repository.FacturaRepository.ClienteInexistenteException;
import edu.xtd.facturacion360.service.ClienteService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * Comprueba el contrato de error de la API: que TODOS los fallos salen con el mismo cuerpo
 * (un ProblemDetail), con su motivo dentro y sin filtrar nada de lo que pasa por debajo.
 *
 * Antes de esto no habia ninguna prueba de ClienteController, y los errores se devolvian de
 * cuatro formas distintas segun el endpoint, incluido el cuerpo vacio en quince sitios.
 */
class ManejadorExcepcionesTests {

	MockMvc clienteHttp;
	ClienteService servicio;
	ValidatorFactory validadores;

	private static final String CLIENTE_VALIDO = """
			{"nombre":"Ana Gil Paz","nifCif":"12345678Z","direccion":"Calle Mayor 15",
			 "codigoPostal":"28001","poblacion":"Madrid","provincia":"Madrid",
			 "telefono":"612345678","email":"ana@ejemplo.es"}
			""";

	@BeforeEach
	void preparar() {
		validadores = Validation.buildDefaultValidatorFactory();
		servicio = mock(ClienteService.class);

		ClienteController controlador = new ClienteController();
		controlador.clienteService = servicio;
		controlador.clienteMapper = new ClienteMapper();

		clienteHttp = MockMvcBuilders.standaloneSetup(controlador)
				.setControllerAdvice(new ManejadorExcepciones())
				.setValidator(new SpringValidatorAdapter(validadores.getValidator())).build();
	}

	@AfterEach
	void cerrar() {
		validadores.close();
	}

	@Test
	void elClienteQueNoExisteDevuelve404ConSuMotivoDentro() throws Exception {
		when(servicio.obtenerPorId(99)).thenReturn(Optional.empty());

		// Antes esto salia con notFound().build(), es decir, un 404 con el cuerpo vacio: el
		// que lo recibia no sabia si le faltaba la ruta o el registro.
		clienteHttp.perform(get("/cliente/99")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("99")))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void unaRutaQueNoExisteEs404YNoUn400Enganoso() throws Exception {
		// Esta es la prueba del fallo que costo semanas y que quedo escrito en
		// docu/fallos_master.txt: al no existir la ruta, la peticion encajaba en
		// @GetMapping("/{id}"), fallaba al convertir el texto a int y salia un 400. Un 400
		// dice "has mandado mal los parametros" y manda a revisar el JavaScript, donde no
		// habia nada que arreglar. Lo honesto es un 404, que manda al backend.
		clienteHttp.perform(get("/cliente/no-soy-un-numero")).andExpect(status().isNotFound());
	}

	@Test
	void elFalloDeBaseDeDatosNoFiltraLoQuePasaPorDebajo() throws Exception {
		when(servicio.obtenerPorId(anyInt()))
				.thenThrow(new DataAccessResourceFailureException("SQL secreto indice interno"));

		clienteHttp.perform(get("/cliente/1")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Error al acceder a la base de datos"))
				.andExpect(content().string(Matchers.not(Matchers.containsString("SQL secreto"))));
	}

	@Test
	void elNifRepetidoEs409ConMotivo() throws Exception {
		when(servicio.crear(any(Cliente.class))).thenThrow(new DuplicateKeyException("uk nif_cif"));

		// El controlador ya no atrapa esta excepcion: sube hasta el manejador global, que es
		// quien decide que un dato duplicado son 409. Antes ese 409 iba sin cuerpo.
		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("Ya existe")));
	}

	@Test
	void elNifRepetidoDiceQueEsElNifYNoUnDatoCualquiera() throws Exception {
		when(servicio.crear(any(Cliente.class)))
				.thenThrow(new NifCifDuplicadoException(new RuntimeException("uk")));

		// El repositorio ha mirado el nombre del indice para saber que era el NIF y no el
		// numero de una factura. Sin eso, los dos casos salian con el mismo mensaje generico.
		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("NIF/CIF")));
	}

	@Test
	void elClienteConFacturasDiceQueSonFacturas() throws Exception {
		org.mockito.Mockito.doThrow(new ClienteConFacturasException(new RuntimeException("fk")))
				.when(servicio).eliminar(7);

		clienteHttp.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/cliente/7"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("facturas")));
	}

	@Test
	void elClienteQueYaNoEstaNoSaleComoDatosRelacionados() throws Exception {
		// El manejador generico de integridad respondia "hay datos relacionados", que dice lo
		// contrario de lo que pasa: no sobran datos relacionados, falta el cliente.
		//
		// Lo que se comprueba aqui es el MENSAJE, no el codigo: el 409 se conserva a proposito
		// porque es el que ya devolvia el generico y el que el formulario de facturas trata.
		// Arreglar el texto no es motivo para mover el codigo y romper a quien lo consume.
		when(servicio.crear(any(Cliente.class)))
				.thenThrow(new ClienteInexistenteException(new RuntimeException("fk")));

		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("ya no existe")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("datos relacionados"))));
	}

	@Test
	void unaRestriccionDesconocidaSigueSaliendoGenericaYSinFiltrar() throws Exception {
		// La red final tiene que seguir ahi: si manana alguien anade un indice y nadie lo
		// traduce, el mensaje sera generico pero NUNCA filtrara el texto de MySQL.
		when(servicio.crear(any(Cliente.class))).thenThrow(new DuplicateKeyException(
				"Duplicate entry 'X' for key 'clientes.indice_que_nadie_ha_traducido'"));

		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ya existe un registro con ese dato"))
				.andExpect(content().string(Matchers.not(Matchers.containsString("indice_que_nadie"))));
	}

	@Test
	void unaEmpresaSePuedeDarDeAltaComoCliente() throws Exception {
		Cliente empresa = new Cliente(2, "Ejemplo S.L.", "B12345674", "Calle Mayor 15", "28001",
				"Madrid", "Madrid", "912345678", "empresa@ejemplo.es", LocalDate.of(2026, 1, 15));
		when(servicio.crear(any(Cliente.class))).thenReturn(empresa);

		// El fallo que bloqueaba el proyecto: el patron anterior solo admitia DNI de persona
		// fisica, asi que en una aplicacion de FACTURACION no se podia registrar una empresa.
		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON)
				.content(CLIENTE_VALIDO.replace("12345678Z", "B12345674")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nifCif").value("B12345674"));
	}

	@Test
	void elDocumentoConLaLetraCambiadaDiceCualEraLaBuena() throws Exception {
		// 12345678A tiene la forma correcta, asi que el navegador lo deja pasar: esto solo lo
		// puede cazar el servidor, y por eso el motivo tiene que llegar hasta el campo.
		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON)
				.content(CLIENTE_VALIDO.replace("12345678Z", "12345678A")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nifCif").value(Matchers.containsString("Z")));

		org.mockito.Mockito.verify(servicio, org.mockito.Mockito.never()).crear(any(Cliente.class));
	}

	@Test
	void losErroresDeValidacionVienenCampoACampo() throws Exception {
		String sinNombre = CLIENTE_VALIDO.replace("\"nombre\":\"Ana Gil Paz\"", "\"nombre\":\"\"");

		// Sin BindingResult en el controlador, Spring lanza MethodArgumentNotValidException y
		// el manejador la devuelve con el motivo de CADA campo en la propiedad "errores".
		// Sin ese mapa, el formulario sabe que algo esta mal pero no cual de los ocho campos.
		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON).content(sinNombre))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errores.nombre").exists());
	}

	@Test
	void losCamposObligatoriosDeLaDireccionSeParanAquiYNoEnMysql() throws Exception {

		String sinDireccion = CLIENTE_VALIDO
				.replace("\"direccion\":\"Calle Mayor 15\"", "\"direccion\":\"\"")
				.replace("\"poblacion\":\"Madrid\"", "\"poblacion\":\"\"")
				.replace("\"provincia\":\"Madrid\"", "\"provincia\":\"\"");

		// Las tres columnas son NOT NULL y el formulario las marca obligatorias, pero el DTO
		// solo les ponia un @Size. Quien no pasa por el formulario las mandaba vacias, la
		// validacion las dejaba pasar y el fallo salia de MySQL, que acababa respondiendo
		// <<hay datos relacionados>>: justo lo contrario de lo que ocurre.
		clienteHttp.perform(post("/cliente").contentType(MediaType.APPLICATION_JSON)
				.content(sinDireccion))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.direccion").exists())
				.andExpect(jsonPath("$.errores.poblacion").exists())
				.andExpect(jsonPath("$.errores.provincia").exists());

		// Lo que de verdad fija esta prueba: que no llega a bajar. Si llegara, el 409 volveria.
		org.mockito.Mockito.verify(servicio, org.mockito.Mockito.never()).crear(any(Cliente.class));
	}

	@Test
	void listarUltimosVuelveAResponder() throws Exception {
		Cliente ana = new Cliente(1, "Ana Gil Paz", "12345678Z", "Calle Mayor 15", "28001", "Madrid",
				"Madrid", "612345678", "ana@ejemplo.es", LocalDate.of(2026, 1, 15));
		when(servicio.listarUltimos(100)).thenReturn(List.of(ana));

		// El endpoint que se perdio en un merge y dejo el desplegable de "Nueva factura"
		// vacio, es decir, sin poder crearse ninguna factura.
		clienteHttp.perform(get("/cliente/listar-ultimos").param("limite", "100"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Ana Gil Paz"));
	}

	@Test
	void elLimitePedidoSeAcotaEnSilencio() throws Exception {
		when(servicio.listarUltimos(anyInt())).thenReturn(List.of());

		// Pedir 9999 no es un error del que haya que avisar: se recorta a LIMITE_MAX y se
		// sigue. Lo que no puede pasar es que alguien se traiga la tabla entera.
		clienteHttp.perform(get("/cliente/listar-ultimos").param("limite", "9999"))
				.andExpect(status().isOk());

		org.mockito.Mockito.verify(servicio).listarUltimos(100);
	}

}
