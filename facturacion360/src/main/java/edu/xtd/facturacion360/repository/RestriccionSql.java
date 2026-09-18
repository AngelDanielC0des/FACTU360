package edu.xtd.facturacion360.repository;

import java.sql.SQLException;

/**
 * Mira por debajo de una excepción de Spring para averiguar QUÉ restricción de la base de
 * datos ha saltado.
 *
 * <p>Spring envuelve los errores de JDBC en su propia jerarquía: un NIF repetido y un número
 * de factura repetido llegan los dos como {@code DuplicateKeyException}, indistinguibles. La
 * información de cuál de los dos ha sido está más abajo, en la {@link SQLException} original,
 * que trae el código de MySQL y el nombre de la restricción.</p>
 *
 * <p><strong>Esto NO es para enseñárselo al usuario.</strong> El mensaje original de MySQL es
 * del tipo {@code Duplicate entry 'B12345674' for key 'clientes.nif_cif_UNIQUE'}: filtra la
 * tabla, el índice y el valor que se intentó meter. Aquí solo se LEE para saber qué pasó, y
 * quien llama traduce eso a una excepción de dominio con un mensaje propio.</p>
 *
 * <p>Es el único fichero del proyecto que sabe de códigos de error de MySQL. Está así a
 * propósito: si algún día se cambia de motor, solo hay que tocar aquí.</p>
 *
 * @author AngelDanielC0des
 */
final class RestriccionSql {

	/**
	 * SQLState estándar de «violación de una restricción de integridad».
	 *
	 * <p>Se comprueba ADEMÁS del código de MySQL y no en su lugar: el mismo 1062 con otro
	 * SQLState no es esta situación, y darlo por bueno haría que un error de conexión se
	 * tradujera como si fuera un dato repetido.</p>
	 */
	private static final String VIOLACION_DE_INTEGRIDAD = "23000";

	/** MySQL: se ha intentado meter un valor que ya existe en un índice único. */
	private static final int DUPLICADO = 1062;

	/** MySQL: no se puede insertar la fila hija porque la fila padre no existe. */
	private static final int PADRE_QUE_FALTA = 1452;

	/** MySQL: no se puede borrar la fila padre porque hay filas hijas apuntándole. */
	private static final int PADRE_CON_HIJOS = 1451;

	private RestriccionSql() {
	}

	/**
	 * ¿Este error es una colisión del índice único indicado?
	 *
	 * @param error       lo que ha lanzado Spring
	 * @param indice      el nombre del índice, sin la tabla: {@code nif_cif_UNIQUE}
	 * @return si la colisión es de ese índice y no de otro
	 */
	static boolean duplicado(Throwable error, String indice) {
		return coincide(error, DUPLICADO, indice);
	}

	/**
	 * ¿Este error es un borrado bloqueado por la clave ajena indicada?
	 *
	 * @param error lo que ha lanzado Spring
	 * @param clave el nombre de la clave ajena: {@code FK_CLIENTE}
	 * @return si el borrado lo bloquea esa clave y no otra
	 */
	static boolean padreConHijos(Throwable error, String clave) {
		return coincide(error, PADRE_CON_HIJOS, clave);
	}

	/**
	 * ¿Este error es una fila que apunta a un padre que no existe?
	 *
	 * <p>No es lo mismo que {@link #padreConHijos}, aunque las dos sean «violación de clave
	 * ajena» y Spring las entregue con la misma excepción. Aquella es «no puedo borrar esto
	 * porque hay cosas colgando»; esta es «no puedo guardar esto porque aquello ya no está».
	 * Confundirlas le cuenta al usuario justo lo contrario de lo que ha pasado.</p>
	 *
	 * @param error lo que ha lanzado Spring
	 * @param clave el nombre de la clave ajena: {@code FK_CLIENTE}
	 * @return si lo que falta es el padre de esa clave y no de otra
	 */
	static boolean padreQueFalta(Throwable error, String clave) {
		return coincide(error, PADRE_QUE_FALTA, clave);
	}

	/**
	 * Recorre la cadena de causas buscando una SQLException con ese código que además nombre
	 * esa restricción.
	 *
	 * <p>Se recorre entera y no se mira solo la causa directa porque Spring encadena varios
	 * niveles: excepción propia, luego la del driver, y dentro la SQLException de verdad.</p>
	 */
	private static boolean coincide(Throwable error, int codigo, String restriccion) {
		Throwable causa = error;

		while (causa != null) {
			if (causa instanceof SQLException errorSql
					&& errorSql.getErrorCode() == codigo
					&& VIOLACION_DE_INTEGRIDAD.equals(errorSql.getSQLState())
					&& nombra(errorSql.getMessage(), restriccion)) {
				return true;
			}

			causa = causa.getCause();
		}

		return false;
	}

	/**
	 * ¿El mensaje de MySQL nombra esta restricción?
	 *
	 * <p>Hay que comprobar tres formas, y no es por gusto: MySQL 8 antepone la tabla al
	 * índice y las versiones anteriores no, y las claves ajenas van entre acentos graves en
	 * vez de entre comillas simples. Mirar solo una de las tres haría que el código
	 * funcionara en la máquina de quien lo escribió y no en la del de al lado, que es el peor
	 * tipo de fallo que se puede dejar puesto.</p>
	 *
	 * <pre>
	 * Duplicate entry 'B12' for key 'nif_cif_UNIQUE'             -- MySQL 5
	 * Duplicate entry 'B12' for key 'clientes.nif_cif_UNIQUE'    -- MySQL 8
	 * ... a foreign key constraint fails (CONSTRAINT `FK_CLIENTE` ...)
	 * </pre>
	 */
	private static boolean nombra(String mensaje, String restriccion) {
		if (mensaje == null) {
			return false;
		}

		return mensaje.contains("'" + restriccion + "'")
				|| mensaje.contains("." + restriccion + "'")
				|| mensaje.contains("`" + restriccion + "`");
	}

}
