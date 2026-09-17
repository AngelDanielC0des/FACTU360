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

		//TODO gestionar el logo por defecto si no hay en la BD
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
	public ResponseEntity<Emisor> save(Emisor emisor, MultipartFile foto) throws IOException {

		if (emisor == null) {
			return ResponseEntity.badRequest().build();
		}

		Emisor emisorCompleto = emisor;

		// si trae foto, le añado al emisor su imagen
		// IDEA: los datos viajan separdos (texto e imagen) por ser de
		// distinto tipo, pero el servidor, contexto java, se integran en
		// un mismo objeto de la clase Emisor
		if (foto != null && !foto.isEmpty())
		// if (!logo.isEmpty() && logo !=null)//susceptible de NullPointerException
		{
			emisorCompleto = new Emisor(emisor.nombre(), emisor.cif(), emisor.direccion(), emisor.email(),
					emisor.telefono(), foto.getBytes());
		}
		
	

		Emisor emisorGuardado = emisorService.save(emisorCompleto);

		if (emisorGuardado == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		return ResponseEntity.ok(emisorGuardado);
	}
	
	/**
	 * AYUDA CLIENTE
	 * 
	 * <input type="text" id="nombre">
<input type="text" id="cif">
<input type="text" id="direccion">
<input type="email" id="email">
<input type="text" id="telefono">

<input type="file" id="foto" accept="image/*">

<button type="button" onclick="actualizarEmisor()">
    Guardar
</button>


async function actualizarEmisor() {

    const nombre = document.getElementById("nombre").value;
    const cif = document.getElementById("cif").value;
    const direccion = document.getElementById("direccion").value;
    const email = document.getElementById("email").value;
    const telefono = document.getElementById("telefono").value;

    const inputFoto = document.getElementById("foto");
    const archivo = inputFoto.files[0];

    const formData = new FormData();

    formData.append("nombre", nombre);
    formData.append("cif", cif);
    formData.append("direccion", direccion);
    formData.append("email", email);
    formData.append("telefono", telefono);

    if (archivo) {
        formData.append("foto", archivo);
    }

    try {

        const response = await fetch(
            "http://localhost:8080/emisor/con-imagen",
            {
                method: "PUT",
                body: formData
            }
        );

        if (!response.ok) {
            throw new Error(
                `Error HTTP: ${response.status}`
            );
        }

        const emisor = await response.json();

        console.log("Emisor actualizado:", emisor);

    } catch (error) {

        console.error(
            "Error actualizando el emisor:",
            error
        );
    }
}
	 */
}