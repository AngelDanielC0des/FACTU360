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
