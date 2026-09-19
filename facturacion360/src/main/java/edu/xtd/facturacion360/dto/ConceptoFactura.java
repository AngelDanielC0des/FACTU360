package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;

/**
 * Línea o concepto que pertenece a una factura.
 *
 * <p>Los dos últimos campos son fiscales y existen por el desglose que exige la AEAT: no
 * basta con el porcentaje para saber en qué grupo cae una línea, hace falta la terna
 * régimen + calificación + tipo. Casi siempre valen lo mismo —régimen general y sujeta y no
 * exenta—, y por eso hay un constructor que los da por supuestos.</p>
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
		BigDecimal total,
		String claveRegimen,
		String calificacion) {

	/**
	 * Los dos campos fiscales nunca quedan a nulo, se construya por donde se construya.
	 *
	 * <p>Sus columnas son {@code NOT NULL}, y {@code @Pattern} de Bean Validation da por
	 * válido el nulo, así que sin esto un null se colaría hasta MySQL y saldría un error de
	 * base de datos en vez de un dato correcto.</p>
	 */
	public ConceptoFactura {
		claveRegimen = claveRegimen == null ? ClaveDesglose.REGIMEN_GENERAL : claveRegimen;
		calificacion = calificacion == null ? ClaveDesglose.SUJETA_NO_EXENTA : calificacion;
	}

	/**
	 * El caso normal: régimen general y sujeta y no exenta.
	 *
	 * <p>Existe para que añadir los dos campos fiscales no obligara a tocar los cinco sitios
	 * que ya construían un concepto sin ellos. Son los mismos valores que pone por defecto la
	 * base de datos, así que un concepto creado por aquí y otro leído de una fila antigua
	 * salen iguales.</p>
	 */
	public ConceptoFactura(long idConcepto, String descripcion, Integer cantidad,
			BigDecimal precioUnitario, BigDecimal descuento, BigDecimal porcentajeIva,
			BigDecimal importeIva, BigDecimal baseImponible, BigDecimal total) {

		this(idConcepto, descripcion, cantidad, precioUnitario, descuento, porcentajeIva,
				importeIva, baseImponible, total, ClaveDesglose.REGIMEN_GENERAL,
				ClaveDesglose.SUJETA_NO_EXENTA);
	}

}
