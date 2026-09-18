package edu.xtd.facturacion360.dto;

import java.time.LocalDate;

/**
 * Un cliente tal y como está en la base de datos.
 *
 * <p><strong>Sin anotaciones de validación a propósito.</strong> Este record es de SALIDA:
 * lo construye el repositorio leyendo una fila que ya está guardada, y no llega nunca por el
 * cuerpo de una petición. Ningún {@code @Valid} del proyecto le apunta, así que lo que se
 * escribiera aquí no se ejecutaría jamás.</p>
 *
 * <p>Y no es una cuestión de estilo: las que tenía estaban mal. Exigía un DNI de
 * <em>siete</em> dígitos, que no existe, de modo que el día que alguien le pusiera un
 * {@code @Valid} habría dejado de poder leerse ningún cliente real de la base de datos. Un
 * validador que no se ejecuta no avisa de que está equivocado.</p>
 *
 * <p>Lo que sí se valida es {@link ClienteRequest}, que es lo que entra por HTTP y lo único
 * que un usuario puede escribir.</p>
 */
public record Cliente(
		int idCliente,
		String nombre,
		String nifCif,
		String direccion,
		String codigoPostal,
		String poblacion,
		String provincia,
		String telefono,
		String email,
		LocalDate fechaAlta) {

}
