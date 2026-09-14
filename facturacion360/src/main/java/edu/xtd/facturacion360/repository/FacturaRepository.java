package edu.xtd.facturacion360.repository;

import java.util.List;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.Factura;

/**
 * Operaciones de base de datos que podemos realizar con las facturas.
 */
public interface FacturaRepository {

	public Factura insertar(Factura factura);

	public List<Factura> buscar(String busqueda);

	public Factura buscarPorId(int idFactura);

	public ClienteFactura buscarCliente(int idCliente);

	public List<ConceptoFactura> buscarConceptos(int idFactura);
}
