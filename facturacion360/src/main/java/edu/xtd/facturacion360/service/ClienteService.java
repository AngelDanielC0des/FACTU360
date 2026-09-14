package edu.xtd.facturacion360.service;

import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import edu.xtd.facturacion360.dto.Cliente;
import edu.xtd.facturacion360.dto.CriteriosCliente;
import edu.xtd.facturacion360.dto.PaginaClienteResponse;
import edu.xtd.facturacion360.repository.ClienteRepository;

/**
 * Define las operaciones de negocio disponibles para la gestión de clientes.
 *
 * El Service actúa como intermediario entre el Controller y el Repository,
 * aplicando la lógica de negocio necesaria antes de acceder a la base de datos.
 */
public interface ClienteService {

	/**

	 * Devuelve los últimos clientes dados de alta.
	 *
	 * @param limite número máximo de clientes a devolver.
	 * @return lista de clientes.
	 * @throws DataAccessException si ocurre un error al acceder a la base de datos.
	 */
	List<Cliente> listarUltimos(int limite);

	/**

	 * Devuelve una página de clientes aplicando búsqueda, filtros y ordenación.
	 *
	 * @param criterios criterios de búsqueda y paginación.
	 * @return página de clientes con metadatos.
	 * @throws DataAccessException si ocurre un error al acceder a la base de datos.
	 */
	PaginaClienteResponse listarPagina(CriteriosCliente criterios);

	/**
	 * Obtiene todas las provincias disponibles.
	 *
	 * @return lista de provincias.
	 * @throws DataAccessException si ocurre un error al acceder a la base de datos.
	 */
	List<String> listarProvincias();

	/**
	 * Obtiene todas las poblaciones de una provincia.
	 *
	 * @param provincia provincia de la que se desean obtener las poblaciones.
	 * @return lista de poblaciones.
	 * @throws DataAccessException si ocurre un error al acceder a la base de datos.
	 */
	public List<String> listarPoblaciones(String provincia);

	/**
	 * El detalle completo de un cliente. Lo usa la tabla del frontend al desplegar una fila,
	 * para mostrar los campos que no caben en el listado, y la edición, para partir de lo que
	 * hay ahora mismo en la base de datos y no de una copia vieja de la pantalla.
	 *
	 * <p>Devuelve un {@link Optional} y no {@code null} porque "ese cliente no existe" es un
	 * resultado normal (acaba en un 404), no un error: así el controller lo resuelve con un
	 * {@code if} y no hay forma de olvidarse de comprobarlo.</p>
	 *
	 * @param id identificador del cliente que se busca
	 * @return el cliente (dominio), o un {@code Optional} vacío si no existe
	 * @throws DataAccessException si falla el acceso a la base de datos
	 * @autor AngelDanielC0des
	 * @see ClienteRepository#findById(int)
	 */
	public Optional<Cliente> obtenerPorId(int id);




	/**
	 * Modifica los datos de un cliente que ya existe.
	 *
	 * <p>La fecha de alta no forma parte de lo editable: se conserva la que hay en la base de
	 * datos, porque es un dato histórico y el {@code ClienteRequest} ni siquiera la trae.</p>
	 *
	 * <p>La comprobación de existencia y la escritura van en la misma transacción, para que
	 * entre las dos no pueda colarse un borrado de otro usuario.</p>
	 *
	 * <p>Devuelve un {@link Optional} por el mismo motivo que
	 * {@link #obtenerPorId(int)}: "ese cliente no existe" es un resultado normal que acaba
	 * en un 404, no un error, y así el controller no puede olvidarse de comprobarlo.</p>
	 *
	 * @param id      identificador del cliente que se modifica
	 * @param cliente los datos nuevos; su id y su fecha de alta se ignoran
	 * @return el cliente ya actualizado (con su fecha de alta original), o un {@code Optional}
	 *         vacío si no existe ningún cliente con ese id
	 * @throws DataAccessException si falla el acceso a la base de datos, incluida la violación
	 *                             del índice único de {@code nif_cif}
	 *                             ({@code DuplicateKeyException})
	 * @autor AngelDanielC0des
	 * @see #obtenerPorId(int)
	 * @see ClienteRepository#update(Cliente)
	 */
	public Optional<Cliente> actualizar(int id, Cliente cliente);
	
	/** Crea un nuevo cliente.
	 *
	 * @param cliente cliente a crear.
	 * @return cliente creado.
	 * @throws DataAccessException si ocurre un error al acceder a la base de datos.
	 */
	Cliente crear(Cliente cliente);



	/**
	 * Elimina un cliente por su identificador.
	 *
	 * Antes de eliminarlo, la implementación comprobará que el cliente exista.
	 * Si no existe, lanzará la excepción correspondiente para que el Controller
	 * pueda devolver un HTTP 404.
	 *
	 * @param id identificador del cliente.
	 * @throws DataAccessException si ocurre un error al acceder a la base de datos.
	 */
	void eliminar(int id);

}
