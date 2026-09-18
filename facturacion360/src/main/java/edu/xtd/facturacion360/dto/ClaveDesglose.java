package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Por qué se agrupa una línea con otra en el desglose del IVA.
 *
 * <p>No basta con el porcentaje, aunque lo parezca. La AEAT agrupa por la <strong>terna</strong>
 * régimen + calificación + tipo impositivo, y dos líneas al 21 % pueden acabar en grupos
 * distintos si una va en régimen general y la otra no. Agrupar solo por el porcentaje da un
 * desglose que cuadra de totales y miente de contenido.</p>
 *
 * <p>Se usa como clave de un mapa, y de ahí viene el detalle que tiene el constructor: en
 * {@link BigDecimal}, {@code equals} <strong>compara también la escala</strong>, así que
 * {@code 21.00} y {@code 21.0} son objetos distintos y abrirían dos grupos para el mismo tipo.
 * Por eso el tipo se normaliza a dos decimales al construir la clave. Es la clase de fallo que
 * no se ve en las pruebas fáciles y aparece con los datos de verdad, donde los decimales llegan
 * como los haya dejado el driver.</p>
 *
 * @param impuesto        código del impuesto: {@code 01} es el IVA
 * @param claveRegimen    clave de régimen: {@code 01} es el general
 * @param calificacion    {@code S1} es sujeta y no exenta
 * @param tipoImpositivo  el porcentaje, siempre con dos decimales
 *
 * @author AngelDanielC0des
 */
public record ClaveDesglose(
		String impuesto,
		String claveRegimen,
		String calificacion,
		BigDecimal tipoImpositivo) {

	/** IVA. Es el único impuesto que maneja hoy la aplicación. */
	public static final String IVA = "01";

	/** Régimen general: el caso normal, y el que se supone si nadie dice otra cosa. */
	public static final String REGIMEN_GENERAL = "01";

	/** Sujeta y no exenta: igual que arriba, el caso normal. */
	public static final String SUJETA_NO_EXENTA = "S1";

	/** Los dos decimales del tipo: ver la explicación de arriba sobre {@code equals}. */
	private static final int DECIMALES_DEL_TIPO = 2;

	public ClaveDesglose {
		// El tipo vacio se cuenta como 0 %, no se deja a null: las filas anteriores a que el
		// servidor calculara los importes pueden traerlo vacio, y un null aqui tumbaria el
		// ordenado del desglose con un NullPointerException.
		tipoImpositivo = tipoImpositivo == null
				? new BigDecimal("0.00")
				: tipoImpositivo.setScale(DECIMALES_DEL_TIPO, RoundingMode.HALF_UP);

		// Lo mismo con los dos fiscales: @Pattern da por bueno el nulo, asi que por aqui puede
		// llegar uno y acabaria contra una columna NOT NULL.
		claveRegimen = claveRegimen == null ? REGIMEN_GENERAL : claveRegimen;
		calificacion = calificacion == null ? SUJETA_NO_EXENTA : calificacion;
		impuesto = impuesto == null ? IVA : impuesto;
	}

	/**
	 * La clave que le corresponde a una línea de factura.
	 *
	 * @param concepto la línea ya calculada
	 * @return su grupo dentro del desglose
	 */
	public static ClaveDesglose de(ConceptoFactura concepto) {
		return new ClaveDesglose(IVA, concepto.claveRegimen(), concepto.calificacion(),
				concepto.porcentajeIva());
	}

}
