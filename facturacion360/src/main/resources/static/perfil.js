document.addEventListener('DOMContentLoaded', () => {

    cargarEmisor();

    const formulario = document.getElementById('formEditarEmisor');

    if (formulario) {
        formulario.addEventListener('submit', actualizarEmisor);
    }

});


/**
 * Carga los datos actuales del emisor desde el backend.
 */
async function cargarEmisor() {

    try {

        const response = await fetch('/emisor');

        // Si todavía no existe ningún emisor,
        // simplemente dejamos el formulario preparado para crear uno.
        if (response.status === 404) {

            limpiarDatosEmisor();

            return;
        }

        if (!response.ok) {

            throw new Error(
                'No se pudo cargar el emisor. Código HTTP: ' +
                response.status
            );
        }

        const emisor = await response.json();

        mostrarEmisor(emisor);

    } catch (error) {

        console.error('Error al cargar el emisor:', error);

        mostrarMensaje(
            'No se han podido cargar los datos del emisor.',
            'danger'
        );
    }
}


/**
 * Envía los datos del formulario al backend.
 *
 * PUT /emisor
 *
 * El backend decide si debe hacer INSERT o UPDATE.
 */
async function actualizarEmisor(event) {

    event.preventDefault();

    const formulario = document.getElementById('formEditarEmisor');

    if (!formulario.checkValidity()) {

        formulario.reportValidity();

        return;
    }

    const emisor = {

        nombre: document
            .getElementById('inputNombre')
            .value
            .trim(),

        cif: document
            .getElementById('inputCif')
            .value
            .trim(),

        direccion: document
            .getElementById('inputDireccion')
            .value
            .trim(),

        email: document
            .getElementById('inputEmail')
            .value
            .trim(),

        telefono: document
            .getElementById('inputTelefono')
            .value
            .trim()
    };


    try {

        const response = await fetch('/emisor', {

            method: 'PUT',

            headers: {
                'Content-Type': 'application/json'
            },

            body: JSON.stringify(emisor)
        });


        if (!response.ok) {

            let mensaje = 'No se han podido guardar los cambios.';

            try {

                const textoError = await response.text();

                if (textoError) {
                    mensaje += ' ' + textoError;
                }

            } catch (error) {
                console.error(error);
            }

            throw new Error(mensaje);
        }


        const texto = await response.text();

        if (!texto) {
            throw new Error(
                'El servidor no ha devuelto los datos del emisor.'
            );
        }


        const emisorGuardado = JSON.parse(texto);

        mostrarEmisor(emisorGuardado);


        // Cerrar el modal después de guardar correctamente.
        const modalEl = document.getElementById('modalEditarEmisor');

        const modal = bootstrap.Modal.getInstance(modalEl);

        if (modal) {
            modal.hide();
        }


        // Mostrar mensaje de éxito.
        mostrarMensaje(
            'Los datos del emisor se han guardado correctamente.',
            'success'
        );


    } catch (error) {

        console.error('Error al guardar el emisor:', error);

        mostrarMensaje(
            error.message ||
            'Ha ocurrido un error al guardar los cambios.',
            'danger'
        );
    }
}


/**
 * Pinta los datos del emisor tanto en la tarjeta
 * como en el formulario.
 */
function mostrarEmisor(emisor) {

    document.getElementById('displayNombre').textContent =
        emisor.nombre || '';

    document.getElementById('displayCif').textContent =
        emisor.cif || '';

    document.getElementById('displayDireccion').textContent =
        emisor.direccion || '';

    document.getElementById('displayEmail').textContent =
        emisor.email || '';

    document.getElementById('displayTelefono').textContent =
        emisor.telefono || '';


    document.getElementById('inputNombre').value =
        emisor.nombre || '';

    document.getElementById('inputCif').value =
        emisor.cif || '';

    document.getElementById('inputDireccion').value =
        emisor.direccion || '';

    document.getElementById('inputEmail').value =
        emisor.email || '';

    document.getElementById('inputTelefono').value =
        emisor.telefono || '';
}


/**
 * Deja los datos vacíos cuando todavía
 * no existe ningún emisor.
 */
function limpiarDatosEmisor() {

    document.getElementById('displayNombre').textContent =
        'Sin configurar';

    document.getElementById('displayCif').textContent =
        '';

    document.getElementById('displayDireccion').textContent =
        '';

    document.getElementById('displayEmail').textContent =
        '';

    document.getElementById('displayTelefono').textContent =
        '';


    document.getElementById('inputNombre').value =
        '';

    document.getElementById('inputCif').value =
        '';

    document.getElementById('inputDireccion').value =
        '';

    document.getElementById('inputEmail').value =
        '';

    document.getElementById('inputTelefono').value =
        '';
}


/**
 * Muestra un mensaje Bootstrap en la parte superior
 * de la tarjeta del emisor.
 */
function mostrarMensaje(mensaje, tipo) {

    const tarjeta = document.getElementById('tarjetaEmisor');

    if (!tarjeta) {
        return;
    }


    // Eliminamos mensajes anteriores.
    const mensajesAnteriores =
        tarjeta.querySelectorAll('.mensaje-emisor');

    mensajesAnteriores.forEach(elemento => {
        elemento.remove();
    });


    const alerta = document.createElement('div');

    alerta.className =
        `alert alert-${tipo} alert-dismissible fade show mensaje-emisor`;

    alerta.setAttribute('role', 'alert');

    alerta.innerHTML = `
        ${mensaje}
        <button
            type="button"
            class="btn-close"
            data-bs-dismiss="alert"
            aria-label="Cerrar">
        </button>
    `;


    tarjeta.prepend(alerta);


    // El mensaje desaparece automáticamente después de 5 segundos.
    setTimeout(() => {

        if (alerta && alerta.parentNode) {

            alerta.remove();
        }

    }, 5000);
}
