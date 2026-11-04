'use strict';

/* Modelo de AlphaSport (web): constantes del dominio, creacion del campeonato y datos de cada partido. */

const LIBRE = -2;          // posicion vacia en una llave (el rival pasa directo)
const SIN_PENALES = -1;

const FORMATOS = {
  LIGA: {
    nombre: 'Todos contra todos (Liga / Round-Robin)',
    corto: 'Liga',
    descripcion: 'Todos los equipos se enfrentan entre sí. Gana el que sume más puntos (3 por victoria, 1 por empate). A una sola vuelta o a ida y vuelta (doble round-robin, como LaLiga o la Premier League).'
  },
  ELIMINACION_DIRECTA: {
    nombre: 'Eliminación directa (Copa / Knockout)',
    corto: 'Copa',
    descripcion: 'El que pierde queda eliminado. Se arma una llave (dieciseisavos, octavos, cuartos...). A partido único (empate = penales) o a ida y vuelta (avanza el mejor marcador global).'
  },
  GRUPOS_Y_ELIMINATORIA: {
    nombre: 'Mixto: fase de grupos + eliminatorias',
    corto: 'Grupos + llave',
    descripcion: 'Los equipos se dividen en grupos (mini ligas, normalmente de 4). Los mejores de cada grupo pasan a una llave de eliminación directa hasta la final (como el Mundial o la Copa América).'
  },
  TORNEO_CORTO: {
    nombre: 'Torneo corto (Apertura/Clausura) + Liguilla',
    corto: 'Apertura/Clausura',
    descripcion: 'Fase regular todos contra todos. Los mejores de la tabla clasifican a una liguilla de eliminación directa o a cuadrangulares semifinales (como la Liga BetPlay o la Liga MX).'
  },
  SUIZO: {
    nombre: 'Sistema suizo (Fase de liga UEFA)',
    corto: 'Suizo',
    descripcion: 'Todos en una sola tabla, pero cada equipo juega solo un número limitado de partidos contra rivales distintos. Los primeros pasan directo a la llave y la zona media juega un repechaje (como la Champions League).'
  },
  DOBLE_ELIMINACION: {
    nombre: 'Doble eliminación (Winner / Loser bracket)',
    corto: 'Doble eliminación',
    descripcion: 'Perder una vez manda al equipo a la llave de perdedores; solo queda fuera al perder dos veces. El campeón de ganadores juega la gran final contra el campeón de perdedores.'
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

const ESTADOS = { PENDIENTE: 'Pendiente', JUGADO: 'Jugado', APLAZADO: 'Aplazado', SUSPENDIDO: 'Suspendido' };

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
  reiniciar(p) {
    Object.assign(p, { estado: 'PENDIENTE', gl: 0, gv: 0, pl: SIN_PENALES, pv: SIN_PENALES, al: 0, rl: 0, av: 0, rv: 0, tarjetas: [] });
  },
  tarjetas: p => p.tarjetas || [], // partidos guardados antes de las tarjetas por jugador
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
    return { estado, gl, gv, pl, pv, al, rl, av, rv, fecha, hora, sede, obs, tarjetas };
  }
};

/* ------------------------------------------------------------------ */
/*  Generador de fixture y llaves                                      */
/* ------------------------------------------------------------------ */
