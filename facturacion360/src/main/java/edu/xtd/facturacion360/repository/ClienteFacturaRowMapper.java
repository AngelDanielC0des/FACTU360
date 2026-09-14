package edu.xtd.facturacion360.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import edu.xtd.facturacion360.dto.ClienteFactura;

/**
 * Convierte una fila de clientes en los datos usados por el visor de facturas.
 */
@Component
public class ClienteFacturaRowMapper implements RowMapper<ClienteFactura> {

	@Override
	public ClienteFactura mapRow(ResultSet resultado, int numeroFila) throws SQLException {
		ClienteFactura cliente = new ClienteFactura(
				resultado.getInt("idcliente"),
				resultado.getString("nombre"),
				resultado.getString("nif_cif"),
				resultado.getString("direccion"),
				resultado.getString("codigopostal"),
				resultado.getString("poblacion"),
				resultado.getString("provincia"),
				resultado.getString("telefono"),
				resultado.getString("email"));

		return cliente;
	}
}
