package edu.xtd.facturacion360.repository;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import edu.xtd.facturacion360.dto.ClienteFactura;
import edu.xtd.facturacion360.dto.ConceptoFactura;
import edu.xtd.facturacion360.dto.Factura;

/**
 * Acceso a la tabla facturas mediante JdbcTemplate.
 */
@Repository
public class FacturaRepositoryJdbcImpl implements FacturaRepository {

	private static final Logger log = LoggerFactory.getLogger(FacturaRepositoryJdbcImpl.class);

	private static final String COLUMNAS_FACTURA = "f.idfactura, f.idcliente, c.nombre AS nombre_cliente, "
			+ "f.num_factura, f.fecha_emision, f.estado, f.observaciones, "
			+ "f.subtotal, f.importe_iva, f.total";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	FacturaRowMapper facturaRowMapper;

	@Autowired
	ClienteFacturaRowMapper clienteFacturaRowMapper;

	@Autowired
	ConceptoFacturaRowMapper conceptoFacturaRowMapper;

	@Override
	public Factura insertar(Factura factura) {
		String sqlInsertar = "INSERT INTO facturas "
				+ "(idcliente, num_factura, fecha_emision, estado, observaciones, subtotal, importe_iva, total, "
				+ "fecha_creacion, fecha_actualizacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

		int filasInsertadas = jdbcTemplate.update(sqlInsertar,
				factura.idCliente(),
				factura.numeroFactura(),
				factura.fechaEmision(),
				factura.estado(),
				factura.observaciones(),
				factura.subtotal(),
				factura.importeIva(),
				factura.total());

		Factura facturaInsertada = null;
		if (filasInsertadas == 1) {
			String sqlBuscar = "SELECT " + COLUMNAS_FACTURA + " FROM facturas f "
					+ "INNER JOIN clientes c ON f.idcliente = c.idcliente WHERE f.num_factura = ?";
			facturaInsertada = jdbcTemplate.queryForObject(sqlBuscar, facturaRowMapper, factura.numeroFactura());
		}

		return facturaInsertada;
	}

	@Override
	public List<Factura> buscar(String busqueda) {
		String sql = "SELECT " + COLUMNAS_FACTURA + " FROM facturas f "
				+ "INNER JOIN clientes c ON f.idcliente = c.idcliente";
		List<Factura> facturas;

		if (busqueda == null || busqueda.isBlank()) {
			sql = sql + " ORDER BY f.fecha_emision DESC, f.idfactura DESC";
			facturas = jdbcTemplate.query(sql, facturaRowMapper);
		} else {
			sql = sql + " WHERE f.num_factura LIKE ? ESCAPE '\\\\' OR c.nombre LIKE ? ESCAPE '\\\\' "
					+ "ORDER BY f.fecha_emision DESC, f.idfactura DESC";
			String textoBuscado = "%" + escaparComodines(busqueda.trim()) + "%";
			facturas = jdbcTemplate.query(sql, facturaRowMapper, textoBuscado, textoBuscado);
		}

		log.debug("buscar({}) devuelve {} facturas", busqueda, facturas.size());
		return facturas;
	}

	@Override
	public Factura buscarPorId(int idFactura) {
		String sql = "SELECT " + COLUMNAS_FACTURA + " FROM facturas f "
				+ "INNER JOIN clientes c ON f.idcliente = c.idcliente WHERE f.idfactura = ?";
		List<Factura> facturas = jdbcTemplate.query(sql, facturaRowMapper, idFactura);

		Factura factura = null;
		if (!facturas.isEmpty()) {
			factura = facturas.get(0);
		}
		return factura;
	}

	@Override
	public ClienteFactura buscarCliente(int idCliente) {
		String sql = "SELECT idcliente, nombre, nif_cif, direccion, codigopostal, poblacion, "
				+ "provincia, telefono, email FROM clientes WHERE idcliente = ?";
		List<ClienteFactura> clientes = jdbcTemplate.query(sql, clienteFacturaRowMapper, idCliente);

		ClienteFactura cliente = null;
		if (!clientes.isEmpty()) {
			cliente = clientes.get(0);
		}
		return cliente;
	}

	@Override
	public List<ConceptoFactura> buscarConceptos(int idFactura) {
		String sql = "SELECT idconcepto, descripcion, cantidad, precio_unitario, descuento, "
				+ "porcentaje_iva, importe_iva, base_imponible, total FROM conceptos "
				+ "WHERE idfactura = ? ORDER BY idconcepto";
		return jdbcTemplate.query(sql, conceptoFacturaRowMapper, idFactura);
	}

	/** Evita que los caracteres propios de LIKE cambien el significado de la búsqueda. */
	private String escaparComodines(String texto) {
		return texto.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
	}
}
