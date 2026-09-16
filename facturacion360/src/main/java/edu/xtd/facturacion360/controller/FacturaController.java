package edu.xtd.facturacion360.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.DetalleFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.dto.ResumenTrimestralFactura;
import edu.xtd.facturacion360.dto.SugerenciaConcepto;
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
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					bindingResult.getAllErrors().get(0).getDefaultMessage());
		} else {
			Factura facturaNueva = facturaService.crear(facturaRequest);
			respuesta = ResponseEntity.status(HttpStatus.CREATED).body(facturaNueva);
		}

		return respuesta;
	}

	@PutMapping("/{idFactura}/borrador")
	public ResponseEntity<Factura> editarBorrador(@PathVariable int idFactura,
			@Valid @RequestBody FacturaRequest facturaRequest, BindingResult errores) {
		if (errores.hasErrors()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					errores.getAllErrors().get(0).getDefaultMessage());
		}
		return ResponseEntity.ok(facturaService.editarBorrador(idFactura, facturaRequest));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<String> mostrarErrorDeFactura(ResponseStatusException error) {
		return ResponseEntity.status(error.getStatusCode()).body(error.getReason());
	}

	@GetMapping("/{idFactura}/detalle")
	public ResponseEntity<DetalleFactura> obtenerDetalle(@PathVariable int idFactura) {
		return ResponseEntity.ok(facturaService.obtenerDetalle(idFactura));
	}

	@GetMapping("/buscar")
	public ResponseEntity<List<Factura>> buscar(
			@RequestParam(required = false, defaultValue = "") String busqueda) {
		List<Factura> facturas = facturaService.buscar(busqueda);
		ResponseEntity<List<Factura>> respuesta = ResponseEntity.ok(facturas);
		return respuesta;
	}


	@GetMapping("/conceptos/sugerencias")
	public ResponseEntity<List<SugerenciaConcepto>> buscarSugerenciasConceptos(
			@RequestParam(defaultValue = "") String texto,
			@RequestParam(defaultValue = "8") int limite) {
		return ResponseEntity.ok(facturaService.buscarSugerenciasConceptos(texto, limite));
	}

	@GetMapping("/trimestral")
	public ResponseEntity<ResumenTrimestralFactura> listarTrimestre(
			@RequestParam int anio,
			@RequestParam int trimestre) {
		ResumenTrimestralFactura resumen = facturaService.listarTrimestre(anio, trimestre);
		return ResponseEntity.ok(resumen);
	}


}
