package edu.xtd.facturacion360.repository;

import java.util.Optional;

import edu.xtd.facturacion360.dto.Emisor;

public interface EmisorRepository {

    /**
     * Actualiza el emisor existente.
     *
     * @return true si se ha actualizado una fila.
     */
    boolean update(Emisor emisor);

    /**
     * Crea un nuevo emisor.
     *
     * @return true si se ha insertado una fila.
     */
    boolean insert(Emisor emisor);

    /**
     * Busca el emisor principal.
     */
    Optional<Emisor> find();
    
    
    /**
     * Busca el emisor principal.
     */
    byte[] findLogo();
}