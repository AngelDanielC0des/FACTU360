package edu.xtd.facturacion360.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Esta clase se encarga de manejar las excepciones que se pueden producir
 * cuando trabajamos con los clientes y las facturas.
 *
 * En vez de repetir los bloques try/catch en cada método del controlador,
 * aquí indicamos qué respuesta HTTP se devuelve para cada tipo de error.
 */
@RestControllerAdvice
public class ManejadorExcepciones {

	private static final Logger log = LoggerFactory.getLogger(ManejadorExcepciones.class);

	/**
	 * Gestiona el error que se produce cuando intentamos guardar un dato que ya
	 * existe.
	 *
	 * @param excepcion excepción producida por tener un dato duplicado
	 * @return respuesta 409 indicando que el registro ya existe
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<String> gestionarDatoDuplicado(DuplicateKeyException excepcion) {
		log.error("Se ha intentado guardar un dato que ya existe", excepcion);
		return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe un registro con ese dato");
	}

	/**
	 * Gestiona el error que se produce cuando no se puede borrar un cliente porque
	 * tiene otros datos relacionados.
	 *
	 * @param excepcion excepción producida por la relación entre los datos
	 * @return respuesta 409 indicando que no se puede realizar la operación
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<String> gestionarIntegridadDatos(DataIntegrityViolationException excepcion) {
		log.error("La operación incumple una relación de la base de datos", excepcion);
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body("No se puede realizar la operación porque hay datos relacionados");
	}

	/**
	 * Gestiona los errores generales que se pueden producir al consultar o
	 * modificar la base de datos.
	 *
	 * @param excepcion excepción producida al acceder a la base de datos
	 * @return respuesta 500 indicando que existe un error de base de datos
	 */
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<String> gestionarBaseDatos(DataAccessException excepcion) {
		log.error("Error al acceder a la base de datos", excepcion);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al acceder a la base de datos");
	}

	/**
	 * Gestiona los errores que se pueden producir al completar una transacción.
	 *
	 * @param excepcion excepción producida durante la transacción
	 * @return respuesta 500 indicando que no se pudo completar la operación
	 */
	@ExceptionHandler(TransactionException.class)
	public ResponseEntity<String> gestionarTransaccion(TransactionException excepcion) {
		log.error("Error al completar la operación en la base de datos", excepcion);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("No se ha podido completar la operación");
	}

	/**
	 * Gestiona las excepciones que ya contienen un código de estado HTTP, como el
	 * error 404 cuando no se encuentra un cliente.
	 *
	 * @param excepcion excepción que contiene el estado y el mensaje del error
	 * @return respuesta con el código y el mensaje de la excepción
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<String> gestionarEstadoHttp(ResponseStatusException excepcion) {
		log.error("No se ha podido realizar la operación solicitada", excepcion);
		String mensaje = excepcion.getReason();
		return ResponseEntity.status(excepcion.getStatusCode()).body(mensaje);
	}

}
