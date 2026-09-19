package edu.xtd.facturacion360.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import edu.xtd.facturacion360.validacion.NifCif;

/**
 * Datos que puede enviar un cliente HTTP para crear o actualizar un cliente.
 */
public record ClienteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
        String nombre,

        // @NifCif y no un @Pattern: la letra de control no se puede calcular con una
        // expresion regular, y el patron que habia aqui solo admitia DNI de persona fisica,
        // asi que no se podia dar de alta a ninguna empresa como cliente.
        @NotBlank(message = "El NIF/CIF es obligatorio")
        @NifCif
        String nifCif,

        // Estos tres campos y los dos de arriba son NOT NULL en la base de datos, y el
        // formulario los marca obligatorios con su asterisco. Sin el @NotBlank, quien no
        // pase por el formulario -Postman, curl, otra pantalla- los manda vacios, pasan la
        // validacion y revienta MySQL; y ese fallo acaba respondiendo <<no se puede realizar
        // la operacion porque hay datos relacionados>>, que dice lo contrario de lo que pasa:
        // el problema no es que sobren datos relacionados, es que falta uno obligatorio.
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 90, message = "La dirección no puede superar 90 caracteres")
        String direccion,

        @Size(max = 6, message = "El código postal no puede superar 6 caracteres")
        @Pattern(
                regexp = "^$|^[0-9]{5}$",
                message = "El código postal debe tener 5 números"
        )
        String codigoPostal,

        @NotBlank(message = "La población es obligatoria")
        @Size(max = 30, message = "La población no puede superar 30 caracteres")
        String poblacion,

        @NotBlank(message = "La provincia es obligatoria")
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
