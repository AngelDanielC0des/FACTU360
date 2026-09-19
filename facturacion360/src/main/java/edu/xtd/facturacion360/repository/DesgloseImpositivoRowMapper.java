package edu.xtd.facturacion360.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import edu.xtd.facturacion360.dto.DesgloseImpositivo;

/**
 * Convierte una fila de desglose_impositivo en una línea del desglose.
 */
@Component
public class DesgloseImpositivoRowMapper implements RowMapper<DesgloseImpositivo> {

	@Override
	public DesgloseImpositivo mapRow(ResultSet resultado, int numeroFila) throws SQLException {
		return new DesgloseImpositivo(
				resultado.getString("impuesto"),
				resultado.getString("clave_regimen"),
				resultado.getString("calificacion"),
				resultado.getBigDecimal("tipo_impositivo"),
				resultado.getBigDecimal("base_imponible"),
				resultado.getBigDecimal("cuota_repercutida"));
	}
}
