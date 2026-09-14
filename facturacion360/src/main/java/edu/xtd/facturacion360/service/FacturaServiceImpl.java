package edu.xtd.facturacion360.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.DetalleFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.repository.FacturaRepository;

/**
 * Lógica necesaria para crear y buscar facturas.
 */
@Service
public class FacturaServiceImpl implements FacturaService {

	@Autowired
	FacturaRepository facturaRepository;

	@Override
	public Factura crear(FacturaRequest facturaRequest) {
		BigDecimal total = facturaRequest.subtotal().add(facturaRequest.importeIva());

		Factura factura = new Factura(
				0,
				facturaRequest.idCliente(),
				null,
				facturaRequest.numeroFactura(),
				facturaRequest.fechaEmision(),
				facturaRequest.estado(),
				facturaRequest.observaciones(),
				facturaRequest.subtotal(),
				facturaRequest.importeIva(),
				total);

		Factura facturaNueva = facturaRepository.insertar(factura);
		if (facturaNueva == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al insertar la factura");
		}

		return facturaNueva;
	}

	@Override
	public List<Factura> buscar(String busqueda) {
		return facturaRepository.buscar(busqueda);
	}

	@Transactional(readOnly = true)
	@Override
	public DetalleFactura obtenerDetalle(int idFactura) {
		if (idFactura <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de factura no es válido");
		}

		Factura factura = facturaRepository.buscarPorId(idFactura);
		if (factura == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la factura");
		}

		ClienteFactura cliente = facturaRepository.buscarCliente(factura.idCliente());
		if (cliente == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el cliente de la factura");
		}

		List<ConceptoFactura> conceptos = facturaRepository.buscarConceptos(idFactura);
		return new DetalleFactura(factura, cliente, conceptos);
	}
}
