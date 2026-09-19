package edu.xtd.facturacion360.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.DesgloseImpositivo;

/**
 * El desglose del IVA: qué líneas caen juntas y qué importes salen.
 *
 * <p>Sin base de datos ni Spring, porque lo que se está vigilando es una regla de aritmética.
 * Si un día hay que discutir un céntimo con Hacienda, se discute aquí.</p>
 */
class CalculadoraDesgloseTests {

	/** Una línea ya calculada, que es como le llegan a la calculadora. */
	private static ConceptoFactura linea(String porcentajeIva, String base, String cuota) {
		return new ConceptoFactura(0, "linea", 1, new BigDecimal(base), new BigDecimal("0.00"),
				new BigDecimal(porcentajeIva), new BigDecimal(cuota), new BigDecimal(base),
				new BigDecimal(base).add(new BigDecimal(cuota)));
	}

	/** Igual, pero eligiendo el régimen y la calificación. */
	private static ConceptoFactura linea(String porcentajeIva, String base, String cuota,
			String regimen, String calificacion) {

		return new ConceptoFactura(0, "linea", 1, new BigDecimal(base), new BigDecimal("0.00"),
				new BigDecimal(porcentajeIva), new BigDecimal(cuota), new BigDecimal(base),
				new BigDecimal(base).add(new BigDecimal(cuota)), regimen, calificacion);
	}

	@Test
	@DisplayName("dos lineas al mismo tipo caen en un solo grupo y se suman")
	void dosLineasAlMismoTipoSeSuman() {
		List<DesgloseImpositivo> desglose = CalculadoraDesglose.calcular(List.of(
				linea("21.00", "100.00", "21.00"),
				linea("21.00", "50.00", "10.50")));

		assertEquals(1, desglose.size());
		assertEquals(0, new BigDecimal("150.00").compareTo(desglose.get(0).baseImponible()));
		assertEquals(0, new BigDecimal("31.50").compareTo(desglose.get(0).cuotaRepercutida()));
	}

	@Test
	@DisplayName("tipos distintos, grupos distintos")
	void tiposDistintosVanSeparados() {
		List<DesgloseImpositivo> desglose = CalculadoraDesglose.calcular(List.of(
				linea("21.00", "100.00", "21.00"),
				linea("10.00", "200.00", "20.00")));

		assertEquals(2, desglose.size());

		// Ordenado por la clave, asi que el 10 % va antes que el 21 %.
		assertEquals(0, new BigDecimal("10.00").compareTo(desglose.get(0).tipoImpositivo()));
		assertEquals(0, new BigDecimal("20.00").compareTo(desglose.get(0).cuotaRepercutida()));
		assertEquals(0, new BigDecimal("21.00").compareTo(desglose.get(1).tipoImpositivo()));
	}

	@Test
	@DisplayName("mismo tipo pero distinto regimen son grupos distintos")
	void elGrupoEsLaTernaEnteraYNoSoloElPorcentaje() {
		// Este es el motivo de que existan las dos columnas fiscales. Agrupando solo por el
		// porcentaje, estas dos lineas se fundirian en una y el desglose cuadraria de totales
		// mientras declara algo que no es.
		List<DesgloseImpositivo> desglose = CalculadoraDesglose.calcular(List.of(
				linea("21.00", "100.00", "21.00", "01", "S1"),
				linea("21.00", "100.00", "21.00", "02", "S1")));

		assertEquals(2, desglose.size());
		assertEquals("01", desglose.get(0).claveRegimen());
		assertEquals("02", desglose.get(1).claveRegimen());
	}

	@Test
	@DisplayName("el mismo tipo escrito con otra escala no abre un grupo nuevo")
	void veintiunoConUnDecimalEsElMismoVeintiuno() {
		// BigDecimal.equals compara TAMBIEN la escala, asi que 21.0 y 21.00 son objetos
		// distintos. Sin normalizar la clave, una factura recien calculada y otra leida de la
		// base de datos abririan dos grupos para el mismo tipo. No se ve hasta que pasa.
		List<DesgloseImpositivo> desglose = CalculadoraDesglose.calcular(List.of(
				linea("21.00", "100.00", "21.00"),
				linea("21.0", "100.00", "21.00")));

		assertEquals(1, desglose.size(), "21.0 y 21.00 son el mismo tipo impositivo");
		assertEquals(0, new BigDecimal("200.00").compareTo(desglose.get(0).baseImponible()));
	}

	@Test
	@DisplayName("la suma del desglose cuadra al centimo con la cabecera")
	void elDesgloseSumaLoMismoQueLaCabecera() {
		// La prueba que protege la regla de redondeo. Las cuotas vienen redondeadas POR LINEA,
		// y el desglose solo las suma. Si alguien cambiara el orden -sumar bases y aplicar el
		// porcentaje al final- aqui saldria un centimo de diferencia. Ese centimo acabaria
		// dentro de la huella que se comunica a Hacienda.
		List<ConceptoFactura> conceptos = List.of(
				linea("21.00", "33.33", "7.00"),
				linea("21.00", "66.67", "14.00"),
				linea("10.00", "19.99", "2.00"));

		BigDecimal ivaDeLaCabecera = conceptos.stream()
				.map(ConceptoFactura::importeIva)
				.reduce(new BigDecimal("0.00"), BigDecimal::add);

		BigDecimal ivaDelDesglose = CalculadoraDesglose.calcular(conceptos).stream()
				.map(DesgloseImpositivo::cuotaRepercutida)
				.reduce(new BigDecimal("0.00"), BigDecimal::add);

		assertEquals(0, ivaDeLaCabecera.compareTo(ivaDelDesglose),
				"el desglose y la cabecera tienen que declarar el mismo IVA");
	}

	@Test
	@DisplayName("una factura sin conceptos no tiene desglose, y no revienta")
	void sinConceptosNoHayDesglose() {
		assertTrue(CalculadoraDesglose.calcular(List.of()).isEmpty());
		assertTrue(CalculadoraDesglose.calcular(null).isEmpty());
	}

	@Test
	@DisplayName("una linea antigua con columnas a NULL no tumba el desglose")
	void aguantaLasFilasViejasConNulos() {
		// En el esquema, porcentaje_iva, importe_iva y base_imponible ADMITEN NULL. Cualquier
		// fila guardada antes de que el servidor calculara los importes puede tenerlos vacios,
		// y esas facturas se siguen pudiendo abrir e imprimir. Si esto reventara, el detalle
		// respondaria un 500 y la factura seria inaccesible para siempre.
		ConceptoFactura viejo = new ConceptoFactura(1, "linea antigua", 1,
				new BigDecimal("10.00"), BigDecimal.ZERO, null, null, null, new BigDecimal("10.00"));

		List<DesgloseImpositivo> desglose = CalculadoraDesglose.calcular(List.of(viejo));

		assertEquals(1, desglose.size());
		assertEquals(0, BigDecimal.ZERO.compareTo(desglose.get(0).tipoImpositivo()));
		assertEquals(0, BigDecimal.ZERO.compareTo(desglose.get(0).baseImponible()));
		assertEquals(0, BigDecimal.ZERO.compareTo(desglose.get(0).cuotaRepercutida()));
	}

	@Test
	@DisplayName("el orden es estable, porque acabara dentro de la huella")
	void elOrdenNoDependeDeComoLleguenLasLineas() {
		List<ConceptoFactura> unOrden = List.of(
				linea("21.00", "100.00", "21.00"),
				linea("4.00", "50.00", "2.00"),
				linea("10.00", "70.00", "7.00"));

		List<ConceptoFactura> elContrario = List.of(
				linea("10.00", "70.00", "7.00"),
				linea("4.00", "50.00", "2.00"),
				linea("21.00", "100.00", "21.00"));

		assertEquals(CalculadoraDesglose.calcular(unOrden),
				CalculadoraDesglose.calcular(elContrario));
	}

}
