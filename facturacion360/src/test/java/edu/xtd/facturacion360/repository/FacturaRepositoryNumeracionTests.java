package edu.xtd.facturacion360.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.repository.FacturaRepository.NumeroFacturaDuplicadoException;

class FacturaRepositoryNumeracionTests {
	@Test
	void reconoceSoloElIndiceDelNumeroEnElInsertDeCabecera() {
		for (String indice : List.of("num_factura_UNIQUE", "facturas.num_factura_UNIQUE")) {
			FacturaRepositoryJdbcImpl repositorio = repositorioConError(
					new SQLException("Duplicate entry 'F-2026-0001' for key '" + indice + "'", "23000", 1062));
			assertThrows(NumeroFacturaDuplicadoException.class, () -> repositorio.insertar(factura()));
		}
	}

	@Test
	void noConfundeOtrosIndicesCodigosOMensajesConLaColisionDeNumero() {
		for (SQLException error : List.of(
				new SQLException("Duplicate entry 'x' for key 'otra_UNIQUE'", "23000", 1062),
				new SQLException("Duplicate entry 'x' for key 'num_factura_UNIQUE_extra'", "23000", 1062),
				new SQLException("Duplicate entry 'x' for key 'num_factura_UNIQUE'", "08000", 1062),
				new SQLException("Duplicate entry 'x' for key 'num_factura_UNIQUE'", "23000", 1213),
				new SQLException("num_factura_UNIQUE sin identificación inequívoca", "23000", 1062),
				new SQLException(null, "23000", 1062))) {
			FacturaRepositoryJdbcImpl repositorio = repositorioConError(error);
			assertThrows(DuplicateKeyException.class, () -> repositorio.insertar(factura()));
		}
	}

	@Test
	void unErrorDeConceptosNuncaSeTraduceAColisionDeNumero() {
		FacturaRepositoryJdbcImpl repositorio = repositorioConError(
				new SQLException("Duplicate entry 'x' for key 'num_factura_UNIQUE'", "23000", 1062));
		ConceptoFactura concepto = new ConceptoFactura(0, "Prueba", 1, BigDecimal.ONE, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
		assertThrows(DuplicateKeyException.class, () -> repositorio.insertarConceptos(1, List.of(concepto)));
	}

	private FacturaRepositoryJdbcImpl repositorioConError(SQLException error) {
		FacturaRepositoryJdbcImpl repositorio = new FacturaRepositoryJdbcImpl();
		repositorio.jdbcTemplate = mock(JdbcTemplate.class);
		when(repositorio.jdbcTemplate.update(anyString(), any(Object[].class)))
				.thenThrow(new DuplicateKeyException("Error de prueba", error));
		return repositorio;
	}

	private Factura factura() {
		return new Factura(0, 1, null, "F-2026-0001", LocalDate.of(2026, 1, 1), "BORRADOR", "",
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
	}
}
