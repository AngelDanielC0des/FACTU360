package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Datos de entrada de una línea; los importes resultantes los calcula Spring. */
public record ConceptoRequest(
		@NotBlank(message = "La descripción del concepto es obligatoria")
		@Size(max = 50, message = "La descripción no puede superar 50 caracteres")
		String descripcion,

		@NotNull(message = "La cantidad es obligatoria")
		@Positive(message = "La cantidad debe ser mayor que cero")
		Integer cantidad,

		@NotNull(message = "El precio unitario es obligatorio")
		@DecimalMin(value = "0.00", message = "El precio unitario no puede ser negativo")
		@Digits(integer = 8, fraction = 2, message = "El precio unitario supera la precisión admitida")
		BigDecimal precioUnitario,

		@NotNull(message = "El descuento es obligatorio")
		@DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
		@DecimalMax(value = "100.00", message = "El descuento no puede superar el 100 %")
		@Digits(integer = 3, fraction = 2, message = "El descuento admite como máximo dos decimales")
		BigDecimal descuento,

		@NotNull(message = "El porcentaje de IVA es obligatorio")
		@DecimalMin(value = "0.00", message = "El IVA no puede ser negativo")
		@DecimalMax(value = "99.99", message = "El IVA no puede superar el 99,99 %")
		@Digits(integer = 2, fraction = 2, message = "El IVA admite como máximo dos decimales")
		BigDecimal porcentajeIva,

		// Los dos fiscales. No son obligatorios en la peticion: quien no sepa de regimenes
		// -que es el caso de todo el formulario de hoy- los omite y se toma el caso normal.
		// El @Pattern esta para que, si alguien SI los manda, no cuele cualquier cosa: estos
		// dos valores acaban en el desglose que se declara a Hacienda.
		//
		// AVISO para quien anada exenciones a la pantalla: el formulario de facturas NO reenvia
		// estos dos campos al editar un borrador -recogerConceptos() en facturas.js solo recoge
		// descripcion, cantidad, precio, descuento e IVA-, asi que una linea exenta guardada por
		// API perderia su calificacion en silencio la primera vez que alguien la edite. Hoy no
		// pasa porque no hay forma de ponerla desde la aplicacion, pero el dia que la haya hay
		// que llevarlos tambien en el formulario.
		// Se queda en dos digitos a proposito y sin enumerar los validos: la AEAT amplia esa
		// lista cada pocos anos, y un patron cerrado dejaria fuera un regimen nuevo sin motivo.
		@Pattern(regexp = "[0-9]{2}", message = "La clave de régimen son dos dígitos")
		String claveRegimen,

		// Aqui SI se enumeran, porque la lista es corta y estable: sujeta (S1 y S2), no sujeta
		// (N1 y N2) y exenta (E1 a E6). El patron anterior era [SNE][0-9] y daba por buenos S0,
		// S7 o E0, que no existen. Un patron que valida y deja pasar basura es peor que no
		// tener ninguno, porque da confianza sin darla: estos dos caracteres acaban dentro de
		// una declaracion a Hacienda.
		@Pattern(regexp = "S[12]|N[12]|E[1-6]",
				message = "La calificación tiene que ser S1, S2, N1, N2 o de E1 a E6")
		String calificacion) {

	/**
	 * Los dos campos fiscales nunca quedan a nulo, se construya por donde se construya.
	 *
	 * <p>{@code @Pattern} da por válido el nulo —es la convención de Bean Validation—, así que
	 * quien construyera esto a mano con nulos pasaría la validación y reventaría después
	 * contra una columna {@code NOT NULL}.</p>
	 */
	public ConceptoRequest {
		claveRegimen = claveRegimen == null ? ClaveDesglose.REGIMEN_GENERAL : claveRegimen;
		calificacion = calificacion == null ? ClaveDesglose.SUJETA_NO_EXENTA : calificacion;
	}

	/**
	 * El caso normal: régimen general y sujeta y no exenta.
	 *
	 * <p>Los dos campos fiscales son opcionales para quien construye una petición a mano, igual
	 * que lo son en el JSON. Casi nadie va a querer decidirlos: hoy no hay ni un sitio en la
	 * aplicación donde se puedan elegir, y el día que lo haya seguirán siendo estos valores en
	 * la inmensa mayoría de las facturas.</p>
	 */
	public ConceptoRequest(String descripcion, Integer cantidad, BigDecimal precioUnitario,
			BigDecimal descuento, BigDecimal porcentajeIva) {

		this(descripcion, cantidad, precioUnitario, descuento, porcentajeIva,
				ClaveDesglose.REGIMEN_GENERAL, ClaveDesglose.SUJETA_NO_EXENTA);
	}

	/**
	 * Construye el concepto a partir del JSON que llega por HTTP.
	 *
	 * <p>La cantidad entra como {@code BigDecimal} y no como {@code Integer} a propósito:
	 * así el lector de JSON no trunca una cantidad fraccionaria <em>antes</em> de que la
	 * validación pueda quejarse. Un {@code 2.5} escrito a mano llegaría como {@code 2} y
	 * pasaría por bueno; con esto, {@code intValueExact()} lo rechaza.</p>
	 *
	 * <p>Los dos campos fiscales se pasan tal cual vengan, incluso a nulo: de ponerles el
	 * valor por defecto ya se encarga el constructor compacto, y repetirlo aquí dejaría la
	 * misma regla escrita en dos sitios del mismo fichero.</p>
	 */
	@JsonCreator
	public static ConceptoRequest desdeJson(
			@JsonProperty("descripcion") String descripcion,
			@JsonProperty("cantidad") BigDecimal cantidad,
			@JsonProperty("precioUnitario") BigDecimal precioUnitario,
			@JsonProperty("descuento") BigDecimal descuento,
			@JsonProperty("porcentajeIva") BigDecimal porcentajeIva,
			@JsonProperty("claveRegimen") String claveRegimen,
			@JsonProperty("calificacion") String calificacion) {

		return new ConceptoRequest(descripcion, cantidad == null ? null : cantidad.intValueExact(),
				precioUnitario, descuento, porcentajeIva, claveRegimen, calificacion);
	}
}
