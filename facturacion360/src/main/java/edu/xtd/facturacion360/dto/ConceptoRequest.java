package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
		BigDecimal porcentajeIva) {

	/** Evita que el lector JSON trunque una cantidad fraccionaria antes de validarla. */
	@JsonCreator
	public static ConceptoRequest desdeJson(
			@JsonProperty("descripcion") String descripcion,
			@JsonProperty("cantidad") BigDecimal cantidad,
			@JsonProperty("precioUnitario") BigDecimal precioUnitario,
			@JsonProperty("descuento") BigDecimal descuento,
			@JsonProperty("porcentajeIva") BigDecimal porcentajeIva) {
		return new ConceptoRequest(descripcion, cantidad == null ? null : cantidad.intValueExact(),
				precioUnitario, descuento, porcentajeIva);
	}
}
