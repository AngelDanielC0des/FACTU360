package edu.xtd.facturacion360.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import edu.xtd.facturacion360.dto.Emisor;

@Repository
public class EmisorRepositoryImpl implements EmisorRepository{
	
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Override
	public boolean update(Emisor emisor) {

	    // Sentencia SQL que actualiza los datos de un cliente.
	    // Solo se modifican los campos editables; la fecha de alta se mantiene.
	    String sql = """
	        UPDATE `bd_facturacion`.`emisor` 
	        SET `nombre` = ?, 
	        `nif_cif` = ?, 
	        `direccion` = ?, 
	        `email` = ?, 
	        `telefono` = ? 
	        WHERE (`idemisor` = '1');
	        """;

	    // Ejecutamos la sentencia SQL utilizando JdbcTemplate.
	    // Cada '?' de la consulta se sustituye por el valor correspondiente
	    // del objeto Cliente.
	    int filas = jdbcTemplate.update(
	            sql,
	            emisor.nombre(),
	            emisor.cif(),
	            emisor.direccion(),
	            emisor.email(),
	            emisor.telefono()
	            );

	    // Si se ha modificado al menos una fila, devolvemos true.
	    // Si no se ha modificado ninguna, devolvemos false.
	    return filas == 1;
	}

}
