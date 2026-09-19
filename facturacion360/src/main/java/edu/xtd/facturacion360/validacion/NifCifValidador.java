package edu.xtd.facturacion360.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Comprueba que un documento es un DNI, un NIE, un NIF especial o un CIF español:
 * la forma y la letra de control.
 *
 * <p>Las cuatro formas que admite Hacienda:</p>
 *
 * <ul>
 *   <li><strong>DNI</strong>: ocho dígitos y una letra, que sale del resto de dividir el
 *       número entre 23.</li>
 *   <li><strong>NIE</strong>: X, Y o Z, siete dígitos y una letra. Se sustituye la inicial
 *       por su dígito (X→0, Y→1, Z→2) y se calcula igual que un DNI.</li>
 *   <li><strong>NIF de K, L o M</strong>: menores de catorce años, españoles residentes
 *       fuera y extranjeros sin NIE. Llevan inicial como el NIE, pero la letra sale de
 *       sus siete dígitos, no de sustituir la inicial por un número.</li>
 *   <li><strong>CIF</strong>: letra de organización, siete dígitos y un carácter de control
 *       que, según el tipo de entidad, es un dígito, una letra, o cualquiera de los dos.</li>
 * </ul>
 *
 * <p>Cuando la forma es correcta pero el control no cuadra, el mensaje dice <em>cuál
 * tocaba</em>. Es la diferencia entre "el documento no es válido", que deja al usuario
 * mirando la pantalla, y "para 12345678 la letra es la Z", que se corrige sola.</p>
 *
 * @author AngelDanielC0des
 */
public class NifCifValidador implements ConstraintValidator<NifCif, String> {

	/** El orden NO es alfabético: es el que fija la tabla oficial, indexada por el resto. */
	private static final String LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE";

	/**
	 * Las letras con las que puede empezar un CIF, una por tipo de entidad (A sociedad
	 * anónima, B limitada, G asociación, Q organismo público...). No están ni la I, ni la O,
	 * ni la Ñ, para que nadie las confunda con el 1 y el 0.
	 */
	private static final String LETRAS_CIF = "ABCDEFGHJNPQRSUVW";

	/** Entidades cuyo carácter de control es SIEMPRE una letra. */
	private static final String CIF_CONTROL_LETRA = "PQRSNW";

	/** Entidades cuyo carácter de control es SIEMPRE un dígito. */
	private static final String CIF_CONTROL_DIGITO = "ABEH";

	/** La letra de control de un CIF sale de indexar aquí, no de la tabla del DNI. */
	private static final String LETRAS_CONTROL_CIF = "JABCDEFGHI";

	@Override
	public boolean isValid(String valor, ConstraintValidatorContext contexto) {

		// En blanco se da por bueno: de si el campo es obligatorio decide @NotBlank. Si esto
		// devolviera false, un campo vacío soltaría dos errores diciendo lo mismo.
		if (valor == null || valor.isBlank()) {
			return true;
		}

		String documento = valor.trim().toUpperCase();

		if (documento.matches("[0-9]{8}[A-Z]")) {
			return comprobarDniONie(documento, documento.substring(0, 8), "DNI", contexto);
		}

		if (documento.matches("[XYZ][0-9]{7}[A-Z]")) {
			// La inicial vale por su posición en XYZ: X es 0, Y es 1 y Z es 2. Con eso el NIE
			// se convierte en ocho dígitos y se calcula como un DNI cualquiera.
			String comoNumero = "XYZ".indexOf(documento.charAt(0)) + documento.substring(1, 8);
			return comprobarDniONie(documento, comoNumero, "NIE", contexto);
		}

		// K, L y M van antes que el CIF a proposito: su forma tambien encaja en el patron de
		// abajo, y si se comprobaran despues caerian ahi y se rechazarian diciendo que la K no
		// es una inicial valida de CIF... a alguien que no esta escribiendo ningun CIF.
		//
		// La letra sale de los siete digitos, sin tocar la inicial. Esa es la diferencia con el
		// NIE, donde la inicial SI cuenta como un digito mas.
		if (documento.matches("[KLM][0-9]{7}[A-Z]")) {
			return comprobarDniONie(documento, documento.substring(1, 8), "NIF", contexto);
		}

		if (documento.matches("[A-Z][0-9]{7}[0-9A-J]")) {
			return comprobarCif(documento, contexto);
		}

		return false;
	}

	/**
	 * Comprueba la letra final de un DNI o un NIE.
	 *
	 * @param documento el documento entero, ya en mayúsculas
	 * @param numero    los ocho dígitos con los que se calcula
	 * @param tipo      cómo nombrarlo en el mensaje
	 * @param contexto  para poder contar cuál era la letra buena
	 * @return si la letra es la que toca
	 */
	private boolean comprobarDniONie(String documento, String numero, String tipo,
			ConstraintValidatorContext contexto) {

		char esperada = LETRAS_DNI.charAt(Integer.parseInt(numero) % 23);

		if (documento.charAt(documento.length() - 1) == esperada) {
			return true;
		}

		return rechazar(contexto, "La letra del " + tipo + " no corresponde con el número: "
				+ "debería ser la " + esperada);
	}

	/**
	 * Comprueba la letra de organización y el carácter de control de un CIF.
	 *
	 * <p>El control se calcula sumando los dígitos de las posiciones pares tal cual, y los de
	 * las impares multiplicados por dos y sumando las cifras del resultado.</p>
	 *
	 * @param documento el CIF entero, ya en mayúsculas
	 * @param contexto  para poder contar qué control tocaba
	 * @return si el CIF es válido
	 */
	private boolean comprobarCif(String documento, ConstraintValidatorContext contexto) {

		char organizacion = documento.charAt(0);

		if (LETRAS_CIF.indexOf(organizacion) < 0) {
			return rechazar(contexto, "La letra " + organizacion
					+ " no es una inicial válida de CIF");
		}

		String digitos = documento.substring(1, 8);
		int suma = 0;

		for (int posicion = 0; posicion < digitos.length(); posicion++) {
			int cifra = digitos.charAt(posicion) - '0';

			if (posicion % 2 == 0) {
				// Impares en la numeración de toda la vida (la primera, la tercera...): se
				// duplican y se suman las dos cifras del resultado, no el resultado.
				int doble = cifra * 2;
				suma += doble / 10 + doble % 10;
			} else {
				suma += cifra;
			}
		}

		int digitoControl = (10 - suma % 10) % 10;
		char letraControl = LETRAS_CONTROL_CIF.charAt(digitoControl);
		char control = documento.charAt(8);

		if (CIF_CONTROL_LETRA.indexOf(organizacion) >= 0) {
			return control == letraControl
					|| rechazar(contexto, "El CIF de una entidad " + organizacion
							+ " acaba en letra, y aquí debería ser la " + letraControl);
		}

		if (CIF_CONTROL_DIGITO.indexOf(organizacion) >= 0) {
			return control == (char) ('0' + digitoControl)
					|| rechazar(contexto, "El CIF de una entidad " + organizacion
							+ " acaba en número, y aquí debería ser el " + digitoControl);
		}

		// El resto admite las dos formas del mismo control.
		return control == letraControl || control == (char) ('0' + digitoControl)
				|| rechazar(contexto, "El control del CIF debería ser " + digitoControl
						+ " o " + letraControl);
	}

	/**
	 * Sustituye el mensaje por defecto por uno que dice qué se esperaba.
	 *
	 * <p>Solo se interpolan valores calculados aquí —dígitos y letras de las tablas—, nunca
	 * lo que haya tecleado el usuario: el texto de una violación se interpreta como plantilla
	 * y meter ahí entrada ajena es la forma conocida de colar expresiones.</p>
	 *
	 * @return siempre false, para poder escribir {@code return rechazar(...)}
	 */
	private boolean rechazar(ConstraintValidatorContext contexto, String motivo) {
		if (contexto != null) {
			contexto.disableDefaultConstraintViolation();
			contexto.buildConstraintViolationWithTemplate(motivo).addConstraintViolation();
		}

		return false;
	}

}
