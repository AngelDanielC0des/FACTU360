package edu.xtd.facturacion360.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @GetMapping
    public ResponseEntity<Emisor> find() {

        Emisor emisor = emisorService.find();

        if (emisor == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(emisor);
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
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        return ResponseEntity.ok(emisorGuardado);
    }
}