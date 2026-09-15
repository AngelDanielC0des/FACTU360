package edu.xtd.facturacion360.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.ConceptoRequest;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.repository.FacturaRepository;
import edu.xtd.facturacion360.repository.FacturaRepository.NumeroFacturaDuplicadoException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Pruebas de cálculo y validación sin contexto Spring ni base de datos. */
class FacturaGuardadoTests {
	static ValidatorFactory fabricaValidadores;
	FacturaServiceImpl servicio;

	@BeforeAll
	static void prepararValidador() {
		fabricaValidadores = Validation.buildDefaultValidatorFactory();
	}

	@AfterAll
	static void cerrarValidador() {
		fabricaValidadores.close();
	}

	@BeforeEach
	void prepararServicio() {
		servicio = new FacturaServiceImpl();
		servicio.validador = fabricaValidadores.getValidator();
	}

	@Test
	void permiteBorradorSinConceptosConTotalesCero() {
		var calculo = servicio.calcularImportes(List.of(), "BORRADOR");
		assertTrue(calculo.conceptos().isEmpty());
		assertEquals(new BigDecimal("0.00"), calculo.subtotal());
		assertEquals(new BigDecimal("0.00"), calculo.importeIva());
		assertEquals(new BigDecimal("0.00"), calculo.total());
	}

	@Test
	void rechazaEmisionSinConceptos() {
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
				() -> servicio.calcularImportes(List.of(), "EMITIDA")).getStatusCode());
	}

	@Test
	void rechazaListaNulaYLineasNulas() {
		assertThrows(ResponseStatusException.class, () -> servicio.calcularImportes(null, "BORRADOR"));
		assertThrows(ResponseStatusException.class,
				() -> servicio.calcularImportes(Arrays.asList((ConceptoRequest) null), "BORRADOR"));
	}

	@Test
	void rechazaEstadoDesconocidoONulo() {
		assertThrows(ResponseStatusException.class, () -> servicio.calcularImportes(List.of(), "OTRO"));
		assertThrows(ResponseStatusException.class, () -> servicio.calcularImportes(List.of(), null));
	}

	@Test
	void calculaDescuentoBaseIvaYTotal() {
		var calculo = servicio.calcularImportes(List.of(concepto(3, "19.99", "10", "21")), "EMITIDA");
		assertEquals(new BigDecimal("53.97"), calculo.subtotal());
		assertEquals(new BigDecimal("11.33"), calculo.importeIva());
		assertEquals(new BigDecimal("65.30"), calculo.total());
		assertEquals(calculo.total(), calculo.conceptos().get(0).total());
	}

	@Test
	void noRedondeaElDescuentoAntesDeCalcularLaBase() {
		var calculo = servicio.calcularImportes(List.of(concepto(1, "0.05", "10", "10")), "BORRADOR");
		assertEquals(new BigDecimal("0.05"), calculo.subtotal());
		assertEquals(new BigDecimal("0.01"), calculo.importeIva());
		assertEquals(new BigDecimal("0.06"), calculo.total());
	}

	@Test
	void sumaElIvaRedondeadoPorLineaNoElDeLaBaseAcumulada() {
		ConceptoRequest linea = concepto(1, "0.03", "0", "21");
		var calculo = servicio.calcularImportes(List.of(linea, linea), "BORRADOR");
		assertEquals(new BigDecimal("0.06"), calculo.subtotal());
		assertEquals(new BigDecimal("0.02"), calculo.importeIva());
		assertEquals(new BigDecimal("0.08"), calculo.total());
	}

	@Test
	void admiteDescuentoCompletoEIvaCero() {
		var calculo = servicio.calcularImportes(List.of(concepto(2, "10.00", "100", "21"),
				concepto(1, "10.00", "0", "0")), "BORRADOR");
		assertEquals(new BigDecimal("0.00"), calculo.conceptos().get(0).total());
		assertEquals(new BigDecimal("10.00"), calculo.total());
		assertEquals(new BigDecimal("0.00"), calculo.importeIva());
	}

	@Test
	void admiteLimitesDelEsquema() {
		ConceptoRequest linea = new ConceptoRequest("a".repeat(50), 1, new BigDecimal("99999999.99"),
				BigDecimal.ZERO, BigDecimal.ZERO);
		assertEquals(new BigDecimal("99999999.99"), servicio.calcularImportes(List.of(linea), "BORRADOR").total());
		assertTrue(servicio.validador.validate(concepto(1, "1.00", "100.00", "99.99")).isEmpty());
	}

	@Test
	void rechazaDesbordamientoDeBaseOTotalDeLinea() {
		for (ConceptoRequest linea : List.of(concepto(2, "99999999.99", "0", "0"),
				concepto(1, "99999999.99", "0", "0.01"),
				concepto(Integer.MAX_VALUE, "99999999.99", "0", "99.99"))) {
			assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
					() -> servicio.calcularImportes(List.of(linea), "BORRADOR")).getStatusCode());
		}
	}

	@Test
	void rechazaDesbordamientoDeSumasAunqueCadaLineaSeaValida() {
		ConceptoRequest linea = concepto(1, "50000000.00", "0", "0");
		assertThrows(ResponseStatusException.class,
				() -> servicio.calcularImportes(List.of(linea, linea), "BORRADOR"));
		ConceptoRequest lineaConIva = concepto(1, "40000000.00", "0", "50");
		assertThrows(ResponseStatusException.class,
				() -> servicio.calcularImportes(List.of(lineaConIva, lineaConIva), "BORRADOR"));
	}

	@Test
	void rechazaEntradasFueraDeRangoOPrecision() {
		for (ConceptoRequest linea : List.of(concepto(0, "1", "0", "0"), concepto(-1, "1", "0", "0"),
				concepto(1, "-0.01", "0", "0"), concepto(1, "1.001", "0", "0"),
				concepto(1, "100000000", "0", "0"), concepto(1, "1", "-1", "0"),
				concepto(1, "1", "100.01", "0"), concepto(1, "1", "0.001", "0"),
				concepto(1, "1", "0", "-1"), concepto(1, "1", "0", "100"),
				concepto(1, "1", "0", "1.001"))) {
			assertFalse(servicio.validador.validate(linea).isEmpty());
			assertThrows(ResponseStatusException.class, () -> servicio.calcularImportes(List.of(linea), "BORRADOR"));
		}
	}

	@Test
	void rechazaCamposObligatoriosNulosYDescripcionInvalida() {
		for (ConceptoRequest linea : List.of(new ConceptoRequest(null, 1, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
				new ConceptoRequest(" ", 1, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
				new ConceptoRequest("a".repeat(51), 1, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
				new ConceptoRequest("Línea", null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
				new ConceptoRequest("Línea", 1, null, BigDecimal.ZERO, BigDecimal.ZERO),
				new ConceptoRequest("Línea", 1, BigDecimal.ONE, null, BigDecimal.ZERO),
				new ConceptoRequest("Línea", 1, BigDecimal.ONE, BigDecimal.ZERO, null))) {
			assertThrows(ResponseStatusException.class, () -> servicio.calcularImportes(List.of(linea), "BORRADOR"));
		}
	}

	@Test
	void validaConceptosAnidadosEnLaPeticion() {
		assertFalse(servicio.validador.validate(peticion(List.of(concepto(0, "1", "0", "0")))).isEmpty());
		assertFalse(servicio.validador.validate(peticion(Arrays.asList((ConceptoRequest) null))).isEmpty());
		assertTrue(servicio.validador.validate(peticion(List.of())).isEmpty());
		assertFalse(servicio.validador.validate(peticion(null)).isEmpty());
	}

	@Test
	void rechazaPeticionesInvalidasAntesDeAbrirLaTransaccion() {
		prepararGuardado();
		assertThrows(ResponseStatusException.class, () -> servicio.crear(peticion(null)));
		assertThrows(ResponseStatusException.class, () -> servicio.crear(null));
		assertThrows(ResponseStatusException.class, () -> servicio.crear(new FacturaRequest(1,
				LocalDate.of(999, 1, 1), "BORRADOR", "", List.of())));
		verifyNoInteractions(servicio.gestorTransacciones, servicio.facturaRepository);
	}

	@Test
	void guardaCabeceraYLineasConTotalesCalculados() {
		prepararGuardado();
		Factura factura = servicio.crear(peticion(List.of(concepto(1, "100", "0", "21"))));
		assertEquals("F-2026-0001", factura.numeroFactura());
		assertEquals(new BigDecimal("100.00"), factura.subtotal());
		assertEquals(new BigDecimal("21.00"), factura.importeIva());
		assertEquals(new BigDecimal("121.00"), factura.total());
		verify(servicio.facturaRepository).insertarConceptos(eq(0), anyList());
		verify(servicio.gestorTransacciones).commit(any());
	}

	@Test
	void recalculaTrasRollbackYAbreUnaTransaccionNuevaPorIntento() {
		prepararGuardado();
		when(servicio.facturaRepository.obtenerUltimoNumero(2026)).thenReturn(8, 9);
		when(servicio.facturaRepository.insertar(any(Factura.class)))
				.thenThrow(new NumeroFacturaDuplicadoException(new RuntimeException("colisión simulada")))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
		assertEquals("F-2026-0010", servicio.crear(peticion(List.of())).numeroFactura());
		var orden = inOrder(servicio.gestorTransacciones, servicio.facturaRepository);
		orden.verify(servicio.gestorTransacciones).getTransaction(any());
		orden.verify(servicio.facturaRepository).obtenerUltimoNumero(2026);
		orden.verify(servicio.facturaRepository).insertar(any());
		orden.verify(servicio.gestorTransacciones).rollback(any());
		orden.verify(servicio.gestorTransacciones).getTransaction(any());
		orden.verify(servicio.facturaRepository).obtenerUltimoNumero(2026);
		orden.verify(servicio.facturaRepository).insertar(any());
		orden.verify(servicio.facturaRepository).insertarConceptos(eq(0), anyList());
		orden.verify(servicio.gestorTransacciones).commit(any());
	}

	@Test
	void noSuperaTresIntentosDeNumeracion() {
		prepararGuardado();
		when(servicio.facturaRepository.insertar(any())).thenThrow(new NumeroFacturaDuplicadoException(null));
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
				() -> servicio.crear(peticion(List.of()))).getStatusCode());
		verify(servicio.gestorTransacciones, times(3)).getTransaction(any());
		verify(servicio.gestorTransacciones, times(3)).rollback(any());
		verify(servicio.facturaRepository, times(3)).obtenerUltimoNumero(2026);
	}

	@Test
	void rechazaNumeracionAgotadaSinInsertarNiReintentar() {
		prepararGuardado();
		when(servicio.facturaRepository.obtenerUltimoNumero(2026)).thenReturn(9999);
		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> servicio.crear(peticion(List.of())));
		assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
		assertTrue(error.getReason().contains("9999"));
		verify(servicio.facturaRepository, times(0)).insertar(any());
		verify(servicio.gestorTransacciones).rollback(any());
	}

	@Test
	void noReintentaDuplicadosNoAcreditadosNiErroresDeConexion() {
		for (RuntimeException error : List.of(new DuplicateKeyException("otro índice"),
				new DataAccessResourceFailureException("sin conexión"), new DataIntegrityViolationException("otra restricción"))) {
			prepararGuardado();
			when(servicio.facturaRepository.insertar(any())).thenThrow(error);
			assertEquals(error, assertThrows(RuntimeException.class, () -> servicio.crear(peticion(List.of()))));
			verify(servicio.gestorTransacciones).getTransaction(any());
			verify(servicio.gestorTransacciones).rollback(any());
		}
	}

	@Test
	void errorEnConceptosRevierteSinReintentarAunqueSeaDuplicidad() {
		prepararGuardado();
		doThrow(new DuplicateKeyException("duplicado en conceptos"))
				.when(servicio.facturaRepository).insertarConceptos(eq(0), anyList());
		assertThrows(DuplicateKeyException.class, () -> servicio.crear(peticion(List.of(concepto(1, "1", "0", "0")))));
		verify(servicio.gestorTransacciones).getTransaction(any());
		verify(servicio.gestorTransacciones).rollback(any());
		verify(servicio.gestorTransacciones, times(0)).commit(any());
	}

	@Test
	void elCalculoNoModificaLaEntradaNiDependeDeImportesManuales() {
		ConceptoRequest linea = new ConceptoRequest(" Servicio ", 2, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
		var calculo = servicio.calcularImportes(List.of(linea), "BORRADOR");
		assertEquals(" Servicio ", linea.descripcion());
		assertEquals("Servicio", calculo.conceptos().get(0).descripcion());
		assertEquals(new BigDecimal("20.00"), calculo.total());
		assertThrows(UnsupportedOperationException.class, () -> calculo.conceptos().clear());
	}

	private ConceptoRequest concepto(int cantidad, String precio, String descuento, String iva) {
		return new ConceptoRequest("Servicio", cantidad, new BigDecimal(precio), new BigDecimal(descuento), new BigDecimal(iva));
	}

	private FacturaRequest peticion(List<ConceptoRequest> conceptos) {
		return new FacturaRequest(1, LocalDate.of(2026, 9, 14), "BORRADOR", "", conceptos);
	}

	private void prepararGuardado() {
		servicio.facturaRepository = mock(FacturaRepository.class);
		servicio.gestorTransacciones = mock(PlatformTransactionManager.class);
		when(servicio.gestorTransacciones.getTransaction(any())).thenAnswer(invocacion -> {
			TransactionDefinition definicion = invocacion.getArgument(0);
			assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definicion.getPropagationBehavior());
			return new SimpleTransactionStatus();
		});
		when(servicio.facturaRepository.insertar(any(Factura.class)))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
	}
}
