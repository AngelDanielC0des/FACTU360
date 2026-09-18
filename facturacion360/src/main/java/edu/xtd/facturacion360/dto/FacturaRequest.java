package edu.xtd.facturacion360.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos que recibimos para crear una factura o editar un borrador.
 * El número y los importes resultantes los determina el servidor.
 */
public record FacturaRequest(
		@NotNull(message = "El cliente es obligatorio")
		@Positive(message = "Elige el cliente al que se factura")
		Integer idCliente,

		@NotNull(message = "La fecha de emisión es obligatoria")
		LocalDate fechaEmision,

		@NotBlank(message = "El estado es obligatorio")
		@Pattern(regexp = "BORRADOR|EMITIDA|ANULADA", message = "El estado de la factura no es válido")
		String estado,

		@Size(max = 90, message = "Las observaciones no pueden superar 90 caracteres")
		String observaciones,

		@NotNull(message = "La lista de conceptos es obligatoria")
		List<@NotNull(message = "El concepto no puede ser nulo") @Valid ConceptoRequest> conceptos) {

}
