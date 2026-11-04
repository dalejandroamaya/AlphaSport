'use strict';

/* Persistencia de la version web: campeonato en curso e historial en el almacenamiento del navegador. */

const Almacen = (() => {
  const CLAVE_ACTUAL = 'alphasport.actual';
  const CLAVE_HISTORIAL = 'alphasport.historial';

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

  return {
    campeonato: {
      cargar: () => leer(CLAVE_ACTUAL, null),
      guardar: c => escribir(CLAVE_ACTUAL, c)
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
