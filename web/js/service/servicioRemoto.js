'use strict';

/* Servicio que delega en el programa Java (capa service) a traves de su API local. */

function crearServicioRemoto() {
  /*
   * Codigo del campeonato que muestra esta pestaña: viaja en la cabecera X-AlphaSport-Campeonato de cada
   * peticion (asi dos pestañas pueden ver campeonatos distintos). Sin el, el programa usa el ultimo elegido en la
   * sesion o el unico que el usuario puede ver. Se toma de cada respuesta: si el campeonato cambia de codigo
   * (otro formato, la siguiente temporada) o deja de estar, se sigue solo.
   */
  let campeonato = null;
  const cabeceras = (extra = {}) => (campeonato ? Object.assign({ 'X-AlphaSport-Campeonato': campeonato }, extra) : extra);
  const recordar = r => {
    const estado = r && (r.estado || (r.hay !== undefined ? r : null));
    if (estado) campeonato = estado.hay && estado.campeonato ? estado.campeonato.codigo : null;
    if (r && r.ok && 'sesion' in r && !r.sesion) campeonato = null; // salio: la proxima cuenta elige el suyo
    return r;
  };

  const pedir = async (ruta, opciones = {}) => {
    try {
      const respuesta = await fetch('/api/' + ruta, Object.assign({}, opciones, { headers: cabeceras(opciones.headers) }));
      return recordar(await respuesta.json());
    } catch (e) {
      return { ok: false, codigo: 'CONEXION', error: 'No hay conexión con el programa AlphaSport. ¿Sigue abierto?' };
    }
  };

  const descargar = async ruta => {
    const respuesta = await fetch('/api/' + ruta, { headers: cabeceras() });
    if (!respuesta.ok) throw new Error('No se pudo descargar el archivo.');
    const disposicion = respuesta.headers.get('Content-Disposition') || '';
    const nombre = decodeURIComponent((/filename\*=UTF-8''([^;]+)/.exec(disposicion) || [, 'archivo'])[1]);
    return { nombre, contenido: await respuesta.blob() };
  };

  return {
    modo: 'java',
    extensionRespaldo: '.dat',
    catalogo: () => pedir('catalogo'),
    sesion: () => pedir('sesion'),
    estado: () => pedir('estado'),
    historial: () => pedir('historial'),
    // [REQ-07] El id anónimo del navegador permite mostrar en las calificaciones lo que ya votó.
    detalle: id => pedir('partido/' + id + '/' + encodeURIComponent(Almacen.dispositivo())),
    votaciones: () => pedir('votaciones/' + encodeURIComponent(Almacen.dispositivo())),
    noticias: () => pedir('noticias'),
    /** Con el programa Java la foto es un archivo de datos/imagenes/ que se sirve en /api/imagen/{nombre}. */
    urlImagen: imagen => (imagen ? '/api/imagen/' + encodeURIComponent(imagen) : ''),
    urlFoto: foto => (foto ? '/api/foto/' + encodeURIComponent(foto) : ''),
    ejecutar: (ruta, cuerpo = {}) => pedir(ruta, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo)
    }),
    respaldo: () => descargar('respaldo'),
    exportarHistorial: () => descargar('exportar/historial'),
    abrirRespaldo: async archivo => pedir('respaldo', { method: 'POST', body: await archivo.arrayBuffer() }),
    salir: () => pedir('salir', { method: 'POST' })
  };
}

/* Usa el programa Java si la pagina la sirve el; si se abre sola en el navegador, usa el servicio local. */
async function crearServicio() {
  if (location.protocol.startsWith('http')) {
    try {
      const respuesta = await fetch('/api/catalogo', { cache: 'no-store' });
      if (respuesta.ok && (await respuesta.json()).modo === 'java') return crearServicioRemoto();
    } catch (e) {
      /* sin programa Java: version web independiente */
    }
  }
  return crearServicioLocal();
}