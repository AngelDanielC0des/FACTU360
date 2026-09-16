package edu.xtd.facturacion360.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Cliente(

        int idCliente,

        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El DNI/NIF es obligatorio")
        @Pattern(
            regexp = "^[0-9]{7}[A-Z]$",
            message = "El DNI debe tener 7 números y una letra mayúscula. Ejemplo: 1234567A"
        )
        String nifCif,

        @NotBlank(message = "La dirección es obligatoria")
        String direccion,

        @NotBlank(message = "El código postal es obligatorio")
        @Pattern(
            regexp = "^[0-9]{5}$",
            message = "El código postal debe tener 5 números"
        )
        String codigoPostal,

        @NotBlank(message = "La población es obligatoria")
        String poblacion,

        @NotBlank(message = "La provincia es obligatoria")
        String provincia,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(
            regexp = "^(\\+34\\s?)?[6789][0-9]{8}$",
            message = "El teléfono debe ser un teléfono español válido. Ejemplo: 612345678"
        )
        String telefono,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        String email,

        @NotNull(message = "La fecha de alta es obligatoria")
        LocalDate fechaAlta
) {

}
