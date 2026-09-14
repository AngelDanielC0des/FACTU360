package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de una factura guardada en la base de datos.
 */
public record Factura(
		int idFactura,
		int idCliente,
		String nombreCliente,
		String numeroFactura,
		LocalDate fechaEmision,
		String estado,
		String observaciones,
		BigDecimal subtotal,
		BigDecimal importeIva,
		BigDecimal total) {

}
