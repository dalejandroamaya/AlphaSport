'use strict';

/* Persistencia de la version web: campeonatos en curso, historial, noticias y cuentas en el almacenamiento del navegador. */

const Almacen = (() => {
  const CLAVE_ACTUAL = 'alphasport.actual'; // un solo campeonato: versión anterior (se migra a CLAVE_CAMPEONATOS)
  const CLAVE_CAMPEONATOS = 'alphasport.campeonatos';
  const CLAVE_ELEGIDOS = 'alphasport.elegidos';
  const CLAVE_HISTORIAL = 'alphasport.historial';
  const CLAVE_SESION = 'alphasport.usuario';
  const CLAVE_NOTICIAS = 'alphasport.noticias';
  const CLAVE_DISPOSITIVO = 'alphasport.dispositivo';
  const CLAVE_CUENTAS = 'alphasport.cuentas';
  let dispositivo = null;

  const leer = (clave, defecto) => {
    try {
      const texto = localStorage.getItem(clave);
      return texto ? JSON.parse(texto) : defecto;
    } catch (e) {
      return defecto;
    }
  };

  const escribir = (clave, valor) => {
    try {
      localStorage.setItem(clave, JSON.stringify(valor));
    } catch (e) {
      throw new Error('No se pudo guardar en el navegador. Descargue un respaldo.');
    }
  };

  /**
   * Varios campeonatos en curso: {codigo: campeonato}. La primera vez se pasa aquí el campeonato que guardaba la
   * versión anterior (un solo campeonato, sin dueño) y sus noticias quedan en él, como la migración 006 en Java.
   */
  const leerCampeonatos = () => {
    let todos = leer(CLAVE_CAMPEONATOS, null);
    if (todos && typeof todos === 'object' && !Array.isArray(todos)) return todos;
    todos = {};
    const anterior = leer(CLAVE_ACTUAL, null);
    if (anterior && anterior.formato) {
      if (!anterior.codigo) anterior.codigo = Date.now().toString(36);
      anterior.propietario = anterior.propietario || '';
      todos[anterior.codigo] = anterior;
      const noticias = leer(CLAVE_NOTICIAS, []);
      if (Array.isArray(noticias) && noticias.some(n => !n.campeonato)) {
        try { escribir(CLAVE_NOTICIAS, noticias.map(n => (n.campeonato ? n : Object.assign(n, { campeonato: anterior.codigo })))); } catch (e) { /* quedan generales */ }
      }
    }
    try {
      escribir(CLAVE_CAMPEONATOS, todos);
      localStorage.removeItem(CLAVE_ACTUAL);
    } catch (e) { /* sin almacenamiento: se reintenta la próxima vez */ }
    return todos;
  };

  return {
    // Campeonatos en curso de este navegador; cada uno con su dueño (campeonato.propietario = usuario local; '' = sin dueño).
    campeonatos: {
      listar: () => Object.values(leerCampeonatos()),
      cargar: codigo => leerCampeonatos()[codigo] || null,
      /** Lanza error si el navegador no tiene espacio (no se guarda nada). */
      guardar: c => { const todos = leerCampeonatos(); todos[c.codigo] = c; escribir(CLAVE_CAMPEONATOS, todos); },
      eliminar: codigo => { const todos = leerCampeonatos(); delete todos[codigo]; escribir(CLAVE_CAMPEONATOS, todos); }
    },
    // Último campeonato que eligió cada usuario de este navegador (usuario -> código).
    elegido: {
      cargar: usuario => (leer(CLAVE_ELEGIDOS, {}) || {})[usuario] || null,
      guardar: (usuario, codigo) => {
        const todos = leer(CLAVE_ELEGIDOS, {}) || {};
        if (codigo) todos[usuario] = codigo; else delete todos[usuario];
        try { escribir(CLAVE_ELEGIDOS, todos); } catch (e) { /* sin almacenamiento: vale mientras la página esté abierta */ }
      }
    },
    // Noticias con sus comentarios (la foto va dentro como «data URL»). Lanza error si el navegador no tiene espacio.
    noticias: {
      cargar: () => leer(CLAVE_NOTICIAS, []),
      guardar: lista => escribir(CLAVE_NOTICIAS, lista)
    },
    // Cuentas registradas en este navegador (web sola): {usuario, nombre, correo, rol, claveHash}. Nunca la clave.
    cuentas: {
      listar: () => { const l = leer(CLAVE_CUENTAS, []); return Array.isArray(l) ? l.filter(c => c && typeof c.usuario === 'string') : []; },
      guardar: lista => escribir(CLAVE_CUENTAS, lista)
    },
    // Usuario con sesión abierta ({usuario, rol}); se recuerda al recargar la página.
    sesion: {
      cargar: () => { const s = leer(CLAVE_SESION, null); return s && typeof s === 'object' && s.rol ? s : null; },
      guardar: s => { try { localStorage.setItem(CLAVE_SESION, JSON.stringify(s)); } catch (e) { /* sin almacenamiento */ } },
      eliminar: () => { try { localStorage.removeItem(CLAVE_SESION); } catch (e) { /* sin almacenamiento */ } }
    },
    /**
     * [REQ-07] Identificador anónimo de este navegador para las votaciones del público: se crea al azar la
     * primera vez y se recuerda. Evita votar dos veces al mismo jugador en el mismo partido. No es un dato personal.
     */
    dispositivo() {
      if (dispositivo) return dispositivo;
      try { dispositivo = localStorage.getItem(CLAVE_DISPOSITIVO); } catch (e) { /* sin almacenamiento */ }
      if (!dispositivo || !/^[A-Za-z0-9-]{8,64}$/.test(dispositivo)) {
        dispositivo = (crypto.randomUUID ? crypto.randomUUID()
          : Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join(''));
        try { localStorage.setItem(CLAVE_DISPOSITIVO, dispositivo); } catch (e) { /* vale mientras la página esté abierta */ }
      }
      return dispositivo;
    },
    historial: {
      listar: () => leer(CLAVE_HISTORIAL, []),
      archivar(c) {
        const lista = leer(CLAVE_HISTORIAL, []).filter(h => h.codigo !== c.codigo);
        lista.unshift({ codigo: c.codigo, fecha: c.fechaArchivo, datos: JSON.parse(JSON.stringify(c)) });
        escribir(CLAVE_HISTORIAL, lista);
      },
      eliminar(codigo) {
        const lista = leer(CLAVE_HISTORIAL, []);
        const restantes = lista.filter(h => h.codigo !== codigo);
        escribir(CLAVE_HISTORIAL, restantes);
        return restantes.length < lista.length;
      }
    }
  };
})();