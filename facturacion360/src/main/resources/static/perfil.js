document.addEventListener('DOMContentLoaded', function () {

    // =========================================================================
    // 1. GESTIÓN Y SINCRONIZACIÓN DEL EMISOR (Inicio / Modal / Factura)
    // =========================================================================
    const formEditarEmisor = document.getElementById('formEditarEmisor');

    // Función auxiliar para construir el texto ordenado del emisor
    function formatearTextoEmisor(datos) {
        if (!datos) return '';
        const lineas = [];
        if (datos.nombre) lineas.push(datos.nombre);
        if (datos.cif) lineas.push(`CIF/NIF: ${datos.cif}`);
        if (datos.direccion) lineas.push(`Dirección: ${datos.direccion}`);
        if (datos.email) lineas.push(`Email: ${datos.email}`);
        if (datos.telefono) lineas.push(`Teléfono: ${datos.telefono}`);
        return lineas.join('\n');
    }

    // Función para aplicar los datos del emisor en los elementos correspondientes
    function aplicarDatosEmisor(datos) {
        if (!datos) return;

        // 1. Actualizar Tarjeta de inicio / vista emisor (si existe en el HTML actual)
        const displayNombre = document.getElementById('displayNombre');
        const displayCif = document.getElementById('displayCif');
        const displayDireccion = document.getElementById('displayDireccion');

        if (displayNombre) displayNombre.textContent = datos.nombre || '';
        if (displayCif) displayCif.textContent = datos.cif ? `NIF/CIF: ${datos.cif}` : '';
        if (displayDireccion) displayDireccion.textContent = datos.direccion ? `Dirección: ${datos.direccion}` : '';

        // 2. Sincronizar con la sección/textarea de Factura
        const inputEmisor = document.getElementById('inputEmisor');
        if (inputEmisor) {
            const textoFormateado = formatearTextoEmisor(datos);
            inputEmisor.value = textoFormateado;
            localStorage.setItem("factura_emisor", textoFormateado);
        }
    }

    // Cargar emisor guardado al iniciar
    const emisorGuardado = JSON.parse(localStorage.getItem('datosEmisor'));
    if (emisorGuardado) {
        aplicarDatosEmisor(emisorGuardado);
    }

    // Guardar cambios desde el modal del Emisor
    if (formEditarEmisor) {
        formEditarEmisor.addEventListener('submit', function (e) {
            e.preventDefault();

            const nuevosDatos = {
                nombre: document.getElementById('inputNombre')?.value.trim() || '',
                cif: document.getElementById('inputCif')?.value.trim() || '',
                direccion: document.getElementById('inputDireccion')?.value.trim() || '',
                email: document.getElementById('inputEmail')?.value.trim() || '',
                telefono: document.getElementById('inputTelefono')?.value.trim() || ''
            };

            // Guardar en localStorage
            localStorage.setItem('datosEmisor', JSON.stringify(nuevosDatos));

            // Aplicar en la vista
            aplicarDatosEmisor(nuevosDatos);

            // Cerrar modal de Bootstrap si existe
            const modalElement = document.getElementById('modalEditarEmisor');
            if (modalElement && typeof bootstrap !== 'undefined') {
                const modalInstance = bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);
                modalInstance.hide();
            }
        });
    }


    // =========================================================================
    // 2. LÓGICA DE FACTURAS EDITABLES Y CÁLCULOS (facturas.html)
    // =========================================================================
    const formFactura = document.getElementById('formFacturaEditable');

    if (formFactura) {
        const inputNumFactura = document.getElementById('inputNumFactura');
        const inputNumPedido = document.getElementById('inputNumPedido');
        const inputFecha = document.getElementById('inputFecha');
        const inputFechaVencida = document.getElementById('inputFechaVencida');
        const inputEmisor = document.getElementById('inputEmisor');
        const inputReceptor = document.getElementById('inputReceptor');
        const inputDescripcion = document.getElementById('inputDescripcion');
        const inputCantidad = document.getElementById('inputCantidad');
        const inputPrecioUnitario = document.getElementById('inputPrecioUnitario');

        const displayImporte = document.getElementById('displayImporte');
        const displaySubtotal = document.getElementById('displaySubtotal');
        const displayIva = document.getElementById('displayIva');
        const displayTotal = document.getElementById('displayTotal');

        const btnDescargar = document.getElementById('btnDescargarFactura');
        const btnLimpiar = document.getElementById('btnLimpiarCampos');

        // Función para calcular importes y persistir en localStorage
        function calcularYGuardar() {
            const cantidad = parseFloat(inputCantidad?.value) || 0;
            const precioUnitario = parseFloat(inputPrecioUnitario?.value) || 0;

            const importeCalculado = cantidad * precioUnitario;
            const subtotal = importeCalculado;
            const iva = subtotal * 0.21;
            const total = subtotal + iva;

            if (displayImporte) displayImporte.textContent = `${importeCalculado.toFixed(2)} €`;
            if (displaySubtotal) displaySubtotal.textContent = `${subtotal.toFixed(2)} €`;
            if (displayIva) displayIva.textContent = `${iva.toFixed(2)} €`;
            if (displayTotal) displayTotal.textContent = `${total.toFixed(2)} €`;

            if (inputNumFactura) localStorage.setItem("factura_num", inputNumFactura.value);
            if (inputNumPedido) localStorage.setItem("factura_pedido", inputNumPedido.value);
            if (inputFecha) localStorage.setItem("factura_fecha", inputFecha.value);
            if (inputFechaVencida) localStorage.setItem("factura_vencida", inputFechaVencida.value);
            if (inputEmisor) localStorage.setItem("factura_emisor", inputEmisor.value);
            if (inputReceptor) localStorage.setItem("factura_receptor", inputReceptor.value);
            if (inputDescripcion) localStorage.setItem("factura_descripcion", inputDescripcion.value);
            if (inputCantidad) localStorage.setItem("factura_cantidad", cantidad);
            if (inputPrecioUnitario) localStorage.setItem("factura_precio", precioUnitario);
        }

        // Carga inicial de datos de factura
        const fechaHoy = new Date().toISOString().split('T')[0];

        if (inputNumFactura) inputNumFactura.value = localStorage.getItem("factura_num") || "FAC-2024-001";
        if (inputNumPedido) inputNumPedido.value = localStorage.getItem("factura_pedido") || "PED-8842";
        if (inputFecha) inputFecha.value = localStorage.getItem("factura_fecha") || fechaHoy;
        if (inputFechaVencida) inputFechaVencida.value = localStorage.getItem("factura_vencida") || fechaHoy;
        if (inputReceptor) inputReceptor.value = localStorage.getItem("factura_receptor") || "Cliente de Prueba S.L.\nNIF: B12345678\nCalle Ejemplo 123";
        if (inputDescripcion) inputDescripcion.value = localStorage.getItem("factura_descripcion") || "Servicios de desarrollo web";
        if (inputCantidad) inputCantidad.value = localStorage.getItem("factura_cantidad") || "1";
        if (inputPrecioUnitario) inputPrecioUnitario.value = localStorage.getItem("factura_precio") || "100.00";

        // Asignar el emisor al campo de la factura
        if (inputEmisor) {
            const emisorGuardadoObj = JSON.parse(localStorage.getItem('datosEmisor'));
            if (emisorGuardadoObj) {
                inputEmisor.value = formatearTextoEmisor(emisorGuardadoObj);
            } else if (localStorage.getItem("factura_emisor")) {
                inputEmisor.value = localStorage.getItem("factura_emisor");
            } else {
                inputEmisor.value = "FUNDACION ONCE\nNIF/CIF: G-2801539RW\nDirección: Calle Fray Luis de León,11, 28012 Madrid\nEmail: contacto@xtd.com\nTeléfono: +34 911 106 106";
            }
        }

        // Evento input para actualización automática
        formFactura.addEventListener('input', calcularYGuardar);

        // Botón Limpiar
        if (btnLimpiar) {
            btnLimpiar.addEventListener('click', function () {
                if (!confirm("¿Deseas reiniciar la factura?")) return;

                localStorage.removeItem("factura_num");
                localStorage.removeItem("factura_pedido");
                localStorage.removeItem("factura_fecha");
                localStorage.removeItem("factura_vencida");
                localStorage.removeItem("factura_emisor");
                localStorage.removeItem("factura_receptor");
                localStorage.removeItem("factura_descripcion");
                localStorage.removeItem("factura_cantidad");
                localStorage.removeItem("factura_precio");

                if (inputNumFactura) inputNumFactura.value = "FAC-2024-001";
                if (inputNumPedido) inputNumPedido.value = "PED-8842";
                if (inputFecha) inputFecha.value = fechaHoy;
                if (inputFechaVencida) inputFechaVencida.value = fechaHoy;
                if (inputReceptor) inputReceptor.value = "Cliente de Prueba S.L.\nNIF: B12345678\nCalle Ejemplo 123";
                if (inputDescripcion) inputDescripcion.value = "Servicios de desarrollo web";
                if (inputCantidad) inputCantidad.value = "1";
                if (inputPrecioUnitario) inputPrecioUnitario.value = "100.00";

                const emisorObj = JSON.parse(localStorage.getItem('datosEmisor'));
                if (emisorObj) {
                    inputEmisor.value = formatearTextoEmisor(emisorObj);
                } else if (inputEmisor) {
                    inputEmisor.value = "FUNDACION ONCE\nNIF/CIF: G-2801539RW\nDirección: Calle Fray Luis de León,11, 28012 Madrid\nEmail: contacto@xtd.com\nTeléfono: +34 911 106 106";
                }

                calcularYGuardar();
            });
        }

        // Botón Descargar PDF
        if (btnDescargar) {
            btnDescargar.addEventListener('click', function () {
                if (typeof html2pdf === 'undefined') {
                    alert("Error: La librería html2pdf no está cargada.");
                    return;
                }

                const elemento = document.getElementById('areaFacturaDescargable');
                if (!elemento) {
                    alert("Error: No se encuentra el contenedor 'areaFacturaDescargable'.");
                    return;
                }

                const numFactura = inputNumFactura?.value || 'Factura';

                const opciones = {
                    margin:       10,
                    filename:     `Factura_${numFactura}.pdf`,
                    image:        { type: 'jpeg', quality: 0.98 },
                    html2canvas:  { scale: 2, useCORS: true, logging: false },
                    jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
                };

                html2pdf().set(opciones).from(elemento).save().catch(err => {
                    console.error("Error generando PDF:", err);
                    alert("Ocurrió un error al generar el PDF.");
                });
            });
        }

        // Ejecutar cálculo inicial
        calcularYGuardar();
    }
});

function actualizarEmisor(evento) {
    evento.preventDefault();
    let nuevosDatos = {
        nombre: document.getElementById('inputNombre')?.value.trim() || '',
        cif: document.getElementById('inputCif')?.value.trim() || '',
        direccion: document.getElementById('inputDireccion')?.value.trim() || '',
        email: document.getElementById('inputEmail')?.value.trim() || '',
        telefono: document.getElementById('inputTelefono')?.value.trim() || ''
    };

    let emisorJson = JSON.stringify(nuevosDatos);
    console.log("Datos del emisor a guardar:", emisorJson);

    //TODO: ENVIAR AL SERVIDOR CON FETCH 
    fetch('emisor', {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json'
  },
  body: emisorJson
})
.then(response => {
 console.log('Hemos vuelto');
})
.catch(error => console.error('Error:', error));

}