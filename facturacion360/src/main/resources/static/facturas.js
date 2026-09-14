const RUTA_FACTURAS = "/factura/buscar";
const RUTA_CREAR_FACTURA = "/factura";
const RUTA_CLIENTES = "/cliente/listar-ultimos?limite=100";

const tablaFacturas = document.getElementById("tablaFacturas");
const inputBusqueda = document.getElementById("busquedaFactura");
const mensajeFacturas = document.getElementById("mensaje-facturas");
const formularioFactura = document.getElementById("formularioFactura");
const selectCliente = document.getElementById("clienteFactura");
const inputSubtotal = document.getElementById("subtotalFactura");
const inputIva = document.getElementById("ivaFactura");
const inputTotal = document.getElementById("totalFactura");

/** Carga las facturas que coinciden con el texto buscado. */
async function cargarFacturas() {
    const textoBuscado = inputBusqueda.value.trim();
    const parametros = new URLSearchParams({ busqueda: textoBuscado });

    try {
        const respuesta = await fetch(`${RUTA_FACTURAS}?${parametros}`);
        if (respuesta.ok) {
            const facturas = await respuesta.json();
            mostrarFacturas(facturas);
            ocultarMensaje();
        } else {
            mostrarMensaje("No se pudieron consultar las facturas.", "danger");
        }
    } catch (error) {
        console.error("Error al buscar facturas", error);
        mostrarMensaje("No se pudo conectar con el servidor.", "danger");
    }
}

/** Muestra las facturas recibidas dentro de la tabla. */
function mostrarFacturas(facturas) {
    tablaFacturas.replaceChildren();

    if (facturas.length == 0) {
        const fila = document.createElement("tr");
        const celda = document.createElement("td");
        celda.colSpan = 8;
        celda.className = "text-center text-muted py-4";
        celda.textContent = "No se han encontrado facturas.";
        fila.appendChild(celda);
        tablaFacturas.appendChild(fila);
    } else {
        for (const factura of facturas) {
            const fila = document.createElement("tr");
            agregarCelda(fila, factura.numeroFactura);
            agregarCelda(fila, factura.nombreCliente);
            agregarCelda(fila, formatearFecha(factura.fechaEmision));
            agregarCelda(fila, factura.estado);
            agregarCelda(fila, formatearImporte(factura.subtotal), "text-end");
            agregarCelda(fila, formatearImporte(factura.importeIva), "text-end");
            agregarCelda(fila, formatearImporte(factura.total), "text-end fw-bold");
            agregarAccionVisor(fila, factura.idFactura);
            tablaFacturas.appendChild(fila);
        }
    }
}

/** Añade a la fila el botón que abre la factura preparada para imprimir. */
function agregarAccionVisor(fila, idFactura) {
    const celda = document.createElement("td");
    celda.className = "text-end";

    const boton = document.createElement("button");
    boton.type = "button";
    boton.className = "btn btn-sm btn-outline-primary";
    boton.title = "Ver e imprimir factura";
    boton.setAttribute("aria-label", "Ver e imprimir factura");
    boton.textContent = "Ver / PDF";
    boton.addEventListener("click", function () {
        window.open("factura-imprimir.html?idFactura=" + idFactura, "_blank");
    });

    celda.appendChild(boton);
    fila.appendChild(celda);
}

/** Añade una celda de texto a una fila. */
function agregarCelda(fila, texto, clases) {
    const celda = document.createElement("td");
    celda.textContent = texto;
    if (clases != null) {
        celda.className = clases;
    }
    fila.appendChild(celda);
}

/** Carga los clientes para poder elegir uno al crear la factura. */
async function cargarClientes() {
    try {
        const respuesta = await fetch(RUTA_CLIENTES);
        if (respuesta.ok) {
            const clientes = await respuesta.json();
            for (const cliente of clientes) {
                const opcion = document.createElement("option");
                opcion.value = cliente.idCliente;
                opcion.textContent = cliente.nombre + " - " + cliente.nifCif;
                selectCliente.appendChild(opcion);
            }
        } else {
            mostrarMensaje("No se pudieron cargar los clientes.", "danger");
        }
    } catch (error) {
        console.error("Error al cargar clientes", error);
        mostrarMensaje("No se pudieron cargar los clientes.", "danger");
    }
}

/** Envía los datos del formulario para guardar una factura. */
async function guardarFactura() {
    if (formularioFactura.reportValidity()) {
        const datosFactura = {
            idCliente: Number(selectCliente.value),
            numeroFactura: document.getElementById("numeroFactura").value.trim(),
            fechaEmision: document.getElementById("fechaEmision").value,
            estado: document.getElementById("estadoFactura").value,
            observaciones: document.getElementById("observacionesFactura").value.trim(),
            subtotal: Number(inputSubtotal.value),
            importeIva: Number(inputIva.value)
        };

        try {
            const respuesta = await fetch(RUTA_CREAR_FACTURA, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(datosFactura)
            });

            if (respuesta.ok) {
                formularioFactura.reset();
                inputTotal.value = "0,00 €";
                bootstrap.Modal.getOrCreateInstance(document.getElementById("facturaModal")).hide();
                mostrarMensaje("Factura creada correctamente.", "success");
                cargarFacturas();
            } else {
                const mensajeError = await respuesta.text();
                mostrarMensaje(mensajeError || "No se pudo crear la factura.", "danger");
            }
        } catch (error) {
            console.error("Error al crear la factura", error);
            mostrarMensaje("No se pudo conectar con el servidor.", "danger");
        }
    }
}

/** Calcula en pantalla el total sumando el subtotal y el IVA. */
function calcularTotal() {
    const subtotal = Number(inputSubtotal.value) || 0;
    const iva = Number(inputIva.value) || 0;
    inputTotal.value = formatearImporte(subtotal + iva);
}

function formatearFecha(fecha) {
    const partes = fecha.split("-");
    return partes[2] + "/" + partes[1] + "/" + partes[0];
}

function formatearImporte(importe) {
    return Number(importe).toLocaleString("es-ES", { style: "currency", currency: "EUR" });
}

function mostrarMensaje(texto, tipo) {
    mensajeFacturas.textContent = texto;
    mensajeFacturas.className = "alert alert-" + tipo;
}

function ocultarMensaje() {
    mensajeFacturas.className = "alert d-none";
}

document.getElementById("botonBuscar").addEventListener("click", cargarFacturas);
document.getElementById("botonLimpiar").addEventListener("click", function () {
    inputBusqueda.value = "";
    cargarFacturas();
});
document.getElementById("botonGuardarFactura").addEventListener("click", guardarFactura);
inputSubtotal.addEventListener("input", calcularTotal);
inputIva.addEventListener("input", calcularTotal);
inputBusqueda.addEventListener("keydown", function (evento) {
    if (evento.key == "Enter") {
        cargarFacturas();
    }
});

cargarClientes();
cargarFacturas();
