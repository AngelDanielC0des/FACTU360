package edu.xtd.facturacion360.validacion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * El documento tiene que ser un DNI, un NIE o un CIF español de verdad.
 *
 * <p>Una anotación propia y no un {@code @Pattern} porque la letra de control <strong>no se
 * puede expresar con una expresión regular</strong>: hay que calcularla. Un patrón acepta
 * {@code 12345678A}, que tiene toda la pinta de un DNI y no lo es, porque a ese número le
 * corresponde la Z. En datos fiscales, cazar una errata de tecleo es justo lo que se
 * pretende.</p>
 *
 * <p>De paso resuelve el problema que tenía el proyecto: la misma regla estaba escrita de
 * tres maneras distintas —una por record— y ninguna aceptaba las tres formas. Ahora está
 * escrita una vez y se aplica poniendo la anotación.</p>
 *
 * <p>El campo en blanco se da por válido a propósito: de si es obligatorio o no se encarga
 * {@code @NotBlank}. Es la convención de Bean Validation y evita que un campo vacío suelte
 * dos errores a la vez diciendo lo mismo.</p>
 *
 * @author AngelDanielC0des
 */
@Documented
@Constraint(validatedBy = NifCifValidador.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface NifCif {

	/** Lo que se cuenta cuando no hay nada más concreto que decir. */
	String message() default "Introduce un DNI, un NIE o un CIF válido. "
			+ "Ejemplos: 12345678Z, X1234567L o A58818501";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
