package edu.xtd.facturacion360.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
 * Los dos campos fiscales de una línea: qué valores se aceptan y qué pasa si no vienen.
 *
 * <p>Importa más de lo que parece para dos caracteres: la calificación acaba dentro del
 * desglose que se declara a Hacienda, así que un valor inventado no se puede quedar guardado
 * a la espera de que alguien lo mire.</p>
 */
class ConceptoRequestTests {

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

	private static ConceptoRequest conCalificacion(String calificacion) {
		return new ConceptoRequest("Servicio", 1, new BigDecimal("100.00"), new BigDecimal("0.00"),
				new BigDecimal("21.00"), ClaveDesglose.REGIMEN_GENERAL, calificacion);
	}

	private Set<ConstraintViolation<ConceptoRequest>> validar(ConceptoRequest peticion) {
		return validador.validate(peticion);
	}

	@ParameterizedTest
	@ValueSource(strings = { "S1", "S2", "N1", "N2", "E1", "E2", "E3", "E4", "E5", "E6" })
	@DisplayName("acepta las calificaciones que existen de verdad")
	void aceptaLasDiez(String calificacion) {
		assertTrue(validar(conCalificacion(calificacion)).isEmpty(),
				"deberia aceptar " + calificacion);
	}

	@ParameterizedTest
	@ValueSource(strings = { "S0", "S3", "S9", "N0", "N3", "E0", "E7", "X1", "1S", "SS", "S" })
	@DisplayName("rechaza las que no existen, que el patron anterior colaba")
	void rechazaLoQueNoExiste(String calificacion) {
		// El patron era [SNE][0-9], asi que daba por buenos S0, S9, N0, E0 y E7. Ninguno de
		// esos es una calificacion de la AEAT. Un patron que valida y deja pasar basura es peor
		// que no tener ninguno: da confianza sin darla.
		assertEquals(1, validar(conCalificacion(calificacion)).size(),
				"deberia rechazar " + calificacion);
	}

	@Test
	@DisplayName("si no vienen, se toma el caso normal en vez de quedarse a nulo")
	void sinFiscalesSeAsumeElCasoNormal() {
		// Por el constructor corto, que es el que usa todo el codigo de hoy...
		ConceptoRequest corto = new ConceptoRequest("Servicio", 1, new BigDecimal("100.00"),
				new BigDecimal("0.00"), new BigDecimal("21.00"));

		assertEquals(ClaveDesglose.REGIMEN_GENERAL, corto.claveRegimen());
		assertEquals(ClaveDesglose.SUJETA_NO_EXENTA, corto.calificacion());

		// ...y tambien pasando nulos a proposito, porque @Pattern da por bueno el nulo y sin el
		// constructor compacto acabaria contra una columna NOT NULL.
		ConceptoRequest conNulos = new ConceptoRequest("Servicio", 1, new BigDecimal("100.00"),
				new BigDecimal("0.00"), new BigDecimal("21.00"), null, null);

		assertEquals(ClaveDesglose.REGIMEN_GENERAL, conNulos.claveRegimen());
		assertEquals(ClaveDesglose.SUJETA_NO_EXENTA, conNulos.calificacion());
		assertTrue(validar(conNulos).isEmpty());
	}

	@Test
	@DisplayName("la clave de regimen se queda abierta, y es a proposito")
	void elRegimenAdmiteCualquierParDeDigitos() {
		// La AEAT amplia esa lista cada pocos anos. Un patron cerrado dejaria fuera un regimen
		// nuevo sin motivo, y el dato no se inventa: viene de una lista oficial.
		assertTrue(validar(new ConceptoRequest("Servicio", 1, new BigDecimal("100.00"),
				new BigDecimal("0.00"), new BigDecimal("21.00"), "17",
				ClaveDesglose.SUJETA_NO_EXENTA)).isEmpty());

		assertEquals(1, validar(new ConceptoRequest("Servicio", 1, new BigDecimal("100.00"),
				new BigDecimal("0.00"), new BigDecimal("21.00"), "AB",
				ClaveDesglose.SUJETA_NO_EXENTA)).size());
	}

}
