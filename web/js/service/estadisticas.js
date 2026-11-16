'use strict';

/*
 * Estadísticas GLOBALES del campeonato (versión web sola): goleadores, asistidores,
 * valla menos vencida y juego limpio acumulado. Misma regla que
 * service.EstadisticasCampeonato en Java. Las estadísticas de UN partido no viven
 * aquí: se muestran en el detalle del partido.
 */

const EstadisticasCampeonato = (() => {
  function calcular(c) {
    const m = new Motor(c);
    const porJugador = new Map(), invictas = new Map(), registroGoles = [];
    let golesSinAutor = 0;
    const equipoDe = (idJugador, porDefecto) => {
      const e = c.equipos.find(q => q.jugadores.some(j => j.id === idJugador));
      return e ? e.id : porDefecto;
    };
    const fila = (id, nombre, dorsal, idEquipoDelGol) => {
      if (!porJugador.has(id)) {
        const idEquipo = equipoDe(id, idEquipoDelGol);
        porJugador.set(id, { idJugador: id, nombre, dorsal, idEquipo, equipo: m.nombre(idEquipo), goles: 0, asistencias: 0 });
      }
      return porJugador.get(id);
    };
    for (const p of c.partidos) {
      if (!P.jugado(p)) continue; // como en la tabla: un partido «En juego» todavía no cuenta
      if (p.gv === 0) invictas.set(p.local, (invictas.get(p.local) || 0) + 1);
      if (p.gl === 0) invictas.set(p.visitante, (invictas.get(p.visitante) || 0) + 1);
      golesSinAutor += p.gl + p.gv - P.goles(p).length;
      for (const g of P.goles(p)) {
        registroGoles.push({ partido: p, gol: g });
        if (!g.enContra) fila(g.autor, g.autorNombre, g.autorDorsal, g.equipo).goles++; // un autogol no cuenta para el goleador
        if (g.asistente) fila(g.asistente, g.asistenteNombre, g.asistenteDorsal, g.equipo).asistencias++;
      }
    }
    const jugadores = [...porJugador.values()];
    const goleadores = jugadores.filter(f => f.goles > 0)
      .sort((a, b) => b.goles - a.goles || b.asistencias - a.asistencias || a.nombre.localeCompare(b.nombre));
    const asistidores = jugadores.filter(f => f.asistencias > 0)
      .sort((a, b) => b.asistencias - a.asistencias || b.goles - a.goles || a.nombre.localeCompare(b.nombre));

    const general = m.tablaGeneral().filter(f => f.pj > 0);
    const puntosFair = f => f.ta + 3 * f.tr;
    // Valla: menos goles recibidos POR PARTIDO (igual que el premio del historial), luego menos goles en total.
    const valla = general.map(f => ({ fila: f, vallasInvictas: invictas.get(f.id) || 0, promedio: f.gc / f.pj }))
      .sort((a, b) => a.promedio - b.promedio || a.fila.gc - b.fila.gc || b.vallasInvictas - a.vallasInvictas);
    // Juego limpio: menos puntos (amarilla = 1, roja = 3) por partido, luego menos en total.
    const fairPlay = general.slice().sort((a, b) => puntosFair(a) / a.pj - puntosFair(b) / b.pj || puntosFair(a) - puntosFair(b));
    return { goleadores, asistidores, registroGoles, golesSinAutor, valla, fairPlay, motor: m };
  }

  return { calcular };
})();
