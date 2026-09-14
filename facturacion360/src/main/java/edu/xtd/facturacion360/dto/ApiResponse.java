package edu.xtd.facturacion360.dto;

/**
 * Respuesta estándar para las operaciones de la API.
 */
public record ApiResponse(
        boolean success,
        String message
) {}