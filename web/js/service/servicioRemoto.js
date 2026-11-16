'use strict';

/* Servicio que delega en el programa Java (capa service) a traves de su API local. */

function crearServicioRemoto() {
  const pedir = async (ruta, opciones) => {
    try {
      const respuesta = await fetch('/api/' + ruta, opciones);
      return await respuesta.json();
    } catch (e) {
      return { ok: false, codigo: 'CONEXION', error: 'No hay conexión con el programa AlphaSport. ¿Sigue abierto?' };
    }
  };

  const descargar = async ruta => {
    const respuesta = await fetch('/api/' + ruta);
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
    detalle: id => pedir('partido/' + id),
    noticias: () => pedir('noticias'),
    /** Con el programa Java la foto es un archivo de datos/imagenes/ que se sirve en /api/imagen/{nombre}. */
    urlImagen: imagen => (imagen ? '/api/imagen/' + encodeURIComponent(imagen) : ''),
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
