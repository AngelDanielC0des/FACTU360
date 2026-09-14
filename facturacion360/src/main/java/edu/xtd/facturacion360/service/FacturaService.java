package edu.xtd.facturacion360.service;

import java.util.List;

import edu.xtd.facturacion360.dto.DetalleFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;

/**
 * Operaciones que ofrece la aplicación para trabajar con facturas.
 */
public interface FacturaService {

	public Factura crear(FacturaRequest facturaRequest);

	public List<Factura> buscar(String busqueda);

	public DetalleFactura obtenerDetalle(int idFactura);
}
