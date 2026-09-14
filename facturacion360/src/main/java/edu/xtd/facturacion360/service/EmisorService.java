package edu.xtd.facturacion360.service;

import edu.xtd.facturacion360.dto.Emisor;

public interface


EmisorService {

	/**
     * Actualiza un emisor existente por su ID.
     *
     * @param id          Identificador único del emisor (idemisor).
     * @param emisorDatos Objeto con los campos actualizados.
     * @return El emisor guardado en la base de datos.
     */
    Emisor update(Emisor emisor);
}
