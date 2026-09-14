package edu.xtd.facturacion360.dto;


import org.springframework.stereotype.Component;

/**
 * Traduce entre los distintos objetos de Cliente.
 * Con @Component Spring lo crea y lo inyecta donde haga falta (p. ej. en el controller).
 */
@Component
public class ClienteMapper {

	// OJO al construir el Cliente: el id queda a 0 y la fecha de alta a null, porque el
	// ClienteRequest no trae ninguno de los dos (el id viaja en la URL y la fecha es un dato
	// histórico que no se edita). Quien llame a este método tiene que poner los dos por su
	// cuenta: en actualizar(), propagar ese 0 lanzaría el UPDATE con "WHERE idcliente = 0" y
	// copiar ese null borraría la fecha de la respuesta. Las dos cosas están fijadas con sus
	// pruebas en ClienteServiceImplTest.
	public Cliente toDomain(ClienteRequest clienteRequest) {
		Cliente cliente = null;
			
			cliente =  new Cliente(
					0,
					clienteRequest.nombre(),
					clienteRequest.nifCif(),
					clienteRequest.direccion(),
					clienteRequest.codigoPostal(),
					clienteRequest.poblacion(),
					clienteRequest.provincia(),
					clienteRequest.telefono(),
					clienteRequest.email(),
					null);
		
			System.out.println("ClienteRequest2Cliente "+ cliente);
		
		return cliente;
	}


	
	/**
	 * Convierte el {@link Cliente} de dominio (lo que sale de la BD) en un {@link ClienteResponse}
	 * (lo que viaja al navegador como JSON). Tener un DTO de salida separado desacopla la entidad
	 * interna del contrato con el frontend: podemos cambiar el modelo por dentro sin romper la API.
	 *
	 * @param cliente el cliente de dominio a convertir; puede ser {@code null}
	 * @return el {@link ClienteResponse} equivalente, o {@code null} si {@code cliente} es null
	 */

	public ClienteResponse toResponse (Cliente cliente)
	{
		ClienteResponse clienteResponse = null;

			if (cliente!=null)
			{
				clienteResponse = new ClienteResponse(
						cliente.idCliente(),
						cliente.nombre(),
						cliente.nifCif(),
						cliente.direccion(),
						cliente.codigoPostal(),
						cliente.poblacion(),
						cliente.provincia(),
						cliente.telefono(),
						cliente.email(),
						cliente.fechaAlta());
			}

		return clienteResponse;
	}
}
