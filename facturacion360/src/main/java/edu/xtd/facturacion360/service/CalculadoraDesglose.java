package edu.xtd.facturacion360.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.xtd.facturacion360.dto.ClaveDesglose;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.DesgloseImpositivo;

/**
 * Convierte las líneas de una factura en el desglose por tipo impositivo.
 *
 * <p><strong>Suma, no recalcula.</strong> Y ése es el punto entero de esta clase. La regla de
 * redondeo que fija el reglamento es «se redondea la cuota de CADA LÍNEA a dos decimales y
 * después se suman», nunca al revés: redondear al final puede dar un céntimo de diferencia con
 * lo que aparece impreso en la factura, y ese céntimo acaba dentro de la huella que se comunica
 * a Hacienda. Como {@code FacturaServiceImpl.calcularImportes} ya redondea línea a línea, aquí
 * basta con agrupar y sumar lo que ya viene hecho. Si algún día alguien tiene la tentación de
 * recalcular desde el precio unitario, es exactamente la tentación que hay que resistir.</p>
 *
 * <p>Es una función pura —entran conceptos, sale el desglose— y por eso no es un componente de
 * Spring: se puede probar sin levantar la aplicación ni la base de datos, que es lo que hace
 * falta para vigilar una regla de redondeo.</p>
 *
 * @author AngelDanielC0des
 */
public final class CalculadoraDesglose {

	private static final BigDecimal CERO = new BigDecimal("0.00");

	private CalculadoraDesglose() {
	}

	/**
	 * Lo que lleva sumado un grupo mientras se recorren las líneas.
	 *
	 * <p>Un record y no un array de dos posiciones, que es como estaba: {@code acumulado[0]} y
	 * {@code acumulado[1]} no dicen cuál es la base y cuál la cuota, hay que recordarlo. Aquí
	 * van importes, y si alguien los intercambia en una edición futura el compilador no dice
	 * nada y las pruebas de totales siguen pasando, porque la suma de los dos no cambia.</p>
	 */
	private record Acumulado(BigDecimal base, BigDecimal cuota) {

		Acumulado mas(Acumulado otro) {
			return new Acumulado(base.add(otro.base()), cuota.add(otro.cuota()));
		}
	}

	/**
	 * Un importe que puede venir vacío, contado como cero.
	 *
	 * <p>En el esquema, {@code porcentaje_iva}, {@code importe_iva} y {@code base_imponible}
	 * <strong>admiten NULL</strong>: cualquier línea guardada antes de que el servidor
	 * calculara los importes puede tenerlos vacíos. Sin esto, abrir una de esas facturas
	 * antiguas lanza un NullPointerException al sumar y el detalle responde un 500, dejándola
	 * inaccesible para siempre. Contar el vacío como cero es además lo que dicen sus datos:
	 * esa línea no tiene IVA registrado.</p>
	 */
	private static BigDecimal oCero(BigDecimal importe) {
		return importe == null ? CERO : importe;
	}

	/**
	 * Agrupa las líneas por la terna régimen + calificación + tipo, y suma sus importes.
	 *
	 * <p>El orden del resultado es estable y no da igual: este desglose va a acabar dentro del
	 * XML que se firma, y una lista que salga hoy en un orden y mañana en otro cambiaría la
	 * huella sin que hubiera cambiado ni un importe. Se ordena por la clave entera.</p>
	 *
	 * @param conceptos las líneas ya calculadas, con sus importes redondeados
	 * @return una línea por grupo, o lista vacía si no hay conceptos
	 */
	public static List<DesgloseImpositivo> calcular(List<ConceptoFactura> conceptos) {
		if (conceptos == null || conceptos.isEmpty()) {
			return List.of();
		}

		// LinkedHashMap y no HashMap: mantiene el orden de aparicion mientras se agrupa, que es
		// el que se ve en la factura. Aun asi se ordena despues, por lo dicho arriba.
		Map<ClaveDesglose, Acumulado> grupos = new LinkedHashMap<>();

		for (ConceptoFactura concepto : conceptos) {
			Acumulado linea = new Acumulado(oCero(concepto.baseImponible()),
					oCero(concepto.importeIva()));

			grupos.merge(ClaveDesglose.de(concepto), linea, Acumulado::mas);
		}

		List<DesgloseImpositivo> desglose = new ArrayList<>(grupos.size());
		for (Map.Entry<ClaveDesglose, Acumulado> grupo : grupos.entrySet()) {
			desglose.add(DesgloseImpositivo.de(grupo.getKey(),
					grupo.getValue().base(), grupo.getValue().cuota()));
		}

		desglose.sort(Comparator.comparing(DesgloseImpositivo::impuesto)
				.thenComparing(DesgloseImpositivo::claveRegimen)
				.thenComparing(DesgloseImpositivo::calificacion)
				.thenComparing(DesgloseImpositivo::tipoImpositivo));

		return List.copyOf(desglose);
	}

}
