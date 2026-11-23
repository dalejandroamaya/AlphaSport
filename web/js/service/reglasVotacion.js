'use strict';

/*
 * [REQ-07] Reglas de la votación del público: cuánto dura (la programa el administrador en cada partido), cuándo
 * está abierta, cómo se cierra antes de tiempo y qué votos se aceptan. La nota se calcula aparte, en
 * calificaciones.js. Gemelo de alphasport.service.ReglasVotacion en Java: mismas reglas y mismos mensajes.
 */
const ReglasVotacion = (() => {
  const HORAS_POR_DEFECTO = 24, HORAS_MINIMO = 1, HORAS_MAXIMO = 720; // 30 días
  const HORA_MS = 60 * 60 * 1000;
  const VOTO_MINIMO = 1, VOTO_MAXIMO = 10;

  /* ---------------------------------------------------------------- tiempo */

  /** Horas que dura la votación desde el final del partido (los partidos anteriores no la traen: 24). */
  const horas = p => (p.horasVotacion > 0 ? p.horasVotacion : HORAS_POR_DEFECTO);
  /** true si el administrador la cerró a mano. */
  const cerradaAntes = p => p.votacionCerradaEn > 0;

  /** Hora (ms) en que cierra: la programada o, si el administrador la cerró antes, esa. 0 = sin hora de cierre. */
  function cierraEn(p) {
    if (!(p.finalizadoEn > 0)) return 0;
    const programado = p.finalizadoEn + horas(p) * HORA_MS;
    return cerradaAntes(p) ? Math.min(p.votacionCerradaEn, programado) : programado;
  }

  /** SIN_RESULTADO, ABIERTA, CERRADA o SIN_REGISTRO (jugado antes de existir las votaciones). */
  function estado(p, ahora) {
    if (!P.jugado(p)) return 'SIN_RESULTADO';
    if (!(p.finalizadoEn > 0)) return 'SIN_REGISTRO';
    return ahora >= p.finalizadoEn && ahora < cierraEn(p) ? 'ABIERTA' : 'CERRADA';
  }

  /** true si el visitante puede votar ahora: abierta, sin W.O. y con jugadores que calificar. */
  const abierta = (c, p, ahora) => estado(p, ahora) === 'ABIERTA' && !P.walkover(p)
    && (Calificaciones.participantes(c, p, p.local).length > 0 || Calificaciones.participantes(c, p, p.visitante).length > 0);

  /** Partidos con la votación abierta, primero los que cierran antes. */
  const abiertas = (c, ahora) => c.partidos.filter(p => abierta(c, p, ahora)).sort((a, b) => cierraEn(a) - cierraEn(b));

  /**
   * La primera vez que el partido queda JUGADO se guarda la hora del final: abre la votación. Corregir después
   * el resultado no la mueve; si deja de estar jugado, se descarta junto con los votos.
   */
  function marcarFinal(p, ahora) {
    if (P.jugado(p)) {
      if (!(p.finalizadoEn > 0)) p.finalizadoEn = ahora;
    } else {
      p.finalizadoEn = 0;
      p.votacionCerradaEn = 0;
      p.votos = [];
    }
  }

  /* ---------------------------------------------------------------- administrador */

  /**
   * Cambia cuántas horas dura (contadas desde el final). Se puede programar antes de jugar. En un partido terminado
   * la vuelve a abrir, así que el nuevo cierre tiene que ser futuro. Con las mismas horas no cambia nada.
   */
  function programar(p, nuevas, ahora, ErrorRegla) {
    if (!Number.isInteger(nuevas) || nuevas < HORAS_MINIMO || nuevas > HORAS_MAXIMO) {
      throw new ErrorRegla(`El tiempo de votación debe estar entre ${HORAS_MINIMO} y ${HORAS_MAXIMO} horas.`);
    }
    if (nuevas === horas(p)) return;
    if (P.jugado(p) && p.finalizadoEn > 0 && p.finalizadoEn + nuevas * HORA_MS <= ahora) {
      throw new ErrorRegla(`El partido terminó hace más de ${nuevas} ${nuevas === 1 ? 'hora' : 'horas'}: elija un tiempo mayor o use «Cerrar votación ahora».`);
    }
    p.horasVotacion = nuevas;
    p.votacionCerradaEn = 0;
  }

  /** Cierra la votación antes de tiempo: desde ahora no se aceptan votos y la nota final queda definitiva. */
  function cerrar(p, ahora, ErrorRegla) {
    if (estado(p, ahora) !== 'ABIERTA') throw new ErrorRegla('La votación de este partido no está abierta.');
    p.votacionCerradaEn = ahora;
  }

  /* ---------------------------------------------------------------- visitante */

  /** Registra los votos ([{jugador, valor}]). Primero se validan todos: si uno no se acepta no se guarda ninguno. */
  function votar(c, p, votos, dispositivo, ahora, ErrorRegla) {
    if (!/^[A-Za-z0-9-]{8,64}$/.test(String(dispositivo || ''))) throw new ErrorRegla('No se pudo identificar este dispositivo para registrar el voto.');
    if (P.walkover(p)) throw new ErrorRegla('Partido definido por W.O.: no hay jugadores que calificar.');
    const e = estado(p, ahora);
    if (e === 'SIN_RESULTADO') throw new ErrorRegla('La votación se abre cuando el partido termina.');
    if (e === 'SIN_REGISTRO') throw new ErrorRegla('Este partido se jugó antes de existir las votaciones: solo tiene nota del sistema.');
    if (e === 'CERRADA') throw new ErrorRegla('La votación de este partido ya cerró.');
    const notas = new Map(votos.map(v => [v.jugador, v.valor])); // un voto por jugador, como en Java
    if (!notas.size) throw new ErrorRegla('Elija la nota de al menos un jugador.');
    for (const [jugador, valor] of notas) {
      if (!Number.isInteger(valor) || valor < VOTO_MINIMO || valor > VOTO_MAXIMO) throw new ErrorRegla('La calificación debe ser un número entero del 1 al 10.');
      if (!Calificaciones.participantes(c, p, p.local).includes(jugador) && !Calificaciones.participantes(c, p, p.visitante).includes(jugador)) {
        throw new ErrorRegla('Ese jugador no participó en este partido.');
      }
      if (P.votos(p).some(v => v.jugador === jugador && v.dispositivo === dispositivo)) {
        throw new ErrorRegla('Ya calificó a este jugador en este partido desde este dispositivo.');
      }
    }
    p.votos = P.votos(p).concat([...notas].map(([jugador, valor]) => ({ jugador, valor, dispositivo, fecha: ahora })));
    return notas.size;
  }

  return { HORAS_POR_DEFECTO, HORAS_MINIMO, HORAS_MAXIMO, horas, cerradaAntes, cierraEn, estado, abierta, abiertas, marcarFinal, programar, cerrar, votar };
})();
