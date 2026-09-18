import { crearAvisos } from './js/notificaciones.js';
import { limpiarValidacion, validar } from './js/validacion.js';

// Los avisos de esta pantalla. El comportamiento —cuándo se borra uno, cómo se lee en alto—
// vive en js/notificaciones.js, el mismo módulo que usan clientes, facturas y el visor. Aquí
// solo se dice cuáles son los dos contenedores del perfil.
const { anunciar, fijar } = crearAvisos({
    franja: document.getElementById('aviso-emisor'),
    region: document.getElementById('anuncios'),
});

document.addEventListener('DOMContentLoaded', () => {

    cargarEmisor();

    const formulario = document.getElementById('formEditarEmisor');

    if (formulario) {
        formulario.addEventListener('submit', actualizarEmisor);

        // Al reabrir el modal se quita la marca de validación. Si no, un intento fallido
        // dejaría los cinco campos en rojo desde el momento de abrirlo, sin tocar nada.
        document.getElementById('modalEditarEmisor')
            ?.addEventListener('show.bs.modal', () => limpiarValidacion(formulario));
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

        fijar(
            'No se han podido cargar los datos del emisor.',
            { esError: true }
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

    // De pintar en rojo el campo que falla, enseñar su mensaje y llevar el cursor hasta él
    // se encarga validar(), igual que en clientes y en facturas.
    if (!validar(formulario)) return;

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
        anunciar(
            'Los datos del emisor se han guardado correctamente.',
            { visible: true }
        );


    } catch (error) {

        console.error('Error al guardar el emisor:', error);

        fijar(
            error.message ||
            'Ha ocurrido un error al guardar los cambios.',
            { esError: true }
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


