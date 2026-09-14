package edu.xtd.facturacion360.service;

import edu.xtd.facturacion360.dto.Emisor;
import edu.xtd.facturacion360.repository.EmisorRepository;
import org.springframework.stereotype.Service;

@Service
public class EmisorServiceImpl implements EmisorService {

    private final EmisorRepository emisorRepository;

    public EmisorServiceImpl(EmisorRepository emisorRepository) {
        this.emisorRepository = emisorRepository;
    }

  

	@Override
	public Emisor update(Emisor emisor) {
		Emisor  emisorModificado = null;
		boolean modificado = emisorRepository.update(emisor);
		if (modificado)
		{
			emisorModificado = emisor;
		}
		return emisorModificado;
	
	}
}