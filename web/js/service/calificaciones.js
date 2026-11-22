'use strict';

/*
 * [REQ-07] Calificación de los jugadores de un partido (escala 1.0 a 10.0), enfoque híbrido.
 * Gemelo de alphasport.service.CalculadoraCalificaciones en Java (las reglas) y de su resultado
 * CalificacionesPartido: mismas reglas, mismos mensajes y el mismo JSON (Presentador.calificaciones),
 * así la interfaz web es una sola.
 *
 *  - Nota del sistema: 6.0 por jugar (titular o ingresado); gol +1.0, asistencia +0.5,
 *    amarilla -0.5, roja -2.0. Portero: +1.5 con la valla invicta y -0.3 por gol recibido. Entre 1.0 y 10.0.
 *  - Voto del público: durante las 24 horas siguientes al cierre, del 1 al 10, una vez por dispositivo.
 *  - Nota final: la del sistema sin votos; con votos, (sistema + promedio) / 2 redondeado a 1 decimal.
 */
const Calificaciones = (() => {
  const NOTA_BASE = 6.0, PUNTOS_GOL = 1.0, PUNTOS_ASISTENCIA = 0.5, PUNTOS_AMARILLA = -0.5, PUNTOS_ROJA = -2.0;
  const PUNTOS_VALLA_INVICTA = 1.5, PUNTOS_GOL_RECIBIDO = -0.3, NOTA_MINIMA = 1.0, NOTA_MAXIMA = 10.0;
  const VENTANA_VOTACION_MS = 24 * 60 * 60 * 1000;
  const VOTO_MINIMO = 1, VOTO_MAXIMO = 10;

  /** Redondeo a 1 decimal «mitad hacia arriba» igual que BigDecimal en Java (8.45 -> 8.5, no 8.4). */
  const redondear = v => Number(Math.round(Number(v + 'e1')) + 'e-1');
  const texto = v => redondear(v).toFixed(1);
  const conSigno = v => (v >= 0 ? '+' : '−') + texto(Math.abs(v));

  const esWalkover = p => String(p.obs || '').startsWith('W.O.');
  const esPortero = j => GRUPOS_POSICIONES[0].posiciones.some(x => x.toLowerCase() === String(j.posicion || '').toLowerCase());

  /** SIN_RESULTADO, ABIERTA, CERRADA o SIN_REGISTRO (jugado antes de existir las calificaciones). */
  function estadoVotacion(p, ahora) {
    if (!P.jugado(p)) return 'SIN_RESULTADO';
    if (!(p.finalizadoEn > 0)) return 'SIN_REGISTRO';
    return ahora >= p.finalizadoEn && ahora < p.finalizadoEn + VENTANA_VOTACION_MS ? 'ABIERTA' : 'CERRADA';
  }

  /** Ids de quienes jugaron por ese equipo: titulares, ingresados y cualquiera con gol, asistencia o tarjeta. */
  function participantes(c, p, idEquipo) {
    const ids = new Set(idEquipo === p.local ? (p.titularesLocal || []) : (p.titularesVisitante || []));
    P.cambios(p).filter(x => x.equipo === idEquipo).forEach(x => ids.add(x.entra));
    for (const g of P.goles(p)) {
      const equipoAutor = g.enContra ? P.rival(p, g.equipo) : g.equipo;
      if (equipoAutor === idEquipo) ids.add(g.autor);
      if (g.equipo === idEquipo && g.asistente) ids.add(g.asistente);
    }
    P.tarjetas(p).filter(t => t.equipo === idEquipo).forEach(t => ids.add(t.jugador));
    // Solo cuentan los que siguen en la plantilla.
    const equipo = c.equipos.find(e => e.id === idEquipo);
    return [...ids].filter(id => equipo && equipo.jugadores.some(j => j.id === id));
  }

  /** Nota del sistema con su explicación línea por línea en «desglose». */
  function notaSistema(portero, goles, asistencias, amarillas, rojas, golesRecibidos, desglose) {
    let nota = NOTA_BASE;
    desglose.push('Base por jugar: ' + texto(NOTA_BASE));
    const sumar = (concepto, veces, puntos) => {
      if (veces <= 0) return 0;
      desglose.push(concepto + (veces > 1 ? ' ×' + veces : '') + ': ' + conSigno(veces * puntos));
      return veces * puntos;
    };
    nota += sumar('Gol', goles, PUNTOS_GOL);
    nota += sumar('Asistencia', asistencias, PUNTOS_ASISTENCIA);
    nota += sumar('Tarjeta amarilla', amarillas, PUNTOS_AMARILLA);
    nota += sumar('Tarjeta roja', rojas, PUNTOS_ROJA);
    if (portero) {
      if (golesRecibidos === 0) {
        nota += PUNTOS_VALLA_INVICTA;
        desglose.push('Valla invicta: ' + conSigno(PUNTOS_VALLA_INVICTA));
      } else {
        nota += sumar('Gol recibido', golesRecibidos, PUNTOS_GOL_RECIBIDO);
      }
    }
    const acotada = Math.max(NOTA_MINIMA, Math.min(NOTA_MAXIMA, nota));
    if (acotada !== nota) desglose.push(acotada === NOTA_MAXIMA ? 'Ajustada al máximo: ' + texto(NOTA_MAXIMA) : 'Ajustada al mínimo: ' + texto(NOTA_MINIMA));
    return redondear(acotada);
  }

  function calificar(c, p, idEquipo, dispositivo) {
    const equipo = c.equipos.find(e => e.id === idEquipo);
    const titulares = new Set(idEquipo === p.local ? (p.titularesLocal || []) : (p.titularesVisitante || []));
    const recibidos = P.golesDe(p, P.rival(p, idEquipo));
    const lista = participantes(c, p, idEquipo).map(id => {
      const j = equipo.jugadores.find(x => x.id === id);
      const portero = esPortero(j);
      const goles = P.goles(p).filter(g => !g.enContra && g.autor === id).length;
      const asistencias = P.goles(p).filter(g => g.asistente === id).length;
      const amarillas = P.tarjetas(p).filter(t => t.jugador === id && t.tipo !== 'ROJA').length;
      const rojas = P.tarjetas(p).filter(t => t.jugador === id && t.tipo === 'ROJA').length;
      const desglose = [];
      const nota = notaSistema(portero, goles, asistencias, amarillas, rojas, recibidos, desglose);
      const votos = P.votos(p).filter(v => v.jugador === id);
      const promedio = votos.length ? votos.reduce((s, v) => s + v.valor, 0) / votos.length : 0;
      const propio = dispositivo ? votos.find(v => v.dispositivo === dispositivo) : null;
      return {
        id, equipo: idEquipo, nombre: j.nombre, dorsal: j.dorsal, posicion: j.posicion || '', portero, titular: titulares.has(id),
        goles, asistencias, amarillas, rojas, golesRecibidos: portero ? recibidos : 0, notaSistema: nota, desglose,
        votos: votos.length, promedioPublico: redondear(promedio), notaFinal: votos.length ? redondear((nota + promedio) / 2) : nota,
        mvp: false, miVoto: propio ? propio.valor : 0
      };
    });
    // Titulares primero y luego los que entraron; dentro de cada grupo, por dorsal.
    return lista.sort((a, b) => (a.titular === b.titular ? a.dorsal - b.dorsal : a.titular ? -1 : 1));
  }

  /** MVP: nota final más alta; empates por goles, asistencias, nota del sistema, titular, local y dorsal. */
  function mvp(local, visitante) {
    const todos = local.map(x => ({ x, lado: 0 })).concat(visitante.map(x => ({ x, lado: 1 })));
    todos.sort((a, b) => b.x.notaFinal - a.x.notaFinal || b.x.goles - a.x.goles || b.x.asistencias - a.x.asistencias
      || b.x.notaSistema - a.x.notaSistema || (b.x.titular - a.x.titular) || a.lado - b.lado || a.x.dorsal - b.x.dorsal);
    return todos.length ? todos[0].x.id : 0;
  }

  /** Mismo JSON que Presentador.calificaciones en Java. */
  function calcular(c, p, ahora, dispositivo) {
    const votacion = estadoVotacion(p, ahora);
    let motivo = '', local = [], visitante = [];
    if (!P.jugado(p)) motivo = 'Las calificaciones aparecen cuando el partido termina.';
    else if (esWalkover(p)) motivo = 'Partido definido por W.O.: no se jugó, así que no hay calificaciones.';
    else {
      local = calificar(c, p, p.local, dispositivo);
      visitante = calificar(c, p, p.visitante, dispositivo);
      if (!local.length && !visitante.length) motivo = 'No se registró quién jugó (XI inicial, cambios, goles ni tarjetas): no hay a quién calificar.';
    }
    const idMvp = mvp(local, visitante);
    [...local, ...visitante].forEach(x => { x.mvp = x.id === idMvp; });
    return {
      votacion, cierraEn: p.finalizadoEn > 0 ? p.finalizadoEn + VENTANA_VOTACION_MS : 0,
      disponible: local.length > 0 || visitante.length > 0, motivo, mvp: idMvp, local, visitante
    };
  }

  /** Registra un voto con las mismas validaciones que CalculadoraCalificaciones.votar. Lanza ErrorRegla. */
  function votar(c, p, idJugador, valor, dispositivo, ahora, ErrorRegla) {
    if (!/^[A-Za-z0-9-]{8,64}$/.test(String(dispositivo || ''))) throw new ErrorRegla('No se pudo identificar este dispositivo para registrar el voto.');
    if (esWalkover(p)) throw new ErrorRegla('Partido definido por W.O.: no hay jugadores que calificar.');
    const estado = estadoVotacion(p, ahora);
    if (estado === 'SIN_RESULTADO') throw new ErrorRegla('La votación se abre cuando el partido termina.');
    if (estado === 'SIN_REGISTRO') throw new ErrorRegla('Este partido se jugó antes de existir las votaciones: solo tiene nota del sistema.');
    if (estado === 'CERRADA') throw new ErrorRegla('La votación de este partido ya cerró: pasaron más de 24 horas desde el final.');
    if (!Number.isInteger(valor) || valor < VOTO_MINIMO || valor > VOTO_MAXIMO) throw new ErrorRegla('La calificación debe ser un número entero del 1 al 10.');
    if (!participantes(c, p, p.local).includes(idJugador) && !participantes(c, p, p.visitante).includes(idJugador)) {
      throw new ErrorRegla('Ese jugador no participó en este partido.');
    }
    if (P.votos(p).some(v => v.jugador === idJugador && v.dispositivo === dispositivo)) {
      throw new ErrorRegla('Ya calificó a este jugador en este partido desde este dispositivo.');
    }
    if (!p.votos) p.votos = [];
    p.votos.push({ jugador: idJugador, valor, dispositivo, fecha: ahora });
  }

  /**
   * La primera vez que el partido queda JUGADO se guarda la hora de cierre (abre las 24 horas).
   * Corregir después el resultado no la mueve; si deja de estar jugado, se descarta con los votos.
   */
  function marcarFinal(p, ahora) {
    if (P.jugado(p)) {
      if (!(p.finalizadoEn > 0)) p.finalizadoEn = ahora;
    } else {
      p.finalizadoEn = 0;
      p.votos = [];
    }
  }

  return { calcular, votar, marcarFinal, notaSistema, redondear, estadoVotacion, VENTANA_VOTACION_MS };
})();