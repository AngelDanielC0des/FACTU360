package edu.xtd.facturacion360.service;

import org.springframework.stereotype.Service;

import edu.xtd.facturacion360.dto.Emisor;
import edu.xtd.facturacion360.repository.EmisorRepository;

@Service
public class EmisorServiceImpl implements EmisorService {

    private final EmisorRepository emisorRepository;

    public EmisorServiceImpl(EmisorRepository emisorRepository) {
        this.emisorRepository = emisorRepository;
    }

    @Override
    public Emisor save(Emisor emisor) {

        // Comprobamos si ya existe el emisor principal.
        boolean existe = emisorRepository.find().isPresent();

        boolean guardado;

        if (existe) {

            // Ya existe → actualizar.
            guardado = emisorRepository.update(emisor);

        } else {

            // No existe → crear.
            guardado = emisorRepository.insert(emisor);
        }

        if (guardado) {
            return emisor;
        }

        return null;
    }

    @Override
    public Emisor find() {
        return emisorRepository.find().orElse(null);
    }

	@Override
	public byte[] findLogo() {
		// TODO Auto-generated method stub
		return emisorRepository.findLogo();
	}
}