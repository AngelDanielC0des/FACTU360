package edu.xtd.facturacion360.dto;

import java.util.List;

/**
 * Datos completos necesarios para consultar e imprimir una factura.
 */
public record DetalleFactura(
		Factura factura,
		ClienteFactura cliente,
		List<ConceptoFactura> conceptos,

		/**
		 * El IVA agrupado por tipo, que es como lo lleva una factura impresa y como lo exige
		 * la AEAT. Va aparte de los conceptos porque no es una vista de ellos: es lo que se
		 * declaro, congelado.
		 */
		List<DesgloseImpositivo> desglose) {

}
