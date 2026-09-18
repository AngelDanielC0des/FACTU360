const estadoVisor = document.getElementById("estadoVisor");
const contenidoFactura = document.getElementById("contenidoFactura");
const botonImprimir = document.getElementById("botonImprimir");
const tablaConceptos = document.getElementById("tablaConceptos");

/** Carga la factura indicada en la dirección de la página. */
async function cargarDetalleFactura() {
    const parametros = new URLSearchParams(window.location.search);
    const idFactura = Number(parametros.get("idFactura"));

    if (Number.isInteger(idFactura) && idFactura > 0) {
        try {
            const respuesta = await fetch("/factura/" + idFactura + "/detalle");
            if (respuesta.ok) {
                const detalle = await respuesta.json();
                mostrarDetalle(detalle);
            } else {
                const mensaje = await respuesta.text();
                mostrarError(mensaje || "No se pudo cargar la factura.");
            }
        } catch (error) {
            console.error("Error al cargar el detalle de la factura", error);
            mostrarError("No se pudo conectar con el servidor.");
        }
    } else {
        mostrarError("El identificador de factura no es válido.");
    }
}

/** Muestra la cabecera, el cliente, los conceptos y los totales. */
function mostrarDetalle(detalle) {
    const factura = detalle.factura;
    const cliente = detalle.cliente;
    const editar = document.getElementById("botonEditarBorrador");
    editar.classList.toggle("d-none", factura.estado != "BORRADOR");
    if (factura.estado == "BORRADOR") {
        editar.href = "facturas.html?editar=" + factura.idFactura;
    }

    document.getElementById("numeroFactura").textContent = factura.numeroFactura;
    document.getElementById("fechaFactura").textContent = "Fecha: " + formatearFecha(factura.fechaEmision);
    document.getElementById("estadoFactura").textContent = "Estado: " + factura.estado;
    document.getElementById("nombreCliente").textContent = cliente.nombre;
    document.getElementById("nifCliente").textContent = "NIF/CIF: " + cliente.nifCif;
    document.getElementById("direccionCliente").textContent = formarDireccion(cliente);
    document.getElementById("contactoCliente").textContent = formarContacto(cliente);
    document.getElementById("subtotalFactura").textContent = formatearImporte(factura.subtotal);
    document.getElementById("ivaFactura").textContent = formatearImporte(factura.importeIva);
    document.getElementById("totalFactura").textContent = formatearImporte(factura.total);

    mostrarConceptos(detalle.conceptos);

    if (factura.observaciones != null && factura.observaciones.trim() != "") {
        document.getElementById("observacionesFactura").textContent = factura.observaciones;
        document.getElementById("bloqueObservaciones").classList.remove("d-none");
    }

    estadoVisor.classList.add("d-none");
    contenidoFactura.classList.remove("d-none");
    document.getElementById("documentoFactura").setAttribute("aria-busy", "false");
    botonImprimir.disabled = false;
}

/** Rellena la tabla o muestra una fila informativa si la factura no tiene conceptos. */
function mostrarConceptos(conceptos) {
    tablaConceptos.replaceChildren();

    if (conceptos.length == 0) {
        const fila = document.createElement("tr");
        const celda = document.createElement("td");
        celda.colSpan = 7;
        celda.className = "text-center text-muted";
        celda.textContent = "Esta factura no tiene conceptos registrados.";
        fila.appendChild(celda);
        tablaConceptos.appendChild(fila);
    } else {
        for (const concepto of conceptos) {
            const fila = document.createElement("tr");
            agregarCelda(fila, concepto.descripcion || "—");
            agregarCelda(fila, concepto.cantidad ?? "—", "text-end");
            agregarCelda(fila, formatearImporte(concepto.precioUnitario), "text-end");
            agregarCelda(fila, formatearPorcentaje(concepto.descuento), "text-end");
            agregarCelda(fila, formatearImporte(concepto.baseImponible), "text-end");
            agregarCelda(fila, formatearImporte(concepto.importeIva), "text-end");
            agregarCelda(fila, formatearImporte(concepto.total), "text-end");
            tablaConceptos.appendChild(fila);
        }
    }
}
/** Carga los datos de la informacion del emisor */
async function cargarEmisor() {
    try {
        const respuesta = await fetch("/emisor");
        if (respuesta.ok) {
            const emisor = await respuesta.json();
            mostrarEmisor(emisor);
        }
    } catch (error) {
        console.error("Error al cargar los datos del emisor", error);
    }
}
/** Muestra los datos de la informacion del emisor */
function mostrarEmisor(emisor) {
    if (!emisor) return;
    
    // Asignamos los datos únicamente a la sección del recuadro "Emisor"
    document.getElementById("nombreEmisor").textContent = emisor.nombre || "—";
    document.getElementById("cifEmisor").textContent = "NIF/CIF: " + (emisor.cif || emisor.nifCif || "—");
    document.getElementById("direccionEmisor").textContent = emisor.direccion || "—";
    
    const tel = emisor.telefono || "";
    const email = emisor.email || "";
    document.getElementById("contactoEmisor").textContent = [tel, email].filter(Boolean).join(" · ") || "Sin contacto";
}

function agregarCelda(fila, texto, clases) {
    const celda = document.createElement("td");
    celda.textContent = texto;
    if (clases != null) {
        celda.className = clases;
    }
    fila.appendChild(celda);
}

function formarDireccion(cliente) {
    const codigoPostal = cliente.codigoPostal || "";
    return cliente.direccion + ", " + codigoPostal + " " + cliente.poblacion + " (" + cliente.provincia + ")";
}

function formarContacto(cliente) {
    const telefono = cliente.telefono || "Sin teléfono";
    const email = cliente.email || "Sin email";
    return telefono + " · " + email;
}

function formatearFecha(fecha) {
    const partes = fecha.split("-");
    return partes[2] + "/" + partes[1] + "/" + partes[0];
}

function formatearImporte(importe) {
    const valor = importe == null ? 0 : importe;
    return Number(valor).toLocaleString("es-ES", { style: "currency", currency: "EUR" });
}

function formatearPorcentaje(porcentaje) {
    const valor = porcentaje == null ? 0 : porcentaje;
    return Number(valor).toLocaleString("es-ES") + " %";
}

function mostrarError(mensaje) {
    estadoVisor.textContent = mensaje;
    estadoVisor.className = "alert alert-danger";
    document.getElementById("documentoFactura").setAttribute("aria-busy", "false");
}

botonImprimir.addEventListener("click", function () {
    window.print();
});

cargarDetalleFactura();
cargarEmisor();