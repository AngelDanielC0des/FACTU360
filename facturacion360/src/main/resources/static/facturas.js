const RUTA_FACTURAS = "/factura/buscar";
const RUTA_CREAR_FACTURA = "/factura";
const RUTA_FACTURAS_TRIMESTRE = "/factura/trimestral";
const RUTA_CLIENTES = "/cliente/listar-ultimos?limite=100";
const RUTA_SUGERENCIAS_CONCEPTOS = "/factura/conceptos/sugerencias";

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
let siguienteListaSugerencias = 0;
let idFacturaEnEdicion = null;
let ultimaCargaBorrador = 0;
let formularioDisponible = true;
let volverAlDetalle = false;
let listadoTrimestralActivo = false;


// Ambas búsquedas comparten la tabla: una respuesta anterior no debe reemplazar la última consulta.
let ultimaConsultaFacturas = 0;


/** Carga las facturas que coinciden con el texto buscado. */
async function cargarFacturas() {
    listadoTrimestralActivo = false;
    let actualizada = false;
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
                actualizada = true;
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
    return actualizada;
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
            agregarAccionVisor(fila, factura);
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

/** Añade a la fila el botón que abre la factura preparada para imprimir. */
function agregarAccionVisor(fila, factura) {
    const celda = document.createElement("td");
    celda.className = "text-end";

    const boton = document.createElement("button");
    boton.type = "button";
    boton.className = "btn btn-sm btn-outline-primary";
    boton.title = "Ver e imprimir factura";
    boton.setAttribute("aria-label", "Ver e imprimir factura");
    boton.textContent = "Ver / PDF";
    boton.addEventListener("click", function () {
        window.open("factura-imprimir.html?idFactura=" + factura.idFactura, "_blank");
    });

    celda.appendChild(boton);
    if (factura.estado == "BORRADOR") {
        const editar = document.createElement("button");
        editar.type = "button";
        editar.className = "btn btn-sm btn-outline-primary ms-1";
        editar.textContent = "Editar";
        editar.setAttribute("aria-label", "Editar borrador " + factura.numeroFactura);
        editar.addEventListener("click", () => abrirBorrador(factura.idFactura));
        celda.appendChild(editar);
    }
    fila.appendChild(celda);
}

/** Consulta las facturas del año y trimestre elegidos y muestra sus totales. */
async function cargarListadoTrimestral() {
    let actualizada = false;
    if (inputAnio.reportValidity()) {
        listadoTrimestralActivo = true;
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
                    actualizada = true;
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
    return actualizada;
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

function prepararAlta() {
    ultimaCargaBorrador++;
    if (idFacturaEnEdicion != null) {
        formularioFactura.reset();
    }
    idFacturaEnEdicion = null;
    volverAlDetalle = false;
    formularioDisponible = true;
    campoEstado.disabled = false;
    document.getElementById("facturaModalLabel").textContent = "Dar de alta una factura";
    document.getElementById("ayudaNumeroFactura").textContent = "Número automático al guardar por primera vez.";
    document.getElementById("fechaEmision").min = "1000-01-01";
    document.getElementById("fechaEmision").max = "9999-12-31";
    cambiarEstadoGuardado(false);
}

/** Reutiliza el alta; una respuesta tardía no puede rellenar otro formulario. */
async function abrirBorrador(idFactura, desdeDetalle = false) {
    if (!guardandoFactura) {
        const consulta = ++ultimaCargaBorrador;
        formularioFactura.reset();
        idFacturaEnEdicion = idFactura;
        volverAlDetalle = desdeDetalle;
        formularioDisponible = false;
        document.getElementById("facturaModalLabel").textContent = "Editar borrador";
        document.getElementById("ayudaNumeroFactura").textContent = "Cargando borrador…";
        cambiarEstadoGuardado(false);
        bootstrap.Modal.getOrCreateInstance(modalFactura).show();
        try {
            const [respuesta] = await Promise.all([fetch("/factura/" + idFactura + "/detalle"), cargaClientes]);
            if (consulta == ultimaCargaBorrador) {
                if (respuesta.ok) {
                    const detalle = await respuesta.json();
                    if (consulta == ultimaCargaBorrador) {
                        const factura = detalle.factura;
                        if (factura.estado == "BORRADOR") {
                            if (!Array.from(selectCliente.options).some(opcion => opcion.value == String(factura.idCliente))) {
                                const opcion = document.createElement("option");
                                opcion.value = factura.idCliente;
                                opcion.textContent = detalle.cliente.nombre + " - " + detalle.cliente.nifCif;
                                selectCliente.appendChild(opcion);
                            }
                            selectCliente.value = factura.idCliente;
                            const fecha = document.getElementById("fechaEmision");
                            const anio = /^[fF]-[0-9]{4}-[0-9]{4}$/.test(factura.numeroFactura)
                                ? factura.numeroFactura.substring(2, 6) : factura.fechaEmision.substring(0, 4);
                            fecha.min = anio + "-01-01";
                            fecha.max = anio + "-12-31";
                            fecha.value = factura.fechaEmision;
                            campoEstado.value = "BORRADOR";
                            campoEstado.disabled = false;
                            campoObservaciones.value = factura.observaciones ?? "";
                            for (const concepto of detalle.conceptos) {
                                anadirConcepto(concepto);
                            }
                            actualizarContadorObservaciones();
                            document.getElementById("ayudaNumeroFactura").textContent = "Número " + factura.numeroFactura + " · Se conserva al guardar.";
                            formularioDisponible = true;
                        } else {
                            mostrarErrorFormularioFactura("La factura ya no está en BORRADOR y no se puede editar.");
                        }
                    }
                } else {
                    mostrarErrorFormularioFactura(respuesta.status == 404 ? "No se encontró la factura." : "No se pudo cargar el borrador. Cierra y vuelve a intentarlo.");
                }
            }
        } catch (error) {
            if (consulta == ultimaCargaBorrador) {
                mostrarErrorFormularioFactura("No se pudo conectar con el servidor para cargar el borrador.");
            }
        } finally {
            if (consulta == ultimaCargaBorrador) {
                cambiarEstadoGuardado(false);
            }
        }
    }
}

/** Envía cabecera y conceptos en una sola petición, tanto al crear como al editar. */
async function guardarFactura() {
    if (!guardandoFactura && formularioDisponible && validarFormularioFactura()) {
        const editando = idFacturaEnEdicion != null;
        const regresar = volverAlDetalle;
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
            const ruta = editando ? "/factura/" + idFacturaEnEdicion + "/borrador" : RUTA_CREAR_FACTURA;
            const respuesta = await fetch(ruta, {
                method: editando ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(datosFactura)
            });

            if (respuesta.ok) {
                facturaGuardada = await respuesta.json();
                formularioFactura.reset();
            } else {
                // No mostramos cuerpos de error que puedan contener SQL o trazas del servidor.
                if (respuesta.status == 400) {
                    mostrarErrorFormularioFactura(editando
                        ? "Revisa los conceptos y la fecha: debe conservar el año del número y el estado debe ser BORRADOR o EMITIDA."
                        : "El servidor ha rechazado los datos. Revisa los campos y los importes de los conceptos.");
                } else if (respuesta.status == 404 && editando) {
                    mostrarErrorFormularioFactura("No se encontró la factura. Conservamos los datos del formulario.");
                } else if (respuesta.status == 409) {
                    mostrarErrorFormularioFactura(editando
                        ? "No se pudo guardar: la factura puede haber dejado de ser BORRADOR o el cliente ya no estar disponible. Conservamos tus cambios."
                        : "No se pudo asignar un número de factura. Puede haberse alcanzado el límite anual o coincidir con otras altas. Revisa antes de reintentar.");
                } else {
                    mostrarErrorFormularioFactura("El servidor no pudo completar el guardado. Conservamos tus datos; comprueba si la factura se guardó antes de reintentar.");
                }
            }
        } catch (error) {
            console.error("Error al guardar la factura", error);
            mostrarErrorFormularioFactura("No se pudo conectar con el servidor. Comprueba si la factura se guardó antes de volver a intentarlo.");
        } finally {
            cambiarEstadoGuardado(false);
        }
        if (facturaGuardada) {
            bootstrap.Modal.getOrCreateInstance(modalFactura).hide();
            if (regresar) {
                window.location.href = "factura-imprimir.html?idFactura=" + facturaGuardada.idFactura;
            } else {
                const consulta = ultimaConsultaFacturas + 1;
                const actualizada = await (listadoTrimestralActivo ? cargarListadoTrimestral() : cargarFacturas());
                if (consulta == ultimaConsultaFacturas) {
                    mostrarMensaje("Factura " + facturaGuardada.numeroFactura + (editando ? " actualizada. Total confirmado: " : " creada. Total confirmado: ")
                        + formatearImporte(facturaGuardada.total) + (actualizada ? "." : ". No se pudo actualizar el listado; vuelve a consultarlo."),
                        actualizada ? "success" : "warning");
                }
            }
        }
    }
}

function cambiarEstadoGuardado(guardando) {
    guardandoFactura = guardando;
    if (guardando) {
        cerrarSugerenciasConceptos();
    }
    botonGuardarFactura.disabled = guardando || !formularioDisponible;
    botonGuardarFactura.textContent = guardando ? "Guardando…" : (idFacturaEnEdicion == null ? "Guardar factura" : "Guardar cambios");
    camposFactura.disabled = guardando || !formularioDisponible;
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

function anadirConcepto(datos = null) {
    const concepto = plantillaConcepto.content.firstElementChild.cloneNode(true);
    if (datos) {
        for (const campo of ["descripcion", "cantidad", "precioUnitario", "descuento", "porcentajeIva"]) {
            concepto.querySelector('[name="' + campo + '"]').value = datos[campo] ?? "";
        }
    }
    prepararSugerenciasConcepto(concepto);
    concepto.querySelector(".eliminar-concepto").addEventListener("click", function () {
        concepto.dispatchEvent(new Event("cerrar-sugerencias"));
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

function cerrarSugerenciasConceptos() {
    for (const concepto of contenedorConceptos.children) {
        concepto.dispatchEvent(new Event("cerrar-sugerencias"));
    }
}

/** Cada línea mantiene su consulta y selección, sin compartir resultados con otras. */
function prepararSugerenciasConcepto(concepto) {
    const descripcion = concepto.querySelector('[name="descripcion"]');
    const lista = concepto.querySelector(".sugerencias-concepto");
    lista.id = "sugerencias-concepto-" + ++siguienteListaSugerencias;
    descripcion.setAttribute("aria-controls", lista.id);
    let consultaActual = 0;
    let espera = null;
    let controlador = null;
    let sugerencias = [];
    let seleccion = -1;

    function cerrar() {
        consultaActual++;
        clearTimeout(espera);
        espera = null;
        if (controlador) {
            controlador.abort();
            controlador = null;
        }
        sugerencias = [];
        seleccion = -1;
        lista.replaceChildren();
        lista.classList.add("d-none");
        descripcion.setAttribute("aria-expanded", "false");
        descripcion.removeAttribute("aria-activedescendant");
    }

    function elegir(sugerencia) {
        if (!guardandoFactura) {
            descripcion.value = sugerencia.descripcion;
            descripcion.setCustomValidity("");
            for (const campo of ["precioUnitario", "descuento", "porcentajeIva"]) {
                // Un valor histórico ausente queda pendiente de completar, no se inventa un cero.
                concepto.querySelector('[name="' + campo + '"]').value = sugerencia[campo] ?? "";
            }
            cerrar();
            actualizarConceptos();
            descripcion.focus();
        }
    }

    function mostrar() {
        for (const [indice, sugerencia] of sugerencias.entries()) {
            const opcion = document.createElement("div");
            opcion.id = lista.id + "-" + indice;
            opcion.setAttribute("role", "option");
            opcion.setAttribute("aria-selected", "false");
            opcion.textContent = sugerencia.descripcion;
            const detalle = document.createElement("small");
            detalle.className = "d-block";
            detalle.textContent = (sugerencia.precioUnitario == null ? "Precio pendiente" : formatearImporte(sugerencia.precioUnitario))
                + " · IVA " + (sugerencia.porcentajeIva == null ? "pendiente" : sugerencia.porcentajeIva + " %");
            opcion.appendChild(detalle);
            opcion.addEventListener("pointerdown", evento => evento.preventDefault());
            opcion.addEventListener("click", () => elegir(sugerencia));
            lista.appendChild(opcion);
        }
        lista.classList.toggle("d-none", sugerencias.length == 0);
        descripcion.setAttribute("aria-expanded", String(sugerencias.length > 0));
    }

    descripcion.addEventListener("input", function () {
        cerrar();
        const texto = descripcion.value.trim();
        const numeroConsulta = consultaActual;
        if (texto.length >= 2 && !guardandoFactura) {
            espera = setTimeout(async function () {
                espera = null;
                controlador = new AbortController();
                try {
                    const parametros = new URLSearchParams({ texto, limite: 8 });
                    const respuesta = await fetch(RUTA_SUGERENCIAS_CONCEPTOS + "?" + parametros, { signal: controlador.signal });
                    const resultados = respuesta.ok ? await respuesta.json() : [];
                    if (numeroConsulta == consultaActual && concepto.isConnected
                        && document.activeElement == descripcion && !guardandoFactura) {
                        sugerencias = Array.isArray(resultados) ? resultados.slice(0, 8) : [];
                        mostrar();
                    }
                } catch (error) {
                    // Las sugerencias son opcionales: un fallo no impide el alta manual.
                    if (numeroConsulta == consultaActual) {
                        cerrar();
                    }
                }
            }, 250);
        }
    });
    descripcion.addEventListener("keydown", function (evento) {
        if (evento.key == "Escape" && (sugerencias.length > 0 || espera != null || controlador != null)) {
            evento.preventDefault();
            evento.stopPropagation();
            cerrar();
        } else if (sugerencias.length > 0) {
            if (evento.key == "ArrowDown" || evento.key == "ArrowUp") {
                evento.preventDefault();
                seleccion = evento.key == "ArrowDown" ? (seleccion + 1) % sugerencias.length
                    : (seleccion <= 0 ? sugerencias.length : seleccion) - 1;
                for (const [indice, opcion] of Array.from(lista.children).entries()) {
                    opcion.setAttribute("aria-selected", String(indice == seleccion));
                }
                descripcion.setAttribute("aria-activedescendant", lista.children[seleccion].id);
                lista.children[seleccion].scrollIntoView({ block: "nearest" });
            } else if (evento.key == "Enter") {
                evento.preventDefault();
                if (seleccion >= 0) {
                    elegir(sugerencias[seleccion]);
                }
            }
        }
    });
    descripcion.addEventListener("blur", cerrar);
    concepto.addEventListener("cerrar-sugerencias", cerrar);
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
    cerrarSugerenciasConceptos();
    contenedorConceptos.replaceChildren();
    actualizarConceptos();
    mensajeFormularioFactura.classList.add("d-none");
    // El evento reset se recibe antes de que el navegador vacíe los campos.
    setTimeout(actualizarContadorObservaciones, 0);
});
modalFactura.addEventListener("hide.bs.modal", function (evento) {
    if (guardandoFactura) {
        evento.preventDefault();
    } else {
        ultimaCargaBorrador++;
        cerrarSugerenciasConceptos();
    }
});
document.addEventListener("pointerdown", function (evento) {
    for (const concepto of contenedorConceptos.children) {
        if (!concepto.contains(evento.target)) {
            concepto.dispatchEvent(new Event("cerrar-sugerencias"));
        }
    }
});
document.getElementById("botonListarTrimestre").addEventListener("click", cargarListadoTrimestral);
botonAnadirConcepto.addEventListener("click", () => anadirConcepto());
document.getElementById("botonAltaFactura").addEventListener("click", prepararAlta);
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
const cargaClientes = cargarClientes();
cargarFacturas();
const identificadorEdicion = Number(new URLSearchParams(window.location.search).get("editar"));
if (Number.isInteger(identificadorEdicion) && identificadorEdicion > 0) {
    abrirBorrador(identificadorEdicion, true);
}
