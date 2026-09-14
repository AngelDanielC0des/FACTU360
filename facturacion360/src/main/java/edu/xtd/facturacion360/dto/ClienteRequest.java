package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que puede enviar un cliente HTTP para crear o actualizar un cliente.
 */
public record ClienteRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 60, message = "El nombre no puede superar 60 caracteres")
		String nombre,

		@NotBlank(message = "El NIF/CIF es obligatorio")
		@Size(max = 10, message = "El NIF/CIF no puede superar 10 caracteres")
		String nifCif,

		// Quitamos @NotBlank si pueden ir vacíos temporalmente
		@Size(max = 90, message = "La dirección no puede superar 90 caracteres")
		String direccion,

		@Size(max = 6, message = "El código postal no puede superar 6 caracteres")
		String codigoPostal,

		@Size(max = 30, message = "La población no puede superar 30 caracteres")
		String poblacion,

		@Size(max = 15, message = "La provincia no puede superar 15 caracteres")
		String provincia,

		@Size(max = 15, message = "El teléfono no puede superar 15 caracteres")
		String telefono,

		@Email(message = "El email debe tener un formato válido")
		@Size(max = 30, message = "El email no puede superar 30 caracteres")
		String email) {
}