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
const campoObservaciones = document.getElementById("observacionesFactura");
const contadorObservaciones = document.getElementById("contadorObservacionesFactura");
const botonGuardarFactura = document.getElementById("botonGuardarFactura");
const camposFactura = document.getElementById("camposFactura");
const modalFactura = document.getElementById("facturaModal");
const mensajeFormularioFactura = document.getElementById("mensaje-formulario-factura");
const botonesCerrarFactura = modalFactura.querySelectorAll('[data-bs-dismiss="modal"]');
const contenedorConceptos = document.getElementById("conceptosFactura");
const plantillaConcepto = document.getElementById("plantillaConceptoFactura");
const botonAnadirConcepto = document.getElementById("botonAnadirConcepto");
const sinConceptos = document.getElementById("sinConceptosFactura");
const campoEstado = document.getElementById("estadoFactura");
let guardandoFactura = false;

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
    if (!guardandoFactura && validarFormularioFactura()) {
        const datosFactura = {
            idCliente: Number(selectCliente.value),
            fechaEmision: document.getElementById("fechaEmision").value,
            estado: campoEstado.value,
            observaciones: campoObservaciones.value.trim(),
            conceptos: recogerConceptos()
        };

        cambiarEstadoGuardado(true);
        mensajeFormularioFactura.classList.add("d-none");
        let facturaGuardada = null;
        try {
            const respuesta = await fetch(RUTA_CREAR_FACTURA, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(datosFactura)
            });

            if (respuesta.ok) {
                facturaGuardada = await respuesta.json();
                formularioFactura.reset();
            } else {
                // No mostramos cuerpos de error que puedan contener SQL o trazas del servidor.
                if (respuesta.status == 400) {
                    mostrarErrorFormularioFactura("El servidor ha rechazado los datos. Revisa los campos y los importes de los conceptos.");
                } else if (respuesta.status == 409) {
                    mostrarErrorFormularioFactura("No se pudo asignar un número de factura. Puede haberse alcanzado el límite anual o coincidir con otras altas. Revisa antes de reintentar.");
                } else {
                    mostrarErrorFormularioFactura("El servidor no pudo completar el guardado. Conservamos tus datos; comprueba si la factura se guardó antes de reintentar.");
                }
            }
        } catch (error) {
            console.error("Error al crear la factura", error);
            mostrarErrorFormularioFactura("No se pudo conectar con el servidor. Comprueba si la factura se guardó antes de volver a intentarlo.");
        } finally {
            cambiarEstadoGuardado(false);
        }
        if (facturaGuardada) {
            bootstrap.Modal.getOrCreateInstance(modalFactura).hide();
            await cargarFacturas();
            mostrarMensaje("Factura " + facturaGuardada.numeroFactura + " creada. Total confirmado: "
                + formatearImporte(facturaGuardada.total) + ".", "success");
        }
    }
}

function cambiarEstadoGuardado(guardando) {
    guardandoFactura = guardando;
    botonGuardarFactura.disabled = guardando;
    botonGuardarFactura.textContent = guardando ? "Guardando…" : "Guardar factura";
    camposFactura.disabled = guardando;
    formularioFactura.setAttribute("aria-busy", String(guardando));
    for (const boton of botonesCerrarFactura) {
        boton.disabled = guardando;
    }
}

function mostrarErrorFormularioFactura(texto) {
    mensajeFormularioFactura.textContent = texto;
    mensajeFormularioFactura.classList.remove("d-none");
    mensajeFormularioFactura.focus();
}

function actualizarContadorObservaciones() {
    contadorObservaciones.textContent = campoObservaciones.value.length + " / " + campoObservaciones.maxLength + " caracteres";
}

function anadirConcepto() {
    const concepto = plantillaConcepto.content.firstElementChild.cloneNode(true);
    concepto.querySelector(".eliminar-concepto").addEventListener("click", function () {
        concepto.remove();
        actualizarConceptos();
        botonAnadirConcepto.focus();
    });
    concepto.addEventListener("input", function () {
        concepto.querySelector('[name="descripcion"]').setCustomValidity("");
        actualizarConceptos();
    });
    contenedorConceptos.appendChild(concepto);
    actualizarConceptos();
    concepto.querySelector("input").focus();
}

function recogerConceptos() {
    const conceptos = [];
    for (const concepto of contenedorConceptos.children) {
        conceptos.push({
            descripcion: concepto.querySelector('[name="descripcion"]').value.trim(),
            cantidad: Number(concepto.querySelector('[name="cantidad"]').value),
            precioUnitario: Number(concepto.querySelector('[name="precioUnitario"]').value),
            descuento: Number(concepto.querySelector('[name="descuento"]').value),
            porcentajeIva: Number(concepto.querySelector('[name="porcentajeIva"]').value)
        });
    }
    return conceptos;
}

function validarFormularioFactura() {
    for (const concepto of contenedorConceptos.children) {
        const descripcion = concepto.querySelector('[name="descripcion"]');
        descripcion.setCustomValidity(descripcion.value.trim() ? "" : "Escribe una descripción.");
    }
    let valido = formularioFactura.reportValidity();
    if (valido && campoEstado.value == "EMITIDA" && contenedorConceptos.children.length == 0) {
        mostrarErrorFormularioFactura("Para emitir la factura, añade al menos un concepto.");
        valido = false;
    }
    return valido;
}

/** Solo ayuda visual: estos importes no se incluyen en la petición de alta. */
function actualizarConceptos() {
    let subtotalCentimos = 0;
    let ivaCentimos = 0;
    const conceptos = recogerConceptos();
    for (let indice = 0; indice < conceptos.length; indice++) {
        const concepto = conceptos[indice];
        // Céntimos y centésimas de porcentaje evitan restas decimales como 2.30 - 5 %.
        const precioCentimos = Math.round(concepto.precioUnitario * 100);
        const descuentoCentesimas = Math.round(concepto.descuento * 100);
        const ivaCentesimas = Math.round(concepto.porcentajeIva * 100);
        const baseCentimos = Math.round(concepto.cantidad * precioCentimos * (10000 - descuentoCentesimas) / 10000);
        const impuestoCentimos = Math.round(baseCentimos * ivaCentesimas / 10000);
        subtotalCentimos += baseCentimos;
        ivaCentimos += impuestoCentimos;
        const ficha = contenedorConceptos.children[indice];
        ficha.querySelector("legend").textContent = "Concepto " + (indice + 1);
        ficha.querySelector(".eliminar-concepto").setAttribute("aria-label", "Eliminar concepto " + (indice + 1));
        ficha.querySelector(".resumen-concepto").textContent = "Base: " + formatearImporte(baseCentimos / 100)
            + " · IVA: " + formatearImporte(impuestoCentimos / 100)
            + " · Total: " + formatearImporte((baseCentimos + impuestoCentimos) / 100);
    }
    sinConceptos.classList.toggle("d-none", conceptos.length > 0);
    inputSubtotal.textContent = formatearImporte(subtotalCentimos / 100);
    inputIva.textContent = formatearImporte(ivaCentimos / 100);
    inputTotal.textContent = formatearImporte((subtotalCentimos + ivaCentimos) / 100);
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
formularioFactura.addEventListener("submit", function (evento) {
    evento.preventDefault();
    guardarFactura();
});
campoObservaciones.addEventListener("input", actualizarContadorObservaciones);
formularioFactura.addEventListener("reset", function () {
    contenedorConceptos.replaceChildren();
    actualizarConceptos();
    mensajeFormularioFactura.classList.add("d-none");
    // El evento reset se recibe antes de que el navegador vacíe los campos.
    setTimeout(actualizarContadorObservaciones, 0);
});
modalFactura.addEventListener("hide.bs.modal", function (evento) {
    if (guardandoFactura) {
        evento.preventDefault();
    }
});
document.getElementById("botonListarTrimestre").addEventListener("click", cargarListadoTrimestral);
botonAnadirConcepto.addEventListener("click", anadirConcepto);
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
actualizarContadorObservaciones();
cargarClientes();
cargarFacturas();
