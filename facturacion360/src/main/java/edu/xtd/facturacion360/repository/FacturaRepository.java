package edu.xtd.facturacion360.repository;

import java.time.LocalDate;
import java.util.List;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.Factura;

/**
 * Operaciones de base de datos que podemos realizar con las facturas.
 */
public interface FacturaRepository {

	public Factura insertar(Factura factura);

	public int obtenerUltimoNumero(int anio);

	public void insertarConceptos(int idFactura, List<ConceptoFactura> conceptos);

	/** Solo representa la colisión del índice único del número al insertar la cabecera. */
	class NumeroFacturaDuplicadoException extends RuntimeException {
		public NumeroFacturaDuplicadoException(Throwable causa) {
			super("El número de factura ya está ocupado", causa);
		}
	}

	public List<Factura> buscar(String busqueda);

	public Factura buscarPorId(int idFactura);

	public ClienteFactura buscarCliente(int idCliente);

	public List<ConceptoFactura> buscarConceptos(int idFactura);

	public List<Factura> buscarPorTrimestre(LocalDate fechaInicio, LocalDate fechaFin);

}
