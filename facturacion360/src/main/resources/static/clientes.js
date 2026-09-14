/**
 * @file Pantalla de clientes: pide al backend una PÁGINA de clientes y la pinta en la
 * tabla, con buscador, filtros por provincia/población, ordenación, paginación, y el
 * despliegue en la propia fila para ver el detalle y para editar.
 *
 * Endpoint: GET /cliente/listar-pagina?pagina=0&tamano=10&busqueda=&provincia=
 *                                     &poblacion=&ordenarPor=&direccion=
 * Devuelve un PaginaClienteResponse:
 *   { contenido: [ {idCliente, nombre, nifCif, ..., fechaAlta}, ... ],
 *     paginaActual, totalPaginas, totalElementos, hayAnterior, haySiguiente }
 *
 * Buscar, filtrar y ordenar son el MISMO endpoint: para el backend, buscar es
 * "listar con un filtro de texto más". Por eso aquí hay una sola función que pide
 * datos (cargarClientes) en vez de una por cada modo.
 *
 * Idea: separar "pedir" (cargarClientes) de "pintar" (pintarFilas / pintarPaginacion).
 * Usamos el <template> del HTML como molde y textContent (no innerHTML) para que un
 * nombre con < o & no pueda inyectar HTML.
 *
 * @author AngelDanielC0des
 * @see clientes.html — los <template> que se clonan aquí
 * @see edu.xtd.facturacion360.controller.ClienteController — el otro extremo de cada fetch
 */

// Rutas relativas: el HTML lo sirve el propio Spring Boot, mismo origen (sin CORS).
const API_CLIENTE = "/cliente";
const API_LISTAR_PAGINA = "/cliente/listar-pagina";
const API_PROVINCIAS = "/cliente/provincias";
const API_POBLACIONES = "/cliente/poblaciones";

// Clientes por página. Tiene que valer lo mismo que CriteriosCliente.TAMANO_DEFECTO en el
// backend; no hay forma de compartir la constante entre Java y este archivo, así que queda
// anotado en los dos sitios. Si aquí se pusiera más de TAMANO_MAX (100), el backend lo
// acotaría por su cuenta y la paginación seguiría cuadrando, pero la tabla enseñaría menos
// filas de las pedidas sin decir por qué.
const TAMANO_PAGINA = 10;

// Cuánto esperamos tras la última tecla antes de consultar. Sin esta pausa,
// escribir "garcia" lanzaría 6 peticiones a la base de datos en vez de 1.
const ESPERA_TECLEO_MS = 300;

// Cuánto tarda en aparecer el aviso de "Ver detalles" al dejar el ratón encima.
const RETARDO_PISTA_MS = 1000;

// Lo que dura el plegado del panel. Tiene que coincidir con la transición de
// .despliegue-envoltorio en style.css: es el tiempo que esperamos para quitarlo del DOM.
const DURACION_PLEGADO_MS = 250;

// Cuánto se queda en pantalla un aviso ("Cliente guardado") antes de borrarse solo.
const DURACION_AVISO_MS = 5000;

// Referencias del DOM que usamos.
const cuerpoTabla = document.getElementById("tabla-clientes");
const plantillaFila = document.getElementById("fila-cliente-template");
const plantillaDespliegue = document.getElementById("fila-despliegue-template");
const plantillaPanelDetalle = document.getElementById("panel-detalle-template");
const plantillaPanelEdicion = document.getElementById("panel-edicion-template");
const btnAnterior = document.getElementById("btn-anterior");
const btnSiguiente = document.getElementById("btn-siguiente");
const infoPagina = document.getElementById("info-pagina");

const inputBuscador = document.getElementById("buscador-clientes");
const contenedorBuscador = document.querySelector(".buscador-clientes");
const barraFiltros = document.querySelector(".barra-filtros");
const contadorFiltros = document.getElementById("contador-filtros");
const selectProvincia = document.getElementById("filtro-provincia");
const selectPoblacion = document.getElementById("filtro-poblacion");
const selectOrdenarPor = document.getElementById("filtro-ordenar-por");
const btnDireccion = document.getElementById("btn-direccion");
const iconoDireccion = document.getElementById("icono-direccion");
const etiquetaDireccion = document.getElementById("etiqueta-direccion");
const btnLimpiar = document.getElementById("btn-limpiar");
const cabecerasOrdenables = document.querySelectorAll(".cabecera-ordenable");
const regionAnuncios = document.getElementById("anuncios");
const avisoClientes = document.getElementById("aviso-clientes");
const contenedorTabla = cuerpoTabla.closest(".table-responsive");
const dialogoDescartar = document.getElementById("modal-descartar");
const btnDescartar = document.getElementById("btn-descartar");
const pantallaCarga = document.getElementById("pantalla-carga");
const btnAnadirCliente = document.getElementById("btn-anadir-cliente");

// El diálogo de descartar: se construye la primera vez que hace falta y se reutiliza.
let modalDescartar = null;

// Cómo se contesta a quien está esperando la respuesta del diálogo, y qué se le va a
// contestar. Solo puede haber una pregunta a la vez: mientras está abierto, su fondo impide
// pulsar cualquier otra cosa de la página.
let responderDescarte = null;
let descarteAceptado = false;

// Qué control tenía el foco cuando se abrió el diálogo, para devolvérselo al cerrarlo.
let focoPrevioDialogo = null;

// Las cabeceras de la tabla. Se cuentan para saber cuántas columnas tiene que ocupar la fila
// de mensajes y la del despliegue, en vez de escribir un 6 que habría que acordarse de cambiar
// aquí y en el HTML al añadir una columna. El ":scope >" deja fuera las cabeceras de las
// tablas de los paneles, que se insertan dentro de esta.
const CABECERAS_TABLA = cuerpoTabla.closest("table")
    .querySelectorAll(":scope > thead > tr > th");

// El texto por defecto del error del NIF/CIF, sacado del propio <template> donde está escrito.
// Se guarda al arrancar porque al mostrar el error del servidor se pisa, y al cerrar hay que
// devolverlo: copiarlo aquí a mano serían dos textos que acabarían diciendo cosas distintas.
const MENSAJE_NIF_BASE = plantillaPanelEdicion.content
    .querySelector('[name="nifCif"] ~ .invalid-feedback').textContent;

/*
 * ÚNICO estado de la pantalla. Que todo viva en un solo objeto es lo que impide que
 * el botón de dirección y las cabeceras de la tabla se contradigan: los dos leen y
 * escriben aquí, y luego se repinta todo desde este objeto.
 */
const criterios = {
    busqueda: "",
    provincia: "",
    poblacion: "",
    ordenarPor: "fecha_alta",
    direccion: "desc",
};

let paginaActual = 0;

// Peticiones de listado que hay ahora mismo en el aire. Es un contador y no un booleano
// porque una petición cancelada termina DESPUÉS de que arranque la que la sustituye: con un
// booleano, la que muere apagaría el "cargando" de la que sigue viva.
let listadosEnVuelo = 0;

// Y este cuenta TODAS las peticiones, no solo los listados: es el del velo de carga.
// Son dos contadores a propósito y no hay que unificarlos: listadosEnVuelo marca el aria-busy
// de la tabla, así que solo puede contar lo que de verdad la deja a medias; peticionesActivas
// tapa la pantalla entera, así que cuenta también las de los desplegables y las de los paneles.
// Con uno solo, rellenar un desplegable dejaría la tabla anunciada como ocupada sin serlo.
let peticionesActivas = 0;

// Temporizador que borra el aviso de la zona de estado pasado un rato.
let temporizadorAviso = null;

// Id del cliente al que hay que devolver el foco en cuanto se repinte la tabla, o null.
// Guardar destruye la fila que contenía el botón pulsado y, sin esto, el foco se iría al
// <body> y quien navegue con teclado volvería al principio de la página.
let focoPendiente = null;

/*
 * Filas con el panel desplegado: id del cliente -> { modo, borrador }.
 *
 * Es un Map y no un id suelto porque se pueden tener varias abiertas a la vez, y vive fuera
 * del DOM porque la tabla se repinta entera cada vez que se pagina, se busca o se refresca:
 * de aquí se saca qué paneles hay que volver a abrir después.
 *
 *   modo     -> "detalle" (solo lectura) o "edicion" (formulario)
 *   borrador -> lo que el usuario tuviera escrito sin guardar cuando se repintó la tabla,
 *               o null. Sin esto, buscar algo con un formulario abierto le borraría lo
 *               tecleado sin avisar.
 */
const filasDesplegadas = new Map();

/*
 * Los clientes de la página que se está viendo: id -> ClienteResponse, tal cual llegó.
 *
 * El listado ya trae TODOS los campos del cliente, los mismos que devuelve GET /cliente/{id},
 * así que al volver a abrir un panel tras repintar la tabla los datos ya están aquí. Sin esto,
 * cada tecla del buscador con tres paneles abiertos lanzaba cuatro peticiones: la del listado
 * y una por panel, todas para pintar lo que la primera acababa de traer.
 *
 * No es una caché: se tira y se rehace en cada repintado, así que nunca contiene nada más
 * viejo que la tabla que se está viendo. Al abrir un panel a mano se pinta desde aquí para no
 * hacer esperar a nadie, pero se pregunta igualmente al servidor y se pone al día si difiere
 * (ver abrirDespliegue).
 */
const clientesEnPagina = new Map();

/*
 * El alta abierta, o null si no hay ninguna: { borrador } con lo que se lleve tecleado.
 *
 * Va en su propia variable y NO en filasDesplegadas, aunque sea otro panel desplegado, por dos
 * motivos que no tienen vuelta: ese mapa se indexa por id de cliente y un cliente que todavía
 * no existe no tiene id; y el alta tampoco tiene fila de cliente encima, así que filaViva,
 * panelDe, modoDe y marcarFila —que buscan una tr.fila-cliente— no le sirven de nada.
 *
 * Meter un id falso (un 0 centinela) parecía más corto y es peor: se colaría en esas cuatro
 * funciones, que lo darían por un cliente de verdad y fallarían en silencio.
 */
let altaAbierta = null;

// El sufijo de los id del formulario del alta. Los de la edición llevan el id del cliente, que
// siempre es un número, así que "nuevo" no puede chocar con ninguno.
const SUFIJO_ALTA = "nuevo";

// Los campos que se pueden editar, en un orden fijo. Se usa para leer el formulario, para
// rellenarlo y para comparar si algo ha cambiado: al recorrer siempre esta misma lista, los
// dos objetos que se comparan salen con las claves en el mismo orden.
const CAMPOS_EDITABLES = ["nombre", "nifCif", "email", "telefono",
    "direccion", "codigoPostal", "poblacion", "provincia"];

// Los que la BD admite a NULL (el resto son NOT NULL y el formulario los exige).
const CAMPOS_OPCIONALES = ["email", "telefono", "codigoPostal"];

// Todo lo que un cliente puede traer distinto de una vez a otra. La fecha de alta no se edita
// desde aquí, pero se compara igual: si cambiara, la columna "Alta" tendría que enseñarlo.
const CAMPOS_CLIENTE = [...CAMPOS_EDITABLES, "fechaAlta"];

/*
 * Etiqueta e icono del botón según lo que se esté viendo AHORA. Decir "Más recientes"
 * o "A → Z" es mucho más claro que un "asc/desc" genérico, porque nombra el resultado
 * y no el mecanismo.
 */
const ETIQUETAS_ORDEN = {
    "fecha_alta|desc": { texto: "Más recientes", icono: "fa-arrow-down-wide-short" },
    "fecha_alta|asc": { texto: "Más antiguos", icono: "fa-arrow-up-short-wide" },
    "nombre|asc": { texto: "A → Z", icono: "fa-arrow-down-a-z" },
    "nombre|desc": { texto: "Z → A", icono: "fa-arrow-up-z-a" },
};

// Sentido natural de cada columna al empezar a ordenar por ella: las fechas se miran
// de la más nueva a la más vieja, y los nombres de la A a la Z.
const DIRECCION_POR_DEFECTO = {
    fecha_alta: "desc",
    nombre: "asc",
};

/*
 * Petición en vuelo de cada "canal" (listado, provincias, poblaciones). Al lanzar una
 * nueva petición de un canal se cancela la anterior DEL MISMO canal.
 *
 * Hacen falta canales separados y no un único controlador compartido: al cambiar de
 * provincia se piden a la vez las poblaciones y el listado, y con un solo controlador
 * cada una abortaría a la otra.
 */
const peticionesEnVuelo = {};

/**
 * Pide un JSON al backend cancelando la petición anterior del mismo canal.
 *
 * El debounce reduce las peticiones, pero no evita que dos respuestas se crucen: si
 * escribes "gar", sale la petición, sigues hasta "garcia" y la respuesta de "gar" llega
 * la última, pintaría la tabla con resultados que no son los del texto del buscador.
 * Cancelando la anterior, esa respuesta tardía nunca llega a usarse.
 *
 * @param {string} canal nombre del flujo de peticiones ("listado", "provincias"...)
 * @param {string} url la URL a pedir
 * @return {Promise<Object>} el JSON de la respuesta
 * @throws {Error} si el servidor responde con un código que no es 2xx; el error lleva ese
 *         código en la propiedad `estado`
 */
async function pedirJson(canal, url) {
    peticionesEnVuelo[canal]?.abort();
    const controlador = new AbortController();
    peticionesEnVuelo[canal] = controlador;

    mostrarCarga();

    try {
        const respuesta = await fetch(url, { signal: controlador.signal });

        // fetch NO lanza error con códigos 4xx/5xx: hay que comprobarlo a mano.
        if (!respuesta.ok) {
            const error = new Error(`El servidor respondió ${respuesta.status}`);

            // El código va aparte del mensaje porque hay quien necesita distinguirlos: un 404
            // al comprobar un detalle significa "lo han borrado" y se trata distinto del
            // resto. Leerlo del texto del mensaje sería frágil.
            error.estado = respuesta.status;
            throw error;
        }
        return await respuesta.json();
    } finally {
        cerrarCanal(canal, controlador);
        ocultarCarga();
    }
}

/**
 * ¿Este error es de una petición que hemos cancelado nosotros?
 *
 * Cancelar es algo que provocamos a propósito, no un fallo: si lo tratáramos como tal,
 * el usuario vería el mensaje de error justo mientras escribe en el buscador.
 *
 * @param {Error} error el error capturado
 * @return {boolean} true si lo canceló el AbortController de su canal, no el servidor
 */
function esCancelacion(error) {
    return error.name === "AbortError";
}

/**
 * Pide una página de clientes al backend (con los criterios actuales) y repinta todo.
 * @param {number} pagina índice de la página a cargar (empieza en 0)
 */
async function cargarClientes(pagina) {
    // Atenúa la tabla y avisa a los lectores de pantalla de que lo que hay no es definitivo:
    // sin esto se siguen viendo los datos de la página anterior como si fueran los nuevos.
    // El aria-busy va en el contenedor y no en el <tbody> porque es el contenedor el que ya
    // es una región anunciable (role="region" + aria-labelledby): en el cuerpo de la tabla,
    // el atributo no describe ninguna zona que el lector de pantalla reconozca como tal.
    listadosEnVuelo++;
    contenedorTabla.setAttribute("aria-busy", "true");

    try {
        // URLSearchParams se encarga de codificar los valores: un término con '&',
        // '%' o tildes viaja entero y no rompe la URL.
        const parametros = new URLSearchParams({
            pagina: pagina,
            tamano: TAMANO_PAGINA,
            ordenarPor: criterios.ordenarPor,
            direccion: criterios.direccion,
        });

        // Los criterios vacíos NO se mandan: el backend entiende "parámetro ausente"
        // como "no filtres por esto".
        if (criterios.busqueda) parametros.set("busqueda", criterios.busqueda);
        if (criterios.provincia) parametros.set("provincia", criterios.provincia);
        if (criterios.poblacion) parametros.set("poblacion", criterios.poblacion);

        const datos = await pedirJson("listado", `${API_LISTAR_PAGINA}?${parametros}`);

        // Página que ya no existe: se pedía la 4 y entre medias han borrado clientes hasta
        // dejar 3. El backend responde con la página vacía y el total real (no reencamina
        // solo, porque el paginado no puede adivinar si eso es un error o no), así que la
        // corrección la hacemos aquí: se pide la última que sí existe. Sin esto, la tabla se
        // queda vacía diciendo "Página 4 de 3" y sin manera obvia de salir de ahí.
        const hayQueRetroceder = !datos.contenido?.length
            && datos.totalPaginas > 0
            && pagina > datos.totalPaginas - 1;

        if (hayQueRetroceder) {
            await cargarClientes(datos.totalPaginas - 1);
            return;
        }

        paginaActual = datos.paginaActual;
        pintarFilas(datos.contenido);
        pintarPaginacion(datos);
    } catch (error) {
        // Si la hemos cancelado nosotros, ya viene otra petición en camino: salimos sin
        // tocar la tabla para no borrar lo que hay mientras llega la respuesta buena.
        if (esCancelacion(error)) return;
        mostrarError(error);
    } finally {
        listadosEnVuelo--;
        if (listadosEnVuelo === 0) {
            contenedorTabla.setAttribute("aria-busy", "false");
        }
    }
}

/**
 * Vacía el <tbody> y lo rellena clonando el <template> por cada cliente.
 * @param {Array<Object>} clientes lista de ClienteResponse
 */
function pintarFilas(clientes) {
    // Antes de vaciar la tabla: guardar lo que hubiera a medio escribir en un formulario
    // abierto y destruir los avisos emergentes de las filas que van a desaparecer (si no,
    // se quedan flotando sobre la pantalla y además Bootstrap sigue guardando una
    // referencia a cada fila borrada).
    guardarBorradores();
    limpiarPistas();

    cuerpoTabla.replaceChildren(); // limpia la tabla sin usar innerHTML

    // Los datos de esta página, para que los paneles que se reabran no vuelvan a pedirlos.
    // Se vacía aquí, antes de rellenarlo: si no, iría acumulando los clientes de todas las
    // páginas que se hayan visitado y acabaría sirviendo datos de hace diez búsquedas.
    clientesEnPagina.clear();

    if (!Array.isArray(clientes) || clientes.length === 0) {
        // El mensaje cambia según el motivo: "no hay clientes" y "tu búsqueda no
        // encuentra nada" son cosas distintas y el usuario reacciona distinto a cada una.
        // En el segundo caso, la salida se ofrece ahí mismo.
        const filtrando = hayCriteriosActivos();
        const mensaje = filtrando
            ? "No hay clientes que coincidan con la búsqueda."
            : "No hay clientes que mostrar.";

        mostrarMensaje(mensaje, { conLimpiar: filtrando });

        // Y se anuncia, por el mismo motivo que el error de carga: escrito dentro del <tbody>
        // no lo lee nadie, porque no es una región viva. Quien no ve la pantalla solo oía el
        // "Sin resultados" del pie, que no distingue una tabla vacía de una búsqueda sin
        // coincidencias ni deja pista de que hay un botón para quitar los filtros.
        anunciar(filtrando ? `${mensaje} Puedes quitar los filtros.` : mensaje);

        // Aunque no haya a quién devolvérselo, hay que descartar la marca: si no, el foco
        // daría un salto inesperado en el siguiente repintado, que no tiene nada que ver.
        devolverFoco();
        return;
    }

    for (const [indice, cliente] of clientes.entries()) {
        clientesEnPagina.set(cliente.idCliente, cliente);

        // Clonamos el contenido del template (un <tr> completo con sus celdas).
        const fila = plantillaFila.content.cloneNode(true);

        pintarCeldasFila(fila, cliente);

        // Guardamos el id en la fila por si los botones de acción lo necesitan.
        const filaCliente = fila.querySelector("tr");
        filaCliente.dataset.clienteId = cliente.idCliente;

        // El rayado lo marcamos aquí, por CLIENTE. Con el table-striped de Bootstrap
        // (nth-of-type) cada panel de detalle insertado invierte la alternancia de todo lo
        // que viene detrás, y la tabla acaba con dos filas blancas seguidas.
        filaCliente.classList.toggle("fila-par", indice % 2 === 1);

        // Deja la fila en estado "cerrada": aria-expanded a false, y el nombre y el aviso de
        // cada botón con el cliente dentro ("Editar cliente García S.L.").
        marcarFila(filaCliente, null);

        cuerpoTabla.appendChild(fila);
    }

    reabrirDespliegues();
    reabrirAlta();
    devolverFoco();
}

/**
 * Escribe los datos del cliente en las celdas de su fila.
 *
 * Está aparte de pintarFilas porque también se usa al comprobar que un cliente no ha cambiado
 * mientras se miraba: si ha cambiado, la fila tiene que enseñar lo nuevo igual que el panel, o
 * quedaría un nombre en la tabla y otro distinto justo debajo.
 *
 * @param {DocumentFragment|Element} fila la fila (o el clon del template) que se rellena
 * @param {Object} cliente el ClienteResponse que se pinta
 */
function pintarCeldasFila(fila, cliente) {
    // textContent escapa el texto: seguro frente a nombres con < o &.
    fila.querySelector(".cliente-nombre").textContent = cliente.nombre;
    fila.querySelector(".cliente-cif").textContent = cliente.nifCif;
    pintarEnlace(fila.querySelector(".cliente-email"), cliente.email,
        // Se codifican SOLO '?', '&' y '#', que son los tres caracteres que en un mailto
        // dejan de formar parte de la dirección y pasan a añadir cabeceras: un correo
        // guardado como "a@b.com?bcc=otro@c.com" mandaría una copia oculta que nadie ha
        // escrito. La arroba y el punto se dejan tal cual a propósito: codificarlo todo
        // funciona, pero deja una dirección ilegible en la barra de estado del navegador.
        (valor) => `mailto:${valor.replace(/[?&#]/g, encodeURIComponent)}`,
        "Escribir a este correo");
    pintarEnlace(fila.querySelector(".cliente-telefono"), cliente.telefono,
        // Solo dígitos y el '+' del prefijo internacional: es lo único que se puede marcar.
        // Antes se quitaban únicamente los espacios, así que cualquier otro carácter que
        // hubiera en la columna acababa dentro del tel: sin que se supiera qué iba a hacer
        // el marcador con él.
        (valor) => `tel:${valor.replace(/[^\d+]/g, "")}`,
        "Llamar a este número");
    fila.querySelector(".cliente-alta").textContent = formatearFecha(cliente.fechaAlta);
}

/**
 * Devuelve el foco al botón de editar del cliente que se acaba de guardar.
 *
 * Al repintar, la fila que tenía el foco deja de existir y el navegador lo manda al <body>:
 * quien navegue con teclado se encontraría de vuelta al principio de la página después de
 * cada guardado. Se llama tras repintar porque hasta ese momento el botón nuevo no existe.
 */
function devolverFoco() {
    if (focoPendiente === null) return;

    const fila = cuerpoTabla.querySelector(`tr[data-cliente-id="${focoPendiente}"]`);
    focoPendiente = null;

    // Puede no estar: el cliente se ha quedado en otra página, o ya no cumple el filtro. En
    // ese caso no forzamos el foco a ningún sitio raro; el aviso de "Cliente guardado" ya se
    // ha anunciado por su cuenta.
    fila?.querySelector(".btn-editar")?.focus();
}

/**
 * Cuenta lo que acaba de pasar: siempre a quien no ve la pantalla, y además en la franja de
 * avisos cuando no haya otro sitio donde se vea.
 *
 * Son dos elementos y no uno porque resuelven cosas distintas. #anuncios está siempre en el
 * documento, vacío e invisible, y es lo único que lee el lector de pantalla: una región que
 * aparece con el mensaje ya dentro no se anuncia, porque lo que se vigila es el cambio de
 * contenido de algo que ya estaba. Y al ser invisible puede repetir un texto que ya se ve en
 * su sitio (el mensaje de la tabla vacía, el del panel) sin que salga escrito dos veces.
 *
 * @param {string} texto lo que se cuenta
 * @param {Object} [opciones]
 * @param {boolean} [opciones.visible=false] si además se escribe en la franja de avisos.
 *        Para lo que no tiene otro sitio donde verse, como el "Cliente guardado" de una fila
 *        que el refresco se lleva por delante
 * @param {boolean} [opciones.esError=false] si es un problema y no una confirmación
 */
function anunciar(texto, { visible = false, esError = false } = {}) {
    // Se vacía y se reescribe en el fotograma siguiente, en vez de asignar el texto sin más.
    // Lo que el lector de pantalla vigila es el CAMBIO de contenido, así que un mensaje
    // idéntico al anterior no se leería: guardar dos clientes seguidos anunciaba el primero y
    // callaba el segundo, que es justo cuando hace falta la confirmación.
    regionAnuncios.textContent = "";
    requestAnimationFrame(() => {
        regionAnuncios.textContent = texto;
    });

    if (!visible) return;

    clearTimeout(temporizadorAviso);
    avisoClientes.classList.toggle("aviso-error", esError);
    avisoClientes.textContent = texto;

    // Se borra solo: es información de "ha pasado esto ahora", y dejarla fija acaba
    // confundiendo (un "Cliente guardado" de hace diez minutos parece de la última acción).
    // Se vacía en vez de esconderse: la hoja de estilos ya oculta la franja vacía.
    temporizadorAviso = setTimeout(() => {
        avisoClientes.textContent = "";
    }, DURACION_AVISO_MS);
}

/**
 * Pinta una celda como enlace útil (escribir un correo, llamar por teléfono), o con un guion
 * si no hay dato.
 *
 * El esquema (mailto:, tel:) lo construye SIEMPRE el código, nunca el valor que llega del
 * servidor: así un dato manipulado no puede colar un href de tipo javascript:.
 *
 * @param {Element} celda la celda de la tabla
 * @param {string|null} valor el dato del cliente
 * @param {Function} construirHref función que arma el href a partir del valor
 * @param {string} pista qué dice el aviso emergente al pasar el ratón. Es el único que hay en
 *        esta celda —el de la fila vive en la del nombre— y explica lo que hace el enlace, que
 *        no es desplegar el detalle sino escribir un correo o llamar por teléfono
 */
function pintarEnlace(celda, valor, construirHref, pista) {
    const texto = (valor ?? "").trim();
    if (!texto) {
        celda.textContent = "—";
        return;
    }

    const enlace = document.createElement("a");
    enlace.className = "enlace-celda";
    enlace.href = construirHref(texto);
    enlace.textContent = texto;
    enlace.dataset.bsTitle = pista;
    celda.replaceChildren(enlace);
}

/**
 * Pasa la fecha ISO que manda el backend (2026-01-10) al formato español (10/1/2026).
 *
 * Se formatea en UTC a propósito. El backend manda un día suelto, sin hora, y el navegador lo
 * entiende como medianoche UTC: al pasarlo a la hora local de un huso negativo, esa medianoche
 * cae en el día anterior y la fecha de alta se vería con un día menos.
 *
 * @param {string|null} fechaIso fecha en formato ISO, o null si el cliente no la tiene
 * @return {string} la fecha formateada, o un guion si no hay fecha
 */
function formatearFecha(fechaIso) {
    if (!fechaIso) {
        return "—";   // fecha_alta admite NULL en base de datos
    }
    return new Date(fechaIso).toLocaleDateString("es-ES", { timeZone: "UTC" });
}

/**
 * Activa/desactiva los botones y actualiza el texto según los metadatos de la página.
 * @param {Object} datos PaginaClienteResponse
 */
function pintarPaginacion(datos) {
    // El backend nos dice si cada página existe, así el usuario no se sale del rango.
    btnAnterior.disabled = !datos.hayAnterior;
    btnSiguiente.disabled = !datos.haySiguiente;

    infoPagina.textContent = datos.totalElementos === 0
        ? "Sin resultados"
        : `Página ${datos.paginaActual + 1} de ${datos.totalPaginas} · ${datos.totalElementos} clientes`;
}

/**
 * Repinta TODOS los controles de ordenación (el selector, el botón y las flechas de las
 * cabeceras) leyendo del estado.
 *
 * Es una sola función a propósito: el usuario puede cambiar el orden desde el botón o
 * desde la cabecera de la tabla, y si cada camino actualizara su propio control, uno de
 * los dos acabaría mostrando algo distinto de lo que realmente se está viendo.
 */
function pintarControlesOrden() {
    const { ordenarPor, direccion } = criterios;
    const etiqueta = ETIQUETAS_ORDEN[`${ordenarPor}|${direccion}`];

    selectOrdenarPor.value = ordenarPor;
    etiquetaDireccion.textContent = etiqueta.texto;
    iconoDireccion.className = `fa-solid ${etiqueta.icono}`;

    // Cada cabecera muestra si es la columna activa y en qué sentido.
    cabecerasOrdenables.forEach((cabecera) => {
        const esColumnaActiva = cabecera.dataset.columna === ordenarPor;
        const flecha = cabecera.querySelector(".flecha-orden");

        cabecera.classList.toggle("activa", esColumnaActiva);

        if (esColumnaActiva) {
            flecha.className = `fa-solid flecha-orden ${direccion === "asc" ? "fa-arrow-up-long" : "fa-arrow-down-long"}`;
        } else {
            flecha.className = "fa-solid fa-sort flecha-orden";
        }

        // aria-sort es lo que hace que un lector de pantalla anuncie por qué columna
        // está ordenada la tabla; va en el <th>, no en el botón.
        cabecera.closest("th").setAttribute("aria-sort",
            esColumnaActiva ? (direccion === "asc" ? "ascending" : "descending") : "none");
    });
}

/**
 * Cuántas columnas se están viendo ahora mismo.
 *
 * Se cuentan en vez de usar el total porque en móvil hay dos que se ocultan con las clases
 * d-none de Bootstrap: con el número entero, la fila de mensajes y la del panel se extienden
 * sobre dos columnas que no existen y su contenido se centra respecto a un ancho mayor que el
 * de la tabla, saliéndose por la derecha.
 *
 * @return {number} el número de cabeceras visibles; el total si no hubiera ninguna, que es lo
 *         que pasa si la tabla entera está oculta y un colspan de 0 no es válido
 */
function columnasVisibles() {
    // offsetParent es null en un elemento con display:none (y en toda su descendencia), que es
    // justo lo que hace la clase d-none.
    const visibles = [...CABECERAS_TABLA].filter((cabecera) => cabecera.offsetParent !== null);
    return visibles.length || CABECERAS_TABLA.length;
}

/**
 * ¿Hay algún filtro o búsqueda puesto? La ordenación no cuenta: siempre hay una, así que
 * si contase, el botón de limpiar y el contador estarían activos desde que se abre la página.
 *
 * @return {boolean} true si hay búsqueda, provincia o población
 */
function hayCriteriosActivos() {
    return Boolean(criterios.busqueda || criterios.provincia || criterios.poblacion);
}

/**
 * Aplica un cambio de criterios: repinta los controles y VUELVE A LA PÁGINA 1.
 *
 * El reinicio de página es imprescindible: si estás en la página 7 y filtras por una
 * provincia con 12 clientes, esa página ya no existe y verías una tabla vacía sin
 * entender por qué.
 */
function aplicarCriterios() {
    pintarControlesOrden();
    pintarEstadoFiltros();
    cargarClientes(0);
}

/**
 * Marca qué controles tienen un filtro puesto y cuántos son en total.
 *
 * Es la única parte de la barra donde el color dice algo en vez de decorar: con tres
 * controles que por fuera son idénticos estén o no usados, la pregunta "¿por qué solo salen
 * 4 clientes?" obligaba a abrir los desplegables uno a uno.
 */
function pintarEstadoFiltros() {
    const activos = [criterios.busqueda, criterios.provincia, criterios.poblacion]
        .filter(Boolean).length;

    contenedorBuscador.classList.toggle("activo", Boolean(criterios.busqueda));
    selectProvincia.closest(".control-filtro")
        .classList.toggle("activo", Boolean(criterios.provincia));
    selectPoblacion.closest(".control-filtro")
        .classList.toggle("activo", Boolean(criterios.poblacion));

    // La cuenta se ve en un globo junto a "Limpiar"; para quien no lo ve, va en el nombre
    // accesible del botón, porque el globo es aria-hidden y diría un número suelto.
    //
    // El nombre EMPIEZA por "Limpiar", que es lo que pone en el botón: si se sustituyera por
    // otra frase, quien maneja el ordenador por voz diría "pulsa Limpiar" y no pasaría nada
    // (criterio 2.5.3 de WCAG, el nombre tiene que contener el texto visible).
    contadorFiltros.textContent = String(activos);
    contadorFiltros.hidden = activos === 0;
    btnLimpiar.setAttribute("aria-label", activos === 0
        ? "Limpiar los filtros y la ordenación"
        : `Limpiar los ${activos} filtros activos y la ordenación`);
}

/**
 * Pinta una fila que ocupa toda la tabla con un mensaje informativo.
 * @param {string} texto el mensaje
 * @param {Object} opciones
 * @param {boolean} opciones.conLimpiar añade el botón de quitar filtros: cuando la tabla
 *        sale vacía por un filtro, la salida tiene que estar donde se ve el problema
 */
function mostrarMensaje(texto, { conLimpiar = false } = {}) {
    // Este es el OTRO sitio que vacía la tabla, además de pintarFilas, así que lo que hay que
    // rescatar antes de vaciarla hay que rescatarlo también aquí. Cuando se llega desde
    // pintarFilas las dos llamadas se repiten, y no pasa nada porque son idempotentes; cuando
    // se llega desde mostrarError, este es el ÚNICO sitio donde se hacen.
    //
    // Los avisos emergentes, porque el globo que hubiera abierto se queda flotando sobre el
    // mensaje: con container "body" cuelga del <body>, y como detrás no queda ninguna fila que
    // pulsar, no hay forma de provocar la limpieza hasta la siguiente búsqueda.
    //
    // Y los borradores, porque el alta va a volver a pintarse abajo y lo haría con lo que
    // tuviera guardado de antes, perdiendo lo tecleado desde entonces.
    limpiarPistas();
    guardarBorradores();
    cuerpoTabla.replaceChildren();
    const fila = document.createElement("tr");
    const celda = document.createElement("td");
    celda.colSpan = columnasVisibles();
    celda.className = "text-center text-muted py-4";
    celda.textContent = texto;

    if (conLimpiar) {
        const boton = document.createElement("button");
        boton.type = "button";
        boton.className = "btn btn-sm btn-link";
        boton.textContent = "Quitar los filtros";
        boton.addEventListener("click", limpiarCriterios);
        celda.append(" ", boton);
    }

    fila.appendChild(celda);
    cuerpoTabla.appendChild(fila);

    // Y el alta vuelve arriba: sin esto, buscar algo que no existe se llevaba por delante el
    // formulario a medio rellenar. reabrirAlta hace prepend, así que queda por encima del
    // mensaje, que es donde tiene sentido: el formulario es lo que el usuario está usando y
    // el "no hay coincidencias" habla de la tabla que tiene debajo.
    reabrirAlta();
}

/** Muestra el error al usuario y lo deja en consola para depurar. */
function mostrarError(error) {
    console.error("No se pudieron cargar los clientes:", error);

    const mensaje = "No se pudieron cargar los clientes. Inténtalo de nuevo.";

    // El texto va donde se estaría mirando, dentro de la tabla, y se anuncia por la región
    // invisible: escrito ahí dentro no lo lee nadie, porque el <tbody> no es una región viva
    // y quien no ve la pantalla se quedaría esperando unos datos que no van a llegar.
    mostrarMensaje(mensaje);
    anunciar(mensaje);

    btnAnterior.disabled = true;
    btnSiguiente.disabled = true;
}

// --- Desplegables de provincia y población (en cascada) ---

/**
 * Rellena un <select> con las opciones recibidas, conservando la primera (el "Todas...").
 * @param {HTMLSelectElement} select el desplegable a rellenar
 * @param {Array<string>} valores las opciones a añadir
 */
function rellenarSelect(select, valores) {
    // Quitamos todo menos la primera opción, que es la de "sin filtro".
    while (select.options.length > 1) {
        select.remove(1);
    }
    for (const valor of valores) {
        const opcion = document.createElement("option");
        opcion.value = valor;
        opcion.textContent = valor;   // textContent: nunca innerHTML
        select.appendChild(opcion);
    }
}

/** Carga las provincias del backend en su desplegable. */
async function cargarProvincias() {
    try {
        rellenarSelect(selectProvincia, await pedirJson("provincias", API_PROVINCIAS));
    } catch (error) {
        if (esCancelacion(error)) return;
        console.error("No se pudieron cargar las provincias:", error);
    }
}

/**
 * Carga las poblaciones en su desplegable. Si hay provincia elegida, solo las de esa
 * provincia (por eso es "en cascada"): ofrecer poblaciones de otras provincias daría
 * combinaciones que no devuelven ningún cliente.
 * @param {string} provincia provincia elegida, o cadena vacía para todas
 */
async function cargarPoblaciones(provincia) {
    try {
        const url = provincia
            ? `${API_POBLACIONES}?${new URLSearchParams({ provincia })}`
            : API_POBLACIONES;
        rellenarSelect(selectPoblacion, await pedirJson("poblaciones", url));
    } catch (error) {
        // Si el usuario cambia de provincia deprisa, la petición anterior se cancela:
        // sin esto, el desplegable podría quedarse con las poblaciones de la provincia
        // que ya no está seleccionada.
        if (esCancelacion(error)) return;
        console.error("No se pudieron cargar las poblaciones:", error);
    }
}

// --- Enlazado de los controles ---

// Buscador con espera: reiniciamos el temporizador en cada tecla y solo consultamos
// cuando el usuario lleva ESPERA_TECLEO_MS sin escribir.
let temporizadorBusqueda = null;
inputBuscador.addEventListener("input", () => {
    clearTimeout(temporizadorBusqueda);
    temporizadorBusqueda = setTimeout(buscar, ESPERA_TECLEO_MS);
});

// Enter es "ya he terminado de escribir": esperar los 300 ms de rigor después de eso se siente
// como que la tecla no ha hecho nada. No hay submit que evitar, el buscador no está en un
// <form>, pero sí hay que cancelar la espera o la búsqueda se lanzaría dos veces.
inputBuscador.addEventListener("keydown", (evento) => {
    if (evento.key === "Enter") {
        clearTimeout(temporizadorBusqueda);
        buscar();
    }
});

/** Lleva al estado lo que hay escrito en el buscador y recarga la tabla. */
function buscar() {
    criterios.busqueda = inputBuscador.value.trim();
    aplicarCriterios();
}

selectProvincia.addEventListener("change", async () => {
    criterios.provincia = selectProvincia.value;

    // Al cambiar de provincia, la población elegida deja de tener sentido: se limpia
    // (si no, quedaría un filtro "Valencia + Madrid" que no devuelve nada).
    criterios.poblacion = "";
    selectPoblacion.value = "";
    await cargarPoblaciones(criterios.provincia);

    aplicarCriterios();
});

selectPoblacion.addEventListener("change", () => {
    criterios.poblacion = selectPoblacion.value;
    aplicarCriterios();
});

selectOrdenarPor.addEventListener("change", () => {
    criterios.ordenarPor = selectOrdenarPor.value;
    // Al cambiar de columna estrenamos su sentido natural (fechas: nuevas primero;
    // nombres: A → Z), que es lo que se espera la primera vez.
    criterios.direccion = DIRECCION_POR_DEFECTO[criterios.ordenarPor];
    aplicarCriterios();
});

// El botón invierte el sentido sin tocar la columna.
btnDireccion.addEventListener("click", () => {
    criterios.direccion = criterios.direccion === "asc" ? "desc" : "asc";
    aplicarCriterios();
});

// Cabeceras de la tabla: mismo comportamiento que en cualquier tabla ordenable.
cabecerasOrdenables.forEach((cabecera) => {
    cabecera.addEventListener("click", () => {
        const columna = cabecera.dataset.columna;

        if (criterios.ordenarPor === columna) {
            // Ya ordenamos por ella: el segundo clic invierte el sentido.
            criterios.direccion = criterios.direccion === "asc" ? "desc" : "asc";
        } else {
            criterios.ordenarPor = columna;
            criterios.direccion = DIRECCION_POR_DEFECTO[columna];
        }

        aplicarCriterios();
    });
});

/**
 * Deja la pantalla como recién cargada: sin búsqueda, sin filtros y con la ordenación por
 * defecto. Está aparte del botón porque también se ofrece desde la tabla vacía.
 */
async function limpiarCriterios() {
    criterios.busqueda = "";
    criterios.provincia = "";
    criterios.poblacion = "";
    criterios.ordenarPor = "fecha_alta";
    criterios.direccion = "desc";

    inputBuscador.value = "";
    selectProvincia.value = "";
    selectPoblacion.value = "";
    await cargarPoblaciones("");

    aplicarCriterios();
}

btnLimpiar.addEventListener("click", limpiarCriterios);

// --- Paginación ---
btnAnterior.addEventListener("click", () => cargarClientes(paginaActual - 1));
btnSiguiente.addEventListener("click", () => cargarClientes(paginaActual + 1));

// --- Refresco automático tras crear/editar/eliminar ---
// Cuando otro compañero cambie un cliente, avisa disparando este evento y recargamos la
// página actual (así la tabla siempre refleja la BD, sin que su código conozca el nuestro).
// Ellos solo hacen: document.dispatchEvent(new CustomEvent('clientes:cambiaron'));
document.addEventListener("clientes:cambiaron", () => cargarClientes(paginaActual));

/* ============================================================================
 * DESPLIEGUE DE LA FILA: ver detalle y editar
 * ============================================================================
 *
 * Al pulsar una fila (o el botón del ojo) se inserta DEBAJO una fila hermana que ocupa todas
 * las columnas y enseña los campos que no caben en la tabla. Con el lápiz se abre la misma
 * fila, pero con los campos editables.
 *
 * Un solo panel por cliente y con un modo, en vez de dos filas independientes: así es
 * imposible tener a la vez el detalle y el formulario del mismo cliente enseñando cosas
 * distintas, y pasar de uno a otro es cambiar el contenido, no cerrar y abrir.
 *
 * Abrir un panel a mano pide los datos al backend (GET /cliente/{id}), también al cambiar de
 * modo. Es la misma decisión que ya tomó el listado —recargar en vez de guardar copias—: un
 * detalle guardado de antes puede enseñar algo que otro usuario ya cambió, y en el formulario
 * sería peor todavía, porque se guardaría encima sin haberlo visto.
 *
 * Reabrir un panel tras repintar la tabla es el caso contrario: los datos acaban de llegar en
 * la respuesta del listado, que trae los mismos campos, así que se pintan desde ahí. Pedirlos
 * otra vez sería preguntar por lo que ya se sabe, y multiplicado por cada panel abierto y cada
 * tecla del buscador.
 */

/*
 * REGLA: después de un `await`, nada de lo que se capturó antes se da por vivo.
 *
 * La tabla se repinta entera al buscar, al paginar y al recibir `clientes:cambiaron`, así que
 * cualquier referencia a un <tr> o a un <form> puede haber quedado apuntando a un nodo que ya
 * no está en el documento. Escribir en él no da error: simplemente no lo ve nadie, que es peor,
 * porque el usuario se queda pensando que ha pasado algo que no ha pasado.
 *
 * Por eso, tras esperar al servidor se vuelve a buscar por id con estas dos funciones en vez de
 * reutilizar la variable de antes.
 */

/**
 * La fila de un cliente tal y como está AHORA en el documento.
 *
 * @param {number} idCliente el cliente que se busca
 * @return {HTMLTableRowElement|null} su fila, o null si ya no está en la página (se ha ido a
 *         otra por la ordenación, ya no cumple el filtro, o lo han borrado)
 */
function filaViva(idCliente) {
    return cuerpoTabla.querySelector(`tr.fila-cliente[data-cliente-id="${idCliente}"]`);
}

/**
 * El formulario de edición de un cliente tal y como está AHORA en el documento.
 *
 * @param {number} idCliente el cliente que se busca
 * @return {HTMLFormElement|null} su formulario, o null si su panel ya no está abierto
 */
function formularioVivo(idCliente) {
    return cuerpoTabla.querySelector(`.formulario-edicion[data-cliente-id="${idCliente}"]`);
}

/** ¿Qué panel tiene abierto esta fila? "detalle", "edicion" o null si está cerrada. */
function modoDe(fila) {
    return filasDesplegadas.get(Number(fila.dataset.clienteId))?.modo ?? null;
}

/**
 * El panel desplegado de una fila.
 *
 * @param {HTMLTableRowElement} fila la fila del cliente
 * @return {HTMLElement|null} el panel, o null si la fila está cerrada
 */
function panelDe(fila) {
    const hermana = fila.nextElementSibling;
    return hermana?.classList.contains("fila-despliegue") ? hermana : null;
}

/**
 * Abre el panel de una fila en el modo indicado (o le cambia el modo si ya estaba abierto).
 *
 * Se pinta **al momento** con el cliente que trajo el listado, que tiene los mismos campos que
 * `GET /cliente/{id}`, y solo después se comprueba contra el servidor que sigue siendo lo que
 * hay en la base de datos. Esperar a esa comprobación para enseñar algo era medio segundo de
 * "cargando" para dibujar, casi siempre, lo que ya estaba en memoria.
 *
 * La comprobación no sobra: la tabla puede llevar cargada un buen rato, y abrir un cliente
 * —sobre todo para editarlo— es el momento en que sus datos tienen que estar al día. El
 * formulario manda de vuelta los ocho campos, así que trabajar sobre una foto vieja revierte
 * sin querer lo que haya cambiado otro.
 *
 * @param {HTMLTableRowElement} fila la fila del cliente
 * @param {string} modo "detalle" o "edicion"
 * @param {Object} opciones
 * @param {boolean} opciones.animar false al reabrir tras repintar la tabla: el panel ya
 *        estaba desplegado y volver a animarlo se vería como un parpadeo
 * @param {boolean} opciones.enfocar false al reabrir por lo mismo: llevarse el foco mientras
 *        el usuario escribe en el buscador le sacaría el cursor de donde está
 * @param {Object|null} opciones.cliente los datos con los que pintar. Si no vienen, se cogen
 *        del listado y, si tampoco estuvieran, se piden al backend
 * @param {boolean} opciones.revalidar false al reabrir tras repintar: los datos acaban de
 *        llegar con el listado y volver a pedirlos uno por uno es la ráfaga de peticiones que
 *        se quitó en su día
 */
async function abrirDespliegue(fila, modo,
    { animar = true, enfocar = true, cliente = null, revalidar = true } = {}) {

    const idCliente = Number(fila.dataset.clienteId);

    // El borrador se conserva al cambiar de modo: si vuelves de detalle a edición, sigue lo
    // que habías escrito.
    const borrador = filasDesplegadas.get(idCliente)?.borrador ?? null;
    filasDesplegadas.set(idCliente, { modo, borrador });

    const panel = obtenerPanel(fila, animar);
    const contenido = panel.querySelector(".despliegue-contenido");
    marcarFila(fila, modo);

    const conocido = cliente ?? clientesEnPagina.get(idCliente) ?? null;

    if (conocido) {
        pintarContenidoPanel(contenido, modo, conocido, borrador, enfocar);

        if (!revalidar) {
            // Si quedaba una petición en el aire de una apertura anterior, se cancela: su
            // respuesta ya no aporta nada y llegaría a pintar por encima de esto.
            peticionesEnVuelo[`detalle-${idCliente}`]?.abort();
            return;
        }
    } else if (contenido.childElementCount === 0) {
        // Sin nada que enseñar todavía. Es un caso de reserva: la fila viene del listado, así
        // que su cliente está en el mapa salvo que algo haya ido muy mal.
        pintarCargando(contenido);
    } else {
        // Al cambiar de modo sí hay contenido, y sustituirlo por un "cargando" encogería el
        // panel para volver a estirarlo un instante después: se deja atenuado.
        contenido.classList.add("cargando");
    }

    try {
        const recibido = await pedirJson(`detalle-${idCliente}`, `${API_CLIENTE}/${idCliente}`);

        // Mientras llegaba la respuesta la fila ha podido cerrarse, o la tabla repintarse.
        const estado = filasDesplegadas.get(idCliente);
        if (!estado || !panel.isConnected) return;

        if (conocido) {
            conciliar(fila, contenido, estado.modo, recibido);
        } else {
            pintarContenidoPanel(contenido, estado.modo, recibido, estado.borrador, enfocar);
        }
    } catch (error) {
        // Cancelada por nosotros (se cerró o se cambió de modo deprisa): ya viene otra.
        if (esCancelacion(error)) return;

        if (conocido) {
            fallaComprobacion(idCliente, error);
        } else {
            pintarErrorPanel(contenido, error);
        }
    } finally {
        contenido.classList.remove("cargando");
    }
}

/**
 * Pone al día la fila y su panel cuando el servidor devuelve algo distinto de lo que se pintó.
 *
 * Lo normal es que no haya cambiado nada y esto no haga absolutamente nada, que es justo lo
 * que se busca: la comprobación tiene que ser invisible salvo cuando aporta.
 *
 * @param {HTMLTableRowElement} fila la fila del cliente
 * @param {Element} contenido el hueco del panel
 * @param {string} modo "detalle" o "edicion"
 * @param {Object} recibido el cliente tal y como está ahora en la base de datos
 */
function conciliar(fila, contenido, modo, recibido) {
    const anterior = clientesEnPagina.get(recibido.idCliente);
    if (anterior && mismosDatos(anterior, recibido)) return;

    clientesEnPagina.set(recibido.idCliente, recibido);

    // Los avisos emergentes de las celdas que se van a reemplazar, fuera: si no, Bootstrap se
    // queda con una instancia apuntando a un elemento que ya no está en el documento.
    limpiarPistas(fila);
    pintarCeldasFila(fila, recibido);

    // Los nombres accesibles de los botones llevan dentro el del cliente ("Editar cliente
    // García S.L."), así que si el nombre ha cambiado hay que volver a escribirlos.
    marcarFila(fila, modo);

    if (modo !== "edicion") {
        pintarPanelDetalle(contenido, recibido);
        anunciar("Los datos de este cliente han cambiado y se han actualizado.");
        return;
    }

    conciliarFormulario(contenido.querySelector(".formulario-edicion"), recibido);
}

/**
 * Mete los datos nuevos en el formulario abierto **sin pisar lo que el usuario esté
 * escribiendo**, y le cuenta lo que ha pasado.
 *
 * Un campo se considera intacto si su contenido sigue siendo el que se pintó desde la base de
 * datos; en ese caso se actualiza sin más. Si lo ha tocado, mandan sus letras: perder lo
 * escrito por un refresco que nadie ha pedido es de las cosas que más molestan de una pantalla.
 *
 * @param {HTMLFormElement} formulario el formulario de edición abierto
 * @param {Object} recibido el cliente tal y como está ahora en la base de datos
 */
function conciliarFormulario(formulario, recibido) {
    if (!formulario) return;

    const valoresNuevos = valoresDe(recibido);
    const valoresPintados = JSON.parse(formulario.dataset.valoresOriginales);
    const actualizados = [];
    const respetados = [];

    for (const campo of CAMPOS_EDITABLES) {
        if (valoresNuevos[campo] === valoresPintados[campo]) continue;

        const control = formulario.elements[campo];

        // El usuario ha escrito justo lo mismo que acaba de aparecer en la base de datos: no
        // hay nada que actualizar ni nada que contarle.
        if (control.value.trim() === valoresNuevos[campo]) continue;

        if (control.value.trim() === valoresPintados[campo]) {
            control.value = valoresNuevos[campo];
            actualizados.push(etiquetaDe(control));
        } else {
            respetados.push(etiquetaDe(control));
        }
    }

    // La referencia para saber si queda algo sin guardar pasa a ser lo que hay AHORA en la
    // base de datos. Sin esto, al cerrar se preguntaría por unos cambios que ya no existen.
    formulario.dataset.valoresOriginales = JSON.stringify(valoresNuevos);

    if (actualizados.length === 0 && respetados.length === 0) return;

    const partes = ["Otra persona ha cambiado este cliente mientras lo editabas."];
    if (actualizados.length > 0) {
        partes.push(`Se ha actualizado: ${actualizados.join(", ")}.`);
    }
    if (respetados.length > 0) {
        partes.push(`Se ha conservado lo que escribiste en: ${respetados.join(", ")}.`);
    }

    // En la alerta del propio formulario, que es donde está mirando: es role="alert" y estaba
    // en el documento desde que se pintó, así que escribir dentro basta para que se anuncie.
    formulario.querySelector(".alerta-edicion").textContent = partes.join(" ");
}

/**
 * Qué hacer cuando la comprobación no llega a buen puerto.
 *
 * @param {number} idCliente el cliente que se estaba comprobando
 * @param {Error} error lo que devolvió pedirJson, con su código en `estado`
 */
function fallaComprobacion(idCliente, error) {
    if (error.estado === 404) {
        // Lo han borrado. Se avisa fuera de la tabla, que es lo único que sobrevive al
        // refresco, y se olvida el panel para que no se reabra sobre una fila que ya no viene.
        anunciar("Este cliente ya no existe: alguien lo ha eliminado.",
            { visible: true, esError: true });
        filasDesplegadas.delete(idCliente);
        document.dispatchEvent(new CustomEvent("clientes:cambiaron"));
        return;
    }

    // Cualquier otro fallo se calla: en pantalla hay datos buenos, los que acaba de traer el
    // listado, y cambiarlos por un mensaje de error sería empeorar lo que el usuario ya ve.
    console.warn(`No se pudo comprobar si el cliente ${idCliente} había cambiado:`, error);
}

/**
 * ¿Los dos clientes dicen exactamente lo mismo? Es lo que decide si el panel recién pintado
 * con los datos del listado hay que repintarlo con los que acaba de traer el servidor.
 *
 * @param {Object} uno un cliente
 * @param {Object} otro el otro
 * @return {boolean} true si coinciden en los CAMPOS_CLIENTE (null y "" cuentan como iguales)
 */
function mismosDatos(uno, otro) {
    return CAMPOS_CLIENTE.every((campo) => (uno[campo] ?? "") === (otro[campo] ?? ""));
}

/**
 * El nombre visible de un campo del formulario, para poder nombrarlo en un aviso.
 *
 * Se lee de la etiqueta asociada al propio control (la propiedad `labels` da las que le
 * apuntan con `for`) en vez de tener aquí una lista de nombres: son los mismos textos y una
 * copia acabaría diciendo algo distinto del formulario que describe.
 *
 * Solo el texto, no la etiqueta entera: dentro lleva también el lápiz y el asterisco de
 * obligatorio, que son decorativos y no forman parte del nombre del campo. El `name` queda
 * de red de seguridad por si algún campo se quedara sin etiqueta.
 *
 * @param {HTMLInputElement} control el campo del formulario
 * @return {string} su nombre visible ("Código postal"), o su atributo name si no tiene
 */
function etiquetaDe(control) {
    const nombreVisible = control.labels?.[0]
        ?.querySelector(".texto-etiqueta")?.textContent.trim();

    return nombreVisible || control.name;
}

/**
 * Mete en el panel lo que toca según el modo. Está aparte porque los dos caminos de
 * abrirDespliegue (con los datos ya en la mano y esperando al backend) terminan aquí.
 *
 * @param {Element} contenido el hueco del panel
 * @param {string} modo "detalle" o "edicion"
 * @param {Object} cliente los datos del cliente
 * @param {Object|null} borrador lo que hubiera escrito sin guardar
 * @param {boolean} enfocar si se lleva el cursor al primer campo del formulario
 */
function pintarContenidoPanel(contenido, modo, cliente, borrador, enfocar) {
    if (modo === "edicion") {
        pintarPanelEdicion(contenido, cliente, borrador, enfocar);
    } else {
        pintarPanelDetalle(contenido, cliente);
    }
}

/** Cierra el panel de una fila y olvida su estado. */
function cerrarDespliegue(fila) {
    const idCliente = Number(fila.dataset.clienteId);

    filasDesplegadas.delete(idCliente);
    marcarFila(fila, null);

    // Si quedaba una petición de detalle en el aire, su respuesta ya no interesa.
    peticionesEnVuelo[`detalle-${idCliente}`]?.abort();

    const panel = panelDe(fila);
    if (!panel) return;

    panel.classList.remove("abierto");

    // Se quita del DOM cuando termina de plegarse. Se hace con un temporizador y no
    // escuchando 'transitionend' porque ese evento burbujea desde los hijos (un input del
    // formulario con su propia transición lo dispararía antes de tiempo) y porque con las
    // animaciones desactivadas en el sistema no llegaría a dispararse nunca.
    panel.dataset.temporizadorCierre = setTimeout(() => panel.remove(), DURACION_PLEGADO_MS);
}

/**
 * Traduce un clic en la acción que toca: abrir, cerrar o cambiar de modo.
 * @param {HTMLTableRowElement} fila la fila del cliente
 * @param {string} modo el modo que pide el control que se ha pulsado
 */
async function alternarDespliegue(fila, modo) {
    const modoActual = modoDe(fila);

    // El aviso emergente se queda flotando con el texto de antes si no se derriba al pulsar.
    limpiarPistas(fila);

    if (modoActual === null) {
        abrirDespliegue(fila, modo);
        return;
    }

    if (modoActual === "edicion" && !await confirmarDescarte(fila)) return;

    // Preguntar lleva su tiempo, y en ese rato la tabla ha podido repintarse (un refresco de
    // otra pestaña, o la búsqueda que quedara pendiente). La fila que teníamos ya no está en
    // el documento, pero la que la sustituye sí, con el mismo panel reabierto: se sigue con
    // ella. Antes se salía sin hacer nada, y quien acababa de decir "descartar" veía que su
    // decisión no surtía efecto.
    const filaActual = filaViva(Number(fila.dataset.clienteId));
    if (!filaActual) return;   // el cliente ya no está en esta página

    if (modoActual === modo) {
        cerrarDespliegue(filaActual);
    } else {
        // Ya está desplegado: solo cambia lo de dentro, sin volver a animar la apertura.
        abrirDespliegue(filaActual, modo, { animar: false });
    }
}

/**
 * Devuelve el panel de la fila, creándolo si aún no existe.
 *
 * @param {HTMLTableRowElement} fila la fila del cliente
 * @param {boolean} animar si se despliega con transición
 * @return {HTMLElement} el panel, nuevo o el que ya estaba abierto
 */
function obtenerPanel(fila, animar) {
    const existente = panelDe(fila);
    if (existente) {
        // Puede estar plegándose de un cierre reciente: se cancela su borrado o desaparecería
        // a media apertura.
        clearTimeout(Number(existente.dataset.temporizadorCierre));
        existente.classList.add("abierto");
        return existente;
    }

    const panel = plantillaDespliegue.content.firstElementChild.cloneNode(true);
    panel.id = `despliegue-${fila.dataset.clienteId}`;
    panel.dataset.clienteId = fila.dataset.clienteId;

    // La celda del panel ocupa el ancho entero de la tabla, sean las columnas que sean.
    panel.querySelector("td").colSpan = columnasVisibles();

    fila.after(panel);

    // La clase que lo abre se pone en el frame siguiente: puesta a la vez que se inserta, el
    // navegador no llega a ver dos estados distintos y no habría transición que animar.
    if (animar) {
        requestAnimationFrame(() => panel.classList.add("abierto"));
    } else {
        panel.classList.add("abierto");
    }

    return panel;
}

/**
 * Deja la fila coherente con su estado: resaltado, aria-expanded de cada botón, y el nombre y
 * el aviso emergente de cada acción.
 *
 * El nombre accesible y el aviso se escriben en el mismo sitio a propósito. Son dos textos que
 * tienen que decir lo mismo (criterio 2.5.3: quien dicta "editar cliente" a un control por voz
 * necesita que ese sea de verdad el nombre del botón), y repartidos en dos funciones acabarían
 * desincronizados en cuanto uno de los dos cambiase.
 *
 * @param {HTMLTableRowElement} fila la fila del cliente
 * @param {string|null} modo el modo abierto, o null si está cerrada
 */
function marcarFila(fila, modo) {
    const botonVer = fila.querySelector(".btn-ver");
    const botonEditar = fila.querySelector(".btn-editar");
    const botonBorrar = fila.querySelector(".btn-eliminar");

    botonBorrar.addEventListener("click", (event) => {
        confirmarBorradoCliente(
            event.currentTarget,
            fila.dataset.clienteId
        );
    });

    fila.classList.toggle("desplegada", modo !== null);

    // El color del recuadro que envuelve al bloque: verde para mirar, ámbar para editar. Va
    // en la fila Y en el panel porque cada uno pinta la mitad del recuadro, y se quita de los
    // dos a la vez al cerrar para que no quede medio borde dibujado mientras se pliega.
    for (const elemento of [fila, panelDe(fila)]) {
        if (!elemento) continue;
        elemento.classList.toggle("modo-detalle", modo === "detalle");
        elemento.classList.toggle("modo-edicion", modo === "edicion");
    }

    // aria-expanded es lo que hace que un lector de pantalla anuncie que ese botón despliega
    // algo y si está abierto o cerrado. Va en el botón, que es el control de verdad.
    botonVer.setAttribute("aria-expanded", String(modo === "detalle"));
    botonEditar.setAttribute("aria-expanded", String(modo === "edicion"));
    botonBorrar.setAttribute("aria-expanded", String(modo === "borrar"));

    if (modo === null) {
        botonVer.removeAttribute("aria-controls");
        botonEditar.removeAttribute("aria-controls");
        botonBorrar.removeAttribute("aria-controls");
    } else {
        const idPanel = `despliegue-${fila.dataset.clienteId}`;
        botonVer.setAttribute("aria-controls", idPanel);
        botonEditar.setAttribute("aria-controls", idPanel);
        botonBorrar.setAttribute("aria-controls", idPanel);
    }

    // Pulsar la fila o el ojo lleva SIEMPRE al detalle, así que en modo edición el aviso no
    // puede decir "Ocultar": lo que va a pasar es que se cambie al detalle.
    const accionDetalle = modo === "detalle" ? "Ocultar detalles" : "Ver detalles";
    const accionEditar = modo === "edicion" ? "Cancelar la edición" : "Editar cliente";
    //const accionBorrar = modo === "borrar" ? "Cancelar la edición" : "Borrar cliente";

    escribirPista([fila.querySelector("th.cliente-nombre")], accionDetalle);
    nombrarAccion(botonVer, accionDetalle, `${accionDetalle} de`);
    nombrarAccion(botonEditar, accionEditar,
        modo === "edicion" ? "Cancelar la edición de" : "Editar cliente");

    // El de eliminar no cambia nunca, pero pasa por aquí para que use el mismo aviso que los
    // demás: con el title del navegador salía con otro aspecto y con otro retardo.
    nombrarAccion(fila.querySelector(".btn-eliminar"), "Eliminar cliente", "Eliminar cliente");
}

/**
 * Pone a un botón de icono su aviso emergente y su nombre accesible, con el cliente dentro.
 *
 * Sin el nombre del cliente, quien navega con lector de pantalla y pide la lista de botones de
 * la página oye diez veces "Editar cliente" sin saber a cuál pertenece cada uno. El nombre
 * empieza por el mismo texto que se ve en el aviso porque es lo que dicta quien usa control
 * por voz, y así lo que dice y lo que el navegador busca coinciden.
 *
 * @param {HTMLElement} boton el botón de la acción
 * @param {string} accion el texto del aviso emergente ("Editar cliente")
 * @param {string} comienzoNombre con qué empieza el nombre accesible, antes del cliente
 */
function nombrarAccion(boton, accion, comienzoNombre) {
    const nombreCliente = boton.closest("tr").querySelector(".cliente-nombre").textContent;

    escribirPista([boton], accion);
    boton.setAttribute("aria-label", `${comienzoNombre} ${nombreCliente}`);
}

/**
 * Vuelve a abrir los paneles que siguieran desplegados antes de repintar la tabla.
 *
 * Se pintan con el cliente que acaba de traer el listado y **no se comprueban** contra el
 * servidor: la respuesta acaba de llegar, así que preguntar otra vez por cada panel abierto
 * sería una ráfaga de peticiones para dibujar lo mismo. El cliente estará siempre en el mapa
 * (la fila existe porque venía en esa respuesta), pero si faltara se pasa null y
 * abrirDespliegue lo pide.
 */
function reabrirDespliegues() {
    for (const fila of cuerpoTabla.querySelectorAll("tr.fila-cliente")) {
        const idCliente = Number(fila.dataset.clienteId);
        const estado = filasDesplegadas.get(idCliente);

        if (estado) {
            abrirDespliegue(fila, estado.modo, {
                animar: false,
                enfocar: false,
                cliente: clientesEnPagina.get(idCliente) ?? null,
                revalidar: false,
            });
        }
    }
}

// --- Contenido del panel ---

/** Pinta el panel de solo lectura con los campos que la tabla no muestra. */
function pintarPanelDetalle(contenido, cliente) {
    const panel = plantillaPanelDetalle.content.cloneNode(true);

    // textContent, igual que en la tabla: un dato con < o & se ve tal cual y no puede
    // inyectar HTML.
    panel.querySelector(".detalle-id").textContent = cliente.idCliente;
    panel.querySelector(".detalle-direccion").textContent = textoOGuion(cliente.direccion);
    panel.querySelector(".detalle-cp").textContent = textoOGuion(cliente.codigoPostal);
    panel.querySelector(".detalle-poblacion").textContent = textoOGuion(cliente.poblacion);
    panel.querySelector(".detalle-provincia").textContent = textoOGuion(cliente.provincia);

    nombrarPanel(panel.querySelector(".panel-cliente"), "Viendo detalles de", cliente);

    contenido.replaceChildren(panel);
}

/**
 * Hace que el panel se anuncie con el nombre del cliente al que pertenece.
 *
 * Con varios paneles abiertos a la vez, y con la fila del cliente ya por encima, quien no ve
 * la pantalla oía "Editando" sin saber de quién. Ahora oye "Editando García S.L.".
 *
 * El nombre se escribe en la etiqueta de estado, que es texto que YA se ve, y el panel la
 * apunta con aria-labelledby en vez de repetir la cadena en un aria-label: dos textos que
 * dicen lo mismo acaban diciendo cosas distintas en cuanto alguien cambia uno.
 *
 * @param {Element} panel el contenedor del panel (el div del detalle o el form de edición)
 * @param {string} accion con qué empieza la frase ("Viendo detalles de", "Editando")
 * @param {Object} cliente el cliente que se está mostrando
 */
function nombrarPanel(panel, accion, cliente) {
    const etiquetaEstado = panel.querySelector(".etiqueta-estado");

    etiquetaEstado.querySelector(".texto-estado").textContent = `${accion} ${cliente.nombre}`;

    // El id lleva dentro el del cliente porque puede haber varios paneles en la página y dos
    // elementos no pueden compartir id.
    etiquetaEstado.id = `estado-panel-${cliente.idCliente}`;
    panel.setAttribute("aria-labelledby", etiquetaEstado.id);
}

/**
 * Clona el formulario del <template> y lo deja listo. Lo comparten la EDICIÓN y el ALTA, que
 * usan la misma plantilla y solo se diferencian en tres cosas: el sufijo de los id, el cartel
 * de estado y con qué valores se rellenan los campos.
 *
 * @param {Element} contenido el hueco del panel donde va el formulario
 * @param {Object} opciones
 * @param {string|number} opciones.sufijo lo que distingue los id de ESTE formulario de los de
 *        los demás que puedan estar abiertos a la vez
 * @param {Object} opciones.valoresBase contra qué se compara para saber si hay cambios sin
 *        guardar: lo que hay en la BD al editar, y todo en blanco al dar de alta
 * @param {Object|null} opciones.borrador lo que el usuario tenía escrito antes de un repintado
 * @param {string} opciones.etiqueta el texto del cartel ("Editando", "Nuevo cliente")
 * @param {string} opciones.clase la clase del cartel, que es la que le da su color
 * @param {boolean} opciones.enfocar si se lleva el cursor al primer campo
 * @return {HTMLFormElement} el formulario ya insertado, para que quien llame lo remate
 */
function pintarFormularioCliente(contenido,
    { sufijo, valoresBase, borrador, etiqueta, clase, enfocar }) {

    const panel = plantillaPanelEdicion.content.cloneNode(true);
    const formulario = panel.querySelector(".formulario-edicion");

    // Se rellena con el borrador si lo hay, para no perder lo tecleado.
    const valores = borrador ?? valoresBase;

    for (const campo of CAMPOS_EDITABLES) {
        const control = formulario.elements[campo];

        // Cada campo necesita su propio id para que el <label for> lo apunte, y con varios
        // formularios abiertos a la vez no pueden repetirse: se les pega el sufijo. El
        // template los trae con el sufijo PLANTILLA, que además hace que cante a la vista en
        // el inspector si alguno se quedara sin sustituir.
        const idControl = `campo-${campo}-${sufijo}`;
        formulario.querySelector(`label[for="campo-${campo}-PLANTILLA"]`).htmlFor = idControl;
        control.id = idControl;

        control.value = valores[campo];
    }

    // El cartel de estado: el <template> viene con el de edición, que es el caso más común.
    const cartel = formulario.querySelector(".etiqueta-estado");
    cartel.classList.remove("etiqueta-edicion");
    cartel.classList.add(clase);
    cartel.querySelector(".texto-estado").textContent = etiqueta;

    // Contra qué se compara al cerrar para saber si hay algo sin guardar. Al editar es lo que
    // hay en la BD y NO el borrador: si el usuario lo deja como estaba, no hay nada que
    // descartar y no tiene sentido preguntarle.
    formulario.dataset.valoresOriginales = JSON.stringify(valoresBase);

    // El hueco del error del NIF/CIF necesita id propio para que el campo pueda apuntarle con
    // aria-describedby cuando el servidor rechace el guardado. Lleva el sufijo por lo mismo
    // que los campos: puede haber varios formularios abiertos y dos elementos no pueden
    // compartir id.
    mensajeDe(formulario, "nifCif").id = `error-nifcif-${sufijo}`;

    contenido.replaceChildren(panel);

    if (enfocar) {
        formulario.elements.nombre.focus();
    }

    return formulario;
}

/**
 * Pinta el formulario de edición con los datos del cliente.
 * @param {Element} contenido el hueco del panel donde va el formulario
 * @param {Object} cliente lo que hay ahora mismo en la base de datos
 * @param {Object|null} borrador lo que el usuario tenía escrito antes de un repintado
 * @param {boolean} enfocar si se lleva el cursor al primer campo
 */
function pintarPanelEdicion(contenido, cliente, borrador, enfocar) {
    const formulario = pintarFormularioCliente(contenido, {
        sufijo: cliente.idCliente,
        valoresBase: valoresDe(cliente),
        borrador,
        etiqueta: "Editando",
        clase: "etiqueta-edicion",
        enfocar,
    });

    formulario.dataset.clienteId = cliente.idCliente;
    nombrarPanel(formulario, "Editando", cliente);
}

/**
 * Pinta el formulario del alta: la misma plantilla, en blanco y con el cartel en verde.
 *
 * NO lleva dataset.clienteId, y eso es lo que lo distingue del de edición en todas partes:
 * formularioVivo() busca por ese atributo y aquí nunca lo va a encontrar, que es justo lo que
 * se quiere. Quien tenga que distinguirlos a propósito usa la clase .formulario-alta.
 *
 * @param {Element} contenido el hueco del panel donde va el formulario
 * @param {Object|null} borrador lo que se llevara tecleado antes de un repintado
 * @param {boolean} enfocar si se lleva el cursor al primer campo
 */
function pintarPanelAlta(contenido, borrador, enfocar) {
    // Todo en blanco. Se construye con valoresDe({}) y no con un objeto escrito a mano para
    // que siga a CAMPOS_EDITABLES: añadir un campo a esa lista no puede obligar a acordarse
    // de venir aquí a añadirlo también.
    const formulario = pintarFormularioCliente(contenido, {
        sufijo: SUFIJO_ALTA,
        valoresBase: valoresDe({}),
        borrador,
        etiqueta: "Nuevo cliente",
        clase: "etiqueta-alta",
        enfocar,
    });

    formulario.classList.add("formulario-alta");
    formulario.setAttribute("aria-label", "Nuevo cliente");

    // El icono del cartel viene siendo el lápiz de "Editando"; aquí lo que se hace es añadir.
    const icono = formulario.querySelector(".etiqueta-estado i");
    icono.classList.remove("fa-pencil");
    icono.classList.add("fa-plus");
}

/**
 * Aviso de "cargando" mientras llega la respuesta del backend.
 *
 * No lleva role: un elemento que nace con el texto dentro no se anuncia, y tampoco se manda a
 * la región de anuncios a propósito. Es un mensaje de paso, que se sustituye en cuanto llega
 * la respuesta, y anunciarlo dejaría al lector de pantalla leyendo algo que ya no está. Que el
 * panel se ha abierto ya lo dice el aria-expanded del botón que se acaba de pulsar.
 *
 * @param {HTMLElement} contenido el hueco del panel, cuyo contenido se sustituye entero
 */
function pintarCargando(contenido) {
    const aviso = document.createElement("p");
    aviso.className = "panel-aviso";
    aviso.textContent = "Cargando los datos del cliente…";
    contenido.replaceChildren(aviso);
}

/**
 * Aviso de error con un botón para volver a intentarlo, sin cerrar el panel: cerrarlo
 * obligaría a buscar otra vez la fila para reabrirla.
 *
 * @param {HTMLElement} contenido el hueco del panel, cuyo contenido se sustituye entero
 * @param {Error} error el fallo que se va a explicar
 */
function pintarErrorPanel(contenido, error) {
    console.error("No se pudieron cargar los datos del cliente:", error);

    const aviso = document.createElement("p");
    aviso.className = "panel-aviso panel-aviso-error";
    aviso.textContent = "No se pudieron cargar los datos del cliente. ";

    // Este sí se anuncia: es el final del camino, y sin avisar el panel se queda en silencio
    // como si siguiera cargando. El texto se ve aquí dentro, así que va solo a la región
    // invisible y no a la franja de avisos.
    anunciar("No se pudieron cargar los datos del cliente.");

    const boton = document.createElement("button");
    boton.type = "button";
    boton.className = "btn btn-sm btn-link btn-reintentar";
    boton.textContent = "Reintentar";
    aviso.appendChild(boton);

    contenido.replaceChildren(aviso);
}

/**
 * Los campos vacíos se ven mejor como un guion que como una celda en blanco.
 *
 * @param {string|null|undefined} valor el dato tal cual viene del backend
 * @return {string} el valor, o un guion largo si no había nada que enseñar
 */
function textoOGuion(valor) {
    return valor && valor.trim() ? valor : "—";
}

/**
 * Los datos del cliente en la forma que entiende el formulario: solo los campos editables,
 * sin nulos y sin espacios sobrantes. Un input al que se le asigna null escribe la palabra
 * "null" dentro, así que la conversión no es opcional.
 *
 * @param {Object} cliente el cliente tal cual llega del backend
 * @return {Object.<string, string>} un valor por cada campo de CAMPOS_EDITABLES
 */
function valoresDe(cliente) {
    const valores = {};
    for (const campo of CAMPOS_EDITABLES) {
        valores[campo] = (cliente[campo] ?? "").trim();
    }
    return valores;
}

/**
 * Lo que hay escrito ahora mismo en el formulario. Devuelve la MISMA forma que
 * {@link valoresDe} para que las dos se puedan comparar campo a campo (ver hayCambios).
 *
 * @param {HTMLFormElement} formulario el formulario de edición
 * @return {Object.<string, string>} un valor por cada campo de CAMPOS_EDITABLES
 */
function leerFormulario(formulario) {
    const valores = {};
    for (const campo of CAMPOS_EDITABLES) {
        valores[campo] = formulario.elements[campo].value.trim();
    }
    return valores;
}

/**
 * El hueco donde va el mensaje de error de un campo del formulario.
 *
 * Se busca por el nombre del campo y no con nextElementSibling: así el mensaje se puede mover
 * dentro de la celda sin que esto deje de encontrarlo.
 *
 * @param {HTMLFormElement} formulario el formulario de edición
 * @param {string} campo el atributo name del campo
 * @return {Element} el div del mensaje
 */
function mensajeDe(formulario, campo) {
    return formulario.querySelector(`[name="${campo}"] ~ .invalid-feedback`);
}

/** ¿Se ha tocado algo respecto a lo que hay en la base de datos? */
function hayCambios(formulario) {
    return JSON.stringify(leerFormulario(formulario)) !== formulario.dataset.valoresOriginales;
}

/**
 * Pregunta antes de tirar unos cambios sin guardar.
 *
 * @param {HTMLTableRowElement} fila la fila que se va a cerrar o cambiar de modo
 * @return {Promise<boolean>} si se puede seguir adelante
 */
async function confirmarDescarte(fila) {
    const formulario = panelDe(fila)?.querySelector(".formulario-edicion");

    // Sin formulario o sin nada tocado no hay nada que descartar, así que no se pregunta:
    // un diálogo para decir "sí" siempre es un diálogo que sobra.
    if (!formulario || !hayCambios(formulario)) return true;

    return preguntarDescarte();
}

/**
 * Pregunta antes de cerrar un alta con algo escrito.
 *
 * Comparte el diálogo con la edición, y también su regla: si el formulario está como nació
 * —todo en blanco— no hay nada que descartar y no se molesta al usuario.
 *
 * @return {Promise<boolean>} si se puede cerrar
 */
async function confirmarDescarteAlta() {
    const formulario = formularioAlta();
    if (!formulario || !hayCambios(formulario)) return true;

    return preguntarDescarte();
}

/**
 * Abre el diálogo de descartar y espera la respuesta.
 *
 * La promesa se resuelve en 'hidden.bs.modal', cuando el diálogo ya se ha cerrado del todo y
 * el foco ha vuelto a su sitio, y no al pulsar el botón: respondiendo antes, quien nos llama
 * colocaría el foco (en el lápiz de la fila) y la vuelta se lo llevaría de allí.
 *
 * @return {Promise<boolean>} true si se descartan los cambios
 */
function preguntarDescarte() {
    // Si Bootstrap no ha cargado (su CDN caído, por ejemplo), se pregunta con el diálogo del
    // navegador: es feo, pero sin él la fila se cerraría llevándose lo escrito sin avisar.
    if (!window.bootstrap) {
        return Promise.resolve(confirm("Hay cambios sin guardar en este cliente. ¿Quieres descartarlos?"));
    }

    modalDescartar ??= new bootstrap.Modal(dialogoDescartar);

    // De dónde venimos, para devolver el foco al cerrar. Bootstrap solo lo hace con los
    // diálogos que se abren con data-bs-toggle, y este se abre desde el código: sin esto,
    // quien contesta "Seguir editando" se encuentra el foco en el <body>, al principio de la
    // página, justo después de haber dicho que quería seguir donde estaba.
    focoPrevioDialogo = document.activeElement;

    return new Promise((resolver) => {
        responderDescarte = resolver;
        modalDescartar.show();
    });
}

// Pulsar "Descartar" solo deja anotada la respuesta y manda cerrar; quien resuelve la promesa
// es el cierre, que ocurre igual si se pulsa "Seguir editando", la X, Escape o el fondo.
btnDescartar.addEventListener("click", () => {
    descarteAceptado = true;
    modalDescartar.hide();
});

dialogoDescartar.addEventListener("hidden.bs.modal", () => {
    const aceptado = descarteAceptado;
    const resolver = responderDescarte;

    descarteAceptado = false;
    responderDescarte = null;

    // El foco, de vuelta a donde estaba antes de preguntar. Puede que ese control ya no exista
    // (la tabla se ha repintado mientras el diálogo estaba abierto), y entonces no se toca.
    // Se hace ANTES de responder: si la respuesta fue "descartar", quien nos llama va a
    // colocarlo en otro sitio y su decisión es la que debe quedar.
    if (focoPrevioDialogo?.isConnected) {
        focoPrevioDialogo.focus();
    }
    focoPrevioDialogo = null;

    resolver?.(aceptado);
});

/** Guarda lo escrito en los formularios abiertos antes de que la tabla se repinte. */
function guardarBorradores() {
    for (const formulario of cuerpoTabla.querySelectorAll(".formulario-edicion")) {
        // El del alta no está en filasDesplegadas —no tiene id de cliente— y se guarda aparte.
        if (formulario.classList.contains("formulario-alta")) {
            if (altaAbierta) altaAbierta.borrador = leerFormulario(formulario);
            continue;
        }

        const estado = filasDesplegadas.get(Number(formulario.dataset.clienteId));
        if (estado) {
            estado.borrador = leerFormulario(formulario);
        }
    }
}

// --- Avisos emergentes (el "Ver detalles" tras un segundo de ratón encima) ---

/** Escribe el texto del aviso en cada elemento y descarta el globo que ya tuviera. */
function escribirPista(elementos, texto) {
    for (const elemento of elementos) {
        // Repintar una fila casi nunca cambia la palabra. Sin esta salida, cada repaso
        // destruye y recrea una instancia que estaba bien, peleándose con los temporizadores
        // de apertura que hubiera en vuelo.
        if (elemento.dataset.bsTitle === texto) continue;

        elemento.dataset.bsTitle = texto;

        // El globo se crea en el primer hover y se queda con el texto que hubiera entonces.
        // Al destruirlo, el siguiente hover lo vuelve a crear ya con el texto nuevo.
        window.bootstrap?.Tooltip.getInstance(elemento)?.dispose();
    }
}

/**
 * Destruye los globos de una zona y barre los que hayan quedado sueltos.
 *
 * Destruye y no esconde: hide() no sirve aquí. Bootstrap se salta el cierre mientras quede
 * ALGÚN disparador activo, y al pulsar un botón ese botón se queda con el foco, así que el
 * globo sobreviviría al hide() y al mouseleave y se quedaría flotando. dispose() derriba el
 * globo pase lo que pase, y la raíz delegada vuelve a crear la instancia en el siguiente
 * hover, así que no se pierde nada.
 *
 * @param {Element} raiz la tabla entera al repintarla, una sola fila si solo se rehacen sus
 *        celdas, o la fila que se acaba de pulsar
 */
function limpiarPistas(raiz = cuerpoTabla) {
    for (const elemento of raiz.querySelectorAll("[data-bs-title]")) {
        window.bootstrap?.Tooltip.getInstance(elemento)?.dispose();
    }

    // Y lo que haya quedado suelto. Con container: "body" el globo cuelga del <body>, no del
    // elemento, así que si su dueño desapareció sin pasar por aquí nadie lo cierra nunca y se
    // van acumulando. Un globo vivo siempre lo apunta el aria-describedby de su elemento; el
    // que no, es basura. El selector es ~= y no =: aria-describedby admite varios ids
    // separados por espacios.
    for (const globo of document.body.querySelectorAll(".tooltip")) {
        if (!globo.id || !document.querySelector(`[aria-describedby~="${globo.id}"]`)) {
            globo.remove();
        }
    }
}

// Un único aviso delegado en el <tbody>, no uno por celda: así vale también para las filas
// que todavía no se han pintado y no hay que crearlos y destruirlos en cada repintado.
// container: 'body' porque la tabla va dentro de .table-responsive, que tiene overflow y
// recortaría el globo por arriba.
if (window.bootstrap) {
    new bootstrap.Tooltip(cuerpoTabla, {
        // UNA sola celda por fila lleva el aviso de la fila, la del nombre. Antes lo llevaban
        // las cinco, y recorrer una fila con el ratón encendía cinco instancias con sus cinco
        // temporizadores para decir todas lo mismo. Que el resto de la fila también despliega
        // ya lo cuenta el cursor de mano que .fila-cliente pone en style.css.
        // El enlace del correo o del teléfono conserva el suyo, que dice algo distinto, y
        // Bootstrap se queda con el elemento coincidente MÁS INTERNO.
        selector: ".enlace-celda, .fila-cliente th.cliente-nombre, .celda-acciones .btn[data-bs-title]",
        delay: { show: RETARDO_PISTA_MS, hide: 0 },
        container: "body",
        placement: "top",
        trigger: "hover focus",
    });

    // Y otro para la barra de filtros, con los mismos ajustes: un aviso que se abre distinto
    // o con otro retardo según la zona de la pantalla se nota, y ahí es donde estaba el title
    // del navegador que ponía el botón de dirección.
    new bootstrap.Tooltip(barraFiltros, {
        selector: "[data-bs-title]",
        delay: { show: RETARDO_PISTA_MS, hide: 0 },
        container: "body",
        placement: "top",
        trigger: "hover focus",
    });
}

// --- Enlazado de los clics de la tabla ---

// Un solo listener en el <tbody>, y no uno por botón: los botones se crean y se destruyen en
// cada repintado, así que los suyos habría que volver a enlazarlos cada vez (y los que había
// antes en este archivo ni siquiera llegaban a enlazarse, porque se registraban al cargar la
// página, cuando los botones aún vivían dentro del <template>).
cuerpoTabla.addEventListener("click", async (evento) => {
    // El alta, la primera: comparte clases con el panel de una fila (fila-despliegue, el
    // botón de cancelar) pero no tiene fila de cliente ni id, así que manejarClicPanel se
    // saldría sin hacer nada al buscarla con filaViva.
    if (evento.target.closest("tr.fila-alta")) {
        if (evento.target.closest(".btn-cancelar") && await confirmarDescarteAlta()) {
            cerrarAlta();
        }
        return;
    }

    // Después los controles del panel de una fila: viven en la fila hermana, no en la del
    // cliente.
    const panel = evento.target.closest("tr.fila-despliegue");
    if (panel) {
        manejarClicPanel(evento, panel);
        return;
    }

    const fila = evento.target.closest("tr.fila-cliente");
    if (!fila) return;

    const boton = evento.target.closest(".celda-acciones .btn");
    if (boton) {
        if (boton.classList.contains("btn-ver")) alternarDespliegue(fila, "detalle");
        else if (boton.classList.contains("btn-editar")) alternarDespliegue(fila, "edicion");
        // El de eliminar es de otra feature: aquí no se toca.
        return;
    }

    // Un enlace de email o de teléfono hace lo suyo y nada más: desplegar además la fila
    // sería un segundo efecto que nadie ha pedido al pulsarlo.
    if (evento.target.closest("a")) return;

    // Si estaba seleccionando texto de la fila, no quería desplegar nada.
    if (window.getSelection()?.toString()) return;

    alternarDespliegue(fila, "detalle");
});

/** Clic dentro de un panel abierto: cancelar la edición o reintentar la carga. */
async function manejarClicPanel(evento, panel) {
    // Por el id que el propio panel lleva, y no por previousElementSibling: fiarse de la
    // posición en el documento obliga a que el panel esté siempre justo debajo de su fila, y
    // eso es una suposición que nadie ve al leer el código de al lado.
    const fila = filaViva(Number(panel.dataset.clienteId));
    if (!fila) return;

    if (evento.target.closest(".btn-cancelar")) {
        if (!await confirmarDescarte(fila)) return;

        // Igual que al alternar: mientras se preguntaba, la tabla ha podido repintarse y esta
        // fila ya no ser la que está en pantalla. Se cierra la que lo esté ahora.
        const filaActual = filaViva(Number(fila.dataset.clienteId));
        if (!filaActual) return;

        cerrarDespliegue(filaActual);
        // El foco vuelve al lápiz que abrió el formulario: si no, se quedaría en un botón que
        // acaba de desaparecer y saltaría al principio de la página.
        filaActual.querySelector(".btn-editar").focus();
        return;
    }

    if (evento.target.closest(".btn-reintentar")) {
        abrirDespliegue(fila, modoDe(fila) ?? "detalle", { animar: false });
    }
}

// El aviso de NIF repetido lo pone el servidor: en cuanto se toca ese campo deja de tener
// sentido seguir viéndolo en rojo.
cuerpoTabla.addEventListener("input", (evento) => {
    if (evento.target.name === "nifCif") {
        evento.target.classList.remove("is-invalid");
        evento.target.removeAttribute("aria-invalid");
        evento.target.removeAttribute("aria-describedby");
    }
});

// --- Alta de cliente ---
//
// El alta es un panel más, pero NO pasa por abrirDespliegue: ese camino gira todo alrededor de
// una tr.fila-cliente con su id, y aquí no hay ni fila ni id. Son cuatro funciones cortas
// —abrir, cerrar, reabrir tras un repintado y guardar— en vez de un puñado de condiciones
// repartidas por el despliegue preguntando "¿y si es el alta?".

/** La fila del alta si está en el documento ahora mismo, o null. */
function filaAlta() {
    return cuerpoTabla.querySelector("tr.fila-alta");
}

/** El formulario del alta si está en el documento ahora mismo, o null. */
function formularioAlta() {
    return cuerpoTabla.querySelector(".formulario-alta");
}

/**
 * Abre el alta arriba del todo de la tabla, o lleva el foco a la que ya hubiera.
 *
 * No lleva fila de cliente encima, a diferencia del detalle y de la edición: un cliente que
 * todavía no existe no tiene nombre ni CIF que enseñar, y los tres botones de acción no
 * tendrían a qué apuntar.
 */
function abrirAlta() {
    // Pulsar dos veces no puede dejar dos formularios: el segundo se llevaría el sufijo de los
    // id del primero y los <label for> apuntarían al que no es.
    //
    // Se pregunta por el ESTADO y no por si el formulario está en el documento, y esa es la
    // diferencia que importa: al cerrar, la fila se queda 250 ms plegándose, así que hay una
    // ventana en la que el formulario sigue ahí pero el alta ya no está abierta. Mirando el
    // documento, ese caso se confundía con "ya hay una abierta", el botón llevaba el foco a un
    // formulario que estaba desapareciendo y el usuario se quedaba sin nada.
    if (altaAbierta) {
        formularioAlta()?.elements.nombre.focus();
        return;
    }

    // Y si quedaba una plegándose, se retira YA: dos filas de alta a la vez repetirían los id
    // de los campos durante ese cuarto de segundo.
    retirarFilaAlta();

    altaAbierta = { borrador: null };
    insertarFilaAlta(null, { animar: true, enfocar: true });
}

/**
 * Monta la fila del alta con el formulario dentro y la pone arriba del todo.
 *
 * Inserta ella misma en vez de devolver la fila para que quien llame la coloque, y no es un
 * detalle: focus() sobre un elemento que todavía no está en el documento no hace nada, así que
 * el orden "pintar, insertar, enfocar" tiene que estar garantizado en un solo sitio. En la
 * edición no se plantea el problema porque allí el panel se inserta antes de pintarlo.
 *
 * @param {Object|null} borrador lo que se llevara tecleado, al reabrirla tras un repintado
 * @param {Object} opciones
 * @param {boolean} opciones.animar si se despliega con transición
 * @param {boolean} opciones.enfocar si se lleva el cursor al primer campo
 */
function insertarFilaAlta(borrador, { animar, enfocar }) {
    const fila = plantillaDespliegue.content.firstElementChild.cloneNode(true);

    // Comparte plantilla y clases con el despliegue de una fila —el envoltorio que anima el
    // alto y el recuadro de color son los mismos—, y añade fila-alta, que es por donde la
    // reconocen el manejador de clics y el repintado.
    fila.classList.add("fila-alta", "modo-alta");
    fila.querySelector("td").colSpan = columnasVisibles();

    // Se pinta SIN foco y se pide después de insertar, por lo dicho arriba.
    pintarPanelAlta(fila.querySelector(".despliegue-contenido"), borrador, false);
    cuerpoTabla.prepend(fila);

    if (enfocar) {
        fila.querySelector(".formulario-alta").elements.nombre.focus();
    }

    // Igual que en obtenerPanel: la clase que lo abre va en el frame siguiente, porque puesta
    // a la vez que se inserta el navegador no llega a ver dos estados y no hay transición.
    if (animar) {
        requestAnimationFrame(() => fila.classList.add("abierto"));
    } else {
        fila.classList.add("abierto");
    }
}

/**
 * Cierra el alta y olvida lo escrito.
 *
 * @param {boolean} devolverElFoco si el foco vuelve al botón de "Añadir Cliente". Al cancelar
 *        sí: el botón que se acaba de pulsar desaparece y el foco se iría al principio de la
 *        página. Tras guardar no, porque de eso se encarga el repintado.
 */
function cerrarAlta({ devolverElFoco = true } = {}) {
    altaAbierta = null;

    const fila = filaAlta();
    if (!fila) return;

    fila.classList.remove("abierto");

    // El identificador se guarda en la propia fila para poder cancelarlo: si se vuelve a pulsar
    // "Añadir Cliente" antes de que termine el plegado, hay que retirarla en ese momento y no
    // dejar que el temporizador la borre después, cuando ya habría otra en su sitio. Es lo
    // mismo que hace obtenerPanel con el panel de una fila.
    fila.dataset.temporizadorCierre = setTimeout(() => fila.remove(), DURACION_PLEGADO_MS);

    if (devolverElFoco) {
        btnAnadirCliente.focus();
    }
}

/**
 * Retira del documento la fila del alta que estuviera plegándose, cancelando su borrado
 * programado.
 *
 * Sin cancelar el temporizador no basta con quitarla: el que quedó pendiente se dispararía
 * 250 ms después sobre un nodo que ya no está, y aunque eso no da error, deja el código
 * dependiendo de que ese remove() no encuentre nada.
 */
function retirarFilaAlta() {
    const fila = filaAlta();
    if (!fila) return;

    clearTimeout(Number(fila.dataset.temporizadorCierre));
    fila.remove();
}

/**
 * Vuelve a poner el alta arriba después de que la tabla se repinte.
 *
 * Se llama desde los DOS sitios que vacían el <tbody>, pintarFilas y mostrarMensaje: buscar
 * algo que no existe también borra la tabla, y sin esto el alta desaparecía justo ahí.
 *
 * Sin animar y sin robar el foco, por lo mismo que reabrirDespliegues: el repintado suele
 * venir de que el usuario está escribiendo en el buscador, y llevarle el cursor al formulario
 * lo sacaría de donde está.
 */
function reabrirAlta() {
    if (!altaAbierta) return;

    insertarFilaAlta(altaAbierta.borrador, { animar: false, enfocar: false });
}

/**
 * Manda el cliente nuevo al backend y actúa según lo que responda.
 *
 * Es la hermana de guardarEdicion: mismo guion —validar en el navegador, desactivar el botón,
 * enviar y contar el resultado— con POST en vez de PUT y sin id en la URL, porque lo asigna
 * la base de datos.
 *
 * @param {HTMLFormElement} formulario el formulario del alta
 */
async function guardarCliente(formulario) {
    limpiarErrores(formulario);

    // Las restricciones del HTML son las mismas que valida el backend, así que el navegador
    // corta aquí lo que el servidor rechazaría con un 400 y nos ahorramos la petición.
    formulario.classList.add("was-validated");
    if (!formulario.checkValidity()) {
        formulario.querySelector(":invalid")?.focus();
        return;
    }

    const boton = formulario.querySelector(".btn-guardar");
    boton.disabled = true;   // sin esto, dos clics seguidos crean dos clientes

    // Se lee ANTES de enviar: el formulario desaparece al cerrar el alta, y después del await
    // ya no habría de dónde sacarlo.
    const nifCreado = formulario.elements.nifCif.value.trim();

    try {
        const estado = await enviarJson("alta", "POST", API_CLIENTE, cuerpoPeticion(formulario));

        if (estado === 201) {
            cerrarAlta({ devolverElFoco: false });
            avisarClienteCreado(nifCreado);
            return;
        }

        contarErrorAlta(estado);
    } catch (error) {
        if (esCancelacion(error)) return;
        console.error("No se pudo crear el cliente:", error);
        contarErrorAlta(0);
    } finally {
        // El botón, el vivo: si la tabla se repintó mientras viajaba la petición, el que
        // teníamos ya no está y el nuevo nace habilitado de todos modos.
        const botonVivo = formularioAlta()?.querySelector(".btn-guardar");
        if (botonVivo) botonVivo.disabled = false;
    }
}

/**
 * Cuenta un alta que no ha salido bien, en el formulario que esté en pantalla AHORA.
 *
 * Misma regla que contarErrorGuardado: el formulario desde el que se pulsó Guardar puede haber
 * desaparecido mientras viajaba la petición, y escribir el error en él no falla, simplemente
 * no lo lee nadie. El 404 de mostrarErrorGuardado no puede darse aquí —no hay ningún cliente
 * que pueda haber sido borrado— y por eso no se contempla.
 *
 * @param {number} estado el código HTTP (0 si ni siquiera hubo respuesta)
 */
function contarErrorAlta(estado) {
    const formulario = formularioAlta();

    if (formulario) {
        mostrarErrorGuardado(formulario, estado);
        return;
    }

    anunciar("No se pudo crear el cliente. Vuelve a intentarlo.",
        { visible: true, esError: true });
}

/**
 * Refresca la tabla tras crear un cliente y cuenta si se puede ver o no.
 *
 * Es lo que resuelve la pregunta incómoda de "lo he creado, ¿dónde está?". Con el orden por
 * defecto sale el primero: fecha_alta es una fecha SIN hora, así que todos los de hoy empatan
 * y desempata el idcliente más alto, que es el recién creado. Pero con el orden invertido se
 * va a la última página, ordenando por nombre cae donde le toque, y con un filtro de provincia
 * que no cumpla no sale en ninguna.
 *
 * Por eso se pide la página 0 —donde estará si el orden es el de siempre— y después se mira si
 * de verdad ha salido. Si no, se dice por qué en vez de dejar al usuario buscándolo.
 *
 * Se busca por el NIF/CIF y no por el id porque enviarJson solo devuelve el código de la
 * respuesta, y ensancharlo para que a veces devuelva también el cuerpo dejaría a la función
 * con dos formas de retorno según un parámetro. El NIF vale igual de bien: es único —por eso
 * el backend contesta 409 cuando se repite— y es un dato que el usuario acaba de escribir.
 *
 * @param {string} nifCreado el NIF/CIF con el que se ha creado el cliente
 */
async function avisarClienteCreado(nifCreado) {
    // La tabla se pide desde aquí y no con el evento clientes:cambiaron porque hay que esperar
    // a que termine para poder mirar si el cliente está: el evento no devuelve nada que esperar.
    await cargarClientes(0);

    const creado = [...clientesEnPagina.values()].find((c) => c.nifCif === nifCreado);

    if (creado) {
        anunciar("Cliente creado.", { visible: true });

        // El foco, a su fila. Es el mismo mecanismo que usa el guardado de la edición, salvo
        // que aquí la tabla ya está pintada, así que se coloca directamente.
        filaViva(creado.idCliente)?.querySelector(".btn-editar")?.focus();
        return;
    }

    // Está creado, pero no se ve. Decirlo importa: si no, parece que no se ha guardado.
    const porFiltros = hayCriteriosActivos();
    anunciar(porFiltros
        ? "Cliente creado, pero no se ve porque no cumple los filtros que tienes puestos."
        : "Cliente creado. Con este orden no aparece en la primera página.",
        { visible: true });
}

// --- Guardado de la edición ---

// El envío se atiende también aquí arriba, por lo mismo: el formulario aparece y desaparece.
cuerpoTabla.addEventListener("submit", (evento) => {
    evento.preventDefault();

    const formulario = evento.target.closest(".formulario-edicion");
    if (!formulario) return;

    // Los dos formularios salen de la misma plantilla y comparten .formulario-edicion, así que
    // este listener los recoge a los dos. Los distingue la clase que solo lleva el del alta.
    if (formulario.classList.contains("formulario-alta")) {
        guardarCliente(formulario);
    } else {
        guardarEdicion(formulario);
    }
});

/**
 * Manda los cambios al backend y actúa según lo que responda.
 *
 * No se llama guardarCliente a propósito: ese es el nombre que le corresponde al alta de
 * clientes, que es otra feature. Ocupándolo, el día que alguien la implemente se encontraría
 * el nombre cogido por una función que espera un formulario de edición.
 *
 * @param {HTMLFormElement} formulario el formulario de la fila que se está editando
 */
async function guardarEdicion(formulario) {
    const idCliente = Number(formulario.dataset.clienteId);

    limpiarErrores(formulario);

    // Las restricciones del HTML (required, maxlength, type=email) son las mismas que valida
    // el backend, así que el navegador corta aquí lo que el servidor rechazaría con un 400 y
    // nos ahorramos la petición. was-validated es lo que hace que Bootstrap pinte en rojo el
    // campo que falla y enseñe su mensaje.
    formulario.classList.add("was-validated");
    if (!formulario.checkValidity()) {
        formulario.querySelector(":invalid")?.focus();
        return;
    }

    const boton = formulario.querySelector(".btn-guardar");
    boton.disabled = true;   // sin esto, dos clics seguidos mandan dos PUT

    try {
        const estado = await enviarJson(`guardar-${idCliente}`, "PUT",
            `${API_CLIENTE}/${idCliente}`, cuerpoPeticion(formulario));

        if (estado === 200) {
            // Se olvida el panel por el id y no por la fila: si la tabla se ha repintado
            // mientras viajaba la petición, el <tr> que teníamos ya no está en el documento y
            // cerrarDespliegue no llegaría a borrar la entrada del Map. Quedaría un panel
            // abierto de un cliente que el usuario ya había terminado de editar.
            filasDesplegadas.delete(idCliente);

            const fila = filaViva(idCliente);
            if (fila) cerrarDespliegue(fila);

            // Y que la tabla, al repintarse, devuelva el foco al botón de editar de esta fila:
            // el que estaba pulsado deja de existir y el foco se iría al principio de la página.
            focoPendiente = idCliente;

            // Visible: la fila que se estaba editando desaparece con el refresco y no quedaría
            // ninguna señal de que el guardado ha ido bien.
            anunciar("Cliente guardado.", { visible: true });

            // El contrato del proyecto para avisar de un cambio. La tabla se recarga sola, así
            // que la fila enseña lo guardado y se recoloca si el orden la ha movido de sitio.
            document.dispatchEvent(new CustomEvent("clientes:cambiaron"));
            return;
        }

        contarErrorGuardado(idCliente, estado);
    } catch (error) {
        if (esCancelacion(error)) return;
        console.error("No se pudo guardar el cliente:", error);
        contarErrorGuardado(idCliente, 0);
    } finally {
        // El botón, el vivo: si la tabla se repintó, el que teníamos ya no está y el nuevo nace
        // habilitado de todos modos, así que sin esta búsqueda no pasaría nada malo. Se hace
        // igual para que no parezca un olvido y para que la regla de arriba no tenga excepciones.
        const botonVivo = formularioVivo(idCliente)?.querySelector(".btn-guardar");
        if (botonVivo) botonVivo.disabled = false;
    }
}

/**
 * Cuenta un guardado que no ha salido bien, en el formulario que esté en pantalla AHORA.
 *
 * Es la parte que faltaba de la regla del `await`: el formulario desde el que se pulsó Guardar
 * puede haber desaparecido mientras viajaba la petición (basta con teclear en el buscador, o
 * con que otra parte de la aplicación dispare `clientes:cambiaron`). Escribir el error en él no
 * falla, simplemente no lo lee nadie, y el usuario se queda creyendo que ha guardado.
 *
 * Si el panel ya no está abierto no hay dónde poner el detalle del error, así que el aviso va a
 * la franja de fuera, que es lo único que sobrevive a un repintado.
 *
 * @param {number} idCliente el cliente que se intentaba guardar
 * @param {number} estado el código HTTP (0 si ni siquiera hubo respuesta)
 */
function contarErrorGuardado(idCliente, estado) {
    const formulario = formularioVivo(idCliente);

    if (formulario) {
        mostrarErrorGuardado(formulario, estado);
        return;
    }

    anunciar("No se pudo guardar el cliente. Vuelve a abrirlo e inténtalo de nuevo.",
        { visible: true, esError: true });
}

/**
 * El cuerpo JSON del PUT, con la forma que espera ClienteRequest. No lleva ni el id (viaja
 * en la URL) ni la fecha de alta (no es editable y el service conserva la que hay en la BD).
 *
 * @param {HTMLFormElement} formulario el formulario de edición
 * @return {string} el JSON listo para el body del fetch
 */
function cuerpoPeticion(formulario) {
    const valores = leerFormulario(formulario);

    // Los opcionales vacíos viajan como null y no como "": esas columnas admiten NULL y es lo
    // que hay en las filas que nunca se rellenaron. Mandar cadenas vacías dejaría dos formas
    // distintas de decir "no hay dato" conviviendo en la misma tabla.
    for (const campo of CAMPOS_OPCIONALES) {
        if (!valores[campo]) valores[campo] = null;
    }

    return valores;
}

/**
 * Cuenta qué ha pasado según el código que devolvió el servidor.
 * @param {HTMLFormElement} formulario el formulario que se intentó guardar
 * @param {number} estado el código HTTP (0 si ni siquiera hubo respuesta)
 */
function mostrarErrorGuardado(formulario, estado) {
    // El NIF/CIF tiene un índice UNIQUE en la base de datos: es el único dato que puede chocar
    // con otro cliente, así que el 409 se señala en SU campo. Un aviso general obligaría al
    // usuario a adivinar cuál de los ocho campos es el del problema.
    if (estado === 409) {
        const campo = formulario.elements.nifCif;
        const mensaje = mensajeDe(formulario, "nifCif");

        mensaje.textContent = "Ya existe otro cliente con este NIF/CIF.";
        campo.classList.add("is-invalid");

        // El rojo de Bootstrap es solo color. aria-invalid es lo que hace que un lector de
        // pantalla diga "no válido" al llegar al campo, y describedby es lo que le hace leer
        // el motivo: sin ellos, quien no ve la pantalla se queda con el foco en un campo que
        // aparentemente no tiene nada.
        campo.setAttribute("aria-invalid", "true");
        campo.setAttribute("aria-describedby", mensaje.id);
        campo.focus();
        return;
    }

    const alerta = formulario.querySelector(".alerta-edicion");

    if (estado === 404) {
        // Alguien lo ha borrado mientras se editaba: no hay nada que guardar y la tabla que se
        // está viendo ya no es la que hay en la base de datos. Este es el único error que NO
        // se escribe en la alerta del formulario: el refresco que viene a continuación se
        // lleva por delante la fila y con ella la alerta, así que el aviso va a la franja de
        // fuera, que es la que sobrevive.
        anunciar("Este cliente ya no existe: alguien lo ha eliminado mientras lo editabas.",
            { visible: true, esError: true });
        filasDesplegadas.delete(Number(formulario.dataset.clienteId));
        document.dispatchEvent(new CustomEvent("clientes:cambiaron"));
        return;
    }

    // Los demás dejan el panel abierto para poder corregir, así que se cuentan ahí mismo. La
    // alerta es role="alert" y estaba en el documento desde que se pintó el formulario: basta
    // con escribirle el texto para que se anuncie.
    if (estado === 400) {
        alerta.textContent = "El servidor ha rechazado los datos. Revisa los campos marcados.";
    } else {
        alerta.textContent = "No se pudo guardar. Inténtalo de nuevo en unos segundos.";
    }
}

/** Borra las marcas del intento anterior para no mezclar errores viejos con nuevos. */
function limpiarErrores(formulario) {
    // Vaciarla es esconderla: la hoja de estilos oculta la alerta sin texto, y así el elemento
    // no se va nunca del documento, que es lo que necesita su role="alert" para anunciar.
    formulario.querySelector(".alerta-edicion").textContent = "";

    const campo = formulario.elements.nifCif;
    campo.classList.remove("is-invalid");
    campo.removeAttribute("aria-invalid");
    campo.removeAttribute("aria-describedby");

    // Se devuelve el mensaje que el <template> trae de fábrica (el de "es obligatorio"), que es
    // el que le toca enseñar al navegador si el campo se queda vacío.
    mensajeDe(formulario, "nifCif").textContent = MENSAJE_NIF_BASE;
}

/**
 * Manda un JSON al backend y devuelve el código de la respuesta.
 *
 * Es la hermana de pedirJson para las peticiones que escriben, con dos diferencias. Aquí un
 * 4xx NO se trata como excepción: el 404 y el 409 son respuestas útiles que la pantalla tiene
 * que saber contar, cada una con su mensaje. Y del 200 solo interesa que haya ido bien, porque
 * los datos guardados se vuelven a leer al refrescar la tabla; el cuerpo ni se lee.
 *
 * @param {string} canal nombre del flujo de peticiones (una por fila que se guarda)
 * @param {string} metodo el método HTTP ("PUT")
 * @param {string} url la URL a la que se manda
 * @param {Object} cuerpo el objeto que viaja como JSON
 * @return {Promise<number>} el código HTTP de la respuesta
 */
async function enviarJson(canal, metodo, url, cuerpo) {
    peticionesEnVuelo[canal]?.abort();
    const controlador = new AbortController();
    peticionesEnVuelo[canal] = controlador;

    try {
        const respuesta = await fetch(url, {
            method: metodo,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(cuerpo),
            signal: controlador.signal,
        });

        return respuesta.status;
    } finally {
        cerrarCanal(canal, controlador);
    }
}

/**
 * Da por terminado un canal de peticiones.
 *
 * Los canales de guardado y de detalle llevan el id del cliente dentro del nombre, así que se
 * crea uno nuevo por cada fila que se abre o se guarda y se quedarían ahí para siempre. Se
 * comprueba que el controlador siga siendo el mismo antes de borrarlo: si mientras tanto ha
 * arrancado otra petición del canal, la entrada es SUYA y borrarla dejaría sin cancelar a la
 * que viene después.
 *
 * @param {string} canal nombre del flujo de peticiones
 * @param {AbortController} controlador el de la petición que acaba de terminar
 */
function cerrarCanal(canal, controlador) {
    if (peticionesEnVuelo[canal] === controlador) {
        delete peticionesEnVuelo[canal];
    }
}

/**
 * Deja el contenedor de la tabla en el recorrido del tabulador solo si de verdad hay algo que
 * desplazar.
 *
 * Una zona con scroll tiene que poder recorrerse con el teclado, pero una parada del tabulador
 * en un sitio donde no hay nada que hacer es una molestia para quien navega así, y en un
 * escritorio normal la tabla cabe entera y nunca se desplaza.
 */
function ajustarFocoTabla() {
    // 1px de margen: los anchos son decimales y un redondeo hace que scrollWidth salga un
    // pelín mayor que clientWidth en tablas que en realidad caben.
    const desborda = contenedorTabla.scrollWidth - contenedorTabla.clientWidth > 1;

    if (desborda) {
        contenedorTabla.setAttribute("tabindex", "0");
    } else {
        contenedorTabla.removeAttribute("tabindex");
    }
}

/**
 * Reajusta las filas que ocupan la tabla a lo ancho (los paneles y los mensajes) cuando el
 * número de columnas visibles cambia, que es lo que pasa al estrechar la ventana hasta el
 * ancho de móvil con un panel ya abierto.
 */
function ajustarColumnas() {
    const columnas = columnasVisibles();

    // El ":scope >" es importante: dentro de los paneles hay más tablas, y sus celdas no
    // tienen nada que ver con las columnas de esta.
    for (const celda of cuerpoTabla.querySelectorAll(":scope > tr > td[colspan]")) {
        celda.colSpan = columnas;
    }
}

// Se vigilan los dos: la ventana al cambiar de tamaño encoge el contenedor, y un nombre muy
// largo o un panel abierto ensanchan la tabla. Cualquiera de las dos cosas hace aparecer o
// desaparecer el desplazamiento.
//
// El requestAnimationFrame no es adorno: arrastrar el borde de la ventana dispara el
// observador decenas de veces por segundo, y cada medida de scrollWidth obliga al navegador a
// recalcular la disposición de la página. Así se mide una vez por fotograma como mucho.
let ajustePedido = false;
const observadorTabla = new ResizeObserver(() => {
    if (ajustePedido) return;
    ajustePedido = true;

    requestAnimationFrame(() => {
        ajustePedido = false;
        ajustarFocoTabla();
        ajustarColumnas();
    });
});
observadorTabla.observe(contenedorTabla);
observadorTabla.observe(cuerpoTabla.closest("table"));

// --- Enlazado del botón "Añadir Cliente" ---

// Los botones de la fila NO se enlazan aquí: van por delegación en el <tbody>, arriba, en la
// sección del despliegue. Las filas se clonan de un <template> y no existen todavía cuando
// este archivo se ejecuta, así que un querySelectorAll de '.btn-eliminar' aquí no encuentra
// ningún botón y no engancha nada (había uno y por eso no hacía nada). El borrado es de otra
// feature: cuando se implemente, su sitio es un caso más en ese manejador, junto a los de ver
// y editar.

// Este sí se enlaza directamente: vive en la cabecera de la página, fuera de la tabla, así que
// existe desde que se carga el documento y no lo destruye ningún repintado.
btnAnadirCliente.addEventListener("click", abrirAlta);

// --- Enlazado del buscador ---

/*
 * Se despliega al ENFOCARLO y se recoge al salir, si está vacío.
 *
 * Antes esto era un listener de clic en todo el documento, y ahí estaba el fallo: quien
 * llegaba al buscador con el tabulador se quedaba dentro de una píldora de 9rem sin ver el
 * campo ni lo que escribía, porque nada lo abría. focusin/focusout cubren el ratón y el
 * teclado con el mismo código (el clic acaba enfocando el input igual), y de paso ya no hace
 * falta vigilar cada clic de la página para saber cuándo cerrarlo.
 */
contenedorBuscador.addEventListener("focusin", () => {
    contenedorBuscador.classList.add("expandido");
});

contenedorBuscador.addEventListener("focusout", () => {
    // Con texto escrito se queda abierto: es un filtro activo, y plegarlo escondería la
    // razón por la que la tabla enseña lo que enseña.
    if (!inputBuscador.value.trim()) {
        contenedorBuscador.classList.remove("expandido");
    }
});

// --- Pantalla de carga ---

/** Suma una petición en vuelo y destapa el velo si es la primera. */
function mostrarCarga() {
    peticionesActivas++;
    if (pantallaCarga) {
        pantallaCarga.classList.remove("d-none");
    }
}

/** Resta una petición y tapa el velo cuando ya no queda ninguna. */
function ocultarCarga() {
    peticionesActivas--;
    if (peticionesActivas <= 0) {
        peticionesActivas = 0; // Prevenir números negativos
        if (pantallaCarga) {
            pantallaCarga.classList.add("d-none");
        }
    }
}

// --- Carga inicial ---
//
// Va al FINAL del archivo a propósito: cuando estas cinco llamadas se ejecutan, todo lo que
// necesitan —las referencias del DOM, los manejadores de eventos y los avisos emergentes— ya
// está declarado y enlazado. Si se añade algo nuevo, va ANTES de este bloque.
pintarControlesOrden();
pintarEstadoFiltros();
cargarProvincias();
cargarPoblaciones("");
cargarClientes(0);

let clientePendienteEliminar = null;

// ============================================================
// BOTÓN ELIMINAR
// ============================================================
/*
document.addEventListener(
    "click",
    (evento) => {

        const boton =
            evento.target.closest(
                ".btn-eliminar"
            );


        if (!boton) {
            return;
        }


        const idCliente =
            boton.dataset.id;


        clientePendienteEliminar =
            idCliente;


        const modalElemento =
            document.getElementById(
                "confirmarEliminarModal"
            );

// ============================================

        const modal =
            new bootstrap.Modal(
                modalElemento
            );


        modal.show();

    }
);
*/

// ============================================================
// CONFIRMAR ELIMINACIÓN
// ============================================================

function confirmarBorradoCliente(botonClickado, idClienteEliminar) {
    console.log(botonClickado);
    console.log(idClienteEliminar);

    const modalElemento =
        document.getElementById(
            "confirmarEliminarModal"
        );


    const modal =
        new bootstrap.Modal(
            modalElemento
        );


    modal.show();

    document
        .getElementById(
            "btn-confirmar-eliminar"
        )
        .addEventListener(
            "click",
            async () => {



                try {

                    const respuesta =
                        await fetch(
                            `/cliente/${idClienteEliminar}`,
                            {
                                method: "DELETE"
                            }
                        );


                    let datos = null;


                    try {

                        datos =
                            await respuesta.json();

                    } catch {

                        datos = null;

                    }


                    // Error HTTP
                    if (!respuesta.ok) {

                        throw new Error(
                            datos?.message ||
                            "No se pudo eliminar el cliente."
                        );

                    }


                    // Cerramos modal de confirmación
                    cerrarModal(
                        "confirmarEliminarModal"
                    );


                    // Mensaje de éxito
                    mostrarMensajeJ(
                        datos?.message ||
                        "Cliente eliminado correctamente"
                    );

                    // Recargamos tabla
                   /* cargarClientes(
                        paginaActual
                    );*/

                    // Avisamos al resto
                    document.dispatchEvent(
                        new CustomEvent(
                            "clientes:cambiaron"
                        )
                    );


                } catch (error) {

                    console.error(
                        "Error al eliminar cliente:",
                        error
                    );


                    cerrarModal(
                        "confirmarEliminarModal"
                    );


                    clientePendienteEliminar =
                        null;


                    mostrarMensajeJ(
                        error.message ||
                        "No se pudo eliminar el cliente."
                    );

                }

            }
        );

}



// ============================================================
// CERRAR MODAL
// ============================================================

function cerrarModal(idModal) {

    const elemento =
        document.getElementById(
            idModal
        );


    if (!elemento) {
        return;
    }


    const instancia =
        bootstrap.Modal.getInstance(
            elemento
        );


    if (instancia) {

        instancia.hide();

    }

}


// ============================================================
// REFRESCO EXTERNO
// ============================================================

document.addEventListener(
    "clientes:cambiaron",
    () => {

        cargarClientes(
            paginaActual
        );

    }
);



function mostrarMensajeJ(texto) {

    const mensaje =
        document.getElementById(
            "mensaje-modal-texto"
        );

    mensaje.textContent = texto;


    const modalElemento =
        document.getElementById(
            "mensajeModal"
        );


    const modal =
        new bootstrap.Modal(
            modalElemento
        );


    modal.show();

}
