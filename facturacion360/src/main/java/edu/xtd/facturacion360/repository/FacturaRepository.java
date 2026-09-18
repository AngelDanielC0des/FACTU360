package edu.xtd.facturacion360.repository;

import java.time.LocalDate;
import java.util.List;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.SugerenciaConcepto;

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

	/**
	 * Se factura a un cliente que ya no está en la base de datos.
	 *
	 * <p>Pasa de verdad: el desplegable de «Nueva factura» se carga una vez, y si mientras
	 * tanto alguien borra ese cliente, al guardar se manda un identificador que ya no existe.
	 * Sin traducirlo, el manejador global respondía «no se puede realizar la operación porque
	 * hay datos relacionados», que dice justo lo contrario de lo que ha ocurrido: el problema
	 * no es que haya datos relacionados, es que faltan.</p>
	 */
	class ClienteInexistenteException extends RuntimeException {
		public ClienteInexistenteException(Throwable causa) {
			super("El cliente al que se factura ya no existe. Recarga la lista de clientes y "
					+ "vuelve a elegirlo", causa);
		}
	}

	public List<Factura> buscar(String busqueda);
	
	public Factura buscarPorId(int idFactura);

	public Factura buscarPorIdParaActualizar(int idFactura);

	public int actualizarBorrador(Factura factura);

	public void eliminarConceptos(int idFactura);

	public ClienteFactura buscarCliente(int idCliente);

	public List<ConceptoFactura> buscarConceptos(int idFactura);

	public List<SugerenciaConcepto> buscarSugerenciasConceptos(String texto, int limite);

	public List<Factura> buscarPorTrimestre(LocalDate fechaInicio, LocalDate fechaFin);

}
