package edu.xtd.facturacion360.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import edu.xtd.facturacion360.dto.Emisor;

@Repository
public class EmisorRepositoryImpl implements EmisorRepository {

    private static final int ID_EMISOR_PRINCIPAL = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public boolean update(Emisor emisor) {

        String sql = """
                UPDATE `bd_facturacion`.`emisor`
                SET
                    `nombre` = ?,
                    `nif_cif` = ?,
                    `direccion` = ?,
                    `email` = ?,
                    `telefono` = ?, 
                    `logo` = ?,
                WHERE `idemisor` = ?
                """;

        int filas = jdbcTemplate.update(
                sql,
                emisor.nombre(),
                emisor.cif(),
                emisor.direccion(),
                emisor.email(),
                emisor.telefono(),
                emisor.logo(),
                ID_EMISOR_PRINCIPAL
        );

        return filas == 1;
    }

    @Override
    public boolean insert(Emisor emisor) {

        String sql = """
                INSERT INTO `bd_facturacion`.`emisor`
                (
                    `idemisor`,
                    `nombre`,
                    `nif_cif`,
                    `direccion`,
                    `email`,
                    `telefono`, 
                    `logo`
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        int filas = jdbcTemplate.update(
                sql,
                ID_EMISOR_PRINCIPAL,
                emisor.nombre(),
                emisor.cif(),
                emisor.direccion(),
                emisor.email(),
                emisor.telefono(), 
                emisor.logo()
        );

        return filas == 1;
    }

    @Override
    public Optional<Emisor> find() {

        String sql = """
                SELECT
                    `nombre`,
                    `nif_cif`,
                    `direccion`,
                    `email`,
                    `telefono`
                FROM `bd_facturacion`.`emisor`
                WHERE `idemisor` = ?
                """;

        try {

            Emisor emisor = jdbcTemplate.queryForObject(
                    sql,
                    (rs, rowNum) -> new Emisor(
                            rs.getString("nombre"),
                            rs.getString("nif_cif"),
                            rs.getString("direccion"),
                            rs.getString("email"),
                            rs.getString("telefono"),
                            null
                    ),
                    ID_EMISOR_PRINCIPAL
            );

            return Optional.ofNullable(emisor);

        } catch (EmptyResultDataAccessException e) {

            return Optional.empty();
        }
    }

    @Override
    public byte[] findLogo() {

        String sql = """
                SELECT logo
                FROM `bd_facturacion`.`emisor`
                WHERE idemisor = ?
                """;

        try {

            return jdbcTemplate.queryForObject(
                    sql,
                    (rs, rowNum) -> rs.getBytes("logo"),
                    ID_EMISOR_PRINCIPAL
            );

        } catch (EmptyResultDataAccessException e) {

        	    throw e;
            //return null;
        }
    }
}