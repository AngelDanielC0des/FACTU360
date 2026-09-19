package edu.xtd.facturacion360.verifactu.huella;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Compone la cadena {@code campo=valor&campo=valor} de la que sale la huella de un registro.
 *
 * <p>Es la mitad delicada de Verifactu. El SHA-256 no tiene misterio: lo difícil es que el
 * texto que se le pasa sea <em>exactamente</em> el que espera la AEAT, porque un separador
 * distinto o un campo de más cambia la huella entera.</p>
 *
 * <p><strong>Por qué esto se prueba antes que nada.</strong> Si la cadena se compone mal, el
 * hash sale mal en TODOS los registros de forma consistente. La aplicación funcionaría
 * perfectamente contra sí misma —las huellas encadenarían, la cadena sería coherente— y el
 * fallo no aparecería hasta que Hacienda rechazara los envíos. Por eso las pruebas de esta
 * clase no comprueban que el resultado «parezca bien»: reproducen los tres vectores que
 * publica la AEAT, con sus huellas exactas.</p>
 *
 * <h2>Las reglas, que son todo el trabajo</h2>
 *
 * <ul>
 *   <li>Se quitan los espacios de los <strong>extremos</strong> de cada valor, no los
 *       interiores. El ejemplo de la propia especificación:
 *       {@code 12345678 / G33} conserva sus dos espacios centrales.</li>
 *   <li>Si un campo falta o viene vacío, se escribe {@code nombreCampo=} y nada detrás.
 *       <strong>Nunca se omite el campo.</strong> Omitirlo cambia la huella.</li>
 *   <li>El alta lleva ocho campos y la anulación cinco, y los tres primeros
 *       <em>cambian de nombre</em>: {@code IDEmisorFactura} pasa a ser
 *       {@code IDEmisorFacturaAnulada}.</li>
 *   <li>El orden de los campos es fijo. No es alfabético ni casual: es el que fija la
 *       especificación.</li>
 * </ul>
 *
 * <h2>Dos decisiones que se apartan del documento de diseño</h2>
 *
 * <p><strong>Métodos estáticos y no de instancia.</strong> El documento los propone como
 * métodos de objeto. Aquí son estáticos porque son funciones puras —mismos datos, misma
 * cadena, sin estado ni dependencias—, que es lo que el propio documento pide de este
 * paquete. Una instancia sin campos no aporta nada y obligaría a crearla en cada sitio.</p>
 *
 * <p><strong>Parámetros sueltos y no un {@code RegistroFacturacion}.</strong> El documento
 * pasa un record con todo dentro, y es mejor: con ocho parámetros, intercambiar
 * {@code cuotaTotal} e {@code importeTotal} compila y da una huella distinta sin avisar.
 * No se hace así todavía porque ese record pertenece al modelo de datos, que está parado
 * hasta que se decida a quién pertenece la columna {@code facturas.idemisor}. <b>Cuando
 * exista, estos dos métodos deberían recibirlo a él.</b></p>
 *
 * @author AngelDanielC0des
 * @see CalculadorHuella
 */
public final class CadenaCanonica {

	/**
	 * La fecha de expedición va en {@code dd-MM-uuuu}, con guiones.
	 *
	 * <p>No es el formato ISO que usa el resto del proyecto, y no es un descuido: la
	 * especificación lo pide así justo para este campo.</p>
	 *
	 * <p>{@code uuuu} y no {@code yyyy}: el segundo es «año de la era» y necesita saber
	 * la era para resolverse; el primero es el año a secas. Formateando salen iguales,
	 * pero {@code uuuu} es el que no da sorpresas si algún día esto también parsea.</p>
	 */
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-uuuu");

	/**
	 * El instante de generación, con su huso.
	 *
	 * <p>Se escribe el patrón a mano en vez de usar {@code ISO_OFFSET_DATE_TIME} por una
	 * razón concreta: el formateador de la biblioteca <strong>se come los segundos cuando
	 * son cero</strong> y produce {@code 19:20+01:00} en lugar de {@code 19:20:00+01:00}.
	 * Eso pasaría una vez de cada sesenta, con una huella distinta y sin ningún aviso.</p>
	 *
	 * <p>Y {@code xxx} en minúscula, que es la misma trampa un paso más allá: la mayúscula
	 * {@code XXX} colapsa el desplazamiento cero a {@code Z} en vez de escribir
	 * {@code +00:00}. No es un caso de laboratorio —salta con la máquina virtual en UTC, que
	 * es lo que trae un contenedor por defecto, y en <strong>Canarias en horario de
	 * invierno</strong>—, y no lo caza ninguno de los tres vectores oficiales, porque los
	 * tres son de enero peninsular y van a {@code +01:00}.</p>
	 */
	private static final DateTimeFormatter INSTANTE =
			DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx");

	/** Los importes viajan siempre con dos decimales. El porqué está en {@link #importe}. */
	private static final int DECIMALES = 2;

	private CadenaCanonica() {
	}

	/**
	 * La cadena de un registro de alta: ocho campos.
	 *
	 * @param nifEmisor       NIF de quien emite la factura
	 * @param numeroSerie     serie y número, tal y como figuran en la factura
	 * @param fechaExpedicion fecha de expedición
	 * @param tipoFactura     F1, F2, R1… el tipo según la AEAT
	 * @param cuotaTotal      la cuota total de impuestos
	 * @param importeTotal    el total de la factura
	 * @param huellaAnterior  la huella del registro anterior de la cadena; {@code null} o
	 *                        vacía en el primero de todos, que es el caso normal al empezar
	 * @param generacion      instante en que se genera el registro, con su huso horario
	 * @return la cadena lista para {@link CalculadorHuella#de(String)}
	 */
	public static String alta(String nifEmisor, String numeroSerie, LocalDate fechaExpedicion,
			String tipoFactura, BigDecimal cuotaTotal, BigDecimal importeTotal,
			String huellaAnterior, OffsetDateTime generacion) {

		return String.join("&",
				campo("IDEmisorFactura", nifEmisor),
				campo("NumSerieFactura", numeroSerie),
				campo("FechaExpedicionFactura", fecha(fechaExpedicion)),
				campo("TipoFactura", tipoFactura),
				campo("CuotaTotal", importe(cuotaTotal)),
				campo("ImporteTotal", importe(importeTotal)),
				campo("Huella", huellaAnterior),
				campo("FechaHoraHusoGenRegistro", instante(generacion)));
	}

	/**
	 * La cadena de un registro de anulación: cinco campos y sin importes.
	 *
	 * <p>Anular no es cambiar una columna: es generar un registro propio, con su huella, que
	 * entra en la misma cadena que las altas. Los datos que lleva son los de la factura que
	 * se anula, y por eso los campos se llaman {@code …Anulada}.</p>
	 *
	 * @param nifEmisor       NIF de quien emitió la factura anulada
	 * @param numeroSerie     serie y número de la factura anulada
	 * @param fechaExpedicion fecha de expedición de la factura anulada
	 * @param huellaAnterior  la huella del registro anterior de la cadena
	 * @param generacion      instante en que se genera este registro, con su huso
	 * @return la cadena lista para {@link CalculadorHuella#de(String)}
	 */
	public static String anulacion(String nifEmisor, String numeroSerie,
			LocalDate fechaExpedicion, String huellaAnterior, OffsetDateTime generacion) {

		return String.join("&",
				campo("IDEmisorFacturaAnulada", nifEmisor),
				campo("NumSerieFacturaAnulada", numeroSerie),
				campo("FechaExpedicionFacturaAnulada", fecha(fechaExpedicion)),
				campo("Huella", huellaAnterior),
				campo("FechaHoraHusoGenRegistro", instante(generacion)));
	}

	/**
	 * Un par {@code nombre=valor}, con el valor ya limpio.
	 *
	 * <p>Que esto sea una función y no texto suelto es lo que garantiza la regla de «nunca se
	 * omite el campo»: no hay forma de escribir un campo sin su nombre ni de saltárselo por
	 * venir vacío, porque el {@code String.join} de arriba los enumera todos siempre.</p>
	 */
	private static String campo(String nombre, String valor) {
		return nombre + "=" + limpio(valor);
	}

	/**
	 * Quita los espacios de los extremos y deja los de dentro.
	 *
	 * <p>{@code strip()} y no {@code trim()}: {@code trim()} corta por debajo del espacio en
	 * la tabla ASCII y se le escapan los espacios que no lo son, como el espacio duro que
	 * suele colarse al pegar texto desde un documento.</p>
	 *
	 * @param valor el valor, que puede ser {@code null}
	 * @return el valor limpio, o cadena vacía si no había
	 */
	private static String limpio(String valor) {
		return valor == null ? "" : valor.strip();
	}

	/**
	 * El importe con dos decimales.
	 *
	 * <p>La AEAT acepta uno o dos —{@code 123.1} y {@code 123.10} le valen igual, porque
	 * normaliza antes de comparar—, pero conviene no apoyarse en esa tolerancia: lo sensato
	 * es escribir el mismo texto aquí y en el XML, y para eso hay que fijar un formato.</p>
	 *
	 * <p>{@code toPlainString()} y no {@code toString()}: el segundo saca notación científica
	 * para exponentes grandes, y {@code 1.2E+3} en una huella no lo arregla nadie.</p>
	 */
	private static String importe(BigDecimal valor) {
		return valor == null ? "" : valor.setScale(DECIMALES, RoundingMode.HALF_UP).toPlainString();
	}

	private static String fecha(LocalDate valor) {
		return valor == null ? "" : valor.format(FECHA);
	}

	private static String instante(OffsetDateTime valor) {
		return valor == null ? "" : valor.format(INSTANTE);
	}
}
