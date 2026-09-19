package edu.xtd.facturacion360.validacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Las cuatro formas de documento que admite Hacienda, con su letra de control.
 *
 * <p>Los valores de estas pruebas NO están puestos a ojo: se calcularon antes con el
 * algoritmo y se comprobó uno a uno que salieran los que salen. Un ejemplo mal elegido en
 * una prueba de validación envenena todo lo que venga detrás, porque hace pasar al código
 * equivocado.</p>
 */
class NifCifValidadorTests {

	/** Lo mínimo para poder colgar la anotación de algo y validarlo de verdad. */
	record Documento(@NifCif String valor) {
	}

	ValidatorFactory fabrica;
	Validator validador;

	@BeforeEach
	void preparar() {
		fabrica = Validation.buildDefaultValidatorFactory();
		validador = fabrica.getValidator();
	}

	@AfterEach
	void cerrar() {
		fabrica.close();
	}

	private Set<ConstraintViolation<Documento>> validar(String valor) {
		return validador.validate(new Documento(valor));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"12345678Z",   // DNI
			"00000000T",   // DNI, el resto 0 de la tabla
			"99999999R",
			"X1234567L",   // NIE: la X vale 0
			"Y1234567X",   // la Y vale 1
			"Z1234567R",   // la Z vale 2
		"K0000000T",   // NIF de menor de catorce: la letra sale de los 7 digitos
		"L1234567L",   // NIF de espanol residente fuera
		"M1234567L",   // NIF de extranjero sin NIE. Misma letra que la L: la inicial
		               // no entra en el calculo, al reves que en el NIE
			"A58818501",   // CIF de sociedad anónima: control numérico obligatorio
			"B12345674",   // CIF de sociedad limitada: control numérico
			"Q2826000H",   // CIF de organismo público: control por letra obligatorio
			"P1234567D",   // CIF de corporación local: control por letra
			"G12345674",   // CIF de asociación: admite las dos formas del control...
			"G1234567D",   // ...y esta es la otra
			"12345678z",   // la minúscula se acepta y se normaliza
			"  12345678Z " // los espacios de los lados no cuentan
	})
	@DisplayName("acepta DNI, NIE, NIF de K/L/M y CIF con el control correcto")
	void aceptaLosDocumentosValidos(String documento) {
		assertTrue(validar(documento).isEmpty(), "deberia aceptar " + documento);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"12345678A",   // DNI con la letra cambiada: la buena es la Z
			"X1234567M",   // NIE con la letra cambiada
		"M1234567A",   // NIF de M con la letra cambiada: la buena es la L
			"A58818509",   // CIF con el digito de control cambiado
			"Q28260001",   // CIF de organismo publico acabado en digito, y le toca letra
			"I12345678",   // la I no es inicial valida de CIF
			"O12345678",   // la O tampoco
			"1234567Z",    // se queda corto
			"123456789Z",  // se pasa de largo
			"ABCDEFGHI",   // ni se parece
			"12345678"     // sin letra
	})
	@DisplayName("rechaza lo que tiene mala forma o mal control")
	void rechazaLosDocumentosInvalidos(String documento) {
		assertEquals(1, validar(documento).size(), "deberia rechazar " + documento);
	}

	@Test
	@DisplayName("en blanco lo decide @NotBlank, no esta anotacion")
	void enBlancoNoEsAsuntoSuyo() {
		assertTrue(validar(null).isEmpty());
		assertTrue(validar("").isEmpty());
		assertTrue(validar("   ").isEmpty());
	}

	@Test
	@DisplayName("cuando la forma es correcta, el mensaje dice que letra tocaba")
	void elMensajeDiceCualEraLaLetraBuena() {
		// Esto es lo que separa un "documento no valido", que deja al usuario mirando la
		// pantalla, de un error que se corrige solo.
		String mensaje = validar("12345678A").iterator().next().getMessage();

		assertTrue(mensaje.contains("Z"), "deberia decir cual es la letra buena: " + mensaje);
	}

	@Test
	@DisplayName("a quien escribe un NIF de K, L o M no se le habla de CIF")
	void elNifDeKLMNoSeConfundeConUnCif() {

		// Antes caian en la regla del CIF, porque su forma tambien encaja, y se rechazaban
		// con <<la letra M no es una inicial valida de CIF>>: un mensaje sobre un documento
		// que quien lo escribe no esta intentando escribir.
		String mensaje = validar("M1234567A").iterator().next().getMessage();

		assertTrue(mensaje.contains("NIF"), mensaje);
		assertFalse(mensaje.contains("CIF"), mensaje);
		assertTrue(mensaje.contains("L"), "deberia decir cual es la letra buena: " + mensaje);
	}

	@Test
	@DisplayName("cuando no se parece a nada, el mensaje ensena las tres formas")
	void elMensajeGeneralEnsenaLosTresEjemplos() {
		String mensaje = validar("ABCDEFGHI").iterator().next().getMessage();

		assertTrue(mensaje.contains("12345678Z"), mensaje);
		assertTrue(mensaje.contains("X1234567L"), mensaje);
		assertTrue(mensaje.contains("A58818501"), mensaje);
	}

}
