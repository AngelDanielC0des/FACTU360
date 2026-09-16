package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Emisor(

        @NotBlank(message = "El nombre o razón social es obligatorio")
        @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
        String nombre,

        @NotBlank(message = "El NIF/CIF es obligatorio")
        @Pattern(
                regexp = "^(?:[0-9]{8}[A-Za-z]|[ABCDEFGHJNPQRSUVWabcdefghjnpqrsuvw][0-9]{7}[0-9A-Ja-j])$",
                message = "El NIF/CIF no tiene un formato válido"
        )
        String cif,

        @NotBlank(message = "La dirección fiscal es obligatoria")
        @Size(min = 5, max = 250, message = "La dirección debe tener entre 5 y 250 caracteres")
        String direccion,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El correo electrónico no tiene un formato válido")
        @Size(max = 150, message = "El correo electrónico no puede superar los 150 caracteres")
        String email,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(
                regexp = "^(?:\\+34\\s?)?[6789][0-9]{8}$",
                message = "El teléfono no tiene un formato válido"
        )
        String telefono
) {
}