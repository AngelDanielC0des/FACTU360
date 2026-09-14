package edu.xtd.facturacion360.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.Cliente;
import edu.xtd.facturacion360.dto.ClienteMapper;
import edu.xtd.facturacion360.dto.ClienteResponse;
import edu.xtd.facturacion360.dto.CriteriosCliente;
import edu.xtd.facturacion360.dto.PaginaClienteResponse;
import edu.xtd.facturacion360.repository.ClienteRepository;

/**
 * Implementación de {@link ClienteService}: la lógica de negocio de los clientes.
 *
 * <p>Hace de intermediaria entre el controller y el repositorio. Aquí viven las reglas que no
 * son ni HTTP ni SQL: calcular los metadatos de paginación, decidir qué es un error de negocio
 * y traducir el dominio a los DTO de respuesta con {@link ClienteMapper}.</p>
 *
 * <p>El detalle de cada método está documentado en la interfaz.</p>
 *
 * @see ClienteService
 */
@Service
public class ClienteServiceImpl implements ClienteService {

	private static final Logger log = LoggerFactory.getLogger(ClienteServiceImpl.class);


	@Autowired
	ClienteRepository clienteRepository;

	// Lo usamos para traducir Cliente (dominio) -> ClienteResponse dentro de la página.
	@Autowired
	ClienteMapper clienteMapper;

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<Cliente> listarUltimos(int limite) {
		// Regla de negocio ("los últimos N"): de momento solo delega en el repositorio.
		// Guardamos el resultado en una variable para poder loguearlo antes del return.
		List<Cliente> clientes = clienteRepository.findUltimos(limite);
		log.info("listarUltimos({}) -> {} clientes", limite, clientes.size());
		return clientes;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	// Las dos consultas de este método (las filas y el total) tienen que ver la MISMA foto
	// de la tabla. Sin transacción, cada una va por su cuenta: si alguien da de alta o
	// borra un cliente justo entre ambas, el total no cuadraría con las filas devueltas y
	// la paginación mostraría "12 clientes" con 10 filas.
	// readOnly = true además avisa al driver de que no vamos a escribir, y así puede
	// optimizar. Los demás métodos de lectura NO lo llevan a propósito: hacen una sola
	// consulta, así que no hay dos lecturas que puedan discrepar y solo añadiría trabajo.
	@Transactional(readOnly = true)
	@Override
	public PaginaClienteResponse listarPagina(CriteriosCliente criterios) {
		// Los criterios llegan ya acotados y limpios (lo hace el constructor compacto de
		// CriteriosCliente), así que aquí solo queda la lógica de negocio: pedir la
		// página, pedir el total y montar los metadatos de paginación.
		List<Cliente> clientes = clienteRepository.findPagina(criterios);
		// El total se cuenta CON los mismos criterios: si no, el nº de páginas no
		// cuadraría con lo que se ve en pantalla.
		long total = clienteRepository.contarTotal(criterios);

		// Math.ceil redondea HACIA ARRIBA: 28/10 = 2,8 -> 3 páginas (la última con 8). El
		// (double) es clave: sin él la división entera daría 2 y se perdería la última página.
		int totalPaginas = (int) Math.ceil((double) total / criterios.tamano());

		// Cada Cliente (dominio) se traduce a ClienteResponse (JSON). La referencia a método
		// clienteMapper::toResponse equivale a la lambda c -> clienteMapper.toResponse(c).
		List<ClienteResponse> contenido = clientes.stream().map(clienteMapper::toResponse).toList();

		int pagina = criterios.pagina();
		boolean hayAnterior  = pagina > 0;                   // hay anterior salvo en la página 0
		boolean haySiguiente = pagina < totalPaginas - 1;    // hay siguiente salvo en la última

		PaginaClienteResponse respuesta = new PaginaClienteResponse(
				contenido, pagina, totalPaginas, total, hayAnterior, haySiguiente);
		log.info("listarPagina({}) -> pagina {}/{}, {} elementos",
				criterios, pagina + 1, totalPaginas, total);
		return respuesta;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<String> listarProvincias() {
		List<String> provincias = clienteRepository.findProvincias();
		log.info("listarProvincias() -> {} provincias", provincias.size());
		return provincias;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public List<String> listarPoblaciones(String provincia) {
		List<String> poblaciones = clienteRepository.findPoblaciones(provincia);
		log.info("listarPoblaciones(provincia={}) -> {} poblaciones", provincia, poblaciones.size());
		return poblaciones;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	@Override
	public Optional<Cliente> obtenerPorId(int id) {
		Optional<Cliente> cliente = clienteRepository.findById(id);
		log.info("obtenerPorId({}) -> {}", id, cliente.isPresent() ? "encontrado" : "no existe");
		return cliente;
	}

	
	/**
	 * Crea un cliente delegando la persistencia en el repositorio.
	 *
	 * @param cliente datos del cliente que se va a crear
	 * @return true si el repositorio confirma la inserción; false en caso contrario
	 */
	@Override
	public Cliente crear(Cliente cliente) {
		Cliente clienteNuevo = null;

		clienteNuevo = clienteRepository.insert(cliente);
		if (clienteNuevo == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al insertar el cliente");
		}

		return clienteNuevo;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @autor AngelDanielC0des
	 */
	// Leer y escribir dentro de la MISMA transacción: entre la comprobación de que el cliente
	// existe y el UPDATE no puede colarse un borrado de otro usuario y dejarnos devolviendo un
	// cliente que ya no está en la tabla.
	@Transactional
	@Override
	public Optional<Cliente> actualizar(int id, Cliente cliente) {

		// Se comprueba ANTES si existe, en vez de deducirlo del número de filas que devuelve el
		// UPDATE. Ese número no distingue "el cliente no existe" de "existe pero se ha guardado
		// sin cambiar nada", y con la segunda lectura el usuario recibiría un 404 al pulsar
		// Guardar sin haber tocado ningún campo.
		Optional<Cliente> existente = clienteRepository.findById(id);
		Optional<Cliente> resultado;


		if (existente.isEmpty()) {
			log.warn("actualizar({}) -> el cliente no existe", id);
			resultado = Optional.empty();
		} else {
			// La fecha de alta no se edita: el UPDATE no toca esa columna, así que aquí
			// conservamos la que ya está en la BD. Si se copiara la del cliente recibido,
			// viajaría a null (el mapper la deja así al no venir en el ClienteRequest) y la
			// respuesta borraría la columna "Alta" de la fila en cuanto el frontend la
			// repintara.
			Cliente clienteActualizado = new Cliente(id, cliente.nombre(), cliente.nifCif(), cliente.direccion(),
					cliente.codigoPostal(), cliente.poblacion(), cliente.provincia(), cliente.telefono(),
					cliente.email(), existente.get().fechaAlta());


			// El boolean de update() se ignora a propósito: MySQL devuelve 0 filas afectadas
			// cuando la sentencia no cambia ningún valor, así que un false NO significa "no
			// existe". Eso ya lo ha resuelto el findById de arriba, en esta misma transacción.
			clienteRepository.update(clienteActualizado);

			log.info("actualizar({}) -> cliente actualizado", id);
			resultado = Optional.of(clienteActualizado);
		}


		return resultado;
	}
	
	
	@Override
	public void eliminar(int id) {

	    log.info("Intentando eliminar el cliente con ID {}", id);

	    boolean eliminado = clienteRepository.deleteById(id);

	    if (!eliminado) {

	        log.error("No se pudo eliminar el cliente con ID {}", id);

	        throw new ResponseStatusException(
	                HttpStatus.INTERNAL_SERVER_ERROR,
	                "No se pudo eliminar el cliente."
	        );
	    }

	    log.info("Cliente {} eliminado correctamente.", id);
	}
	
	// TODO: valorar la programación del método privado validarCifUnico mirar el
	// Diagrama de Clases
}
