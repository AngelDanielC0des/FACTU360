console.log("pagina cargada ...");
fetch("http://192.168.153.204:8080/cliente/listar-ultimos")
.then(respuesta => respuesta.json())
.then (clientes => {
	console.log ('Aqui la vuelta con JS Normal');
	clientes.forEach (cliente => {
		console.log (cliente.idCliente + " " +cliente.nombre);
	});
})
.catch(function(error) {
  console.log ('Aqui la vuelta con JS Normal');
  console.log('Hubo un problema con la petición Fetch:' + error.message);
});

function mifuncion (cadena_json)
{
	console.log ('Aquí la repuesta con JSONP');
	console.log (cadena_json);//este sera el alumno
}
