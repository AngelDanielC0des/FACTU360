package edu.xtd.facturacion360.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;

import edu.xtd.facturacion360.dto.Cliente;
import edu.xtd.facturacion360.dto.CriteriosCliente;

/**
 * Contrato de acceso a datos para la entidad Cliente.
 *
 * Esta interfaz define todas las operaciones de persistencia disponibles
 * sobre la tabla clientes.
 *
 * La implementación se realiza mediante JdbcTemplate en
 * {@link ClienteRepositoryJdbcImpl}.
 */
public interface ClienteRepository {


    /**
     * Devuelve los últimos clientes registrados.
     */
    List<Cliente> findUltimos(int limite);

    /**
     * Devuelve una página de clientes aplicando filtros y ordenación.
     */
    List<Cliente> findPagina(CriteriosCliente criterios);

    /**
     * Cuenta el total de clientes que cumplen unos criterios.
     */
    long contarTotal(CriteriosCliente criterios);

	/**
	 * Un cliente concreto por su identificador. Es el detalle completo: trae también los
	 * campos que el listado no muestra (dirección, código postal, población y provincia).
	 *
	 * @param id identificador del cliente que se busca
	 * @return el cliente envuelto en un {@link Optional}, o un {@code Optional} vacío si no
	 *         existe ninguno con ese id
	 * @throws DataAccessException si falla el acceso a la base de datos
	 * @autor AngelDanielC0des
	 * @see #findPagina(CriteriosCliente)
	 */
	public Optional<Cliente> findById (int id);
	
	/**
	 * Inserta un cliente nuevo en el almacenamiento persistente.
	 *
	 * @param cliente datos del cliente que se va a insertar
	 * @return true si se inserta correctamente; false en caso contrario
	 */
	public Cliente insert (Cliente cliente);
	
	/**
	 * Guarda los datos de un cliente que ya existe, identificándolo por su
	 * {@link Cliente#idCliente()}. No toca la fecha de alta: es un dato histórico y no forma
	 * parte de lo editable.
	 *
	 * <p><strong>Ojo con el valor devuelto:</strong> es el número de filas afectadas por el
	 * {@code UPDATE} traducido a booleano, y MySQL cuenta 0 filas cuando la sentencia no
	 * cambia ningún valor. Un {@code false} significa "no se ha modificado nada", que puede
	 * ser tanto que el cliente no exista como que se haya guardado igual que estaba: no sirve
	 * para distinguir esos dos casos. Quien necesite saber si existe debe comprobarlo con
	 * {@link #findById(int)}.</p>
	 *
	 * @param cliente los datos nuevos, con el id del cliente que se modifica
	 * @return {@code true} si la sentencia modificó alguna fila; {@code false} si no modificó
	 *         ninguna (ver la nota de arriba)
	 * @throws DataAccessException si falla el acceso a la base de datos; en particular
	 *                             {@code DuplicateKeyException} si el NIF/CIF ya es de otro
	 *                             cliente, porque la columna tiene un índice único
	 * @autor AngelDanielC0des
	 * @see #findById(int)
	 */
	public boolean update (Cliente cliente);
	
	public boolean deleteById (int id);

	
	 /**
     * Devuelve todas las provincias existentes.
     */
    List<String> findProvincias();

    /**
     * Devuelve las poblaciones pertenecientes a una provincia.
     */
    List<String> findPoblaciones(String provincia);

}


