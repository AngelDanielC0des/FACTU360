package edu.xtd.facturacion360.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.ConceptoRequest;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.service.FacturaServiceImpl;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Solo para una instancia temporal identificada expresamente; no carga application.properties. */
@Testcontainers
class FacturaGuardadoIntegracionTests {

	/**
	 * La misma version que produccion. Con "mysql:latest" una subida de version de la
	 * imagen cambiaria el comportamiento de las pruebas sin que nadie tocara el codigo.
	 *
	 * SIN diamante: en Testcontainers 2.x MySQLContainer dejo de llevar parametro de tipo.
	 * Escribir MySQLContainer<> es "type MySQLContainer does not take parameters".
	 */
	@Container
	static final MySQLContainer CONTENEDOR = new MySQLContainer("mysql:8.4")
			.withDatabaseName("facturas_pruebas")
			.withUsername("pruebas_facturas")
			.withPassword("pruebas_facturas");

	static DriverManagerDataSource datos;
	static JdbcTemplate jdbc;
	static ValidatorFactory validadores;
	FacturaRepositoryJdbcImpl repositorio;
	FacturaServiceImpl servicio;

	@BeforeAll
	static void prepararBaseAislada() throws Exception {
		datos = new DriverManagerDataSource(CONTENEDOR.getJdbcUrl(),
				CONTENEDOR.getUsername(), CONTENEDOR.getPassword());
		jdbc = new JdbcTemplate(datos);

		// Ninguna escritura antes de comprobar que se esta hablando con el contenedor y no
		// con otra cosa. Antes esto se comprobaba con @@server_uuid y @@datadir porque el
		// servidor lo levantaba una persona y un puerto mal escrito podia apuntar a una base
		// real. Con el contenedor esa clase de error ya no cabe —la URL la da el propio
		// contenedor—, pero la comprobacion se mantiene: es barata y es la que convierte un
		// "apunta a otro sitio" en un fallo de prueba en lugar de en un DELETE.
		assertEquals(CONTENEDOR.getDatabaseName(),
				jdbc.queryForObject("SELECT DATABASE()", String.class));
		// El v3 y no el v1: es el volcado vigente, el del numero mas alto, como dice el
		// README. El v1 es anterior a la PR #43 y su tabla conceptos no tiene clave_regimen
		// ni calificacion, que el repositorio si inserta desde entonces; cargarlo da
		// "Unknown column 'clave_regimen' in 'field list'" en catorce de estas pruebas.
		// Llevaba roto desde la PR #43 y no se veia porque la clase entera se saltaba.
		String esquema = Files.readString(Path.of("src/main/resources/docu/backupFacturacion360v3.sql"));
		// El orden importa: clientes y facturas van primero porque las otras dos las
		// referencian con clave ajena. desglose_impositivo entro con la PR #43 y faltaba
		// aqui, asi que el INSERT del desglose moria con "Table ... doesn't exist".
		for (String tabla : List.of("clientes", "facturas", "conceptos", "desglose_impositivo")) {
			var definicion = Pattern.compile("CREATE TABLE `" + tabla + "` \\(.*?;", Pattern.DOTALL).matcher(esquema);
			assertTrue(definicion.find());
			jdbc.execute(definicion.group().replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"));
		}
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
				+ "WHERE table_schema=DATABASE() AND table_name IN ('facturas','conceptos') AND engine='InnoDB'", Integer.class));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
				+ "WHERE table_schema=DATABASE() AND table_name='facturas' AND index_name='num_factura_UNIQUE' AND non_unique=0", Integer.class));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.key_column_usage "
				+ "WHERE table_schema=DATABASE() AND table_name='conceptos' AND column_name='idfactura' "
				+ "AND referenced_table_name='facturas' AND referenced_column_name='idfactura'", Integer.class));
		validadores = Validation.buildDefaultValidatorFactory();
	}

	@AfterAll
	static void cerrarValidador() {
		if (validadores != null) validadores.close();
	}

	@BeforeEach
	void prepararCaso() {
		// Limpieza exclusiva de las filas sintéticas del servidor comprobado anteriormente.
		// De hija a madre, o las claves ajenas rechazan el borrado.
		jdbc.update("DELETE FROM desglose_impositivo");
		jdbc.update("DELETE FROM conceptos");
		jdbc.update("DELETE FROM facturas");
		jdbc.update("DELETE FROM clientes");
		jdbc.update("INSERT INTO clientes (idcliente, nombre, nif_cif, direccion, poblacion, provincia) "
				+ "VALUES (1, 'Cliente de prueba', 'PRUEBA-1', 'Dirección de prueba', 'Población', 'Provincia')");
		repositorio = spy(new FacturaRepositoryJdbcImpl());
		repositorio.jdbcTemplate = jdbc;
		repositorio.facturaRowMapper = new FacturaRowMapper();
		repositorio.conceptoFacturaRowMapper = new ConceptoFacturaRowMapper();
		servicio = new FacturaServiceImpl();
		ReflectionTestUtils.setField(servicio, "facturaRepository", repositorio);
		ReflectionTestUtils.setField(servicio, "validador", validadores.getValidator());
		ReflectionTestUtils.setField(servicio, "gestorTransacciones", new DataSourceTransactionManager(datos));
	}

	@Test
	void primerNumeroYBorradorVacio() {
		Factura factura = servicio.crear(peticion(2026, "BORRADOR", List.of()));
		assertEquals("F-2026-0001", factura.numeroFactura());
		assertEquals(new BigDecimal("0.00"), factura.total());
		assertEquals(1, contar("facturas"));
		assertEquals(0, contar("conceptos"));
	}

	@Test
	void edicionReemplazaLineasYConservaNumeroSinTocarOtraFactura() {
		Factura anterior = servicio.crear(peticion(2026, "BORRADOR", List.of(linea("5", 1, "0", "21"), linea("6", 1, "0", "21"))));
		Factura ajena = servicio.crear(peticion(2026, "EMITIDA", List.of(linea("8", 1, "0", "0"))));
		var ajenaAntes = instantanea(ajena.idFactura());
		jdbc.update("INSERT INTO clientes (idcliente,nombre,nif_cif,direccion,poblacion,provincia) VALUES (2,'Otro','PRUEBA-2','Calle','Población','Provincia')");
		var idsAntes = repositorio.buscarConceptos(anterior.idFactura()).stream().map(ConceptoFactura::idConcepto).toList();
		clearInvocations(repositorio);
		FacturaRequest cambios = new FacturaRequest(2, LocalDate.of(2026, 12, 31), "BORRADOR", "Actualizada",
				List.of(linea("19.99", 3, "10", "21"), linea("5", 2, "0", "10")));
		Factura guardada = servicio.editarBorrador(anterior.idFactura(), cambios);
		assertEquals(anterior.numeroFactura(), guardada.numeroFactura());
		assertEquals(anterior.idFactura(), guardada.idFactura());
		assertEquals(2, guardada.idCliente());
		assertEquals("Actualizada", guardada.observaciones());
		assertEquals(cambios.fechaEmision(), guardada.fechaEmision());
		assertEquals(new BigDecimal("63.97"), guardada.subtotal());
		assertEquals(new BigDecimal("12.33"), guardada.importeIva());
		assertEquals(new BigDecimal("76.30"), guardada.total());
		assertEquals(2, repositorio.buscarConceptos(guardada.idFactura()).size());
		assertTrue(repositorio.buscarConceptos(guardada.idFactura()).stream().noneMatch(c -> idsAntes.contains(c.idConcepto())));
		assertEquals(ajenaAntes, instantanea(ajena.idFactura()));
		assertEquals(guardada, servicio.editarBorrador(guardada.idFactura(), cambios));
		Factura vacia = servicio.editarBorrador(guardada.idFactura(), peticion(2026, "BORRADOR", List.of()));
		assertEquals(new BigDecimal("0.00"), vacia.total());
		assertTrue(repositorio.buscarConceptos(vacia.idFactura()).isEmpty());
		assertEquals(2, contar("facturas"));
		verify(repositorio, never()).obtenerUltimoNumero(anyInt());
		verify(repositorio, never()).insertar(any());
	}

	@Test
	void edicionRechazadaNoModificaEstadosNoEditables() {
		for (String estado : List.of("EMITIDA", "ANULADA")) {
			Factura factura = servicio.crear(peticion(2026, estado, List.of(linea("10", 1, "0", "0"))));
			var antes = instantanea(factura.idFactura());
			ResponseStatusException error = assertThrows(ResponseStatusException.class,
					() -> servicio.editarBorrador(factura.idFactura(), peticion(2026, "BORRADOR", List.of())));
			assertEquals(409, error.getStatusCode().value());
			assertEquals(antes, instantanea(factura.idFactura()));
		}
	}

	@Test
	void edicionRechazaAnoYClienteInexistenteSinPerderLineas() {
		Factura factura = servicio.crear(peticion(2026, "BORRADOR", List.of(linea("10", 1, "0", "0"))));
		var antes = instantanea(factura.idFactura());
		assertEquals(400, assertThrows(ResponseStatusException.class,
				() -> servicio.editarBorrador(factura.idFactura(), peticion(2027, "BORRADOR", List.of()))).getStatusCode().value());
		assertEquals(antes, instantanea(factura.idFactura()));
		// ClienteInexistenteException y no DataIntegrityViolationException: desde 08bbfee
		// el repositorio traduce QUE restriccion ha saltado, para poder dar un mensaje que
		// diga que hacer. No hereda de la de Spring —extiende RuntimeException— asi que
		// esperar la generica ya no vale. La traduccion es el contrato de hoy y es el que
		// se fija aqui.
		assertThrows(FacturaRepository.ClienteInexistenteException.class,
				() -> servicio.editarBorrador(factura.idFactura(),
						new FacturaRequest(999, LocalDate.of(2026, 1, 1), "BORRADOR", "", List.of())));
		assertEquals(antes, instantanea(factura.idFactura()));
		assertEquals(404, assertThrows(ResponseStatusException.class,
				() -> servicio.editarBorrador(999999, peticion(2026, "BORRADOR", List.of()))).getStatusCode().value());
	}

	@Test
	void edicionFalloSqlTrasBorrarEInsertarPrimeraLineaRestauraTodo() {
		Factura factura = servicio.crear(peticion(2026, "BORRADOR", List.of(linea("10", 1, "0", "0"))));
		var antes = instantanea(factura.idFactura());
		clearInvocations(repositorio);
		doAnswer(invocacion -> {
			List<ConceptoFactura> lineas = new ArrayList<>(invocacion.getArgument(1));
			ConceptoFactura segunda = lineas.get(1);
			lineas.set(1, new ConceptoFactura(0, segunda.descripcion(), segunda.cantidad(), segunda.precioUnitario(),
					segunda.descuento(), segunda.porcentajeIva(), segunda.importeIva(), segunda.baseImponible(), null));
			FacturaRepositoryJdbcImpl escritor = new FacturaRepositoryJdbcImpl();
			escritor.jdbcTemplate = jdbc;
			escritor.insertarConceptos(invocacion.getArgument(0), lineas);
			return null;
		}).when(repositorio).insertarConceptos(anyInt(), anyList());
		assertThrows(DataIntegrityViolationException.class, () -> servicio.editarBorrador(factura.idFactura(),
				peticion(2026, "BORRADOR", List.of(linea("2", 3, "0", "21"), linea("3", 4, "0", "0")))));
		assertEquals(antes, instantanea(factura.idFactura()));
		verify(repositorio).eliminarConceptos(factura.idFactura());
		verify(repositorio, never()).obtenerUltimoNumero(anyInt());
		verify(repositorio, never()).insertar(any());
	}

	@Test
	void edicionEsperaBloqueoYRevalidaEstadoDentroDeTransaccion() throws Exception {
		Factura factura = servicio.crear(peticion(2026, "BORRADOR", List.of(linea("10", 1, "0", "0"))));
		var cabeceraEsperada = jdbc.queryForMap("SELECT * FROM facturas WHERE idfactura=?", factura.idFactura());
		cabeceraEsperada.put("estado", "EMITIDA");
		var conceptosEsperados = jdbc.queryForList("SELECT * FROM conceptos WHERE idfactura=? ORDER BY idconcepto", factura.idFactura());
		CountDownLatch entrando = new CountDownLatch(1);
		doAnswer(invocacion -> {
			assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
			entrando.countDown();
			return invocacion.callRealMethod();
		}).when(repositorio).buscarPorIdParaActualizar(factura.idFactura());
		var tareas = Executors.newSingleThreadExecutor();
		try (var conexion = datos.getConnection()) {
			conexion.setAutoCommit(false);
			try {
				try (var cambio = conexion.prepareStatement("UPDATE facturas SET estado='EMITIDA' WHERE idfactura=?")) {
					cambio.setInt(1, factura.idFactura());
					cambio.executeUpdate();
				}
				var futura = tareas.submit(() -> servicio.editarBorrador(factura.idFactura(), peticion(2026, "BORRADOR", List.of())));
				assertTrue(entrando.await(5, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> futura.get(200, TimeUnit.MILLISECONDS));
				conexion.commit();
				ExecutionException error = assertThrows(ExecutionException.class, () -> futura.get(5, TimeUnit.SECONDS));
				assertEquals(409, ((ResponseStatusException) error.getCause()).getStatusCode().value());
				assertEquals(List.of(List.of(cabeceraEsperada), conceptosEsperados), instantanea(factura.idFactura()));
			} finally {
				conexion.rollback();
			}
		} finally {
			tareas.shutdownNow();
			assertTrue(tareas.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void edicionesSimultaneasNoMezclanCabeceraNiConceptos() throws Exception {
		Factura factura = servicio.crear(peticion(2026, "BORRADOR", List.of()));
		CyclicBarrier barrera = new CyclicBarrier(2);
		doAnswer(invocacion -> {
			barrera.await(5, TimeUnit.SECONDS);
			return invocacion.callRealMethod();
		}).when(repositorio).buscarPorIdParaActualizar(factura.idFactura());
		var tareas = Executors.newFixedThreadPool(2);
		try {
			var primera = tareas.submit(() -> servicio.editarBorrador(factura.idFactura(), new FacturaRequest(1,
					LocalDate.of(2026, 1, 1), "BORRADOR", "A", List.of(linea("10", 1, "0", "0"), linea("11", 1, "0", "0")))));
			var segunda = tareas.submit(() -> servicio.editarBorrador(factura.idFactura(), new FacturaRequest(1,
					LocalDate.of(2026, 2, 1), "BORRADOR", "B", List.of(linea("20", 2, "0", "0"), linea("21", 2, "0", "0")))));
			primera.get(10, TimeUnit.SECONDS);
			segunda.get(10, TimeUnit.SECONDS);
			Factura guardada = repositorio.buscarPorId(factura.idFactura());
			boolean esPrimera = guardada.observaciones().equals("A");
			assertTrue(esPrimera || guardada.observaciones().equals("B"));
			assertEquals(factura.numeroFactura(), guardada.numeroFactura());
			assertEquals(esPrimera ? new BigDecimal("21.00") : new BigDecimal("82.00"), guardada.total());
			assertEquals(LocalDate.of(2026, esPrimera ? 1 : 2, 1), guardada.fechaEmision());
			var conceptos = repositorio.buscarConceptos(factura.idFactura());
			assertEquals(2, conceptos.size());
			assertEquals(esPrimera ? new BigDecimal("10.00") : new BigDecimal("20.00"), conceptos.get(0).precioUnitario());
			assertEquals(esPrimera ? new BigDecimal("11.00") : new BigDecimal("21.00"), conceptos.get(1).precioUnitario());
			assertTrue(conceptos.stream().allMatch(c -> c.cantidad() == (esPrimera ? 1 : 2)));
		} finally {
			tareas.shutdownNow();
			assertTrue(tareas.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private List<Object> instantanea(int idFactura) {
		return List.of(jdbc.queryForList("SELECT * FROM facturas WHERE idfactura=?", idFactura),
				jdbc.queryForList("SELECT * FROM conceptos WHERE idfactura=? ORDER BY idconcepto", idFactura));
	}

	@Test
	void siguienteNumeroSinRellenarHuecosYAnosIndependientes() {
		manual("F-2026-0002", "EMITIDA");
		manual("F-2026-0008", "BORRADOR");
		assertEquals("F-2026-0009", servicio.crear(peticion(2026, "BORRADOR", List.of())).numeroFactura());
		assertEquals("F-2027-0001", servicio.crear(peticion(2027, "BORRADOR", List.of())).numeroFactura());
		assertEquals(4, contar("facturas"));
	}

	@Test
	void ignoraFormatosAjenosSinAlterarlos() {
		List<String> numeros = List.of("MANUAL-9999", "F-2026-999", "F-2026-10000", "F-2026-ABCD", "F-2026-0000", "X-2026-9999");
		for (String numero : numeros) manual(numero, "BORRADOR");
		assertEquals("F-2026-0001", servicio.crear(peticion(2026, "BORRADOR", List.of())).numeroFactura());
		for (String numero : numeros) {
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM facturas WHERE num_factura=?", Integer.class, numero));
		}
	}

	@Test
	void reservaManualesTodosLosEstadosYVariantesDeMayusculas() {
		int numero = 1;
		for (String estado : List.of("BORRADOR", "EMITIDA", "ANULADA")) {
			manual("F-2026-000" + numero, estado);
			assertEquals(numero, repositorio.obtenerUltimoNumero(2026));
			numero++;
		}
		manual("f-2026-0009", "ANULADA");
		assertEquals("F-2026-0010", servicio.crear(peticion(2026, "BORRADOR", List.of())).numeroFactura());
	}

	@Test
	void rechazaElLimiteSinModificarElHistorico() {
		manual("F-2026-9999", "ANULADA");
		assertThrows(ResponseStatusException.class, () -> servicio.crear(peticion(2026, "BORRADOR", List.of())));
		assertEquals(1, contar("facturas"));
	}

	@Test
	void guardaUnaLineaConSuIdFacturaYTotalesDelServidor() {
		Factura factura = servicio.crear(peticion(2026, "EMITIDA", List.of(linea("19.99", 3, "10", "21"))));
		assertTrue(factura.idFactura() > 0);
		assertEquals(new BigDecimal("65.30"), factura.total());
		assertEquals(new BigDecimal("53.97"), factura.subtotal());
		List<ConceptoFactura> guardados = repositorio.buscarConceptos(factura.idFactura());
		assertEquals(1, guardados.size());
		assertEquals(factura.importeIva(), guardados.get(0).importeIva());
		assertEquals(factura.total(), guardados.get(0).total());
	}

	@Test
	void guardaVariasLineasYLaSumaRedondeada() {
		ConceptoRequest concepto = linea("0.03", 1, "0", "21");
		Factura factura = servicio.crear(peticion(2026, "EMITIDA", List.of(concepto, concepto)));
		assertEquals(new BigDecimal("0.08"), factura.total());
		assertEquals(new BigDecimal("0.02"), factura.importeIva());
		assertEquals(2, repositorio.buscarConceptos(factura.idFactura()).size());
	}

	@Test
	void falloSqlEnSegundaLineaRevierteCabeceraYPrimeraLinea() {
		doAnswer(invocacion -> {
			int idFactura = invocacion.getArgument(0);
			List<ConceptoFactura> lineas = invocacion.getArgument(1);
			List<ConceptoFactura> lineasConFallo = new ArrayList<>(lineas);
			ConceptoFactura segunda = lineas.get(1);
			lineasConFallo.set(1, new ConceptoFactura(0, segunda.descripcion(), segunda.cantidad(),
					segunda.precioUnitario(), segunda.descuento(), segunda.porcentajeIva(),
					segunda.importeIva(), segunda.baseImponible(), null));
			FacturaRepositoryJdbcImpl escritorReal = new FacturaRepositoryJdbcImpl();
			escritorReal.jdbcTemplate = jdbc;
			escritorReal.insertarConceptos(idFactura, lineasConFallo);
			return null;
		}).when(repositorio).insertarConceptos(anyInt(), anyList());
		assertThrows(DataIntegrityViolationException.class,
				() -> servicio.crear(peticion(2026, "EMITIDA", List.of(linea("1", 1, "0", "0"), linea("2", 1, "0", "0")))));
		assertEquals(0, contar("facturas"));
		assertEquals(0, contar("conceptos"));
		verify(repositorio, times(1)).obtenerUltimoNumero(2026);
	}

	@Test
	void dosAltasSimultaneasColisionanYConfirmanNumerosDistintos() throws Exception {
		CyclicBarrier barrera = new CyclicBarrier(2);
		AtomicInteger consultas = new AtomicInteger();
		doAnswer(invocacion -> {
			int numero = (int) invocacion.callRealMethod();
			if (consultas.incrementAndGet() <= 2) barrera.await(10, TimeUnit.SECONDS);
			return numero;
		}).when(repositorio).obtenerUltimoNumero(2026);
		var ejecutor = Executors.newFixedThreadPool(2);
		try {
			var primera = ejecutor.submit(() -> servicio.crear(peticion(2026, "EMITIDA", List.of(linea("10", 1, "0", "21")))));
			var segunda = ejecutor.submit(() -> servicio.crear(peticion(2026, "EMITIDA", List.of(linea("20", 1, "0", "21")))));
			Factura facturaPrimera = primera.get(20, TimeUnit.SECONDS);
			Factura facturaSegunda = segunda.get(20, TimeUnit.SECONDS);
			assertEquals(Set.of("F-2026-0001", "F-2026-0002"), Set.of(facturaPrimera.numeroFactura(), facturaSegunda.numeroFactura()));
			assertEquals(3, consultas.get());
			assertEquals(2, contar("facturas"));
			assertEquals(2, contar("conceptos"));
		} finally {
			ejecutor.shutdownNow();
			assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private void manual(String numero, String estado) {
		repositorio.insertar(new Factura(0, 1, null, numero, LocalDate.of(2026, 1, 1), estado, "",
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
	}

	@Test
	void sugerenciasBuscanDentroDelTextoYEscapanComodines() {
		historico(2026, "Servicio de mantenimiento mensual", "95");
		historico(2026, "Descuento 50% especial", "20");
		historico(2026, "Clave_a especial", "30");
		historico(2026, "Ruta\\a especial", "40");
		assertEquals("Servicio de mantenimiento mensual", servicio.buscarSugerenciasConceptos("MANTEN", 8).get(0).descripcion());
		assertTrue(servicio.buscarSugerenciasConceptos("inexistente", 8).isEmpty());
		for (String texto : List.of("50%", "_a", "\\a")) {
			assertEquals(1, servicio.buscarSugerenciasConceptos(texto, 8).size());
		}
		assertTrue(servicio.buscarSugerenciasConceptos("%' OR 1=1 --", 8).isEmpty());
	}

	@Test
	void sugerenciasDeduplicanYEligenFechaAntesQueIdSinModificarHistorico() {
		Factura reciente = historico(2028, "Mantenimiento web", "120");
		historico(2027, "Mantenimiento servidor", "75");
		Factura antigua = historico(2026, "mANTENIMIENTO WEB", "90");
		assertTrue(antigua.idFactura() > reciente.idFactura());
		jdbc.update("UPDATE conceptos SET descripcion='  Mantenimiento web  ', descuento=5 WHERE idfactura=?", reciente.idFactura());
		var facturasAntes = jdbc.queryForList("SELECT * FROM facturas ORDER BY idfactura");
		var conceptosAntes = jdbc.queryForList("SELECT * FROM conceptos ORDER BY idconcepto");
		var sugerencias = servicio.buscarSugerenciasConceptos("manten", 8);
		assertEquals(List.of("Mantenimiento web", "Mantenimiento servidor"), sugerencias.stream().map(s -> s.descripcion()).toList());
		assertEquals(new BigDecimal("120.00"), sugerencias.get(0).precioUnitario());
		assertEquals(new BigDecimal("5.00"), sugerencias.get(0).descuento());
		assertEquals(new BigDecimal("21.00"), sugerencias.get(0).porcentajeIva());
		assertEquals(facturasAntes, jdbc.queryForList("SELECT * FROM facturas ORDER BY idfactura"));
		assertEquals(conceptosAntes, jdbc.queryForList("SELECT * FROM conceptos ORDER BY idconcepto"));
	}

	@Test
	void sugerenciasDesempatanPorFacturaYConceptoDescendentes() {
		historico(2026, "Mantenimiento", "90");
		ConceptoRequest primera = new ConceptoRequest("Mantenimiento", 9, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.TEN);
		ConceptoRequest ultima = new ConceptoRequest("MANTENIMIENTO", 7, new BigDecimal("120"), new BigDecimal("5"), new BigDecimal("21"));
		servicio.crear(peticion(2026, "EMITIDA", List.of(primera, ultima)));
		var sugerencias = servicio.buscarSugerenciasConceptos("manten", 8);
		assertEquals(1, sugerencias.size());
		assertEquals("MANTENIMIENTO", sugerencias.get(0).descripcion());
		assertEquals(new BigDecimal("120.00"), sugerencias.get(0).precioUnitario());
		assertEquals(new BigDecimal("5.00"), sugerencias.get(0).descuento());
		assertEquals(new BigDecimal("21.00"), sugerencias.get(0).porcentajeIva());
	}

	@Test
	void sugerenciasAplicanLimiteDespuesDeDeduplicarYMaximoVeinte() {
		List<ConceptoRequest> conceptos = new ArrayList<>();
		for (int numero = 1; numero <= 25; numero++) {
			conceptos.add(new ConceptoRequest("Servicio " + numero, 1, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
		}
		for (int numero = 0; numero < 10; numero++) conceptos.add(conceptos.getLast());
		servicio.crear(peticion(2026, "EMITIDA", conceptos));
		var sugerencias = servicio.buscarSugerenciasConceptos("Servicio", 8);
		assertEquals(8, sugerencias.size());
		assertEquals("Servicio 25", sugerencias.getFirst().descripcion());
		assertEquals("Servicio 18", sugerencias.getLast().descripcion());
		assertEquals(20, servicio.buscarSugerenciasConceptos("Servicio", Integer.MAX_VALUE).size());
	}

	@Test
	void sugerenciasNoInventanImportesAusentesDelUsoMasReciente() {
		historico(2026, "Servicio", "90");
		Factura ultima = historico(2027, "Servicio", "120");
		jdbc.update("UPDATE conceptos SET precio_unitario=NULL, descuento=NULL, porcentaje_iva=NULL WHERE idfactura=?", ultima.idFactura());
		var sugerencia = servicio.buscarSugerenciasConceptos("Servicio", 8).getFirst();
		assertNull(sugerencia.precioUnitario());
		assertNull(sugerencia.descuento());
		assertNull(sugerencia.porcentajeIva());
	}

	private Factura historico(int anio, String descripcion, String precio) {
		ConceptoRequest concepto = new ConceptoRequest(descripcion, 9, new BigDecimal(precio), BigDecimal.ZERO, new BigDecimal("21"));
		return servicio.crear(peticion(anio, "EMITIDA", List.of(concepto)));
	}

	private int contar(String tabla) {
		assertTrue(List.of("facturas", "conceptos").contains(tabla));
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
	}

	private FacturaRequest peticion(int anio, String estado, List<ConceptoRequest> conceptos) {
		return new FacturaRequest(1, LocalDate.of(anio, 9, 14), estado, "Prueba aislada", conceptos);
	}

	private ConceptoRequest linea(String precio, int cantidad, String descuento, String iva) {
		return new ConceptoRequest("Servicio", cantidad, new BigDecimal(precio), new BigDecimal(descuento), new BigDecimal(iva));
	}
}
