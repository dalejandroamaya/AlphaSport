/*
 * Prueba automatica del motor web: juega cientos de campeonatos con
 * resultados aleatorios en los 6 formatos y revisa que siempre se llegue
 * a un campeon sin errores.
 *
 * Uso: abrir pruebas/simulacion.html en el navegador,
 *      o con Node.js (desde la carpeta web): node pruebas/simulacion.js
 */
const enNode = typeof require !== 'undefined';
const M = enNode ? require('../js/motor.js')
  : { Motor, P, FORMATOS, configuracionPorDefecto, nuevoCampeonato, resumenCampeonato, palmares };
const salida = [];
const log = (...partes) => {
  const linea = partes.join(' ');
  console.log(linea);
  salida.push(linea);
  if (!enNode) document.getElementById('salida').textContent = salida.join('\n');
};

let semilla = 7;
const rnd = () => { semilla = (semilla * 1103515245 + 12345) % 2147483648; return semilla / 2147483648; };
const entero = n => Math.floor(rnd() * n);
let fallos = 0;

function jugar(c) {
  const m = new M.Motor(c);
  try { m.iniciar(); } catch (e) { return 'INVALIDO ' + e.message; }
  for (let iter = 0; iter < 500; iter++) {
    const i = c.fases.length - 1;
    for (const p of m.partidosDeFase(i)) {
      if (M.P.jugado(p)) continue;
      if (p.local === p.visitante) return 'partido contra si mismo';
      const d = M.P.datos(p);
      Object.assign(d, { estado: 'JUGADO', gl: entero(4), gv: entero(4), al: entero(3), rv: entero(2) });
      if (m.requierePenales(p, d.gl, d.gv)) { d.pl = 4; d.pv = rnd() < 0.5 ? 3 : 5; }
      m.aplicarEdicion(p, d, false);
    }
    const vistos = new Set();
    for (const p of m.partidosDeFase(i)) {
      if (p.pierna === 2) continue;
      for (const id of [p.local, p.visitante]) {
        const k = p.grupo + '/' + p.ronda + '/' + id;
        if (vistos.has(k)) return `equipo ${id} repetido en la ronda ${p.ronda}`;
        vistos.add(k);
      }
    }
    try { m.avanzar(); } catch (e) { return 'ERROR avanzar: ' + e.message; }
    if (m.terminado()) {
      if (c.formato === 'DOBLE_ELIMINACION') {
        const n = c.equipos.length, total = c.partidos.length;
        if (total < 2 * n - 2 || total > 2 * n - 1) return `doble eliminacion con ${total} partidos para ${n} equipos`;
      }
      return 'OK ' + c.fases.map(f => f.nombre).join(' > ') + ' -> campeon ' + m.nombre(c.campeon);
    }
  }
  return 'no termino';
}

const tamanos = [3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 16, 20, 24, 32, 36];
for (const formato of Object.keys(M.FORMATOS)) {
  let ok = 0, total = 0, ejemplo = '';
  for (const n of tamanos) {
    for (let rep = 0; rep < 6; rep++) {
      const cfg = M.configuracionPorDefecto();
      Object.assign(cfg, {
        idaYVuelta: rep % 2 === 1, eliminatoriaIdaYVuelta: rep % 3 === 1, finalUnica: rep !== 4,
        sorteoAleatorio: rep % 2 === 0, granFinalConReinicio: rep % 2 === 0,
        tipoPlayoff: rep >= 3 ? 'CUADRANGULARES' : 'LLAVE', clasificadosPlayoff: Math.min(8, n),
        ventajaDeportiva: rep % 2 === 0, rondasSuizo: Math.min(8, n - 1), clasificadosPorGrupo: rep % 3 === 2 ? 3 : 2
      });
      let p = 1; while (p * 2 <= n / 2) p *= 2; if (p < 2) p = 2;
      cfg.directosSuizo = p / 2; cfg.repechajeSuizo = p + p / 2 <= n ? p : 0;
      if (cfg.repechajeSuizo === 0) cfg.directosSuizo = Math.min(n, p);
      const c = M.nuevoCampeonato('T' + n, formato, cfg);
      const m = new M.Motor(c);
      for (let k = 1; k <= n; k++) m.agregarEquipo('E' + k);
      const r = jugar(c);
      if (r.startsWith('INVALIDO')) continue;
      total++;
      if (r.startsWith('OK')) { ok++; if (n === 8 && rep === 0) ejemplo = r; }
      else { fallos++; log(`  FALLO ${formato} n=${n} rep=${rep}: ${r}`); }
    }
  }
  log(`${formato}: ${ok}/${total} completos. Ej n=8: ${ejemplo}`);
}

// Desempate: A y B empatan a puntos, A le gano a B pero B tiene mejor diferencia
{
  const cfg = M.configuracionPorDefecto();
  cfg.criterios = ['ENFRENTAMIENTO_DIRECTO', 'DIFERENCIA_GOLES'];
  const c = M.nuevoCampeonato('D', 'LIGA', cfg);
  const m = new M.Motor(c);
  ['A', 'B', 'C', 'D'].forEach(x => m.agregarEquipo(x));
  const res = [[1, 2, 1, 0], [2, 3, 3, 0], [3, 1, 2, 0], [1, 4, 0, 1], [2, 4, 0, 1], [3, 4, 1, 0]];
  const ps = res.map(([l, v, gl, gv]) => Object.assign(m.nuevoPartido(0, l, v, 1, ''), { estado: 'JUGADO', gl, gv }));
  const t = m.tabla.calcular(ps, [1, 2, 3, 4]).map(f => f.equipo.nombre).join(' ');
  log("Desempate directo + DG:", t, t === 'C D A B' ? '(correcto)' : '(MAL)');
  if (t !== 'C D A B') fallos++;
}

// Tarjetas: roja con 2 fechas en el primer partido -> fuera de los partidos 2 y 3 de su equipo
{
  const c = M.nuevoCampeonato('S', 'LIGA', M.configuracionPorDefecto());
  const m = new M.Motor(c);
  for (let k = 1; k <= 4; k++) m.agregarEquipo('E' + k);
  const jugador = { id: m.nuevoIdJugador(), nombre: 'Expulsado', documento: '', dorsal: 10, posicion: 'Delantero' };
  m.equipo(1).jugadores.push(jugador);
  m.iniciar();
  const delEquipo = c.partidos.filter(p => M.P.involucra(p, 1));
  const datos = M.P.datos(delEquipo[0]);
  datos.estado = 'JUGADO';
  M.P.ponerTarjetas(datos, [{ equipo: 1, jugador: jugador.id, nombre: jugador.nombre, dorsal: 10, tipo: 'ROJA', fechas: 2 }], delEquipo[0].local);
  const esLocal = delEquipo[0].local === 1;
  const contadaBien = esLocal ? datos.rl === 1 && datos.rv === 0 : datos.rv === 1 && datos.rl === 0;
  m.aplicarEdicion(delEquipo[0], datos, false);
  const fuera = [1, 2].every(k => m.suspendidosEn(delEquipo[k]).some(t => t.jugador === jugador.id));
  const antes = m.fechasPendientesDe(jugador.id);
  delEquipo[1].estado = 'JUGADO';
  const despues = m.fechasPendientesDe(jugador.id);
  const roja = m.tablaGeneral().find(f => f.id === 1).tr === 1;
  const ok = fuera && antes === 2 && despues === 1 && roja && contadaBien;
  log('Tarjetas y suspensiones:', ok ? 'correcto' : 'MAL');
  if (!ok) fallos++;
}

// Historial: el campeon va 1.o, el finalista 2.o y el palmares cuenta los titulos
{
  let ok = true;
  for (const formato of Object.keys(M.FORMATOS)) {
    const cfg = Object.assign(M.configuracionPorDefecto(), { clasificadosPlayoff: 4, directosSuizo: 2, repechajeSuizo: 4, rondasSuizo: 4 });
    const c = M.nuevoCampeonato('R', formato, cfg);
    const m = new M.Motor(c);
    for (let k = 1; k <= 8; k++) m.agregarEquipo('E' + k);
    jugar(c);
    const r = M.resumenCampeonato(c);
    const ultimo = c.partidos[c.partidos.length - 1];
    const segundo = formato === 'LIGA' || r.clasificacion[1].id === M.P.rival(ultimo, c.campeon);
    const pal = M.palmares([c, c]);
    const bien = m.terminado() && r.clasificacion[0].id === c.campeon && r.clasificacion.length === 8 && segundo
      && pal[0].titulos === 2 && pal[0].equipo === m.nombre(c.campeon) && r.premios.goleador && r.texto.length > 0;
    if (!bien) { ok = false; log('  Resumen MAL en ' + formato); }
  }
  log('Resumen, premios y palmarés:', ok ? 'correcto' : 'MAL');
  if (!ok) fallos++;
}

// [REQ-07] Calificaciones: mismas reglas y mismos números que SimulacionFormatos.probarCalificaciones en Java.
if (typeof Calificaciones !== 'undefined') {
  class ErrorRegla extends Error {}
  const rechaza = f => { try { f(); return false; } catch (e) { return e instanceof ErrorRegla; } };
  const d = [];
  const reglas = Calificaciones.notaSistema(false, 0, 0, 0, 0, 0, d) === 6 && Calificaciones.notaSistema(false, 2, 1, 1, 0, 0, d) === 8
    && Calificaciones.notaSistema(true, 0, 0, 0, 0, 0, d) === 7.5 && Calificaciones.notaSistema(true, 0, 0, 0, 0, 3, d) === 5.1
    && Calificaciones.notaSistema(false, 0, 0, 0, 3, 0, d) === 1 && Calificaciones.notaSistema(false, 6, 0, 0, 0, 0, d) === 10
    && Calificaciones.redondear((7 + 9.5) / 2) === 8.3 && Calificaciones.redondear(8.45) === 8.5;
  const plantilla = base => Array.from({ length: 14 }, (_, k) => ({ id: base + k + 1, nombre: 'J' + (k + 1), dorsal: k + 1, posicion: k === 0 ? 'Portero' : 'Mediocampista central' }));
  const c = { equipos: [{ id: 1, nombre: 'A', jugadores: plantilla(100) }, { id: 2, nombre: 'B', jugadores: plantilla(200) }] };
  const ahora = 1800000000000;
  const p = {
    id: 1, local: 1, visitante: 2, estado: 'JUGADO', gl: 2, gv: 0, obs: '', finalizadoEn: ahora,
    titularesLocal: c.equipos[0].jugadores.slice(0, 11).map(j => j.id), titularesVisitante: c.equipos[1].jugadores.slice(0, 11).map(j => j.id),
    goles: [{ equipo: 1, enContra: false, autor: 109, asistente: 108 }, { equipo: 1, enContra: false, autor: 109, asistente: 0 }],
    tarjetas: [{ equipo: 1, jugador: 104, tipo: 'AMARILLA' }, { equipo: 1, jugador: 105, tipo: 'ROJA' }], cambios: []
  };
  const de = (lista, dorsal) => lista.find(x => x.dorsal === dorsal);
  const n = Calificaciones.calcular(c, p, ahora + 1000, null);
  const sistema = n.votacion === 'ABIERTA' && n.local.length === 11 && de(n.local, 1).notaSistema === 7.5 && de(n.local, 9).notaSistema === 8
    && de(n.local, 8).notaSistema === 6.5 && de(n.local, 4).notaSistema === 5.5 && de(n.local, 5).notaSistema === 4
    && de(n.visitante, 1).notaSistema === 5.4 && n.mvp === 109;
  Calificaciones.votar(c, p, 101, 10, 'dispositivo-A1', ahora + 2000, ErrorRegla);
  const repetido = rechaza(() => Calificaciones.votar(c, p, 101, 3, 'dispositivo-A1', ahora + 3000, ErrorRegla));
  Calificaciones.votar(c, p, 101, 9, 'dispositivo-B2', ahora + 4000, ErrorRegla);
  const v = Calificaciones.calcular(c, p, ahora + 5000, 'dispositivo-A1');
  const hibrida = repetido && de(v.local, 1).notaFinal === 8.5 && de(v.local, 1).promedioPublico === 9.5 && v.mvp === 101 && de(v.local, 1).miVoto === 10;
  const rango = rechaza(() => Calificaciones.votar(c, p, 101, 11, 'dispositivo-C3', ahora, ErrorRegla))
    && rechaza(() => Calificaciones.votar(c, p, 114, 7, 'dispositivo-C3', ahora, ErrorRegla)); // suplente sin jugar
  const fuera = ahora + Calificaciones.VENTANA_VOTACION_MS + 1;
  const ventana = rechaza(() => Calificaciones.votar(c, p, 109, 8, 'dispositivo-C3', fuera, ErrorRegla))
    && Calificaciones.calcular(c, p, fuera, null).votacion === 'CERRADA';
  P.reiniciar(p);
  Calificaciones.marcarFinal(p, fuera);
  const descarta = p.finalizadoEn === 0 && p.votos.length === 0 && Calificaciones.calcular(c, p, fuera, null).votacion === 'SIN_RESULTADO';
  const ok = reglas && sistema && hibrida && rango && ventana && descarta;
  if (!ok) log(`  reglas=${reglas} sistema=${sistema} hibrida=${hibrida} rango=${rango} ventana=${ventana} descarta=${descarta}`);
  log('Calificaciones de jugadores (sistema, público y MVP):', ok ? 'correcto' : 'MAL');
  if (!ok) fallos++;
}

log(fallos === 0 ? 'TODO BIEN' : 'FALLOS: ' + fallos);
if (enNode) process.exit(fallos === 0 ? 0 : 1);