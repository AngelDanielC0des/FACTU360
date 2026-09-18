package edu.xtd.facturacion360.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * Comprueba que se distingue QUÉ restricción de la base de datos ha saltado.
 *
 * <p>No hace falta base de datos: la excepción se fabrica a mano con el código y el mensaje
 * que devolvería MySQL. Eso permite probar los formatos de las dos versiones sin tener las
 * dos instaladas, que es justo donde está el riesgo.</p>
 */
class RestriccionSqlTests {

	/** Como llega de verdad: la excepción de Spring envolviendo la del driver. */
	private static DuplicateKeyException duplicadoDe(String mensaje) {
		return new DuplicateKeyException("error de clave duplicada",
				new SQLException(mensaje, "23000", 1062));
	}

	private static DataIntegrityViolationException integridadDe(String mensaje, int codigo) {
		return new DataIntegrityViolationException("error de integridad",
				new SQLException(mensaje, "23000", codigo));
	}

	@Test
	@DisplayName("reconoce el indice sin la tabla delante, como lo nombra MySQL 5")
	void reconoceElFormatoSinTabla() {
		assertTrue(RestriccionSql.duplicado(
				duplicadoDe("Duplicate entry 'B12345674' for key 'nif_cif_UNIQUE'"),
				"nif_cif_UNIQUE"));
	}

	@Test
	@DisplayName("y con la tabla delante, como lo nombra MySQL 8")
	void reconoceElFormatoConTabla() {
		// Este es el caso que haria fallar el codigo en la maquina del de al lado si solo se
		// comprobara uno de los dos formatos.
		assertTrue(RestriccionSql.duplicado(
				duplicadoDe("Duplicate entry 'B12345674' for key 'clientes.nif_cif_UNIQUE'"),
				"nif_cif_UNIQUE"));
	}

	@Test
	@DisplayName("no confunde un indice con otro")
	void noConfundeUnIndiceConOtro() {
		DuplicateKeyException numeroRepetido =
				duplicadoDe("Duplicate entry 'F-2026-0007' for key 'facturas.num_factura_UNIQUE'");

		assertTrue(RestriccionSql.duplicado(numeroRepetido, "num_factura_UNIQUE"));
		assertFalse(RestriccionSql.duplicado(numeroRepetido, "nif_cif_UNIQUE"));
	}

	@Test
	@DisplayName("reconoce la clave ajena, que MySQL nombra entre acentos graves")
	void reconoceLaClaveAjena() {
		DataIntegrityViolationException conHijos = integridadDe(
				"Cannot delete or update a parent row: a foreign key constraint fails "
						+ "(`bd_facturacion`.`facturas`, CONSTRAINT `FK_CLIENTE` FOREIGN KEY "
						+ "(`idcliente`) REFERENCES `clientes` (`idcliente`))",
				1451);

		assertTrue(RestriccionSql.padreConHijos(conHijos, "FK_CLIENTE"));
		assertFalse(RestriccionSql.padreConHijos(conHijos, "FK_FACTURA"));
	}

	@Test
	@DisplayName("no confunde el padre que falta con el padre que tiene hijos")
	void distingueLos1451DeLos1452() {
		// Los dos son violaciones de la MISMA clave ajena y Spring los entrega con la misma
		// excepcion, pero cuentan lo contrario: 1451 es "no puedo borrar esto porque hay cosas
		// colgando", 1452 es "no puedo guardar esto porque aquello ya no esta".
		DataIntegrityViolationException padreQueFalta = integridadDe(
				"Cannot add or update a child row: a foreign key constraint fails "
						+ "(`bd_facturacion`.`facturas`, CONSTRAINT `FK_CLIENTE` FOREIGN KEY "
						+ "(`idcliente`) REFERENCES `clientes` (`idcliente`))",
				1452);

		assertTrue(RestriccionSql.padreQueFalta(padreQueFalta, "FK_CLIENTE"));
		assertFalse(RestriccionSql.padreConHijos(padreQueFalta, "FK_CLIENTE"));
	}

	@Test
	@DisplayName("encuentra la causa aunque este a varios niveles de profundidad")
	void buscaEnTodaLaCadenaDeCausas() {
		SQLException raiz = new SQLException(
				"Duplicate entry 'B12345674' for key 'clientes.nif_cif_UNIQUE'", "23000", 1062);
		RuntimeException intermedia = new RuntimeException("algo por el medio", raiz);
		DuplicateKeyException arriba = new DuplicateKeyException("error", intermedia);

		assertTrue(RestriccionSql.duplicado(arriba, "nif_cif_UNIQUE"));
	}

	@Test
	@DisplayName("el codigo de error tambien tiene que cuadrar")
	void noBastaConQueElNombreAparezca() {
		// Mismo texto, otro codigo: no es la situacion que buscamos.
		DataIntegrityViolationException otroCodigo =
				integridadDe("Duplicate entry 'B12' for key 'nif_cif_UNIQUE'", 1452);

		assertFalse(RestriccionSql.duplicado(otroCodigo, "nif_cif_UNIQUE"));
	}

	@Test
	@DisplayName("el SQLState tambien tiene que cuadrar, no solo el codigo")
	void noBastaConElCodigoDeError() {
		// Mismo 1062 pero con SQLState de error de conexion: no es una violacion de
		// integridad, y tratarlo como tal seria contarle al usuario que su NIF esta repetido
		// cuando lo que ha pasado es que se ha caido la base de datos.
		DuplicateKeyException otroEstado = new DuplicateKeyException("error",
				new SQLException("Duplicate entry 'B12' for key 'nif_cif_UNIQUE'", "08000", 1062));

		assertFalse(RestriccionSql.duplicado(otroEstado, "nif_cif_UNIQUE"));
	}

	@Test
	@DisplayName("no revienta cuando no hay causa, o no es de SQL, o no trae mensaje")
	void aguantaLoQueVengaSinLanzar() {
		assertFalse(RestriccionSql.duplicado(new DuplicateKeyException("sin causa"),
				"nif_cif_UNIQUE"));

		assertFalse(RestriccionSql.duplicado(
				new DuplicateKeyException("otra cosa", new IllegalStateException("nada de SQL")),
				"nif_cif_UNIQUE"));

		assertFalse(RestriccionSql.duplicado(
				new DuplicateKeyException("error", new SQLException(null, "23000", 1062)),
				"nif_cif_UNIQUE"));
	}

}
