package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;

/**
 * Una línea del desglose del IVA: cuánta base y cuánta cuota hay a cada tipo.
 *
 * <p>Es lo que en una factura impresa aparece como «base 100,00 al 21 % → cuota 21,00», y lo
 * que la AEAT llama {@code DetalleDesglose} dentro del registro de alta. Los nombres de los
 * campos son los suyos a propósito, para que el día que se construya el XML no haya que
 * traducir nada por el camino.</p>
 *
 * <p>Va plano y no con la {@link ClaveDesglose} anidada dentro porque así coincide columna a
 * columna con la tabla {@code desglose_impositivo} y con lo que se manda al navegador. La clave
 * se usa para agrupar; esto es el resultado de haber agrupado.</p>
 *
 * @param impuesto          {@code 01} es el IVA
 * @param claveRegimen      {@code 01} es el general
 * @param calificacion      {@code S1} es sujeta y no exenta
 * @param tipoImpositivo    el porcentaje de este grupo
 * @param baseImponible     suma de las bases de las líneas del grupo
 * @param cuotaRepercutida  suma de las cuotas de las líneas del grupo
 *
 * @author AngelDanielC0des
 */
public record DesgloseImpositivo(
		String impuesto,
		String claveRegimen,
		String calificacion,
		BigDecimal tipoImpositivo,
		BigDecimal baseImponible,
		BigDecimal cuotaRepercutida) {

	/**
	 * Arma una línea del desglose a partir de su clave y de los importes ya sumados.
	 *
	 * @param clave            el grupo
	 * @param baseImponible    la base sumada
	 * @param cuotaRepercutida la cuota sumada
	 * @return la línea del desglose
	 */
	public static DesgloseImpositivo de(ClaveDesglose clave, BigDecimal baseImponible,
			BigDecimal cuotaRepercutida) {

		return new DesgloseImpositivo(clave.impuesto(), clave.claveRegimen(), clave.calificacion(),
				clave.tipoImpositivo(), baseImponible, cuotaRepercutida);
	}

}
