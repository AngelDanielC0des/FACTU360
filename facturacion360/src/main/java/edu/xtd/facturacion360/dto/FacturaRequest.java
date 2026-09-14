package edu.xtd.facturacion360.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos que recibimos para dar de alta una factura.
 */
public record FacturaRequest(
		@NotNull(message = "El cliente es obligatorio")
		@Positive(message = "El cliente no es válido")
		Integer idCliente,

		@NotBlank(message = "El número de factura es obligatorio")
		@Size(max = 15, message = "El número de factura no puede superar 15 caracteres")
		String numeroFactura,

		@NotNull(message = "La fecha de emisión es obligatoria")
		LocalDate fechaEmision,

		@NotBlank(message = "El estado es obligatorio")
		@Pattern(regexp = "BORRADOR|EMITIDA|PAGADA|ANULADA", message = "El estado de la factura no es válido")
		String estado,

		@Size(max = 90, message = "Las observaciones no pueden superar 90 caracteres")
		String observaciones,

		@NotNull(message = "El subtotal es obligatorio")
		@DecimalMin(value = "0.00", message = "El subtotal no puede ser negativo")
		@Digits(integer = 8, fraction = 2, message = "El subtotal debe tener como máximo dos decimales")
		BigDecimal subtotal,

		@NotNull(message = "El importe de IVA es obligatorio")
		@DecimalMin(value = "0.00", message = "El importe de IVA no puede ser negativo")
		@Digits(integer = 8, fraction = 2, message = "El IVA debe tener como máximo dos decimales")
		BigDecimal importeIva) {

}
