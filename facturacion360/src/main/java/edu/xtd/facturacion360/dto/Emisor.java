package edu.xtd.facturacion360.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record Emisor(String nombre, String cif, String direccion, String email, String telefono, @JsonIgnore byte[] logo) {

}
