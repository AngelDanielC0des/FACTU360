package edu.xtd.facturacion360.verifactu.huella;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los tres vectores que publica la AEAT, reproducidos exactos.
 *
 * <p>No son ejemplos inventados ni valores sacados de ejecutar el código y darlos por
 * buenos: son las huellas publicadas por Hacienda. Si estas tres pruebas pasan, el generador
 * es correcto; si fallan, lo es el generador y no la prueba.</p>
 *
 * <p>Los tres <strong>encadenan entre sí</strong>, que es la mitad de la gracia: la huella
 * que sale del primero entra como campo {@code Huella} del segundo, y la del segundo en la
 * anulación. Así no se comprueba solo el hash, sino también que el encadenado se hace por
 * donde toca.</p>
 *
 * @author AngelDanielC0des
 */
class CalculadorHuellaTests {

	/** NIF del emisor en los tres vectores oficiales. */
	private static final String NIF = "89890001K";

	private static final LocalDate EXPEDICION = LocalDate.of(2024, 1, 1);

	private static final BigDecimal CUOTA = new BigDecimal("12.35");
	private static final BigDecimal TOTAL = new BigDecimal("123.45");

	private static final String HUELLA_1 =
			"3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60";
	private static final String HUELLA_2 =
			"F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97";
	private static final String HUELLA_3 =
			"177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68";

	private static OffsetDateTime alas(int segundo) {
		return OffsetDateTime.parse("2024-01-01T19:20:" + segundo + "+01:00");
	}

	private static String altaPrimera() {
		// Huella anterior nula: es el primer registro del sistema y no hay ninguno antes.
		return CadenaCanonica.alta(NIF, "12345678/G33", EXPEDICION, "F1", CUOTA, TOTAL,
				null, alas(30));
	}

	private static String altaSegunda(String huellaAnterior) {
		return CadenaCanonica.alta(NIF, "12345679/G34", EXPEDICION, "F1", CUOTA, TOTAL,
				huellaAnterior, alas(35));
	}

	@Test
	@DisplayName("vector 1: el primer registro de la cadena, sin huella anterior")
	void elPrimerRegistroDaLaHuellaOficial() {
		assertEquals(HUELLA_1, CalculadorHuella.de(altaPrimera()));
	}

	@Test
	@DisplayName("vector 2: el segundo registro, encadenado con el primero")
	void elSegundoRegistroEncadenaConElPrimero() {
		assertEquals(HUELLA_2, CalculadorHuella.de(altaSegunda(HUELLA_1)));
	}

	@Test
	@DisplayName("vector 3: la anulacion del segundo registro")
	void laAnulacionDaLaHuellaOficial() {
		String cadena = CadenaCanonica.anulacion(NIF, "12345679/G34", EXPEDICION,
				HUELLA_2, alas(40));

		assertEquals(HUELLA_3, CalculadorHuella.de(cadena));
	}

	@Test
	@DisplayName("los tres eslabones seguidos, encadenando lo que sale de verdad")
	void laCadenaEnteraSeSostieneSola() {

		// Aqui no se le pasa a cada paso la constante, sino lo que ha devuelto el anterior.
		// Si el encadenado se hiciera por el campo equivocado, las pruebas de arriba pasarian
		// igual -cada una comprueba su hash aislado- y esta no.
		String primera = CalculadorHuella.de(altaPrimera());
		String segunda = CalculadorHuella.de(altaSegunda(primera));
		String tercera = CalculadorHuella.de(
				CadenaCanonica.anulacion(NIF, "12345679/G34", EXPEDICION, segunda, alas(40)));

		assertEquals(HUELLA_1, primera);
		assertEquals(HUELLA_2, segunda);
		assertEquals(HUELLA_3, tercera);
	}

	@Test
	@DisplayName("la huella anterior vacia se escribe Huella= y no se omite el campo")
	void elCampoVacioSigueEstando() {
		String cadena = altaPrimera();

		assertTrue(cadena.contains("&Huella=&FechaHoraHusoGenRegistro="),
				"el campo tiene que aparecer vacio, no desaparecer: " + cadena);
	}

	@Test
	@DisplayName("null y cadena vacia dan la misma huella: los dos son -sin valor-")
	void elNuloYElVacioSonLoMismo() {
		String conNulo = CalculadorHuella.de(altaPrimera());
		String conVacio = CalculadorHuella.de(CadenaCanonica.alta(NIF, "12345678/G33",
				EXPEDICION, "F1", CUOTA, TOTAL, "", alas(30)));

		assertEquals(conNulo, conVacio);
	}

	@Test
	@DisplayName("se quitan los espacios de fuera y se respetan los de dentro")
	void losEspaciosDeDentroNoSeTocan() {
		// El ejemplo es de la propia especificacion: de <NumSerieFactura> 12345678 / G33 </>
		// sale "12345678 / G33", con sus dos espacios centrales intactos.
		String cadena = CadenaCanonica.alta(NIF, "  12345678 / G33  ", EXPEDICION, "F1",
				CUOTA, TOTAL, null, alas(30));

		assertTrue(cadena.contains("&NumSerieFactura=12345678 / G33&"),
				"los espacios interiores tienen que sobrevivir: " + cadena);
	}

	@Test
	@DisplayName("los importes van siempre con dos decimales")
	void losImportesSeEscribenIgualPasandoLoQuePase() {
		// 12.35, 12.350 y 12.3500 son el mismo numero y distinto texto. Sin normalizar, la
		// misma factura daria huellas distintas segun de donde saliera el BigDecimal.
		String cadena = CadenaCanonica.alta(NIF, "12345678/G33", EXPEDICION, "F1",
				new BigDecimal("12.3500"), new BigDecimal("123.4"), null, alas(30));

		assertTrue(cadena.contains("&CuotaTotal=12.35&"), cadena);
		assertTrue(cadena.contains("&ImporteTotal=123.40&"), cadena);
	}

	@Test
	@DisplayName("los segundos se escriben aunque sean cero")
	void losSegundosNoDesaparecen() {
		// ISO_OFFSET_DATE_TIME se los come cuando son cero y produce 19:20+01:00. Pasaria una
		// vez de cada sesenta, con una huella distinta y sin aviso ninguno.
		String cadena = CadenaCanonica.alta(NIF, "12345678/G33", EXPEDICION, "F1", CUOTA,
				TOTAL, null, OffsetDateTime.parse("2024-01-01T19:20:00+01:00"));

		assertTrue(cadena.endsWith("&FechaHoraHusoGenRegistro=2024-01-01T19:20:00+01:00"), cadena);
	}

	@Test
	@DisplayName("el huso cero se escribe +00:00 y no Z")
	void elHusoCeroNoSeAbrevia() {

		// La mayuscula XXX de DateTimeFormatter colapsa el desplazamiento cero a "Z". Salta
		// con la maquina virtual en UTC -lo normal en un contenedor- y en Canarias en horario
		// de invierno, que es +00:00. Y no lo caza ninguno de los tres vectores oficiales,
		// porque los tres son de enero peninsular y van a +01:00.
		String cadena = CadenaCanonica.alta(NIF, "12345678/G33", EXPEDICION, "F1", CUOTA,
				TOTAL, null, OffsetDateTime.parse("2024-01-15T19:20:30+00:00"));

		// Basta con mirar el final: si el huso se hubiera abreviado a Z, la cadena no podria
		// acabar en +00:00. Buscar la Z suelta en toda la cadena tambien pasaria hoy, pero se
		// romperia el dia que alguien cambiara la constante NIF: la Z es letra valida de DNI.
		assertTrue(cadena.endsWith("&FechaHoraHusoGenRegistro=2024-01-15T19:20:30+00:00"), cadena);
	}

	@Test
	@DisplayName("la huella son 64 caracteres hexadecimales en mayusculas")
	void laFormaDeLaHuella() {
		assertTrue(CalculadorHuella.de("lo que sea").matches("[0-9A-F]{64}"));
	}

	@Test
	@DisplayName("la cadena se pasa a bytes en UTF-8 y no en la codificacion de la maquina")
	void losAcentosNoDependenDeLaMaquina() {
		// Valor calculado aparte con UTF-8. Si algun dia esta prueba falla en el portatil de
		// alguien y no en el de otro, el fallo es exactamente este: la codificacion.
		assertEquals("B6AD5AC63F70E63C26AD5E2DCB8E94D8E1931BEB60ECBB39A6AA4E06E53D9A49",
				CalculadorHuella.de("ñá"));
	}

	@Test
	@DisplayName("el registro de anulacion lleva cinco campos y ningun importe")
	void laAnulacionNoHablaDeDinero() {
		String cadena = CadenaCanonica.anulacion(NIF, "12345679/G34", EXPEDICION,
				HUELLA_2, alas(40));

		assertEquals(5, cadena.split("&").length, cadena);
		assertTrue(cadena.startsWith("IDEmisorFacturaAnulada="), cadena);
		assertTrue(!cadena.contains("CuotaTotal") && !cadena.contains("ImporteTotal"), cadena);
	}
}
