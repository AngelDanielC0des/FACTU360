package edu.xtd.facturacion360.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.xtd.facturacion360.dto.DetalleFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.service.FacturaService;
import jakarta.validation.Valid;

/**
 * Recibe las peticiones HTTP para crear y buscar facturas.
 */
@RestController
@RequestMapping("/factura")
public class FacturaController {

	private static final Logger log = LoggerFactory.getLogger(FacturaController.class);

	@Autowired
	FacturaService facturaService;

	@PostMapping
	public ResponseEntity<Factura> crear(@Valid @RequestBody FacturaRequest facturaRequest,
			BindingResult bindingResult) {
		ResponseEntity<Factura> respuesta;

		if (bindingResult.hasErrors()) {
			log.error("Factura recibida con errores de validación");
			respuesta = ResponseEntity.badRequest().build();
		} else {
			Factura facturaNueva = facturaService.crear(facturaRequest);
			respuesta = ResponseEntity.status(HttpStatus.CREATED).body(facturaNueva);
		}

		return respuesta;
	}

	@GetMapping("/buscar")
	public ResponseEntity<List<Factura>> buscar(
			@RequestParam(required = false, defaultValue = "") String busqueda) {
		List<Factura> facturas = facturaService.buscar(busqueda);
		ResponseEntity<List<Factura>> respuesta = ResponseEntity.ok(facturas);
		return respuesta;
	}

	@GetMapping("/{idFactura}/detalle")
	public ResponseEntity<DetalleFactura> obtenerDetalle(@PathVariable int idFactura) {
		DetalleFactura detalle = facturaService.obtenerDetalle(idFactura);
		return ResponseEntity.ok(detalle);
	}
}
