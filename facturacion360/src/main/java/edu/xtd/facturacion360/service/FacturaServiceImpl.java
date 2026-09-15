package edu.xtd.facturacion360.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.ConceptoRequest;
import edu.xtd.facturacion360.dto.DetalleFactura;
import edu.xtd.facturacion360.dto.Factura;
import edu.xtd.facturacion360.dto.FacturaRequest;
import edu.xtd.facturacion360.dto.ResumenTrimestralFactura;
import edu.xtd.facturacion360.dto.SugerenciaConcepto;
import edu.xtd.facturacion360.repository.FacturaRepository;
import edu.xtd.facturacion360.repository.FacturaRepository.NumeroFacturaDuplicadoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Lógica necesaria para crear y buscar facturas.
 */
@Service
public class FacturaServiceImpl implements FacturaService {

	@Autowired
	FacturaRepository facturaRepository;

	@Autowired
	Validator validador;

	@Autowired
	PlatformTransactionManager gestorTransacciones;

	private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("99999999.99");

	public record CalculoFactura(List<ConceptoFactura> conceptos, BigDecimal subtotal,
			BigDecimal importeIva, BigDecimal total) {
	}

	/** Calcula sin acceder al repositorio ni modificar los datos recibidos. */
	public CalculoFactura calcularImportes(List<ConceptoRequest> conceptos, String estado) {
		if (estado == null || !List.of("BORRADOR", "EMITIDA", "PAGADA", "ANULADA").contains(estado)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado de la factura no es válido");
		}
		if (conceptos == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista de conceptos es obligatoria");
		}
		if ("EMITIDA".equals(estado) && conceptos.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para emitir la factura hace falta al menos un concepto");
		}

		List<ConceptoFactura> conceptosCalculados = new ArrayList<>();
		BigDecimal subtotal = new BigDecimal("0.00");
		BigDecimal ivaFactura = new BigDecimal("0.00");
		BigDecimal totalFactura = new BigDecimal("0.00");

		for (ConceptoRequest concepto : conceptos) {
			if (concepto == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El concepto no puede ser nulo");
			}
			Set<ConstraintViolation<ConceptoRequest>> errores = validador.validate(concepto);
			if (!errores.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errores.iterator().next().getMessage());
			}

			BigDecimal bruto = concepto.precioUnitario().multiply(BigDecimal.valueOf(concepto.cantidad()));
			BigDecimal descuentoImporte = bruto.multiply(concepto.descuento()).movePointLeft(2);
			BigDecimal baseImponible = bruto.subtract(descuentoImporte).setScale(2, RoundingMode.HALF_UP);
			BigDecimal importeIva = baseImponible.multiply(concepto.porcentajeIva())
					.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
			BigDecimal total = baseImponible.add(importeIva);
			validarImporte(baseImponible);
			validarImporte(importeIva);
			validarImporte(total);

			conceptosCalculados.add(new ConceptoFactura(0, concepto.descripcion().trim(), concepto.cantidad(),
					concepto.precioUnitario(), concepto.descuento(), concepto.porcentajeIva(),
					importeIva, baseImponible, total));
			subtotal = subtotal.add(baseImponible);
			ivaFactura = ivaFactura.add(importeIva);
			totalFactura = totalFactura.add(total);
			validarImporte(subtotal);
			validarImporte(ivaFactura);
			validarImporte(totalFactura);
		}
		return new CalculoFactura(List.copyOf(conceptosCalculados), subtotal, ivaFactura, totalFactura);
	}

	private void validarImporte(BigDecimal importe) {
		if (importe.compareTo(IMPORTE_MAXIMO) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El importe supera el máximo de 99.999.999,99");
		}
	}

	@Override
	public Factura crear(FacturaRequest facturaRequest) {
		if (facturaRequest == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La petición es obligatoria");
		}
		Set<ConstraintViolation<FacturaRequest>> errores = validador.validate(facturaRequest);
		if (!errores.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errores.iterator().next().getMessage());
		}
		int anio = facturaRequest.fechaEmision().getYear();
		if (anio < 1000 || anio > 9999) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El año debe estar entre 1000 y 9999");
		}
		CalculoFactura calculo = calcularImportes(facturaRequest.conceptos(), facturaRequest.estado());
		TransactionTemplate transaccion = new TransactionTemplate(gestorTransacciones);
		transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		for (int intento = 1; intento <= 3; intento++) {
			try {
				return transaccion.execute(estadoTransaccion -> {
					int ultimoNumero = facturaRepository.obtenerUltimoNumero(anio);
					if (ultimoNumero >= 9999) {
						throw new ResponseStatusException(HttpStatus.CONFLICT, "Se ha alcanzado el límite de 9999 facturas para " + anio);
					}
					String numeroFactura = String.format(Locale.ROOT, "F-%04d-%04d", anio, ultimoNumero + 1);
					Factura factura = new Factura(0, facturaRequest.idCliente(), null, numeroFactura,
							facturaRequest.fechaEmision(), facturaRequest.estado(), facturaRequest.observaciones(),
							calculo.subtotal(), calculo.importeIva(), calculo.total());
					Factura facturaNueva = facturaRepository.insertar(factura);
					if (facturaNueva == null) {
						throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al insertar la factura");
					}
					facturaRepository.insertarConceptos(facturaNueva.idFactura(), calculo.conceptos());
					return facturaNueva;
				});
			} catch (NumeroFacturaDuplicadoException error) {
				// execute ya ha revertido el intento; el siguiente vuelve a consultar en otra transacción.
				if (intento == 3) {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"No se pudo asignar el número tras tres intentos. Vuelve a intentarlo.", error);
				}
			}
		}
		throw new IllegalStateException("No se completó el guardado de la factura");
	}

	@Override
	public List<Factura> buscar(String busqueda) {
		return facturaRepository.buscar(busqueda);
	}

	@Override
	public List<SugerenciaConcepto> buscarSugerenciasConceptos(String texto, int limite) {
		String textoBuscado = texto == null ? "" : texto.trim();
		if (textoBuscado.length() < 2 || textoBuscado.length() > 50) {
			return List.of();
		}
		int limiteAcotado = Math.max(1, Math.min(limite, 20));
		return facturaRepository.buscarSugerenciasConceptos(textoBuscado, limiteAcotado);
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

	@Override
	public ResumenTrimestralFactura listarTrimestre(int anio, int trimestre) {
		if (anio < 2000 || anio > 2100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El año debe estar entre 2000 y 2100");
		}
		if (trimestre < 1 || trimestre > 4) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El trimestre debe estar entre 1 y 4");
		}

		int primerMes = (trimestre - 1) * 3 + 1;
		LocalDate fechaInicio = LocalDate.of(anio, primerMes, 1);
		LocalDate fechaFin = fechaInicio.plusMonths(3);
		List<Factura> facturas = facturaRepository.buscarPorTrimestre(fechaInicio, fechaFin);

		BigDecimal subtotal = BigDecimal.ZERO;
		BigDecimal importeIva = BigDecimal.ZERO;
		BigDecimal total = BigDecimal.ZERO;
		for (Factura factura : facturas) {
			subtotal = subtotal.add(factura.subtotal());
			importeIva = importeIva.add(factura.importeIva());
			total = total.add(factura.total());
		}

		return new ResumenTrimestralFactura(anio, trimestre, facturas, subtotal, importeIva, total);
	}

}
