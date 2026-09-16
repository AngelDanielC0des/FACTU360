package edu.xtd.facturacion360.service;

import edu.xtd.facturacion360.dto.Emisor;

public interface EmisorService {

    /**
     * Guarda el emisor.
     *
     * Si ya existe, lo actualiza.
     * Si no existe, lo crea.
     *
     * @return el emisor guardado o null si no se pudo guardar.
     */
    Emisor save(Emisor emisor);

    /**
     * Devuelve el emisor actual.
     *
     * @return el emisor o null si no existe.
     */
    Emisor find();
}