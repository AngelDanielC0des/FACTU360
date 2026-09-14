package edu.xtd.facturacion360.dto;

/**
 * Respuesta estándar de la API.
 */
public record ApiResponseDto(
        boolean success,
        String message
) {
}