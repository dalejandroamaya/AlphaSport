'use strict';

/*
 * Detalle de un partido (versión web sola): cara a cara, racha
 * reciente de cada equipo y alineaciones sobre la cancha según la formación.
 * Es la misma regla que service.DetallePartido en Java y devuelve el mismo JSON
 * que ApiControlador.consultar("partido/{id}").
 *
 * Los equipos se reconocen por su NOMBRE, así se cruzan los datos del campeonato
 * actual con los del historial. Solo cuentan los partidos jugados ANTES del consultado.
 */

const DetallePartido = (() => {
  const MAXIMO_CARA_A_CARA = 10;
  const MAXIMO_RACHA = 5;

  const mismo = (a, b) => a != null && b != null && String(a).trim().toLowerCase() === String(b).trim().toLowerCase();
  const juega = (r, equipo) => mismo(r.local, equipo) || mismo(r.visitante, equipo);
  /** V (victoria), E (empate) o D (derrota) desde el punto de vista del equipo. */
  const resultadoPara = (r, equipo) => (r.ganador == null ? 'E' : mismo(r.ganador, equipo) ? 'V' : 'D');

  function registro(c, m, p) {
    const g = P.ganador(p);
    const ronda = p.nombreRonda || (c.fases[p.fase] ? c.fases[p.fase].nombre : '');
    return {
      competicion: c.nombre, ronda, fecha: p.fecha || 'Por definir', local: m.nombre(p.local), visitante: m.nombre(p.visitante),
      gl: p.gl, gv: p.gv, ganador: g < 0 ? null : m.nombre(g)
    };
  }

  /**
   * Partidos jugados antes del consultado, del más reciente al más antiguo: primero los
   * del campeonato actual (en orden del fixture) y luego los del historial (por fecha de archivo).
   */
  function partidosPrevios(actual, mActual, historial, partido) {
    const lista = [];
    const posicion = actual.partidos.indexOf(partido);
    for (let i = posicion - 1; i >= 0; i--) if (P.jugado(actual.partidos[i])) lista.push(registro(actual, mActual, actual.partidos[i]));
    const anteriores = historial.filter(h => !(h.codigo && h.codigo === actual.codigo))
      .sort((a, b) => String(b.fechaArchivo || '').localeCompare(String(a.fechaArchivo || '')));
    for (const h of anteriores) {
      const mh = new Motor(h);
      for (let i = h.partidos.length - 1; i >= 0; i--) if (P.jugado(h.partidos[i])) lista.push(registro(h, mh, h.partidos[i]));
    }
    return lista;
  }

  /** 0 portero, 1 defensa, 2 mediocampo, 3 ataque (según GRUPOS_POSICIONES). */
  function grupo(j) {
    const posicion = String(j.posicion || '').toLowerCase();
    const g = GRUPOS_POSICIONES.findIndex(x => x.posiciones.some(p => p.toLowerCase() === posicion));
    return g < 0 ? 2 : g; // posición desconocida: al medio
  }

  /** 0 izquierda, 1 centro, 2 derecha. */
  function lado(j) {
    const posicion = String(j.posicion || '').toLowerCase();
    return posicion.includes('izquierd') ? 0 : posicion.includes('derech') ? 2 : 1;
  }

  const fichaJugador = j => (j ? { id: j.id, dorsal: j.dorsal, nombre: j.nombre, posicion: j.posicion, foto: j.foto || '' } : null);

  /**
   * Ubica el XI en la cancha: el portero en la línea 0 y el resto ordenado de atrás
   * hacia adelante por su posición (defensa, medio, ataque) y de izquierda a derecha.
   */
  function alineacion(equipo, textoFormacion, titulares) {
    const elegida = FORMACIONES.includes(textoFormacion) ? textoFormacion : null;
    const formacion = elegida || FORMACION_POR_DEFECTO;
    const xi = [];
    if (equipo) for (const id of titulares || []) { const j = equipo.jugadores.find(x => x.id === id); if (j) xi.push(j); }
    const confirmada = xi.length > 0;
    // Portero: el primero marcado como tal (o el primero de la lista si nadie lo es).
    const portero = xi.find(j => grupo(j) === 0) || xi[0] || null;
    // Orden estable igual que en Java: por grupo y luego por lado.
    const campo = xi.filter(j => j !== portero).map((j, i) => ({ j, i }))
      .sort((a, b) => grupo(a.j) - grupo(b.j) || lado(a.j) - lado(b.j) || a.i - b.i).map(x => x.j);
    const lineas = [[fichaJugador(portero)]];
    let siguiente = 0;
    for (const cantidad of formacion.split('-').map(Number)) {
      const deLinea = [];
      for (let k = 0; k < cantidad; k++) deLinea.push(siguiente < campo.length ? campo[siguiente++] : null);
      // dentro de la línea: izquierda -> centro -> derecha (las casillas vacías van al final)
      const ordenada = deLinea.map((j, i) => ({ j, i })).sort((a, b) => (a.j ? lado(a.j) : 9) - (b.j ? lado(b.j) : 9) || a.i - b.i);
      lineas.push(ordenada.map(x => fichaJugador(x.j)));
    }
    return { formacion, formacionElegida: !!elegida, confirmada, lineas };
  }

  /** JSON del detalle: {partido, caraACara, racha, alineaciones} (el partido lo arma el servicio). */
  function calcular(actual, historial, partido) {
    const m = new Motor(actual);
    const local = m.nombre(partido.local), visitante = m.nombre(partido.visitante);
    const previos = partidosPrevios(actual, m, historial, partido);
    const caraACara = previos.filter(r => juega(r, local) && juega(r, visitante)).slice(0, MAXIMO_CARA_A_CARA);
    const conResultado = equipo => r => {
      const esLocal = mismo(r.local, equipo);
      return Object.assign({}, r, { resultado: resultadoPara(r, equipo), rival: esLocal ? r.visitante : r.local, condicion: esLocal ? 'L' : 'V' });
    };
    let vl = 0, e = 0, vv = 0;
    for (const r of caraACara) { const x = resultadoPara(r, local); if (x === 'V') vl++; else if (x === 'D') vv++; else e++; }
    const equipo = id => actual.equipos.find(q => q.id === id);
    return {
      caraACara: { victoriasLocal: vl, empates: e, victoriasVisitante: vv, partidos: caraACara },
      racha: {
        local: previos.filter(r => juega(r, local)).slice(0, MAXIMO_RACHA).map(conResultado(local)),
        visitante: previos.filter(r => juega(r, visitante)).slice(0, MAXIMO_RACHA).map(conResultado(visitante))
      },
      alineaciones: {
        local: alineacion(equipo(partido.local), partido.formacionLocal, partido.titularesLocal),
        visitante: alineacion(equipo(partido.visitante), partido.formacionVisitante, partido.titularesVisitante)
      }
    };
  }

  return { calcular, alineacion, MAXIMO_CARA_A_CARA, MAXIMO_RACHA };
})();
