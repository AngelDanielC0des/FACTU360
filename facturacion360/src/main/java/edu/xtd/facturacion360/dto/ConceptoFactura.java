package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;

/**
 * Línea o concepto que pertenece a una factura.
 */
public record ConceptoFactura(
		long idConcepto,
		String descripcion,
		Integer cantidad,
		BigDecimal precioUnitario,
		BigDecimal descuento,
		BigDecimal porcentajeIva,
		BigDecimal importeIva,
		BigDecimal baseImponible,
		BigDecimal total) {

}
