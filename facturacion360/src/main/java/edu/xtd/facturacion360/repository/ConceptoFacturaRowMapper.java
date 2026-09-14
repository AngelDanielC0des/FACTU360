package edu.xtd.facturacion360.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import edu.xtd.facturacion360.dto.ConceptoFactura;

/**
 * Convierte una fila de conceptos en una línea de factura.
 */
@Component
public class ConceptoFacturaRowMapper implements RowMapper<ConceptoFactura> {

	@Override
	public ConceptoFactura mapRow(ResultSet resultado, int numeroFila) throws SQLException {
		ConceptoFactura concepto = new ConceptoFactura(
				resultado.getLong("idconcepto"),
				resultado.getString("descripcion"),
				resultado.getObject("cantidad", Integer.class),
				resultado.getBigDecimal("precio_unitario"),
				resultado.getBigDecimal("descuento"),
				resultado.getBigDecimal("porcentaje_iva"),
				resultado.getBigDecimal("importe_iva"),
				resultado.getBigDecimal("base_imponible"),
				resultado.getBigDecimal("total"));

		return concepto;
	}
}
