package edu.xtd.facturacion360.verifactu.huella;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * El SHA-256 de una cadena canónica, en hexadecimal y mayúsculas.
 *
 * <p>La parte fácil de la huella. Todo lo que puede salir mal está en
 * {@link CadenaCanonica}: aquí solo hay que no equivocarse en tres cosas, y las tres están
 * escritas abajo porque las tres cambian el resultado sin dar ningún error.</p>
 *
 * @author AngelDanielC0des
 * @see CadenaCanonica
 */
public final class CalculadorHuella {

	/** El algoritmo que fija la especificación. */
	private static final String ALGORITMO = "SHA-256";

	/**
	 * Cómo se identifica este algoritmo dentro del XML que se le manda a la AEAT.
	 *
	 * <p>El campo {@code TipoHuella} del registro no lleva el nombre del algoritmo, lleva
	 * su código en la lista L12 del anexo de la Orden. Hoy {@code 01} es el único valor
	 * admitido, justo porque SHA-256 es el único algoritmo admitido.</p>
	 *
	 * <p>Está aquí, al lado del algoritmo que nombra, para que el día que se escriba el
	 * constructor del XML no aparezca un {@code "01"} suelto que nadie sabe de dónde
	 * sale. Todavía no lo usa nadie.</p>
	 */
	public static final String TIPO_HUELLA_SHA_256 = "01";

	/**
	 * El conversor a hexadecimal.
	 *
	 * <p>Al revés que {@link MessageDigest}, éste sí puede ser una constante: no tiene
	 * estado y se puede compartir entre hilos.</p>
	 */
	private static final HexFormat HEXADECIMAL = HexFormat.of().withUpperCase();

	private CalculadorHuella() {
	}

	/**
	 * La huella de una cadena canónica.
	 *
	 * <p>Los tres detalles que importan:</p>
	 *
	 * <ol>
	 *   <li>La cadena se pasa a bytes en <strong>UTF-8</strong>, explícitamente. Sin decirlo,
	 *       Java usa la codificación por defecto de la máquina, que en Windows no es UTF-8:
	 *       la misma factura daría una huella distinta según dónde se ejecute el programa, y
	 *       solo cuando el texto llevara acentos.</li>
	 *   <li>La salida va en <strong>mayúsculas</strong>. En hexadecimal
	 *       {@code 3c46…} y {@code 3C46…} son el mismo número, pero la huella se compara y se
	 *       encadena <em>como texto</em>, así que no son intercambiables.</li>
	 *   <li>Son <strong>64 caracteres</strong> siempre. SHA-256 da 32 bytes y
	 *       {@code HexFormat} no recorta ceros a la izquierda, que es justo el fallo clásico
	 *       de escribir esta conversión a mano con {@code Integer.toHexString}.</li>
	 * </ol>
	 *
	 * <p>A diferencia de {@link CadenaCanonica}, aquí el nulo <strong>no se tolera</strong> y
	 * revienta con {@link NullPointerException}. La asimetría es a propósito: allí un campo
	 * nulo es un dato legítimo —la especificación manda escribirlo vacío—, y aquí no hay
	 * ningún caso en el que tenga sentido calcular la huella de nada. Llegar con un nulo es un
	 * error de programación, y taparlo devolviendo el hash de la cadena vacía produciría una
	 * huella de aspecto correcto para un registro que no existe.</p>
	 *
	 * @param cadena la cadena que devuelve {@link CadenaCanonica}; nunca {@code null}
	 * @return la huella, 64 caracteres hexadecimales en mayúsculas
	 */
	public static String de(String cadena) {

		byte[] resumen = resumir(cadena.getBytes(StandardCharsets.UTF_8));

		return HEXADECIMAL.formatHex(resumen);
	}

	/**
	 * Aplica el algoritmo.
	 *
	 * <p>Se pide una instancia nueva en cada llamada a propósito: {@link MessageDigest} tiene
	 * estado interno y no es seguro compartirlo entre hilos. Guardarlo en una constante
	 * parecería un ahorro y daría huellas corruptas en cuanto dos facturas se guardaran a la
	 * vez, que es un fallo imposible de reproducir a mano.</p>
	 */
	private static byte[] resumir(byte[] datos) {
		try {
			return MessageDigest.getInstance(ALGORITMO).digest(datos);

		} catch (NoSuchAlgorithmException imposible) {
			// Todas las máquinas virtuales de Java están obligadas a traer SHA-256, así que
			// llegar aquí significa que la instalación está rota, no que falte un caso por
			// contemplar. Se convierte en un error sin comprobar para no obligar a quien
			// llama a capturar algo que no puede pasar ni podría arreglar.
			throw new IllegalStateException("Esta máquina virtual no trae " + ALGORITMO, imposible);
		}
	}
}
