package edu.xtd.facturacion360.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import edu.xtd.facturacion360.dto.Emisor;
import edu.xtd.facturacion360.service.EmisorService;

@RestController
@RequestMapping("/emisor")
public class EmisorController {

	private final EmisorService emisorService;

	public EmisorController(EmisorService emisorService) {
		this.emisorService = emisorService;
	}

	/**
	 * Obtiene los datos actuales del emisor.
	 */
	// @GetMapping(produces = "application/json")//por defecto al ser RestController
	@GetMapping
	public ResponseEntity<Emisor> find() {

		Emisor emisor = emisorService.find();

		if (emisor == null) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok(emisor);
	}

	/**
	 * Obtiene el logo del emisor
	 */
	@GetMapping("/logo")
	public ResponseEntity<byte[]> findLogo() {

		byte[] logo = emisorService.findLogo();

		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(logo);
	}

	/**
	 * Crea o actualiza el emisor.
	 */
	@PutMapping
	public ResponseEntity<Emisor> save(@RequestBody Emisor emisor) {

		if (emisor == null) {
			return ResponseEntity.badRequest().build();
		}

		Emisor emisorGuardado = emisorService.save(emisor);

		if (emisorGuardado == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		return ResponseEntity.ok(emisorGuardado);
	}

	@PutMapping(path = "/con-imagen")
	public ResponseEntity<Emisor> save(Emisor emisor, MultipartFile logo) throws IOException {

		if (emisor == null) {
			return ResponseEntity.badRequest().build();
		}

		Emisor emisorCompleto = emisor;

		// si trae foto, le añado al emisor su imagen
		// IDEA: los datos viajan separdos (texto e imagen) por ser de
		// distinto tipo, pero el servidor, contexto java, se integran en
		// un mismo objeto de la clase Emisor
		if (logo != null && !logo.isEmpty())
		// if (!logo.isEmpty() && logo !=null)//susceptible de NullPointerException
		{
			emisorCompleto = new Emisor(emisor.nombre(), emisor.cif(), emisor.direccion(), emisor.email(),
					emisor.telefono(), logo.getBytes());
		}

		Emisor emisorGuardado = emisorService.save(emisorCompleto);

		if (emisorGuardado == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		return ResponseEntity.ok(emisorGuardado);
	}
}