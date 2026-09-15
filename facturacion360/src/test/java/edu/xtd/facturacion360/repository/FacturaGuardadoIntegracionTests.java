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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.ConceptoRequest;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.service.FacturaServiceImpl;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Solo para una instancia temporal identificada expresamente; no carga application.properties. */
@EnabledIfSystemProperty(named = "facturas.mysql.puerto", matches = "[0-9]+")
class FacturaGuardadoIntegracionTests {
	static DriverManagerDataSource datos;
	static JdbcTemplate jdbc;
	static ValidatorFactory validadores;
	FacturaRepositoryJdbcImpl repositorio;
	FacturaServiceImpl servicio;

	@BeforeAll
	static void prepararBaseAislada() throws Exception {
		int puerto = Integer.parseInt(System.getProperty("facturas.mysql.puerto"));
		assertTrue(puerto >= 10000 && puerto <= 65535);
		String servidor = System.getProperty("facturas.mysql.servidor");
		String directorio = System.getProperty("facturas.mysql.directorio");
		String clave = System.getProperty("facturas.mysql.clave");
		assertNotNull(servidor);
		assertNotNull(directorio);
		assertTrue(directorio.startsWith("/tmp/facturas-mysql-"));
		assertNotNull(clave);
		datos = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + puerto
				+ "/facturas_pruebas?sslMode=DISABLED&allowPublicKeyRetrieval=true", "pruebas_facturas", clave);
		jdbc = new JdbcTemplate(datos);
		// Ninguna escritura antes de comprobar la identidad y ubicación del servidor temporal.
		assertEquals(servidor, jdbc.queryForObject("SELECT @@server_uuid", String.class));
		assertEquals(directorio, jdbc.queryForObject("SELECT @@datadir", String.class));
		assertEquals("facturas_pruebas", jdbc.queryForObject("SELECT DATABASE()", String.class));
		String esquema = Files.readString(Path.of("src/main/resources/docu/backupFacturacion360v1.sql"));
		for (String tabla : List.of("clientes", "facturas", "conceptos")) {
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
		for (String estado : List.of("BORRADOR", "EMITIDA", "PAGADA", "ANULADA")) {
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
