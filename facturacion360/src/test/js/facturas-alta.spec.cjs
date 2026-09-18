const { test, expect } = require("playwright/test");
const { execFileSync } = require("node:child_process");

// Solo se ejecuta con una aplicación de pruebas local indicada expresamente.
const direccion = process.env.FACTURAS_URL_PRUEBAS;
if (!direccion || !/^http:\/\/127\.0\.0\.1:1\d{4}$/.test(direccion)) {
    throw new Error("Indica FACTURAS_URL_PRUEBAS con el puerto local aislado, entre 10000 y 19999.");
}
test.use({ baseURL: direccion, channel: "chrome", viewport: { width: 1280, height: 900 } });

async function abrirAlta(pagina) {
    await pagina.goto("/facturas.html");
    await pagina.getByRole("button", { name: /Añadir factura$/ }).click();
    // Espera a que Bootstrap termine de abrir y enfocar el modal antes de rellenarlo.
    await expect(pagina.locator("#facturaModal")).toBeFocused();
    await pagina.locator("#clienteFactura").selectOption("1");
    await pagina.getByLabel("Fecha de emisión", { exact: true }).fill("2028-09-15");
}

async function anadirLinea(pagina, descripcion, cantidad, precio, descuento = "0", iva = "21") {
    await pagina.getByRole("button", { name: "Añadir concepto", exact: true }).click();
    const indice = await pagina.locator(".concepto-factura").count() - 1;
    const ficha = pagina.locator(".concepto-factura").nth(indice);
    await ficha.getByLabel("Descripción", { exact: true }).fill(descripcion);
    await ficha.getByLabel("Cantidad", { exact: true }).fill(cantidad);
    await ficha.getByLabel("Precio unitario (€)", { exact: true }).fill(precio);
    await ficha.getByLabel("Descuento (%)", { exact: true }).fill(descuento);
    await ficha.getByLabel("IVA (%)", { exact: true }).fill(iva);
    return ficha;
}

function peticionesDeAlta(pagina) {
    const peticiones = [];
    pagina.on("request", peticion => {
        if (peticion.method() == "POST" && new URL(peticion.url()).pathname == "/factura") {
            peticiones.push(peticion.postDataJSON());
        }
    });
    return peticiones;
}

const sugerenciasHistoricas = [
    { descripcion: "Mantenimiento web", precioUnitario: 120, descuento: 5, porcentajeIva: 21 },
    { descripcion: "Mantenimiento servidor", precioUnitario: 75, descuento: 0, porcentajeIva: 10 }
];

test("3B: escritorio compacto con resumen y acciones siempre visibles", async ({ page: pagina }) => {
    await simularSugerencias(pagina);
    await abrirAlta(pagina);
    const ficha = await anadirLinea(pagina, "Servicio", "3", "19.99", "10");
    const cliente = await pagina.locator("#clienteFactura").boundingBox();
    const fecha = await pagina.locator("#fechaEmision").boundingBox();
    const estado = await pagina.locator("#estadoFactura").boundingBox();
    expect(Math.abs(cliente.y - fecha.y)).toBeLessThanOrEqual(1);
    expect(Math.abs(cliente.y - estado.y)).toBeLessThanOrEqual(1);
    expect(await pagina.locator("#facturaModal .modal-body").evaluate(elemento => elemento.scrollHeight <= elemento.clientHeight + 1)).toBe(true);
    await expect(pagina.locator("#subtotalFactura")).toHaveText("53,97 €");
    await expect(pagina.locator("#ivaFactura")).toHaveText("11,33 €");
    await expect(pagina.locator("#totalFactura")).toHaveText("65,30 €");
    await ficha.getByLabel("Descripción", { exact: true }).fill("manten");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(2);
    const opcion = await pagina.getByRole("listbox").getByRole("option").first().boundingBox();
    expect(opcion.height).toBeLessThanOrEqual(44);
    const observaciones = await pagina.locator("#observacionesFactura").boundingBox();
    const pie = await pagina.locator("#facturaModal .modal-footer").boundingBox();
    expect(observaciones.y + observaciones.height).toBeLessThanOrEqual(pie.y);
    expect(pie.y + pie.height).toBeLessThanOrEqual(900);
    await pagina.screenshot({ path: test.info().outputPath("formulario-3b-escritorio.png") });
});

test("3B: orden de teclado, foco al eliminar y resumen único a cero", async ({ page: pagina }) => {
    await abrirAlta(pagina);
    const ficha = await anadirLinea(pagina, "Servicio", "3", "19.99", "10");
    await ficha.getByLabel("Descripción", { exact: true }).focus();
    for (const nombre of ["cantidad", "precioUnitario", "descuento", "porcentajeIva"]) {
        await pagina.keyboard.press("Tab");
        await expect(ficha.locator('[name="' + nombre + '"]')).toBeFocused();
    }
    await pagina.keyboard.press("Tab");
    await expect(ficha.getByRole("button", { name: "Eliminar concepto 1", exact: true })).toBeFocused();
    await pagina.keyboard.press("Tab");
    await expect(pagina.locator("#botonAnadirConcepto")).toBeFocused();
    await pagina.keyboard.press("Tab");
    await expect(pagina.locator("#observacionesFactura")).toBeFocused();
    await pagina.keyboard.press("Shift+Tab");
    await expect(pagina.locator("#botonAnadirConcepto")).toBeFocused();
    await ficha.getByRole("button", { name: "Eliminar concepto 1", exact: true }).focus();
    await pagina.keyboard.press("Enter");
    await expect(pagina.locator("#botonAnadirConcepto")).toBeFocused();
    for (const identificador of ["subtotalFactura", "ivaFactura", "totalFactura"]) {
        await expect(pagina.locator("#" + identificador)).toHaveCount(1);
        await expect(pagina.locator("#" + identificador)).toHaveText("0,00 €");
    }
    await expect(pagina.locator(".resumen-alta-factura")).toHaveAttribute("aria-live", "polite");
    await expect(pagina.locator(".resumen-alta-factura")).toContainText("provisionales");
});

for (const anchura of [320, 390]) {
    test("3B: móvil " + anchura + " con varias líneas, sugerencias y acciones táctiles", async ({ page: pagina }) => {
        await pagina.setViewportSize({ width: anchura, height: 844 });
        await simularSugerencias(pagina);
        await abrirAlta(pagina);
        for (let numero = 1; numero <= 3; numero++) {
            await anadirLinea(pagina, "Servicio " + numero, "1", "10");
        }
        const ultima = pagina.locator(".concepto-factura").last();
        const anadir = await pagina.locator("#botonAnadirConcepto").boundingBox();
        const ultimaPosicion = await ultima.boundingBox();
        expect(anadir.y).toBeGreaterThanOrEqual(ultimaPosicion.y + ultimaPosicion.height);
        await ultima.getByLabel("Descripción", { exact: true }).fill("manten");
        await expect(ultima.getByRole("option")).toHaveCount(2);
        await ultima.getByLabel("Descripción", { exact: true }).press("ArrowDown");
        const activa = await ultima.getByRole("option", { selected: true }).boundingBox();
        const cabecera = await pagina.locator("#facturaModal .modal-header").boundingBox();
        const pie = await pagina.locator("#facturaModal .modal-footer").boundingBox();
        expect(activa.y).toBeGreaterThanOrEqual(cabecera.y + cabecera.height);
        expect(activa.y + activa.height).toBeLessThanOrEqual(pie.y);
        expect(pie.y + pie.height).toBeLessThanOrEqual(844);
        expect(await pagina.locator("#facturaModal .modal-body").evaluate(elemento => elemento.scrollWidth <= elemento.clientWidth)).toBe(true);
        expect(await pagina.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        for (const selector of ["#botonAnadirConcepto", ".eliminar-concepto", "#botonGuardarFactura", '[data-bs-dismiss="modal"]']) {
            for (const boton of await pagina.locator("#facturaModal").locator(selector).all()) {
                expect((await boton.boundingBox()).height).toBeGreaterThanOrEqual(44);
            }
        }
        await expect(ultima.locator(".eliminar-concepto")).toHaveCSS("color", "rgb(185, 28, 28)");
        await pagina.screenshot({ path: test.info().outputPath("formulario-3b-movil-" + anchura + ".png") });
    });
}

async function simularSugerencias(pagina, sugerencias = sugerenciasHistoricas) {
    await pagina.route("**/factura/conceptos/sugerencias?*", ruta => ruta.fulfill({ json: sugerencias }));
}

test("sugerencias: un carácter no consulta y el debounce espera 250 ms tras la última tecla", async ({ page: pagina }) => {
    await pagina.clock.install({ time: new Date("2028-09-15T10:00:00Z") });
    const consultas = [];
    await pagina.route("**/factura/conceptos/sugerencias?*", ruta => {
        consultas.push(new URL(ruta.request().url()).searchParams.get("texto"));
        return ruta.fulfill({ json: sugerenciasHistoricas });
    });
    await abrirAlta(pagina);
    await pagina.getByRole("button", { name: "Añadir concepto", exact: true }).click();
    // Detiene también el avance automático entre acciones, no solo fija la fecha.
    await pagina.clock.pauseAt(new Date("2028-09-15T11:00:00Z"));
    const descripcion = pagina.getByRole("combobox", { name: "Descripción", exact: true });
    await descripcion.fill("m");
    await pagina.clock.runFor(500);
    expect(consultas).toEqual([]);
    await descripcion.fill("ma");
    await pagina.clock.runFor(100);
    await descripcion.fill("man");
    await pagina.clock.runFor(100);
    await descripcion.fill("manten");
    await pagina.clock.runFor(249);
    expect(consultas).toEqual([]);
    await pagina.clock.runFor(1);
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(2);
    expect(consultas).toEqual(["manten"]);
});

test("sugerencias: selección explícita con ratón conserva cantidad y permite editar todos los campos", async ({ page: pagina }) => {
    await simularSugerencias(pagina);
    await abrirAlta(pagina);
    const ficha = await anadirLinea(pagina, "Manual", "7", "10", "2", "4");
    const descripcion = ficha.getByLabel("Descripción", { exact: true });
    await descripcion.fill("ma");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(2);
    await expect(ficha.getByLabel("Precio unitario (€)", { exact: true })).toHaveValue("10");
    await expect(ficha.getByLabel("Descuento (%)", { exact: true })).toHaveValue("2");
    await expect(ficha.getByLabel("IVA (%)", { exact: true })).toHaveValue("4");
    await pagina.screenshot({ path: test.info().outputPath("sugerencias-escritorio.png") });
    await pagina.getByRole("listbox").getByRole("option", { name: /Mantenimiento web/ }).click();
    await expect(descripcion).toHaveValue("Mantenimiento web");
    await expect(ficha.getByLabel("Cantidad", { exact: true })).toHaveValue("7");
    for (const [campo, valor] of [["precioUnitario", "120"], ["descuento", "5"], ["porcentajeIva", "21"]]) {
        await expect(ficha.locator('[name="' + campo + '"]')).toHaveValue(valor);
        await expect(ficha.locator('[name="' + campo + '"]')).toBeEditable();
        await ficha.locator('[name="' + campo + '"]').fill("6");
        await expect(ficha.locator('[name="' + campo + '"]')).toHaveValue("6");
    }
    await descripcion.fill("Descripción editada");
    await expect(descripcion).toHaveValue("Descripción editada");
    await ficha.getByLabel("Cantidad", { exact: true }).fill("8");
    await expect(ficha.getByLabel("Cantidad", { exact: true })).toHaveValue("8");
});

test("sugerencias: flechas, Enter, Escape y click fuera con foco accesible", async ({ page: pagina }) => {
    await simularSugerencias(pagina);
    await abrirAlta(pagina);
    await anadirLinea(pagina, "Manual", "1", "10");
    const descripcion = pagina.getByRole("combobox", { name: "Descripción", exact: true });
    await descripcion.fill("ma");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(2);
    await descripcion.press("Enter");
    await expect(descripcion).toHaveValue("ma");
    await descripcion.press("ArrowDown");
    await descripcion.press("ArrowDown");
    await descripcion.press("ArrowUp");
    await expect(pagina.getByRole("listbox").getByRole("option", { selected: true })).toContainText("Mantenimiento web");
    await expect(descripcion).toHaveAttribute("aria-activedescendant", await pagina.getByRole("listbox").getByRole("option").first().getAttribute("id"));
    await expect(descripcion).toBeFocused();
    await descripcion.press("Enter");
    await expect(descripcion).toHaveValue("Mantenimiento web");
    await expect(descripcion).toHaveAttribute("aria-expanded", "false");
    await descripcion.fill("manten");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(2);
    await descripcion.press("Escape");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(0);
    await expect(pagina.locator("#facturaModal")).toBeVisible();
    await expect(descripcion).toHaveValue("manten");
    await descripcion.fill("mant");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(2);
    await pagina.getByLabel("Observaciones", { exact: true }).click();
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(0);
});

// Ignora AbortSignal deliberadamente para probar también la defensa por versión,
// incluso cuando un transporte ya no puede cancelar la respuesta antigua.
async function controlarRespuestasSugerencias(pagina) {
    await pagina.addInitScript(() => {
        const consultaOriginal = window.fetch;
        window.consultasSugerencias = [];
        window.fetch = function (ruta, opciones) {
            if (String(ruta).startsWith("/factura/conceptos/sugerencias?")) {
                return new Promise((resolver, rechazar) => window.consultasSugerencias.push({ resolver, rechazar }));
            }
            return consultaOriginal(ruta, opciones);
        };
    });
}

async function responderSugerencias(pagina, indice, descripcion, fallo = false) {
    await pagina.evaluate(async ({ indice, descripcion, fallo }) => {
        const consulta = window.consultasSugerencias[indice];
        if (fallo) consulta.rechazar(new Error("Fallo simulado"));
        else consulta.resolver(new Response(JSON.stringify([{ descripcion, precioUnitario: 80, descuento: 0, porcentajeIva: 21 }])));
        await new Promise(resolver => setTimeout(resolver, 0));
    }, { indice, descripcion, fallo });
}

test("sugerencias: ignora respuestas y errores fuera de orden y tras cerrar, eliminar o cerrar modal", async ({ page: pagina }) => {
    const errores = [];
    pagina.on("pageerror", error => errores.push(error.message));
    await controlarRespuestasSugerencias(pagina);
    await abrirAlta(pagina);
    await pagina.getByRole("button", { name: "Añadir concepto", exact: true }).click();
    const descripcion = pagina.getByRole("combobox", { name: "Descripción", exact: true });
    await descripcion.fill("antigua");
    await pagina.waitForFunction(() => window.consultasSugerencias.length == 1);
    await descripcion.fill("intermedia");
    await pagina.waitForFunction(() => window.consultasSugerencias.length == 2);
    await descripcion.fill("actual");
    await pagina.waitForFunction(() => window.consultasSugerencias.length == 3);
    await responderSugerencias(pagina, 2, "Actual");
    await expect(pagina.getByRole("listbox").getByRole("option")).toContainText("Actual");
    await responderSugerencias(pagina, 0, "Obsoleta");
    await responderSugerencias(pagina, 1, "", true);
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(1);
    await expect(pagina.getByRole("listbox").getByRole("option")).toContainText("Actual");
    for (const [indice, accion] of ["escape", "fuera", "modal", "eliminar"].entries()) {
        await descripcion.fill("pendiente " + accion);
        await pagina.waitForFunction(numero => window.consultasSugerencias.length == numero, indice + 4);
        if (accion == "escape") await descripcion.press("Escape");
        if (accion == "fuera") await pagina.getByLabel("Observaciones", { exact: true }).click();
        if (accion == "modal") await pagina.getByRole("button", { name: "Cancelar", exact: true }).click();
        if (accion == "eliminar") await pagina.getByRole("button", { name: "Eliminar concepto 1", exact: true }).click();
        await responderSugerencias(pagina, indice + 3, "No debe reaparecer");
        await expect(pagina.locator('[role="option"]')).toHaveCount(0);
        if (accion == "modal") {
            await pagina.getByRole("button", { name: /Añadir factura$/ }).click();
            // Bootstrap enfoca el modal al terminar la animación de apertura.
            await expect(pagina.locator("#facturaModal")).toBeFocused();
        }
    }
    expect(errores).toEqual([]);
});

test("sugerencias: dos líneas no mezclan resultados ni datos", async ({ page: pagina }) => {
    await controlarRespuestasSugerencias(pagina);
    await abrirAlta(pagina);
    const primera = await anadirLinea(pagina, "Manual A", "3", "10");
    const segunda = await anadirLinea(pagina, "Manual B", "5", "20");
    await primera.getByLabel("Descripción", { exact: true }).fill("primera");
    await pagina.waitForFunction(() => window.consultasSugerencias.length == 1);
    await segunda.getByLabel("Descripción", { exact: true }).fill("segunda");
    await pagina.waitForFunction(() => window.consultasSugerencias.length == 2);
    await responderSugerencias(pagina, 1, "Histórico B");
    await responderSugerencias(pagina, 0, "Histórico A obsoleto");
    await expect(primera.getByRole("option")).toHaveCount(0);
    await segunda.getByRole("option").click();
    await expect(primera.getByLabel("Descripción", { exact: true })).toHaveValue("primera");
    await expect(primera.getByLabel("Precio unitario (€)", { exact: true })).toHaveValue("10");
    await expect(segunda.getByLabel("Descripción", { exact: true })).toHaveValue("Histórico B");
    await expect(segunda.getByLabel("Cantidad", { exact: true })).toHaveValue("5");
});

for (const estado of [500, 0]) {
    test("sugerencias: fallo " + estado + " no impide el alta manual", async ({ page: pagina }) => {
        await pagina.route("**/factura/conceptos/sugerencias?*", ruta => estado ? ruta.fulfill({ status: estado }) : ruta.abort());
        await abrirAlta(pagina);
        const ficha = await anadirLinea(pagina, "Manual", "1", "10");
        const consulta = pagina.waitForRequest("**/factura/conceptos/sugerencias?*");
        await ficha.getByLabel("Descripción", { exact: true }).fill("Servicio manual");
        await consulta;
        await expect(ficha.getByLabel("Descripción", { exact: true })).toHaveValue("Servicio manual");
        await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(0);
        await expect(pagina.locator("#mensaje-formulario-factura")).toBeHidden();
        await pagina.route("**/factura", ruta => ruta.fulfill({ status: 201, json: { numeroFactura: "F-2028-0099", total: 12.1 } }));
        await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
        await expect(pagina.locator("#mensaje-facturas")).toContainText("F-2028-0099");
    });
}

test("sugerencias: valores históricos ausentes quedan vacíos y editables", async ({ page: pagina }) => {
    await simularSugerencias(pagina, [{ descripcion: "Servicio antiguo", precioUnitario: null, descuento: null, porcentajeIva: null }]);
    await abrirAlta(pagina);
    await anadirLinea(pagina, "Manual", "4", "10");
    await pagina.getByLabel("Descripción", { exact: true }).fill("servicio");
    await pagina.getByRole("listbox").getByRole("option").click();
    for (const campo of ["precioUnitario", "descuento", "porcentajeIva"]) {
        await expect(pagina.locator('[name="' + campo + '"]')).toHaveValue("");
        await expect(pagina.locator('[name="' + campo + '"]')).toBeEditable();
    }
    await expect(pagina.getByLabel("Cantidad", { exact: true })).toHaveValue("4");
});

test("sugerencias: móvil sin desbordamiento, máximo ocho y descripciones tratadas como texto", async ({ page: pagina }) => {
    await pagina.setViewportSize({ width: 390, height: 844 });
    await simularSugerencias(pagina, Array.from({ length: 12 }, (_, indice) => ({
        descripcion: "<img src=x onerror=alert(1)> Servicio " + indice, precioUnitario: 120, descuento: 0, porcentajeIva: 21
    })));
    await abrirAlta(pagina);
    await pagina.getByRole("button", { name: "Añadir concepto", exact: true }).click();
    const descripcion = pagina.getByLabel("Descripción", { exact: true });
    await descripcion.fill("Servicio");
    await expect(pagina.getByRole("listbox").getByRole("option")).toHaveCount(8);
    await descripcion.press("ArrowDown");
    await expect(pagina.getByRole("listbox").getByRole("option", { selected: true })).toBeVisible();
    expect(await pagina.locator(".sugerencias-concepto img").count()).toBe(0);
    expect(await pagina.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    expect(await pagina.locator("#facturaModal .modal-body").evaluate(elemento => elemento.scrollWidth <= elemento.clientWidth)).toBe(true);
    await pagina.screenshot({ path: test.info().outputPath("sugerencias-movil.png") });
});

test("E2E sugerencias: histórico reciente, cantidad intacta y alta real con MySQL", async ({ page: pagina }) => {
    consultarBaseAislada("SELECT COUNT(*) FROM facturas;");
    for (const [fecha, descripcion, precio, descuento] of [
        ["2041-01-01", "Mantenimiento web", 120, 5],
        ["2040-01-01", "Mantenimiento servidor", 75, 0],
        ["2039-01-01", "mantenimiento web", 90, 0]
    ]) {
        const respuesta = await pagina.request.post("/factura", { data: {
            idCliente: 1, fechaEmision: fecha, estado: "EMITIDA", observaciones: "Histórico sintético 3A.1",
            conceptos: [{ descripcion, cantidad: 9, precioUnitario: precio, descuento, porcentajeIva: 21 }]
        } });
        expect(respuesta.status()).toBe(201);
    }
    const limiteHistorico = Number(consultarBaseAislada("SELECT MAX(idfactura) FROM facturas;"));
    const consultaHistorico = "SELECT c.* FROM conceptos c WHERE c.idfactura<=" + limiteHistorico + " ORDER BY c.idconcepto;";
    const historicoAntes = consultarBaseAislada(consultaHistorico);
    await abrirAlta(pagina);
    await pagina.getByLabel("Fecha de emisión", { exact: true }).fill("2042-01-01");
    const ficha = await anadirLinea(pagina, "Manual", "3", "10");
    await ficha.getByLabel("Descripción", { exact: true }).fill("manten");
    await expect(pagina.getByRole("listbox").getByRole("option", { name: /Mantenimiento web/ })).toHaveCount(1);
    await expect(pagina.getByRole("listbox").getByRole("option").first()).toContainText("Mantenimiento web");
    await pagina.getByRole("listbox").getByRole("option", { name: /Mantenimiento web/ }).click();
    await expect(ficha.getByLabel("Cantidad", { exact: true })).toHaveValue("3");
    await expect(ficha.getByLabel("Precio unitario (€)", { exact: true })).toHaveValue("120");
    await expect(ficha.getByLabel("Descuento (%)", { exact: true })).toHaveValue("5");
    await expect(ficha.getByLabel("IVA (%)", { exact: true })).toHaveValue("21");
    const peticiones = peticionesDeAlta(pagina);
    const pendiente = pagina.waitForResponse(respuesta => new URL(respuesta.url()).pathname == "/factura" && respuesta.request().method() == "POST");
    await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
    const respuesta = await pendiente;
    expect(respuesta.status()).toBe(201);
    const factura = await respuesta.json();
    expect(factura.numeroFactura).toMatch(/^F-2042-\d{4}$/);
    expect([factura.subtotal, factura.importeIva, factura.total]).toEqual([342, 71.82, 413.82]);
    expect(peticiones).toHaveLength(1);
    expect(peticiones[0].conceptos).toEqual([{ descripcion: "Mantenimiento web", cantidad: 3, precioUnitario: 120, descuento: 5, porcentajeIva: 21 }]);
    expect(Number.isInteger(factura.idFactura)).toBe(true);
    expect(consultarBaseAislada("SELECT descripcion,cantidad,precio_unitario,descuento,porcentaje_iva,base_imponible,importe_iva,total FROM conceptos WHERE idfactura=" + factura.idFactura + ";"))
        .toBe("Mantenimiento web\t3\t120.00\t5.00\t21.00\t342.00\t71.82\t413.82");
    expect(consultarBaseAislada(consultaHistorico)).toBe(historicoAntes);
    await expect(pagina.locator("#mensaje-facturas")).toContainText(factura.numeroFactura);
});

test("añadir, modificar, eliminar y recalcular conceptos", async ({ page: pagina }) => {
    await abrirAlta(pagina);
    const primera = await anadirLinea(pagina, "Servicio", "3", "19.99", "10");
    await expect(pagina.locator("#totalFactura")).toHaveText("65,30 €");
    await anadirLinea(pagina, "Material", "2", "10", "0", "0");
    await expect(pagina.locator("#totalFactura")).toHaveText("85,30 €");
    await primera.getByLabel("Cantidad", { exact: true }).fill("1");
    await expect(pagina.locator("#totalFactura")).toHaveText("41,77 €");
    await pagina.getByRole("button", { name: "Eliminar concepto 1", exact: true }).click();
    await expect(pagina.locator(".concepto-factura")).toHaveCount(1);
    await expect(pagina.locator("#totalFactura")).toHaveText("20,00 €");
    await expect(pagina.getByRole("button", { name: "Eliminar concepto 1", exact: true })).toBeVisible();
});

test("redondeo provisional de descuentos e IVA por línea", async ({ page: pagina }) => {
    await abrirAlta(pagina);
    const ficha = await anadirLinea(pagina, "Redondeo", "1", "2.30", "5", "0");
    await expect(pagina.locator("#totalFactura")).toHaveText("2,19 €");
    await ficha.getByLabel("Precio unitario (€)", { exact: true }).fill("2.32");
    await ficha.getByLabel("Descuento (%)", { exact: true }).fill("6.25");
    await expect(pagina.locator("#totalFactura")).toHaveText("2,18 €");
    await ficha.getByLabel("Precio unitario (€)", { exact: true }).fill("0.05");
    await ficha.getByLabel("Descuento (%)", { exact: true }).fill("10");
    await ficha.getByLabel("IVA (%)", { exact: true }).fill("10");
    await expect(pagina.locator("#totalFactura")).toHaveText("0,06 €");
    await ficha.getByLabel("Precio unitario (€)", { exact: true }).fill("0.03");
    await ficha.getByLabel("Descuento (%)", { exact: true }).fill("0");
    await ficha.getByLabel("IVA (%)", { exact: true }).fill("21");
    await anadirLinea(pagina, "Segunda", "1", "0.03");
    await expect(pagina.locator("#ivaFactura")).toHaveText("0,02 €");
    await expect(pagina.locator("#totalFactura")).toHaveText("0,08 €");
});

test("JSON exacto, una POST y bloqueo de un segundo envío pendiente", async ({ page: pagina }) => {
    await abrirAlta(pagina);
    await anadirLinea(pagina, " Servicio ", "2", "100", "10", "21");
    const peticiones = peticionesDeAlta(pagina);
    let liberar;
    const espera = new Promise(resolver => { liberar = resolver; });
    await pagina.route("**/factura", async ruta => {
        await espera;
        await ruta.fulfill({ status: 201, contentType: "application/json",
            body: JSON.stringify({ numeroFactura: "F-2028-0123", total: 217.80 }) });
    });
    await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
    await expect(pagina.locator("#botonGuardarFactura")).toBeDisabled();
    await expect(pagina.locator("#clienteFactura")).toBeDisabled();
    await expect(pagina.getByLabel("Cantidad", { exact: true })).toBeDisabled();
    await expect(pagina.getByRole("button", { name: "Añadir concepto", exact: true })).toBeDisabled();
    // Simula un segundo submit incluso si el botón ya está deshabilitado.
    await pagina.locator("#formularioFactura").dispatchEvent("submit");
    await expect.poll(() => peticiones.length).toBe(1);
    expect(peticiones[0]).toEqual({ idCliente: 1, fechaEmision: "2028-09-15", estado: "BORRADOR", observaciones: "",
        conceptos: [{ descripcion: "Servicio", cantidad: 2, precioUnitario: 100, descuento: 10, porcentajeIva: 21 }] });
    liberar();
    await expect(pagina.locator("#mensaje-facturas")).toContainText("F-2028-0123");
    await expect(pagina.locator("#mensaje-facturas")).toContainText("217,80");
    expect(peticiones).toHaveLength(1);
    await expect(pagina.locator("#facturaModal")).toBeHidden();
    await pagina.getByRole("button", { name: /Añadir factura$/ }).click();
    await expect(pagina.locator(".concepto-factura")).toHaveCount(0);
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
});

for (const caso of [
    { estado: 400, mensaje: "rechazado los datos" },
    { estado: 409, mensaje: "asignar un número" },
    { estado: 500, mensaje: "no pudo completar" },
    { estado: 0, mensaje: "No se pudo conectar" }
]) {
    test("conserva formulario y permite reintentar ante error " + caso.estado, async ({ page: pagina }) => {
        await abrirAlta(pagina);
        await anadirLinea(pagina, "Conservar", "2", "10");
        await pagina.getByLabel("Observaciones", { exact: true }).fill("No perder estos datos");
        await pagina.route("**/factura", async ruta => {
            if (caso.estado == 0) await ruta.abort("connectionrefused");
            else await ruta.fulfill({ status: caso.estado, body: "SQLException: dato interno que no debe mostrarse" });
        });
        await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
        await expect(pagina.locator("#mensaje-formulario-factura")).toContainText(caso.mensaje);
        await expect(pagina.locator("#mensaje-formulario-factura")).not.toContainText("SQLException");
        await expect(pagina.locator("#facturaModal")).toBeVisible();
        await expect(pagina.getByLabel("Descripción", { exact: true })).toHaveValue("Conservar");
        await expect(pagina.getByLabel("Observaciones", { exact: true })).toHaveValue("No perder estos datos");
        await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
        await expect(pagina.locator("#clienteFactura")).toBeEnabled();
        await expect(pagina.getByLabel("Cantidad", { exact: true })).toBeEnabled();
    });
}

test("borrador vacío válido y emisión sin conceptos rechazada", async ({ page: pagina }) => {
    await abrirAlta(pagina);
    const peticiones = peticionesDeAlta(pagina);
    await pagina.locator("#estadoFactura").selectOption("EMITIDA");
    await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
    await expect(pagina.locator("#mensaje-formulario-factura")).toContainText("al menos un concepto");
    expect(peticiones).toHaveLength(0);
    await pagina.locator("#estadoFactura").selectOption("BORRADOR");
    await pagina.route("**/factura", ruta => ruta.fulfill({ status: 201, contentType: "application/json",
        body: JSON.stringify({ numeroFactura: "F-2028-0001", total: 0 }) }));
    await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
    await expect(pagina.locator("#facturaModal")).toBeHidden();
    expect(peticiones).toHaveLength(1);
    expect(peticiones[0].conceptos).toEqual([]);
});

test("campos inválidos impiden la POST", async ({ page: pagina }) => {
    await abrirAlta(pagina);
    const ficha = await anadirLinea(pagina, "Válido", "1", "10");
    const peticiones = peticionesDeAlta(pagina);
    for (const [campo, incorrecto, correcto] of [
        ["Descripción", "   ", "Válido"], ["Cantidad", "1.5", "1"], ["Cantidad", "0", "1"],
        ["Precio unitario (€)", "-1", "10"], ["Precio unitario (€)", "1.001", "10"],
        ["Descuento (%)", "100.01", "0"], ["IVA (%)", "100", "21"]
    ]) {
        await ficha.getByLabel(campo, { exact: true }).fill(incorrecto);
        await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
        expect(await pagina.locator("#formularioFactura").evaluate(formulario => formulario.checkValidity())).toBe(false);
        expect(peticiones).toHaveLength(0);
        await ficha.getByLabel(campo, { exact: true }).fill(correcto);
    }
});

test("modal utilizable en móvil y conceptos accesibles por teclado", async ({ page: pagina }) => {
    await pagina.setViewportSize({ width: 390, height: 844 });
    await abrirAlta(pagina);
    await pagina.getByRole("button", { name: "Añadir concepto", exact: true }).focus();
    await pagina.keyboard.press("Enter");
    await expect(pagina.getByLabel("Descripción", { exact: true })).toBeFocused();
    await pagina.screenshot({ path: test.info().outputPath("alta-movil.png") });
    const anchura = await pagina.locator("#facturaModal .modal-body").evaluate(elemento => ({ contenido: elemento.scrollWidth, visible: elemento.clientWidth }));
    expect(anchura.contenido).toBeLessThanOrEqual(anchura.visible + 1);
    await pagina.getByRole("button", { name: "Eliminar concepto 1", exact: true }).focus();
    await pagina.keyboard.press("Enter");
    await expect(pagina.getByRole("button", { name: "Añadir concepto", exact: true })).toBeFocused();
});

async function simularBorrador(pagina, estado = "BORRADOR") {
    const factura = { idFactura: 7, idCliente: 999, nombreCliente: "Cliente antiguo", numeroFactura: "F-2026-0007",
        fechaEmision: "2026-09-15", estado, observaciones: "Original", subtotal: 25, importeIva: 4.2, total: 29.2 };
    const conceptos = [
        { descripcion: "Servicio anterior", cantidad: 2, precioUnitario: 10, descuento: 0, porcentajeIva: 21 },
        { descripcion: "Eliminar", cantidad: 1, precioUnitario: 5, descuento: 0, porcentajeIva: 0 }
    ];
    await pagina.route("**/factura/buscar?*", ruta => ruta.fulfill({ json: [factura,
        ...["EMITIDA", "ANULADA"].map((valor, indice) => ({ ...factura, idFactura: 8 + indice, estado: valor }))] }));
    await pagina.route("**/factura/7/detalle", ruta => ruta.fulfill({ json: {
        factura, cliente: { nombre: "Cliente antiguo", nifCif: "ANTIGUO" }, conceptos
    } }));
    return factura;
}

async function abrirEdicionSimulada(pagina) {
    await pagina.goto("/facturas.html");
    await pagina.getByRole("button", { name: "Editar borrador F-2026-0007", exact: true }).click();
    await expect(pagina.locator("#facturaModal")).toBeFocused();
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
}

test("4: precarga, edita conceptos y envía una sola PUT con respuesta definitiva", async ({ page: pagina }) => {
    const factura = await simularBorrador(pagina);
    await simularSugerencias(pagina);
    await abrirEdicionSimulada(pagina);
    await expect(pagina.getByRole("button", { name: /^Editar borrador/ })).toHaveCount(1);
    await expect(pagina.locator("#clienteFactura")).toHaveValue("999");
    await expect(pagina.locator("#fechaEmision")).toHaveValue("2026-09-15");
    await expect(pagina.locator("#estadoFactura")).toBeDisabled();
    await expect(pagina.locator("#observacionesFactura")).toHaveValue("Original");
    await expect(pagina.locator(".concepto-factura")).toHaveCount(2);
    const primera = pagina.locator(".concepto-factura").first();
    await primera.getByLabel("Cantidad", { exact: true }).fill("3");
    await pagina.getByRole("button", { name: "Eliminar concepto 2", exact: true }).click();
    const nueva = await anadirLinea(pagina, "manten", "2", "5");
    await nueva.getByLabel("Descripción", { exact: true }).fill("manten");
    await nueva.getByRole("option", { name: /Mantenimiento web/ }).click();
    const peticiones = [];
    pagina.on("request", peticion => { if (["POST", "PUT"].includes(peticion.method())) peticiones.push(peticion); });
    let liberar;
    const espera = new Promise(resolver => { liberar = resolver; });
    await pagina.route("**/factura/7/borrador", async ruta => {
        await espera;
        factura.total = 999;
        await ruta.fulfill({ json: factura });
    });
    await pagina.getByRole("button", { name: "Guardar cambios", exact: true }).click();
    await expect(pagina.locator("#botonGuardarFactura")).toBeDisabled();
    await pagina.locator("#formularioFactura").dispatchEvent("submit");
    await pagina.keyboard.press("Escape");
    await expect(pagina.locator("#facturaModal")).toBeVisible();
    await expect.poll(() => peticiones.length).toBe(1);
    expect(peticiones[0].method()).toBe("PUT");
    expect(peticiones[0].postDataJSON()).toEqual({ idCliente: 999, fechaEmision: "2026-09-15", estado: "BORRADOR", observaciones: "Original",
        conceptos: [{ descripcion: "Servicio anterior", cantidad: 3, precioUnitario: 10, descuento: 0, porcentajeIva: 21 },
            { descripcion: "Mantenimiento web", cantidad: 2, precioUnitario: 120, descuento: 5, porcentajeIva: 21 }] });
    liberar();
    await expect(pagina.locator("#mensaje-facturas")).toContainText("actualizada. Total confirmado: 999,00");
    await expect(pagina.locator("#facturaModal")).toBeHidden();
    expect(peticiones).toHaveLength(1);
    await pagina.locator("#botonAltaFactura").click();
    await expect(pagina.locator("#botonGuardarFactura")).toHaveText("Guardar factura");
    await expect(pagina.locator(".concepto-factura")).toHaveCount(0);
    await expect(pagina.locator("#estadoFactura")).toBeEnabled();
});

for (const estado of [400, 404, 409, 500, 0]) {
    test("4: error de edición " + estado + " conserva datos y permite reintentar", async ({ page: pagina }) => {
        const factura = await simularBorrador(pagina);
        await abrirEdicionSimulada(pagina);
        await pagina.locator("#observacionesFactura").fill("No perder");
        await pagina.route("**/factura/7/borrador", ruta => estado == 0 ? ruta.abort("connectionrefused") : ruta.fulfill({ status: estado, body: "SQLException secreto" }));
        await pagina.locator("#botonGuardarFactura").click();
        await expect(pagina.locator("#mensaje-formulario-factura")).toBeVisible();
        await expect(pagina.locator("#mensaje-formulario-factura")).not.toContainText("SQLException");
        await expect(pagina.locator("#observacionesFactura")).toHaveValue("No perder");
        await expect(pagina.locator(".concepto-factura")).toHaveCount(2);
        await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
        await expect(pagina.locator("#estadoFactura")).toBeDisabled();
        await pagina.route("**/factura/7/borrador", ruta => ruta.fulfill({ json: factura }));
        await pagina.locator("#botonGuardarFactura").click();
        await expect(pagina.locator("#facturaModal")).toBeHidden();
    });
}

test("4: cancelar precarga e iniciar alta ignora la respuesta tardía", async ({ page: pagina }) => {
    const factura = await simularBorrador(pagina);
    let liberar;
    const espera = new Promise(resolver => { liberar = resolver; });
    let solicitada = false;
    await pagina.route("**/factura/7/detalle", async ruta => {
        solicitada = true;
        await espera;
        await ruta.fulfill({ json: { factura, cliente: { nombre: "Antiguo", nifCif: "A" }, conceptos: [] } });
    });
    await pagina.goto("/facturas.html");
    await pagina.getByRole("button", { name: "Editar borrador F-2026-0007", exact: true }).click();
    await expect.poll(() => solicitada).toBe(true);
    await expect(pagina.locator("#facturaModal")).toBeFocused();
    await expect(pagina.locator("#botonGuardarFactura")).toBeDisabled();
    await pagina.getByRole("button", { name: "Cancelar", exact: true }).click();
    await expect(pagina.locator("#facturaModal")).toBeHidden();
    await pagina.locator("#botonAltaFactura").click();
    await expect(pagina.locator("#facturaModal")).toBeFocused();
    const respuesta = pagina.waitForResponse("**/factura/7/detalle");
    liberar();
    await respuesta;
    await expect(pagina.locator("#facturaModalLabel")).toHaveText("Dar de alta una factura");
    await expect(pagina.locator("#clienteFactura")).toHaveValue("");
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
});

test("4: enlace forzado no habilita edición de una factura emitida", async ({ page: pagina }) => {
    await simularBorrador(pagina, "EMITIDA");
    await pagina.goto("/facturas.html?editar=7");
    await expect(pagina.locator("#mensaje-formulario-factura")).toContainText("ya no está en BORRADOR");
    await expect(pagina.locator("#botonGuardarFactura")).toBeDisabled();
    await expect(pagina.getByRole("button", { name: /^Editar borrador/ })).toHaveCount(0);
});

test("4: guardado correcto con refresco fallido avisa sin ocultar el resultado", async ({ page: pagina }) => {
    const factura = await simularBorrador(pagina);
    await abrirEdicionSimulada(pagina);
    await pagina.route("**/factura/7/borrador", ruta => ruta.fulfill({ json: factura }));
    await pagina.route("**/factura/buscar?*", ruta => ruta.fulfill({ status: 500 }));
    await pagina.locator("#botonGuardarFactura").click();
    await expect(pagina.locator("#mensaje-facturas")).toContainText("actualizada");
    await expect(pagina.locator("#mensaje-facturas")).toContainText("No se pudo actualizar el listado");
    await expect(pagina.locator("#mensaje-facturas")).toHaveClass(/aviso-error/);
});

test("4: editar desde trimestre conserva filtro y refresca sus totales", async ({ page: pagina }) => {
    const factura = await simularBorrador(pagina);
    let consultas = 0;
    await pagina.route("**/factura/trimestral?*", ruta => {
        consultas++;
        return ruta.fulfill({ json: { anio: 2026, trimestre: 3, facturas: [factura], subtotal: factura.subtotal,
            importeIva: factura.importeIva, total: factura.total } });
    });
    await pagina.goto("/facturas.html");
    await pagina.locator("#anioTrimestre").fill("2026");
    await pagina.locator("#trimestreFactura").selectOption("3");
    await pagina.locator("#botonListarTrimestre").click();
    await expect(pagina.locator("#totalTrimestre")).toHaveText("29,20 €");
    await pagina.getByRole("button", { name: "Editar borrador F-2026-0007", exact: true }).click();
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
    await pagina.route("**/factura/7/borrador", ruta => {
        factura.total = 42;
        return ruta.fulfill({ json: factura });
    });
    await pagina.locator("#botonGuardarFactura").click();
    await expect(pagina.locator("#totalTrimestre")).toHaveText("42,00 €");
    await expect(pagina.locator("#resumenTrimestral")).toBeVisible();
    expect(consultas).toBe(2);
});

test("4 E2E: editar desde visor en móvil guarda y devuelve el detalle real", async ({ page: pagina }) => {
    consultarBaseAislada("SELECT COUNT(*) FROM facturas;");
    const datos = { idCliente: 1, fechaEmision: "2050-01-02", estado: "BORRADOR", observaciones: "Editar E2E",
        conceptos: [{ descripcion: "Primera", cantidad: 1, precioUnitario: 10, descuento: 0, porcentajeIva: 21 },
            { descripcion: "Retirar", cantidad: 1, precioUnitario: 5, descuento: 0, porcentajeIva: 0 }] };
    const creada = await pagina.request.post("/factura", { data: datos });
    expect(creada.status()).toBe(201);
    const factura = await creada.json();
    const cabeceras = consultarBaseAislada("SELECT COUNT(*) FROM facturas;");
    await pagina.setViewportSize({ width: 390, height: 844 });
    await pagina.goto("/factura-imprimir.html?idFactura=" + factura.idFactura);
    await pagina.getByRole("link", { name: "Editar borrador", exact: true }).click();
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
    await expect(pagina.locator(".concepto-factura")).toHaveCount(2);
    await pagina.locator(".concepto-factura").first().getByLabel("Cantidad", { exact: true }).fill("3");
    await pagina.getByRole("button", { name: "Eliminar concepto 2", exact: true }).click();
    await anadirLinea(pagina, "Nueva", "2", "5", "0", "10");
    await pagina.locator("#observacionesFactura").fill("Editada E2E");
    expect(await pagina.locator("#facturaModal .modal-body").evaluate(elemento => elemento.scrollWidth <= elemento.clientWidth)).toBe(true);
    const respuestaPendiente = pagina.waitForResponse(respuesta => respuesta.request().method() == "PUT");
    await pagina.locator("#botonGuardarFactura").click();
    const respuesta = await respuestaPendiente;
    expect(respuesta.status()).toBe(200);
    await expect(pagina).toHaveURL(new RegExp("factura-imprimir.html\\?idFactura=" + factura.idFactura + "$"));
    // La navegación descarta el cuerpo de la PUT en Chrome; comprobamos el detalle persistido.
    const detalle = await pagina.request.get("/factura/" + factura.idFactura + "/detalle");
    expect(detalle.status()).toBe(200);
    const { factura: guardada } = await detalle.json();
    expect(guardada.numeroFactura).toBe(factura.numeroFactura);
    expect([guardada.subtotal, guardada.importeIva, guardada.total]).toEqual([40, 7.3, 47.3]);
    await expect(pagina.locator("#totalFactura")).toHaveText("47,30 €");
    await expect(pagina.locator("#tablaConceptos")).toContainText("Nueva");
    await expect(pagina.locator("#tablaConceptos")).not.toContainText("Retirar");
    expect(consultarBaseAislada("SELECT COUNT(*) FROM facturas;")).toBe(cabeceras);
    expect(consultarBaseAislada("SELECT descripcion,cantidad,total FROM conceptos WHERE idfactura=" + factura.idFactura + " ORDER BY idconcepto;"))
        .toBe("Primera\t3\t36.30\nNueva\t2\t11.00");
    await pagina.screenshot({ path: test.info().outputPath("edicion-movil-confirmada.png") });
});

test("4 E2E: estado cambiado durante edición y peticiones forzadas no alteran datos", async ({ page: pagina }) => {
    consultarBaseAislada("SELECT COUNT(*) FROM facturas;");
    const datos = { idCliente: 1, fechaEmision: "2051-01-02", estado: "BORRADOR", observaciones: "Protección E2E",
        conceptos: [{ descripcion: "Intacta", cantidad: 1, precioUnitario: 10, descuento: 0, porcentajeIva: 21 }] };
    const creada = await pagina.request.post("/factura", { data: datos });
    expect(creada.status()).toBe(201);
    const factura = await creada.json();
    await pagina.goto("/facturas.html?editar=" + factura.idFactura);
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
    await pagina.locator("#observacionesFactura").fill("No guardar");
    consultarBaseAislada("UPDATE facturas SET estado='EMITIDA' WHERE idfactura=" + factura.idFactura + ";");
    const consulta = "SELECT * FROM facturas WHERE idfactura=" + factura.idFactura + "; SELECT * FROM conceptos WHERE idfactura=" + factura.idFactura + ";";
    const antes = consultarBaseAislada(consulta);
    await pagina.locator("#botonGuardarFactura").click();
    await expect(pagina.locator("#mensaje-formulario-factura")).toContainText("dejado de ser BORRADOR");
    await expect(pagina.locator("#observacionesFactura")).toHaveValue("No guardar");
    expect(consultarBaseAislada(consulta)).toBe(antes);
    for (const estado of ["EMITIDA", "ANULADA"]) {
        consultarBaseAislada("UPDATE facturas SET estado='" + estado + "' WHERE idfactura=" + factura.idFactura + ";");
        const estadoAntes = consultarBaseAislada(consulta);
        const rechazada = await pagina.request.put("/factura/" + factura.idFactura + "/borrador", { data: datos });
        expect(rechazada.status()).toBe(409);
        expect(consultarBaseAislada(consulta)).toBe(estadoAntes);
        await pagina.goto("/factura-imprimir.html?idFactura=" + factura.idFactura);
        await expect(pagina.locator("#contenidoFactura")).toBeVisible();
        await expect(pagina.locator("#botonEditarBorrador")).toBeHidden();
    }
});

function consultarBaseAislada(sql) {
    const socket = process.env.FACTURAS_MYSQL_SOCKET;
    const servidor = process.env.FACTURAS_MYSQL_SERVIDOR;
    const directorio = process.env.FACTURAS_MYSQL_DIRECTORIO;
    if (!socket || !/^\/tmp\/facturas-mysql-[a-zA-Z0-9]+\/mysql.sock$/.test(socket) || !servidor || !directorio) {
        throw new Error("Faltan identidad y socket del MySQL temporal aislado.");
    }
    const argumentos = ["--no-defaults", "--protocol=SOCKET", "--socket=" + socket, "-u", "root", "--batch", "--skip-column-names"];
    const identidad = execFileSync("mysql", [...argumentos, "-e", "SELECT @@server_uuid, @@datadir;"], { encoding: "utf8" }).trim();
    expect(identidad).toBe(servidor + "\t" + directorio);
    return execFileSync("mysql", [...argumentos, "facturas_pruebas", "-e", sql], { encoding: "utf8" }).trim();
}

test("E2E real: una cabecera, varias líneas y totales confirmados en MySQL aislado", async ({ page: pagina }) => {
    const marca = "E2E-" + Date.now();
    const maximoAnterior = Number(consultarBaseAislada("SELECT COALESCE(MAX(CAST(SUBSTRING(num_factura,8) AS UNSIGNED)),0) FROM facturas WHERE num_factura REGEXP '^F-2028-[0-9]{4}$';"));
    await abrirAlta(pagina);
    await pagina.locator("#estadoFactura").selectOption("EMITIDA");
    await pagina.getByLabel("Observaciones", { exact: true }).fill(marca);
    await anadirLinea(pagina, "Servicio E2E", "3", "19.99", "10", "21");
    await anadirLinea(pagina, "Material E2E", "2", "5", "0", "10");
    await expect(pagina.locator("#totalFactura")).toHaveText("76,30 €");
    await pagina.screenshot({ path: test.info().outputPath("alta-conceptos.png") });
    const peticiones = peticionesDeAlta(pagina);
    const respuestaPendiente = pagina.waitForResponse(respuesta => new URL(respuesta.url()).pathname == "/factura" && respuesta.request().method() == "POST");
    await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
    const respuesta = await respuestaPendiente;
    expect(respuesta.status()).toBe(201);
    const factura = await respuesta.json();
    const numeroEsperado = "F-2028-" + String(maximoAnterior + 1).padStart(4, "0");
    expect(factura.numeroFactura).toBe(numeroEsperado);
    expect([factura.subtotal, factura.importeIva, factura.total]).toEqual([63.97, 12.33, 76.3]);
    await expect(pagina.locator("#mensaje-facturas")).toContainText(numeroEsperado);
    await expect(pagina.locator("#tablaFacturas")).toContainText(numeroEsperado);
    expect(peticiones).toHaveLength(1);
    expect(Object.keys(peticiones[0]).sort()).toEqual(["conceptos", "estado", "fechaEmision", "idCliente", "observaciones"]);
    const cabeceras = consultarBaseAislada("SELECT num_factura,subtotal,importe_iva,total FROM facturas WHERE observaciones='" + marca + "';").split("\n");
    expect(cabeceras).toEqual([numeroEsperado + "\t63.97\t12.33\t76.30"]);
    const lineas = consultarBaseAislada("SELECT c.descripcion,c.cantidad,c.precio_unitario,c.descuento,c.porcentaje_iva,c.base_imponible,c.importe_iva,c.total FROM conceptos c JOIN facturas f ON f.idfactura=c.idfactura WHERE f.observaciones='" + marca + "' ORDER BY c.idconcepto;").split("\n");
    expect(lineas).toEqual(["Servicio E2E\t3\t19.99\t10.00\t21.00\t53.97\t11.33\t65.30", "Material E2E\t2\t5.00\t0.00\t10.00\t10.00\t1.00\t11.00"]);
});

test("E2E real: Spring rechaza un desbordamiento y conserva lo escrito", async ({ page: pagina }) => {
    const anteriores = consultarBaseAislada("SELECT COUNT(*) FROM facturas;");
    await abrirAlta(pagina);
    await anadirLinea(pagina, "Conservar tras rechazo real", "2", "99999999.99", "0", "0");
    const respuestaPendiente = pagina.waitForResponse(respuesta => new URL(respuesta.url()).pathname == "/factura" && respuesta.request().method() == "POST");
    await pagina.getByRole("button", { name: "Guardar factura", exact: true }).click();
    expect((await respuestaPendiente).status()).toBe(400);
    await expect(pagina.locator("#mensaje-formulario-factura")).toContainText("rechazado los datos");
    await expect(pagina.getByLabel("Descripción", { exact: true })).toHaveValue("Conservar tras rechazo real");
    await expect(pagina.getByLabel("Cantidad", { exact: true })).toHaveValue("2");
    await expect(pagina.getByLabel("Precio unitario (€)", { exact: true })).toHaveValue("99999999.99");
    await expect(pagina.locator("#botonGuardarFactura")).toBeEnabled();
    expect(consultarBaseAislada("SELECT COUNT(*) FROM facturas;")).toBe(anteriores);
});

// Los dos que fijan el criterio de la franja de avisos: un EVENTO se retira solo, un ESTADO
// se queda. Si alguien vuelve a poner un mensaje fijo donde iba uno temporal (o al revés),
// es aquí donde salta.

test("5: la confirmación de una factura guardada se retira sola", async ({ page: pagina }) => {
    const factura = await simularBorrador(pagina);
    await abrirEdicionSimulada(pagina);
    await pagina.route("**/factura/7/borrador", ruta => ruta.fulfill({ json: factura }));
    await pagina.locator("#botonGuardarFactura").click();

    // Cuenta algo que acaba de pasar, así que sale en verde y se va: un "Factura creada" de
    // hace diez minutos engaña, porque parece de la última acción.
    await expect(pagina.locator("#mensaje-facturas")).toContainText(factura.numeroFactura);
    await expect(pagina.locator("#mensaje-facturas")).toHaveClass(/aviso-exito/);
    await expect(pagina.locator("#mensaje-facturas")).toBeEmpty({ timeout: 8000 });
});

test("5: el rótulo del trimestre se queda mientras se esté viendo", async ({ page: pagina }) => {
    await pagina.route("**/factura/trimestral?*", ruta => ruta.fulfill({ json: {
        anio: 2026, trimestre: 3, facturas: [], subtotal: 0, importeIva: 0, total: 0
    } }));
    await pagina.goto("/facturas.html");
    await pagina.locator("#anioTrimestre").fill("2026");
    await pagina.locator("#trimestreFactura").selectOption("3");
    await pagina.locator("#botonListarTrimestre").click();

    // No es un evento: describe QUÉ hay en la tabla, y eso sigue siendo verdad. Va en tono
    // neutro, ni verde de confirmación ni rojo de error.
    await expect(pagina.locator("#mensaje-facturas")).toContainText("3º trimestre de 2026");
    await expect(pagina.locator("#mensaje-facturas")).not.toHaveClass(/aviso-exito|aviso-error/);

    // La espera es a propósito y no se puede sustituir por un reintento: lo que se comprueba
    // es justamente que pasado el tiempo de un aviso temporal el rótulo NO se ha ido.
    await pagina.waitForTimeout(6000);
    await expect(pagina.locator("#mensaje-facturas")).toContainText("3º trimestre de 2026");
});
