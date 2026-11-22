'use strict';

/* Modelo de AlphaSport (web): constantes del dominio, creacion del campeonato y datos de cada partido. */

const LIBRE = -2;          // posicion vacia en una llave (el rival pasa directo)
const SIN_PENALES = -1;

const FORMATOS = {
  LIGA: {
    nombre: 'Liga',
    descripcion: 'Todos juegan contra todos. Gana el que sume más puntos al final (3 por ganar, 1 por empatar). Se puede jugar a una vuelta o a ida y vuelta.'
  },
  ELIMINACION_DIRECTA: {
    nombre: 'Copa (eliminación directa)',
    descripcion: 'Partidos de llave: el que pierde queda eliminado. Se juegan octavos, cuartos, semifinal y final. Si hay empate, se define por penales.'
  },
  GRUPOS_Y_ELIMINATORIA: {
    nombre: 'Tipo Mundial (grupos y eliminatorias)',
    descripcion: 'Primero se juega en grupos pequeños. Los mejores de cada grupo pasan a una llave de eliminación hasta la final, como en el Mundial.'
  },
  TORNEO_CORTO: {
    nombre: 'Apertura y Clausura (con liguilla)',
    descripcion: 'Todos contra todos en la fase regular; los mejores de la tabla pasan a la liguilla o a cuadrangulares para definir al campeón, como en la Liga BetPlay.'
  },
  SUIZO: {
    nombre: 'Tipo Champions (sistema suizo)',
    descripcion: 'Todos en una sola tabla, pero cada equipo juega solo algunos partidos contra rivales distintos. Los primeros pasan directo a la llave y los del medio juegan un repechaje.'
  },
  DOBLE_ELIMINACION: {
    nombre: 'Doble oportunidad (doble eliminación)',
    descripcion: 'Nadie se va al primer tropiezo: quien pierde una vez pasa a la llave de perdedores y solo queda eliminado si pierde dos veces.'
  }
};

const CRITERIOS = {
  DIFERENCIA_GOLES: 'Diferencia de goles',
  GOLES_A_FAVOR: 'Mayor número de goles a favor',
  ENFRENTAMIENTO_DIRECTO: 'Enfrentamiento directo entre los empatados',
  PARTIDOS_GANADOS: 'Mayor número de partidos ganados',
  FAIR_PLAY: 'Juego limpio (amarilla = 1, roja = 3; gana el que tenga menos)',
  SORTEO: 'Sorteo'
};

const ESTADOS = { PENDIENTE: 'Pendiente', EN_JUEGO: 'En juego', JUGADO: 'Jugado', APLAZADO: 'Aplazado', SUSPENDIDO: 'Suspendido' };

/* Formaciones tácticas (líneas de campo de atrás hacia adelante, sin el portero): igual que model.Formacion en Java. */
const FORMACIONES = ['4-4-2', '4-3-3', '4-2-3-1', '4-5-1', '4-1-4-1', '3-5-2', '3-4-3', '5-3-2', '5-4-1'];
const FORMACION_POR_DEFECTO = '4-4-2';

/* Roles del sistema: cada rol sabe si puede editar (igual que model.Rol en Java). */
const ROLES = {
  ADMINISTRADOR: { nombre: 'Administrador', puedeEditar: true },
  VISITANTE: { nombre: 'Visitante', puedeEditar: false }
};

const LLAVE_GANADORES = 'Ganadores';
const LLAVE_PERDEDORES = 'Perdedores';
const LLAVE_GRAN_FINAL = 'Gran final';

function configuracionPorDefecto() {
  return {
    puntosVictoria: 3, puntosEmpate: 1,
    idaYVuelta: false,
    eliminatoriaIdaYVuelta: false, finalUnica: true, sorteoAleatorio: true,
    tamanoGrupo: 4, clasificadosPorGrupo: 2,
    clasificadosPlayoff: 8, tipoPlayoff: 'LLAVE', ventajaDeportiva: true,
    rondasSuizo: 8, directosSuizo: 8, repechajeSuizo: 16,
    granFinalConReinicio: true,
    divisiones: 1, ascensos: 2,   // liga con divisiones (Liga A, B...) y equipos que suben/bajan por temporada
    criterios: ['DIFERENCIA_GOLES', 'GOLES_A_FAVOR', 'ENFRENTAMIENTO_DIRECTO', 'FAIR_PLAY', 'SORTEO']
  };
}

function nuevoCampeonato(nombre, formato, config) {
  return {
    version: 1, nombre, formato, config,
    equipos: [], partidos: [], fases: [],
    sigEquipo: 1, sigPartido: 1, paso: 0, campeon: -1,
    semilla: Math.floor(Math.random() * 2147483647),
    codigo: nuevoCodigo(), fechaArchivo: null // identifican el campeonato en el historial
  };
}

function nuevoCodigo() {
  return Date.now().toString(36) + Math.floor(Math.random() * 1e6).toString(36);
}

/* Generador pseudoaleatorio con semilla (para que los sorteos se puedan repetir). */

const P = {
  jugado: p => p.estado === 'JUGADO',
  tienePenales: p => p.pl !== SIN_PENALES && p.pv !== SIN_PENALES,
  involucra: (p, id) => p.local === id || p.visitante === id,
  rival: (p, id) => (p.local === id ? p.visitante : p.local),
  golesDe: (p, id) => (p.local === id ? p.gl : p.gv),
  ganador(p) {
    if (!P.jugado(p)) return -1;
    if (p.gl > p.gv) return p.local;
    if (p.gv > p.gl) return p.visitante;
    if (P.tienePenales(p) && p.pl !== p.pv) return p.pl > p.pv ? p.local : p.visitante;
    return -1;
  },
  // El XI inicial y la formación NO se borran: son datos de la previa (se eligen antes del pitazo).
  // [REQ-07] Sin resultado no hay partido terminado: la votación (finalizadoEn) y sus votos se descartan. Las horas
  // que dura la votación se conservan: se deciden antes de jugar.
  reiniciar(p) {
    Object.assign(p, { estado: 'PENDIENTE', gl: 0, gv: 0, pl: SIN_PENALES, pv: SIN_PENALES, al: 0, rl: 0, av: 0, rv: 0, tarjetas: [], cambios: [], goles: [], finalizadoEn: 0, votacionCerradaEn: 0, votos: [] });
  },
  votos: p => p.votos || [],       // [REQ-07] partidos guardados antes de las calificaciones
  tarjetas: p => p.tarjetas || [], // partidos guardados antes de las tarjetas por jugador
  cambios: p => p.cambios || [],   // partidos guardados antes de registrar cambios
  goles: p => p.goles || [],       // partidos guardados antes de registrar goles por jugador
  // guarda las tarjetas por jugador y actualiza los totales por equipo (juego limpio)
  // (p puede ser el partido o la copia de sus datos, por eso se pasa el id del local)
  ponerTarjetas(p, tarjetas, idLocal) {
    p.tarjetas = tarjetas;
    p.al = p.rl = p.av = p.rv = 0;
    for (const t of tarjetas) {
      const local = t.equipo === idLocal;
      if (t.tipo === 'ROJA') { if (local) p.rl++; else p.rv++; } else if (local) p.al++; else p.av++;
    }
  },
  marcador(p) {
    if (!P.jugado(p)) return 'vs';
    return p.gl + ' - ' + p.gv + (P.tienePenales(p) ? ` (${p.pl}-${p.pv} pen)` : '');
  },
  datos(p) {
    // copia del resultado y la programacion (lo que se puede editar)
    const { estado, gl, gv, pl, pv, al, rl, av, rv, fecha, hora, sede, obs } = p;
    const tarjetas = P.tarjetas(p).map(t => Object.assign({}, t));
    const cambios = P.cambios(p).map(x => Object.assign({}, x));
    const goles = P.goles(p).map(x => Object.assign({}, x));
    const titularesLocal = (p.titularesLocal || []).slice(), titularesVisitante = (p.titularesVisitante || []).slice();
    const formacionLocal = p.formacionLocal || '', formacionVisitante = p.formacionVisitante || '';
    // La votación también se copia (como Partido.copiarDatosDe en Java): si se cancela el cambio, no se pierden los votos.
    const finalizadoEn = p.finalizadoEn || 0, horasVotacion = p.horasVotacion || 0, votacionCerradaEn = p.votacionCerradaEn || 0;
    const votos = P.votos(p).map(x => Object.assign({}, x));
    return { estado, gl, gv, pl, pv, al, rl, av, rv, fecha, hora, sede, obs, tarjetas, cambios, goles, titularesLocal, titularesVisitante, formacionLocal, formacionVisitante,
      finalizadoEn, horasVotacion, votacionCerradaEn, votos };
  }
};

/* ------------------------------------------------------------------ */
/*  Generador de fixture y llaves                                      */
/* ------------------------------------------------------------------ */