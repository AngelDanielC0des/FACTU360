package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;

/** Última configuración utilizada; no incluye cantidad ni datos de la factura. */
public record SugerenciaConcepto(String descripcion, BigDecimal precioUnitario,
		BigDecimal descuento, BigDecimal porcentajeIva) {
}
