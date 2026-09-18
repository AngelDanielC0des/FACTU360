package edu.xtd.facturacion360.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.repository.FacturaRepository;

class FacturaServiceImplTests {

	FacturaServiceImpl facturaService;
	FacturaRepositoryFalso facturaRepository;

	@BeforeEach
	void prepararPrueba() {
		facturaRepository = new FacturaRepositoryFalso();
		facturaService = new FacturaServiceImpl();
		facturaService.facturaRepository = facturaRepository;
	}

	@Test
	void informaCuandoLaFacturaNoExiste() {
		ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
				() -> facturaService.obtenerDetalle(99));

		assertEquals(HttpStatus.NOT_FOUND, excepcion.getStatusCode());
	}

	private static class FacturaRepositoryFalso implements FacturaRepository {

		@Override
		public Factura buscarPorIdParaActualizar(int idFactura) {
			throw new UnsupportedOperationException("Esta prueba no edita borradores");
		}

		@Override
		public int actualizarBorrador(Factura factura) {
			throw new UnsupportedOperationException("Esta prueba no edita borradores");
		}

		@Override
		public void eliminarConceptos(int idFactura) {
			throw new UnsupportedOperationException("Esta prueba no edita borradores");
		}

		@Override
		public void insertarDesglose(int idFactura,
				List<edu.xtd.facturacion360.dto.DesgloseImpositivo> desglose) {
			// No se comprueba nada del desglose aqui: de eso se encarga CalculadoraDesgloseTests,
			// que lo prueba sin repositorio ni base de datos de por medio.
		}

		@Override
		public void eliminarDesglose(int idFactura) {
			throw new UnsupportedOperationException("Esta prueba no edita borradores");
		}

		@Override
		public List<edu.xtd.facturacion360.dto.DesgloseImpositivo> buscarDesglose(int idFactura) {
			return List.of();
		}

		@Override
		public List<edu.xtd.facturacion360.dto.SugerenciaConcepto> buscarSugerenciasConceptos(String texto, int limite) {
			return List.of();
		}

		@Override
		public int obtenerUltimoNumero(int anio) {
			return 0;
		}

		@Override
		public void insertarConceptos(int idFactura, List<ConceptoFactura> conceptos) {
			throw new UnsupportedOperationException("Esta prueba solo consulta facturas");
		}

		@Override
		public Factura insertar(Factura factura) {
			return factura;
		}

		@Override
		public List<Factura> buscar(String busqueda) {
			return List.of();
		}

		@Override
		public Factura buscarPorId(int idFactura) {
			return null;
		}

		@Override
		public ClienteFactura buscarCliente(int idCliente) {
			return null;
		}

		@Override
		public List<ConceptoFactura> buscarConceptos(int idFactura) {
			return List.of();
		}

		@Override
		public List<Factura> buscarPorTrimestre(LocalDate fechaInicio, LocalDate fechaFin) {
			return List.of();
		}
	}
}
