package edu.xtd.facturacion360.dto;

/**
 * Datos del cliente que se muestran en el visor de una factura.
 */
public record ClienteFactura(
		int idCliente,
		String nombre,
		String nifCif,
		String direccion,
		String codigoPostal,
		String poblacion,
		String provincia,
		String telefono,
		String email) {

}
