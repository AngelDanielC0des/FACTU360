package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado del listado de facturas de un trimestre.
 */
public record ResumenTrimestralFactura(
		int anio,
		int trimestre,
		List<Factura> facturas,
		BigDecimal subtotal,
		BigDecimal importeIva,
		BigDecimal total) {

}
