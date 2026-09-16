package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos que puede enviar un cliente HTTP para crear o actualizar un cliente.
 */
public record ClienteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
        String nombre,

        @NotBlank(message = "El NIF/CIF es obligatorio")
        @Pattern(
                regexp = "^[0-9]{8}[A-Z]$",
                message = "El DNI debe tener 8 números y una letra mayúscula. Ejemplo: 12345678Z"
        )
        String nifCif,

        @Size(max = 90, message = "La dirección no puede superar 90 caracteres")
        String direccion,

        @Size(max = 6, message = "El código postal no puede superar 6 caracteres")
        @Pattern(
                regexp = "^$|^[0-9]{5}$",
                message = "El código postal debe tener 5 números"
        )
        String codigoPostal,

        @Size(max = 30, message = "La población no puede superar 30 caracteres")
        String poblacion,

        @Size(max = 15, message = "La provincia no puede superar 15 caracteres")
        String provincia,

        @Pattern(
                regexp = "^$|^(\\+34\\s?)?[6789][0-9]{8}$",
                message = "El teléfono debe tener un formato válido. Ejemplo: 612345678"
        )
        @Size(max = 15, message = "El teléfono no puede superar 15 caracteres")
        String telefono,

        @Email(message = "El email debe tener un formato válido")
        @Size(max = 30, message = "El email no puede superar 30 caracteres")
        String email
) {
}
