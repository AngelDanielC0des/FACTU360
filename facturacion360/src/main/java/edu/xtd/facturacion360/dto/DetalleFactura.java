package edu.xtd.facturacion360.dto;

import java.util.List;

/**
 * Datos completos necesarios para consultar e imprimir una factura.
 */
public record DetalleFactura(
		Factura factura,
		ClienteFactura cliente,
		List<ConceptoFactura> conceptos) {

}
