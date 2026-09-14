package edu.xtd.facturacion360.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import edu.xtd.facturacion360.dto.Factura;

/**
 * Convierte una fila de la consulta SQL en una factura de Java.
 */
@Component
public class FacturaRowMapper implements RowMapper<Factura> {

	@Override
	public Factura mapRow(ResultSet resultado, int numeroFila) throws SQLException {
		Factura factura = new Factura(
				resultado.getInt("idfactura"),
				resultado.getInt("idcliente"),
				resultado.getString("nombre_cliente"),
				resultado.getString("num_factura"),
				resultado.getDate("fecha_emision").toLocalDate(),
				resultado.getString("estado"),
				resultado.getString("observaciones"),
				resultado.getBigDecimal("subtotal"),
				resultado.getBigDecimal("importe_iva"),
				resultado.getBigDecimal("total"));

		return factura;
	}
}
