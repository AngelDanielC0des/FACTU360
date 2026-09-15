const RUTA_FACTURAS = "/factura/buscar";
const RUTA_CREAR_FACTURA = "/factura";
const RUTA_FACTURAS_TRIMESTRE = "/factura/trimestral";
const RUTA_CLIENTES = "/cliente/listar-ultimos?limite=100";

const tablaFacturas = document.getElementById("tablaFacturas");
const inputBusqueda = document.getElementById("busquedaFactura");
const contenedorBuscador = document.getElementById("buscadorFacturas");
const mensajeFacturas = document.getElementById("mensaje-facturas");
const formularioFactura = document.getElementById("formularioFactura");
const selectCliente = document.getElementById("clienteFactura");
const inputSubtotal = document.getElementById("subtotalFactura");
const inputIva = document.getElementById("ivaFactura");
const inputTotal = document.getElementById("totalFactura");
const inputAnio = document.getElementById("anioTrimestre");
const selectTrimestre = document.getElementById("trimestreFactura");
const resumenTrimestral = document.getElementById("resumenTrimestral");
const subtotalTrimestre = document.getElementById("subtotalTrimestre");
const ivaTrimestre = document.getElementById("ivaTrimestre");
const totalTrimestre = document.getElementById("totalTrimestre");


// Ambas búsquedas comparten la tabla: una respuesta anterior no debe reemplazar la última consulta.
let ultimaConsultaFacturas = 0;


/** Carga las facturas que coinciden con el texto buscado. */
async function cargarFacturas() {
    ultimaConsultaFacturas++;
    const numeroConsulta = ultimaConsultaFacturas;
    const textoBuscado = inputBusqueda.value.trim();
    const parametros = new URLSearchParams({ busqueda: textoBuscado });

    try {
        const respuesta = await fetch(`${RUTA_FACTURAS}?${parametros}`);
        if (respuesta.ok) {
            const facturas = await respuesta.json();
            if (numeroConsulta == ultimaConsultaFacturas) {
                mostrarFacturas(facturas);
                resumenTrimestral.classList.add("d-none");
                ocultarMensaje();
            }
        } else if (numeroConsulta == ultimaConsultaFacturas) {
            mostrarMensaje("No se pudieron consultar las facturas.", "danger");
        }
    } catch (error) {
        if (numeroConsulta == ultimaConsultaFacturas) {
            console.error("Error al buscar facturas", error);
            mostrarMensaje("No se pudo conectar con el servidor.", "danger");
        }
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

/** Consulta las facturas del año y trimestre elegidos y muestra sus totales. */
async function cargarListadoTrimestral() {
    if (inputAnio.reportValidity()) {
        const parametros = new URLSearchParams({
            anio: inputAnio.value,
            trimestre: selectTrimestre.value
        });

        try {
            const respuesta = await fetch(RUTA_FACTURAS_TRIMESTRE + "?" + parametros);
            if (respuesta.ok) {
                const resumen = await respuesta.json();
                mostrarFacturas(resumen.facturas);
                subtotalTrimestre.textContent = formatearImporte(resumen.subtotal);
                ivaTrimestre.textContent = formatearImporte(resumen.importeIva);
                totalTrimestre.textContent = formatearImporte(resumen.total);
                resumenTrimestral.classList.remove("d-none");
                mostrarMensaje("Mostrando el " + resumen.trimestre + "º trimestre de " + resumen.anio + ".", "info");
            } else {
                const mensajeError = await respuesta.text();
                mostrarMensaje(mensajeError || "No se pudo cargar el listado trimestral.", "danger");
            }
        } catch (error) {
            console.error("Error al cargar el listado trimestral", error);
            mostrarMensaje("No se pudo conectar con el servidor.", "danger");
        }
    }

}

/** Consulta las facturas del año y trimestre elegidos y muestra sus totales. */
async function cargarListadoTrimestral() {
    if (inputAnio.reportValidity()) {
        ultimaConsultaFacturas++;
        const numeroConsulta = ultimaConsultaFacturas;
        const parametros = new URLSearchParams({
            anio: inputAnio.value,
            trimestre: selectTrimestre.value
        });

        try {
            const respuesta = await fetch(RUTA_FACTURAS_TRIMESTRE + "?" + parametros);
            if (respuesta.ok) {
                const resumen = await respuesta.json();
                if (numeroConsulta == ultimaConsultaFacturas) {
                    mostrarFacturas(resumen.facturas);
                    subtotalTrimestre.textContent = formatearImporte(resumen.subtotal);
                    ivaTrimestre.textContent = formatearImporte(resumen.importeIva);
                    totalTrimestre.textContent = formatearImporte(resumen.total);
                    resumenTrimestral.classList.remove("d-none");
                    mostrarMensaje("Mostrando el " + resumen.trimestre + "º trimestre de " + resumen.anio + ".", "info");
                }
            } else {
                const mensajeError = await respuesta.text();
                if (numeroConsulta == ultimaConsultaFacturas) {
                    mostrarMensaje(mensajeError || "No se pudo cargar el listado trimestral.", "danger");
                }
            }
        } catch (error) {
            if (numeroConsulta == ultimaConsultaFacturas) {
                console.error("Error al cargar el listado trimestral", error);
                mostrarMensaje("No se pudo conectar con el servidor.", "danger");
            }
        }
    }
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
    contenedorBuscador.classList.remove("expandido");
    cargarFacturas();
});
document.getElementById("botonGuardarFactura").addEventListener("click", guardarFactura);
document.getElementById("botonListarTrimestre").addEventListener("click", cargarListadoTrimestral);
inputSubtotal.addEventListener("input", calcularTotal);
inputIva.addEventListener("input", calcularTotal);
inputBusqueda.addEventListener("focus", function () {
    contenedorBuscador.classList.add("expandido");
});
// Se recoge después del clic para no desplazar el botón antes de activarlo.
document.addEventListener("click", function (evento) {
    if (!contenedorBuscador.contains(evento.target) && inputBusqueda.value.trim() == "") {
        contenedorBuscador.classList.remove("expandido");
    }
});
inputBusqueda.addEventListener("keydown", function (evento) {
    if (evento.key == "Enter") {
        cargarFacturas();
    } else if (evento.key == "Tab" && inputBusqueda.value.trim() == "") {
        contenedorBuscador.classList.remove("expandido");
    }
});

inputAnio.value = new Date().getFullYear();
cargarClientes();
cargarFacturas();
