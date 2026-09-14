package edu.xtd.facturacion360.repository;

import org.springframework.stereotype.Repository;

import edu.xtd.facturacion360.dto.Emisor;

import java.util.Optional;


public interface EmisorRepository {

    // Método personalizado opcional por si necesitas buscar por NIF/CIF
   // Optional<Emisor> findByNifCif(String nifCif);
    boolean update(Emisor emisor);
}