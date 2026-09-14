package edu.xtd.facturacion360.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.xtd.facturacion360.dto.Emisor;
import edu.xtd.facturacion360.service.EmisorService;

@RestController
@RequestMapping("/emisor")
public class EmisorController {

	EmisorService emisorService;

    public EmisorController(EmisorService emisorService) {
        this.emisorService = emisorService;
    }

    @PutMapping("")
    public ResponseEntity<Emisor> update(@RequestBody Emisor emisor) {
    	Emisor emisorActualizado = emisorService.update(emisor);
        return ResponseEntity.ok(emisorActualizado);
    }
}