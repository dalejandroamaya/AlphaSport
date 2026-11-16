'use strict';

/*
 * Servicio de la version web independiente: aplica las reglas con motor.js y guarda con Almacen.
 * Responde exactamente lo mismo que la API del programa Java, para que la interfaz sea una sola.
 */

function crearServicioLocal() {
  let c = Almacen.campeonato.cargar();
  if (c && !FORMATOS[c.formato]) c = null;
  let m = c ? new Motor(c) : null;

  class ErrorRegla extends Error {
    constructor(mensaje, codigo = 'REGLA') { super(mensaje); this.codigo = codigo; }
  }

  const exigir = () => { if (!c) throw new ErrorRegla('Primero cree un campeonato.'); };
  const exigirLigaSinIniciar = () => {
    exigir();
    if (c.formato !== 'LIGA') throw new ErrorRegla('Las divisiones solo existen en el formato Liga.');
    if (m.iniciado()) throw new ErrorRegla('El campeonato ya inició: las divisiones se cambian antes de iniciar o en la siguiente temporada.');
  };
  const exigirNoIniciado = () => { if (m.iniciado()) throw new ErrorRegla('El campeonato ya inició: no se pueden agregar equipos nuevos.'); };
  const usar = nuevo => { c = nuevo; m = new Motor(c); };
  const guardar = () => Almacen.campeonato.guardar(c);

  function archivarActual() {
    if (!c || !c.partidos.some(P.jugado)) return;
    if (!c.codigo) c.codigo = nuevoCodigo();
    if (!c.fechaArchivo || m.terminado()) c.fechaArchivo = new Date().toISOString().slice(0, 10);
    Almacen.historial.archivar(c);
    guardar();
  }

  /* ---------------------------------------------------------------- presentacion */

  const fila = f => ({
    id: f.id, nombre: f.equipo.nombre, retirado: !!f.equipo.retirado, pj: f.pj, g: f.g, e: f.e, p: f.p,
    gf: f.gf, gc: f.gc, dg: f.gf - f.gc, ta: f.ta, tr: f.tr, pts: f.pts
  });

  function tablas() {
    const cfg = c.config, corto = c.formato === 'TORNEO_CORTO', lista = [];
    const tabla = (texto, directos, repechaje, filas) => ({ texto, directos, repechaje, descenso: 0, division: false, filas: filas.map(fila) });
    c.fases.forEach((f, i) => {
      if (f.tipo === 'LIGA') lista.push(tabla(f.nombre, corto ? cfg.clasificadosPlayoff : 1, 0, m.tablaDeFase(i)));
      if (f.tipo === 'SUIZO') lista.push(tabla(f.nombre, cfg.directosSuizo, cfg.repechajeSuizo, m.tablaDeFase(i)));
      if (f.tipo === 'GRUPOS' && f.divisiones) {
        // Liga con divisiones: verde = asciende (no en la Liga A), rojo = desciende (no en la última).
        const n = f.grupos.length;
        f.grupos.forEach((_, g) => {
          const filas = m.tablaDeGrupo(i, g), k = Math.max(0, Math.min(cfg.ascensos == null ? 2 : cfg.ascensos, filas.length - 1));
          lista.push(Object.assign(tabla(nombreGrupo(f, g), g > 0 ? k : 0, 0, filas), { descenso: g < n - 1 ? k : 0, division: true }));
        });
      } else if (f.tipo === 'GRUPOS') {
        f.grupos.forEach((_, g) => lista.push(tabla(`${f.nombre} - ${nombreGrupo(f, g)}`, corto ? 1 : cfg.clasificadosPorGrupo, 0, m.tablaDeGrupo(i, g))));
      }
    });
    lista.push(tabla('Estadísticas generales (todos los partidos)', 0, 0, m.tablaGeneral()));
    return lista;
  }

  const lado = (nombre, goles, gana) => ({ nombre, goles, gana });

  function golesSerie(serie, id) {
    let goles = serie.map(p => (P.jugado(p) ? String(P.golesDe(p, id)) : '·')).join(' + ');
    const ultimo = serie[serie.length - 1];
    if (ultimo && P.tienePenales(ultimo)) goles += ` (${ultimo.local === id ? ultimo.pl : ultimo.pv})`;
    return goles;
  }

  function columnasEliminatoria(f, i) {
    const columnas = [];
    for (let r = 1; r <= m.ultimaRonda(i); r++) {
      const entrantes = m.entrantesDeRonda(f, i, r), ganadores = m.ganadoresDeRonda(f, i, r), cruces = [];
      for (let k = 0; k + 1 < entrantes.length; k += 2) {
        const a = entrantes[k], b = entrantes[k + 1];
        if (a === LIBRE || b === LIBRE) {
          cruces.push([lado(m.nombre(a === LIBRE ? b : a), 'pasa directo', true)]);
          continue;
        }
        const g = ganadores[k / 2], serie = m.partidosDeSerie(Motor.claveSerie(i, r, k / 2));
        cruces.push([lado(m.nombre(a), golesSerie(serie, a), g === a), lado(m.nombre(b), golesSerie(serie, b), g === b)]);
      }
      columnas.push({ nombre: f.maxRondas === 1 ? f.nombre : Fixture.nombreRonda(entrantes.length), cruces });
    }
    return columnas;
  }

  function columnasDoble(i, llave) {
    const columnas = [];
    for (let r = 1; r <= m.ultimaRonda(i); r++) {
      const ps = m.partidosDeFase(i).filter(p => p.ronda === r && p.llave === llave);
      if (!ps.length) continue;
      columnas.push({
        nombre: ps[0].nombreRonda,
        cruces: ps.map(p => {
          const g = P.jugado(p) ? P.ganador(p) : -1;
          return [lado(m.nombre(p.local), golesSerie([p], p.local), g === p.local),
            lado(m.nombre(p.visitante), golesSerie([p], p.visitante), g === p.visitante)];
        })
      });
    }
    return columnas;
  }

  function llaves() {
    const lista = [];
    c.fases.forEach((f, i) => {
      if (f.tipo === 'ELIMINATORIA') {
        lista.push({ nombre: f.nombre, secciones: [{ titulo: null, columnas: columnasEliminatoria(f, i) }], vivos: null });
      } else if (f.tipo === 'DOBLE_ELIMINACION') {
        const secciones = [[LLAVE_GANADORES, 'Llave de ganadores'], [LLAVE_PERDEDORES, 'Llave de perdedores'], [LLAVE_GRAN_FINAL, 'Gran final']]
          .map(([llave, titulo]) => ({ titulo, columnas: columnasDoble(i, llave) })).filter(s => s.columnas.length);
        const nombres = ids => ids.map(id => m.nombre(id));
        lista.push({ nombre: f.nombre, secciones, vivos: { ganadores: nombres(m.vivosDoble(f, i, 0)), perdedores: nombres(m.vivosDoble(f, i, 1)) } });
      }
    });
    return lista;
  }

  function sanciones() {
    const registros = m.listarTarjetas();
    const suspendidos = registros.filter(r => r.pendientes > 0)
      .map(r => `${r.tarjeta.nombre} – ${m.nombre(r.tarjeta.equipo)} (${r.pendientes})`);
    return {
      resumen: suspendidos.length ? 'Suspendidos (fechas que faltan): ' + suspendidos.join(', ') : 'No hay jugadores suspendidos.',
      registros: registros.map(({ partido: p, tarjeta: t, pendientes }) => ({
        idPartido: p.id, partido: `${m.nombre(p.local)} vs ${m.nombre(p.visitante)}`, ronda: p.nombreRonda, fecha: p.fecha, hora: p.hora,
        equipo: m.nombre(t.equipo), jugador: t.nombre, dorsal: t.dorsal, tipo: t.tipo, fechas: t.fechas, pendientes
      }))
    };
  }

  function partido(p) {
    return {
      id: p.id, fase: p.fase, grupo: p.grupo || '', grupoNombre: p.grupo ? nombreGrupo(c.fases[p.fase], p.grupo.charCodeAt(0) - 65) : '', ronda: p.ronda, nombreRonda: p.nombreRonda, pierna: p.pierna || 0,
      llave: p.llave || '', eliminatorio: !!p.eliminatorio, local: p.local, visitante: p.visitante,
      localNombre: m.nombre(p.local), visitanteNombre: m.nombre(p.visitante), estado: p.estado, jugado: P.jugado(p),
      gl: p.gl, gv: p.gv, pl: p.pl, pv: p.pv, penales: P.tienePenales(p), ganador: P.jugado(p) ? P.ganador(p) : -1,
      fecha: p.fecha || 'Por definir', hora: p.hora || '', sede: p.sede || '', obs: p.obs || '',
      tarjetas: P.tarjetas(p).map(t => Object.assign({ minuto: 0 }, t)),
      cambios: P.cambios(p).map(x => Object.assign({}, x)),
      goles: P.goles(p).map(x => Object.assign({}, x)),
      titularesLocal: (p.titularesLocal || []).slice(), titularesVisitante: (p.titularesVisitante || []).slice(),
      formacionLocal: p.formacionLocal || '', formacionVisitante: p.formacionVisitante || '',
      suspendidos: m.suspendidosEn(p).map(t => ({ jugador: t.jugador, nombre: t.nombre, equipo: t.equipo, equipoNombre: m.nombre(t.equipo) }))
    };
  }

  /** Estadísticas globales (mismo JSON que Presentador.estadisticas en Java). */
  function estadisticas() {
    const est = EstadisticasCampeonato.calcular(c);
    const filaJugador = f => ({ jugador: f.idJugador, nombre: f.nombre, dorsal: f.dorsal, equipo: f.idEquipo, equipoNombre: f.equipo, goles: f.goles, asistencias: f.asistencias });
    return {
      goleadores: est.goleadores.map(filaJugador),
      asistidores: est.asistidores.map(filaJugador),
      goles: est.registroGoles.map(({ partido: p, gol: g }) => Object.assign({}, g, {
        partido: p.id, partidoTexto: `${m.nombre(p.local)} ${p.gl}-${p.gv} ${m.nombre(p.visitante)}`, ronda: p.nombreRonda,
        fecha: p.fecha || 'Por definir', hora: p.hora || '', equipoNombre: m.nombre(g.equipo)
      })),
      golesSinAutor: est.golesSinAutor,
      valla: est.valla.map(v => ({ id: v.fila.id, nombre: v.fila.equipo.nombre, pj: v.fila.pj, gc: v.fila.gc, promedio: v.promedio.toFixed(2), invictas: v.vallasInvictas })),
      fairPlay: est.fairPlay.map(f => ({ id: f.id, nombre: f.equipo.nombre, pj: f.pj, ta: f.ta, tr: f.tr, puntos: f.ta + 3 * f.tr, promedio: ((f.ta + 3 * f.tr) / f.pj).toFixed(2) }))
    };
  }

  function estado() {
    if (!c) return { hay: false };
    const general = m.tablaGeneral();
    const campeon = m.terminado() ? general.find(f => f.id === c.campeon) : null;
    return {
      hay: true,
      campeonato: {
        nombre: c.nombre, codigo: c.codigo, formato: c.formato, formatoNombre: FORMATOS[c.formato].nombre,
        formatoDescripcion: FORMATOS[c.formato].descripcion, iniciado: m.iniciado(), terminado: m.terminado(),
        estado: m.describirEstado(), campeon: c.campeon, campeonNombre: m.terminado() ? m.nombre(c.campeon) : null,
        reglas: resumenReglas(c), criterios: c.config.criterios.slice(), equiposActivos: m.activos().length,
        puedeSiguiente: c.formato === 'TORNEO_CORTO' || c.formato === 'LIGA',
        textoSiguiente: c.formato === 'LIGA' ? 'Nueva temporada' + (tieneDivisiones(c) ? ' (ascensos y descensos)…' : '…') : 'Siguiente torneo corto (Clausura)…',
        // Sin llaves (formato Liga) la interfaz oculta la sección «Llaves».
        usaLlaves: usaLlaves(c), esLiga: c.formato === 'LIGA',
        divisiones: Array.from({ length: c.formato === 'LIGA' ? numeroDivisiones(c) : 1 }, (_, d) => ({ indice: d, nombre: nombreDivision(d), equipos: equiposDeDivision(c, d).length })),
        ascensos: c.config.ascensos == null ? 2 : c.config.ascensos, maximoDivisiones: MAXIMO_DIVISIONES
      },
      equipos: c.equipos.map(e => ({
        id: e.id, nombre: e.nombre, estadio: e.estadio || '', retirado: !!e.retirado, division: divisionDe(c, e),
        dt: e.dt ? Object.assign({}, e.dt) : null,
        jugadores: e.jugadores.map(j => ({
          id: j.id || 0, nombre: j.nombre, documento: j.documento || '', dorsal: j.dorsal, posicion: j.posicion,
          amarillas: j.id ? m.contarTarjetas(j.id, 'AMARILLA') : 0, rojas: j.id ? m.contarTarjetas(j.id, 'ROJA') : 0,
          pendientes: j.id ? m.fechasPendientesDe(j.id) : 0
        }))
      })),
      fases: c.fases.map(f => ({ nombre: f.nombre, tipo: f.tipo })),
      partidos: c.partidos.map(partido),
      tablas: tablas(),
      leyenda: 'Orden: puntos › ' + c.config.criterios.map(x => CRITERIOS[x]).join(' › ') + '.',
      llaves: llaves(),
      sanciones: sanciones(),
      estadisticas: estadisticas(),
      campeonFicha: campeon ? {
        nombre: campeon.equipo.nombre, dt: campeon.equipo.dt ? campeon.equipo.dt.nombre : 'Sin asignar',
        pj: campeon.pj, g: campeon.g, e: campeon.e, p: campeon.p, gf: campeon.gf, gc: campeon.gc
      } : null,
      // Liga con divisiones: un campeón por división, cada uno con las cifras de su propia tabla.
      campeonesDivision: m.campeonesPorDivision().map((id, d) => {
        const f = m.tablaDeGrupo(0, d).find(x => x.id === id);
        return f ? {
          division: nombreGrupo(c.fases[0], d), nombre: f.equipo.nombre, dt: f.equipo.dt ? f.equipo.dt.nombre : 'Sin asignar',
          pj: f.pj, g: f.g, e: f.e, p: f.p, gf: f.gf, gc: f.gc, pts: f.pts
        } : null;
      }).filter(Boolean)
    };
  }

  function detalleHistorial(datos) {
    const h = new Motor(datos), r = resumenCampeonato(datos), p = r.premios, premios = [];
    const premio = (titulo, valor, detalle) => premios.push({ titulo, valor, detalle });
    if (p.goleador) premio('⚽ Equipo más goleador', p.goleador.equipo.nombre, `${p.goleador.gf} goles`);
    if (p.valla) premio('🧤 Valla menos vencida', p.valla.equipo.nombre, `${p.valla.gc} goles recibidos en ${p.valla.pj} partidos`);
    if (p.fairPlay) premio('🤝 Premio Fair Play', p.fairPlay.equipo.nombre, `${p.fairPlay.ta} amarillas, ${p.fairPlay.tr} rojas`);
    if (p.goleada) premio('💥 Mayor goleada', `${h.nombre(p.goleada.local)} ${p.goleada.gl} - ${p.goleada.gv} ${h.nombre(p.goleada.visitante)}`, p.goleada.nombreRonda);
    premio('📊 Cifras', `${r.jugados} partidos · ${r.goles} goles`, r.jugados ? `${(r.goles / r.jugados).toFixed(2)} goles por partido` : '');
    return {
      codigo: datos.codigo, nombre: datos.nombre, formato: FORMATOS[datos.formato].nombre, fecha: datos.fechaArchivo,
      campeon: r.terminado ? h.nombre(datos.campeon) : null, terminado: r.terminado,
      podio: r.clasificacion.slice(0, 3).map(f => ({ nombre: f.equipo.nombre, dt: f.equipo.dt ? f.equipo.dt.nombre : 'Sin asignar' })),
      premios,
      clasificacion: r.clasificacion.map(fila),
      resultados: datos.partidos.map(x => ({
        fase: datos.fases[x.fase].nombre + (x.grupo ? ' · ' + nombreGrupo(datos.fases[x.fase], x.grupo.charCodeAt(0) - 65) : ''), ronda: x.nombreRonda,
        local: h.nombre(x.local), marcador: P.marcador(x), visitante: h.nombre(x.visitante), estado: ESTADOS[x.estado]
      })),
      tarjetas: h.listarTarjetas().map(({ partido: x, tarjeta: t }) => ({
        equipo: h.nombre(t.equipo), jugador: `#${t.dorsal} ${t.nombre}`, tipo: t.tipo, fechas: t.fechas,
        partido: `${h.nombre(x.local)} vs ${h.nombre(x.visitante)}`
      })),
      texto: r.texto
    };
  }

  function historial() {
    const lista = Almacen.historial.listar().filter(x => x.datos && FORMATOS[x.datos.formato]).map(x => x.datos);
    return { lista: lista.map(detalleHistorial), palmares: palmares(lista) };
  }

  function textoHistorial() {
    const l = [], fechaTexto = p => (p.hora ? `${p.fecha} ${p.hora}` : p.fecha);
    const filaTxt = (f, i) => [i + 1, f.equipo.nombre, f.pj, f.g, f.e, f.p, f.gf, f.gc, f.gf - f.gc, f.pts].join('\t');
    const tabla = filas => l.push('Pos\tEquipo\tPJ\tG\tE\tP\tGF\tGC\tDG\tPts', ...filas.map(filaTxt));
    l.push('ALPHASPORT - HISTORIAL DEL CAMPEONATO', '', 'CAMPEONATO: ' + c.nombre, 'FORMATO: ' + FORMATOS[c.formato].nombre, ...resumenReglas(c));
    l.push('Desempate: ' + c.config.criterios.map(x => CRITERIOS[x]).join(' > '), '', 'EQUIPOS:');
    for (const e of c.equipos) l.push(`- ${e.nombre} | DT: ${e.dt ? e.dt.nombre : 'Sin asignar'} | Jugadores: ${e.jugadores.length}${e.retirado ? ' | RETIRADO' : ''}`);
    c.fases.forEach((f, i) => {
      l.push('', '=== ' + f.nombre.toUpperCase() + ' ===');
      if (f.tipo === 'LIGA' || f.tipo === 'SUIZO') tabla(m.tablaDeFase(i));
      if (f.tipo === 'GRUPOS') f.grupos.forEach((_, g) => { l.push(nombreGrupo(f, g) + ':'); tabla(m.tablaDeGrupo(i, g)); });
      l.push('RESULTADOS:');
      for (const p of m.partidosDeFase(i)) {
        l.push([(p.grupo ? 'Grupo ' + p.grupo + ' | ' : '') + p.nombreRonda, m.nombre(p.local), P.marcador(p), m.nombre(p.visitante), fechaTexto(p), ESTADOS[p.estado]].join('\t'));
      }
    });
    l.push('', 'TARJETAS:');
    for (const { tarjeta: t, pendientes } of m.listarTarjetas()) {
      l.push([m.nombre(t.equipo), `#${t.dorsal} ${t.nombre}`, t.tipo === 'ROJA' ? `Roja (${plural(t.fechas, '1 fecha', `${t.fechas} fechas`)})` : 'Amarilla',
        pendientes ? plural(pendientes, 'le falta 1 fecha', `le faltan ${pendientes} fechas`) : ''].join('\t'));
    }
    l.push('', m.terminado() ? 'CAMPEON: ' + m.nombre(c.campeon) : 'CAMPEONATO EN CURSO');
    return l.join('\r\n');
  }

  /* ---------------------------------------------------------------- comandos */

  function guardarEquipo(d) {
    const nombre = String(d.nombre || '').trim();
    if (!nombre) throw new ErrorRegla('Debe ingresar un nombre.');
    const existente = d.id == null ? null : m.equipo(d.id);
    const homonimo = c.equipos.find(x => x.nombre.toLowerCase() === nombre.toLowerCase());
    if (homonimo && homonimo !== existente) throw new ErrorRegla(`El equipo ${nombre} ya existe.`);
    const usados = new Set();
    for (const j of d.jugadores) {
      if (!String(j.nombre || '').trim()) throw new ErrorRegla('Escriba el nombre del jugador.');
      if (!(j.dorsal >= 1 && j.dorsal <= 99)) throw new ErrorRegla('El dorsal debe estar entre 1 y 99.');
      if (usados.has(j.dorsal)) throw new ErrorRegla(`El dorsal ${j.dorsal} ya está en uso.`);
      usados.add(j.dorsal);
    }
    let equipo = existente;
    if (!equipo) { exigirNoIniciado(); const division = divisionConMenosEquipos(c); equipo = m.agregarEquipo(nombre); equipo.division = division; }
    equipo.nombre = nombre;
    equipo.estadio = String(d.estadio || '').trim();
    equipo.dt = d.dt && String(d.dt.nombre || '').trim()
      ? { nombre: d.dt.nombre.trim(), documento: String(d.dt.documento || '').trim(), nacionalidad: String(d.dt.nacionalidad || '').trim() } : null;
    equipo.jugadores = d.jugadores.map(j => ({ id: j.id || m.nuevoIdJugador(), nombre: j.nombre.trim(), documento: String(j.documento || '').trim(), dorsal: j.dorsal, posicion: j.posicion }));
  }

  const MINUTO_MAXIMO = 130; // 90 + prórroga + descuento

  const TITULARES = 11;

  /** XI inicial de un equipo: jugadores de su plantilla, exactamente 11 (o toda la plantilla si tiene menos). */
  function leerTitulares(lista, idEquipo) {
    const ids = [...new Set(lista.map(Number))];
    if (!ids.length) return [];
    const e = c.equipos.find(q => q.id === idEquipo);
    if (ids.some(id => !e.jugadores.some(j => j.id === id))) throw new ErrorRegla(`Uno de los titulares de ${e.nombre} no está en su plantilla.`);
    const necesarios = Math.min(TITULARES, e.jugadores.length);
    if (ids.length !== necesarios) throw new ErrorRegla(`El XI inicial de ${e.nombre} debe tener ${necesarios} jugadores (tiene ${ids.length}).`);
    return ids;
  }

  /**
   * Revisa los cambios en orden de minuto: sale alguien que está en la cancha
   * (titular o que ya entró), entra un suplente, y quien salió no vuelve.
   */
  function leerCambios(lista, p, datos) {
    const cambios = lista.map(x => {
      const equipo = x.equipo | 0;
      if (equipo !== p.local && equipo !== p.visitante) throw new ErrorRegla('Uno de los cambios es de un equipo que no juega este partido.');
      const minuto = x.minuto | 0;
      if (minuto < 1 || minuto > MINUTO_MAXIMO) throw new ErrorRegla(`El minuto del cambio debe estar entre 1 y ${MINUTO_MAXIMO}.`);
      const e = c.equipos.find(q => q.id === equipo);
      const sale = e && e.jugadores.find(j => j.id === x.sale), entra = e && e.jugadores.find(j => j.id === x.entra);
      if (!sale || !entra) throw new ErrorRegla('Uno de los cambios es de un jugador que ya no existe.');
      if (sale.id === entra.id) throw new ErrorRegla('En un cambio el jugador que sale y el que entra deben ser distintos.');
      return { equipo, minuto, sale: sale.id, saleNombre: sale.nombre, saleDorsal: sale.dorsal, entra: entra.id, entraNombre: entra.nombre, entraDorsal: entra.dorsal };
    }).sort((a, b) => a.minuto - b.minuto);
    const salieron = new Set(), entraron = new Set();
    const titularesDe = id => (id === p.local ? datos.titularesLocal : datos.titularesVisitante) || [];
    const enCancha = new Map();
    for (const id of [p.local, p.visitante]) if (titularesDe(id).length) enCancha.set(id, new Set(titularesDe(id)));
    for (const x of cambios) {
      const cancha = enCancha.get(x.equipo);
      if (!cancha) throw new ErrorRegla(`Antes de registrar cambios escoja el XI inicial de ${m.nombre(x.equipo)}.`);
      if (!cancha.has(x.sale) && !salieron.has(x.sale)) throw new ErrorRegla(`#${x.saleDorsal} ${x.saleNombre} no está en la cancha en el minuto ${x.minuto}: solo puede salir un titular o alguien que ya entró.`);
      if (titularesDe(x.equipo).includes(x.entra)) throw new ErrorRegla(`#${x.entraDorsal} ${x.entraNombre} es titular: solo pueden entrar suplentes.`);
      if (salieron.has(x.sale)) throw new ErrorRegla(`#${x.saleDorsal} ${x.saleNombre} ya había salido antes del minuto ${x.minuto}.`);
      if (salieron.has(x.entra)) throw new ErrorRegla(`#${x.entraDorsal} ${x.entraNombre} ya fue sustituido y no puede volver a entrar.`);
      if (entraron.has(x.entra)) throw new ErrorRegla(`#${x.entraDorsal} ${x.entraNombre} ya había entrado en otro cambio.`);
      salieron.add(x.sale);
      entraron.add(x.entra);
      cancha.delete(x.sale);
      cancha.add(x.entra);
    }
    return cambios;
  }

  /**
   * Goles por jugador: el autor es del equipo que suma el gol (o del rival si es en contra)
   * y la asistencia, opcional, es de un compañero distinto del autor. Igual que en Java.
   */
  function leerGoles(lista, p) {
    return lista.map(x => {
      const equipo = x.equipo | 0;
      if (equipo !== p.local && equipo !== p.visitante) throw new ErrorRegla('Uno de los goles es de un equipo que no juega este partido.');
      const minuto = x.minuto | 0;
      if (minuto < 0 || minuto > MINUTO_MAXIMO) throw new ErrorRegla(`El minuto del gol debe estar entre 1 y ${MINUTO_MAXIMO}.`);
      const enContra = !!x.enContra;
      const eqAutor = c.equipos.find(q => q.id === (enContra ? P.rival(p, equipo) : equipo));
      const autor = eqAutor && eqAutor.jugadores.find(j => j.id === x.autor);
      if (!autor) throw new ErrorRegla(`Uno de los goles es de un jugador que no está en la plantilla${enContra ? ' del rival' : ' de su equipo'}.`);
      let asistente = null;
      if (x.asistente && !enContra) {
        const eq = c.equipos.find(q => q.id === equipo);
        asistente = eq.jugadores.find(j => j.id === x.asistente);
        if (!asistente) throw new ErrorRegla(`La asistencia del gol de ${autor.nombre} es de un jugador que no está en su equipo.`);
        if (asistente.id === autor.id) throw new ErrorRegla(`${autor.nombre} no puede darse la asistencia a sí mismo.`);
      }
      return {
        equipo, minuto, enContra, autor: autor.id, autorNombre: autor.nombre, autorDorsal: autor.dorsal,
        asistente: asistente ? asistente.id : 0, asistenteNombre: asistente ? asistente.nombre : '', asistenteDorsal: asistente ? asistente.dorsal : 0
      };
    }).sort((a, b) => (a.minuto || 9999) - (b.minuto || 9999));
  }

  /** Valida el texto de una formación ('' = sin definir). */
  function leerFormacion(texto) {
    const f = String(texto || '').trim();
    if (!f) return '';
    if (!FORMACIONES.includes(f)) throw new ErrorRegla(`La formación «${f}» no existe.`);
    return f;
  }

  /** Equipos de prueba con plantilla completa generados con una semilla (mismo algoritmo que la versión Java). */
  function cargarDatosPrueba(d) {
    exigir();
    const semilla = d.semilla | 0;
    const pedidos = Math.max(0, Math.min(DatosPrueba.MAXIMO_EQUIPOS, d.cantidad | 0));
    if (pedidos > 0) exigirNoIniciado();
    const completar = d.completar !== false;
    const sinPlantilla = completar ? c.equipos.filter(e => !e.retirado && !e.jugadores.length) : [];
    if (!pedidos && !sinPlantilla.length) {
      throw new ErrorRegla(completar ? 'Todos los equipos ya tienen plantilla. Indique cuántos equipos nuevos crear.' : 'Indique cuántos equipos de prueba crear.');
    }
    const r = DatosPrueba.generar(semilla, pedidos, c.equipos.map(e => e.nombre), sinPlantilla.length);
    const conIds = jugadores => jugadores.map(j => Object.assign(j, { id: m.nuevoIdJugador() }));
    for (const e of r.equipos) {
      const division = divisionConMenosEquipos(c); // reparte los equipos entre las divisiones
      const equipo = m.agregarEquipo(e.nombre);
      equipo.division = division;
      equipo.estadio = e.estadio;
      equipo.dt = e.dt;
      equipo.jugadores = e.jugadores;
      conIds(equipo.jugadores);
    }
    sinPlantilla.forEach((e, i) => { e.jugadores = conIds(r.plantillas[i]); });
    const creados = r.equipos.length;
    let texto = creados ? plural(creados, '1 equipo de prueba creado', `${creados} equipos de prueba creados`) : '';
    if (sinPlantilla.length) texto += (texto ? '; plantilla completada en ' : 'Plantilla completada en ') + plural(sinPlantilla.length, '1 equipo', `${sinPlantilla.length} equipos`);
    if (creados < pedidos) texto += ' (no hay más nombres de prueba libres)';
    return `${texto}. Semilla ${semilla}.`;
  }

  function guardarPartido({ id, datos: d, accion, confirmado }) {
    const p = c.partidos.find(x => x.id === id);
    if (!p) throw new ErrorRegla('El partido no existe.');
    const datos = P.datos(p);
    // XI inicial de los dos equipos, si viene (la carga rápida no lo envía: se conserva).
    const ponerTitulares = () => {
      if (!d.titularesLocal) return;
      datos.titularesLocal = leerTitulares(d.titularesLocal, p.local);
      datos.titularesVisitante = leerTitulares(d.titularesVisitante || [], p.visitante);
    };
    if (d.estado === 'JUGADO' || d.estado === 'EN_JUEGO') {
      // «En juego»: el marcador, las tarjetas y los cambios se registran en vivo (sin penales).
      const jugado = d.estado === 'JUGADO';
      datos.estado = d.estado;
      Object.assign(datos, { gl: Math.max(0, d.gl | 0), gv: Math.max(0, d.gv | 0), pl: jugado ? d.pl : SIN_PENALES, pv: jugado ? d.pv : SIN_PENALES });
      P.ponerTarjetas(datos, d.tarjetas.map(t => {
        const minuto = t.minuto | 0;
        if (minuto < 0 || minuto > MINUTO_MAXIMO) throw new ErrorRegla(`El minuto de la tarjeta debe estar entre 1 y ${MINUTO_MAXIMO}.`);
        return Object.assign({}, t, { minuto, fechas: t.tipo === 'ROJA' ? Math.max(1, t.fechas | 0) : 0 });
      }), p.local);
      ponerTitulares(); // antes de los cambios: se validan contra este XI
      if (d.cambios) datos.cambios = leerCambios(d.cambios, p, datos);
      if (d.goles) datos.goles = leerGoles(d.goles, p); // la carga rápida no envía los goles: se conservan
      // Nunca puede haber más goles con autor que goles en el marcador (también al bajar el marcador).
      for (const [id, marcador] of [[p.local, datos.gl], [p.visitante, datos.gv]]) {
        const registrados = P.goles(datos).filter(g => g.equipo === id).length;
        if (registrados > marcador) {
          throw new ErrorRegla(`Hay ${registrados} ${registrados === 1 ? 'gol registrado' : 'goles registrados'} de ${m.nombre(id)}, pero el marcador dice ${marcador}. Corrija el marcador o quite goles en el editor del partido.`);
        }
      }
    } else {
      P.reiniciar(datos);
      datos.estado = d.estado;
      ponerTitulares(); // «Previa del partido»: el XI se elige antes de jugar
    }
    // La formación se elige antes del partido: se guarda en cualquier estado.
    if ('formacionLocal' in d) {
      datos.formacionLocal = leerFormacion(d.formacionLocal);
      datos.formacionVisitante = leerFormacion(d.formacionVisitante);
    }
    datos.fecha = d.fecha || 'Por definir';
    datos.hora = d.hora || '';
    datos.sede = String(d.sede || '').trim();
    datos.obs = String(d.obs || '').trim();
    if (accion === 'woLocal' || accion === 'woVisitante') {
      const ganador = accion === 'woLocal' ? p.local : p.visitante;
      Motor.walkover(datos, p, ganador, 'W.O. a favor de ' + m.nombre(ganador));
    } else if (accion === 'borrar') {
      P.reiniciar(datos);
    }
    if (P.jugado(datos)) {
      const necesita = m.requierePenales(p, datos.gl, datos.gv);
      if (necesita && (datos.pl === SIN_PENALES || datos.pl === datos.pv)) {
        throw new ErrorRegla(`Este partido de eliminación terminó empatado${p.pierna === 2 ? ' en el global' : ''}: marque «Se definió por penales» y escriba un resultado sin empate.`, 'PENALES');
      }
      if (!necesita) { datos.pl = SIN_PENALES; datos.pv = SIN_PENALES; }
    }
    const afecta = m.afectaRondasPosteriores(p, datos);
    if (afecta && !confirmado) {
      throw new ErrorRegla('Este cambio modifica quién clasifica o avanza.\nLas rondas y fases generadas después de este partido se borrarán y tendrá que volver a pulsar «Avanzar» para generarlas de nuevo.\n\n¿Desea continuar?', 'CONFIRMAR');
    }
    m.aplicarEdicion(p, datos, afecta);
  }

  /* ---------------------------------------------------------------- sesión y permisos (igual que ApiControlador) */

  /** Cuentas demostrativas, como AutenticacionService en Java (sin base de datos). */
  const CUENTAS = { admin: { clave: '123', rol: 'ADMINISTRADOR' }, visitante: { clave: '321', rol: 'VISITANTE' } };
  /** Acciones que no exigen ser administrador. Todo lo demás sí (lista blanca). */
  const RUTAS_PUBLICAS = new Set(['login', 'login/visitante', 'logout']);

  let usuario = Almacen.sesion.cargar();
  if (usuario && !ROLES[usuario.rol]) usuario = null;

  const sesion = () => (usuario ? {
    usuario: usuario.usuario, rol: usuario.rol, rolNombre: ROLES[usuario.rol].nombre, puedeEditar: ROLES[usuario.rol].puedeEditar
  } : null);

  function abrirSesion(nombre, rol) {
    usuario = { usuario: nombre, rol };
    Almacen.sesion.guardar(usuario);
  }

  function exigirSesion() {
    if (!usuario) throw new ErrorRegla('Inicie sesión para continuar.', 'LOGIN');
  }

  function exigirAdministrador() {
    exigirSesion();
    if (!ROLES[usuario.rol].puedeEditar) {
      throw new ErrorRegla(`Su rol (${ROLES[usuario.rol].nombre}) es de solo lectura: no puede crear, editar ni eliminar.`, 'PERMISO');
    }
  }

  /** Respuesta de error con el mismo formato que la API de Java. */
  const fallo = e => ({ ok: false, codigo: e.codigo || 'ERROR', error: e.message });

  const comandos = {
    login: d => {
      const nombre = String(d.usuario || '').trim().toLowerCase();
      const cuenta = CUENTAS[nombre];
      if (!cuenta || cuenta.clave !== d.clave) throw new ErrorRegla('Usuario o contraseña incorrectos.', 'LOGIN');
      abrirSesion(nombre, cuenta.rol);
    },
    'login/visitante': () => abrirSesion('visitante', 'VISITANTE'),
    logout: () => {
      usuario = null;
      Almacen.sesion.eliminar();
    },
    nuevo: d => {
      const nombre = String(d.nombre || '').trim();
      if (!nombre) throw new ErrorRegla('Escriba el nombre del campeonato.');
      if (!d.config.criterios.length) throw new ErrorRegla('Escoja al menos un criterio de desempate.');
      const nuevo = nuevoCampeonato(nombre, d.formato, Object.assign(configuracionPorDefecto(), d.config));
      if (d.mismosEquipos && c) importarEquipos(nuevo, c.equipos);
      archivarActual();
      usar(nuevo);
    },
    cancelar: () => {
      exigir();
      c = null;
      m = null;
      Almacen.campeonato.eliminar();
    },
    cambiarFormato: d => {
      exigir();
      if (!d.config.criterios.length) throw new ErrorRegla('Escoja al menos un criterio de desempate.');
      const nombre = String(d.nombre || '').trim() || c.nombre;
      const nuevo = nuevoCampeonato(nombre, d.formato, Object.assign(configuracionPorDefecto(), d.config));
      importarEquipos(nuevo, c.equipos);
      usar(nuevo);
    },
    // Clausura de un torneo corto o siguiente temporada de una liga (con ascensos y descensos si hay divisiones).
    siguiente: d => {
      exigir();
      const nombre = String(d.nombre || '').trim();
      if (!nombre) throw new ErrorRegla('Escriba el nombre del nuevo torneo.');
      if (c.formato !== 'TORNEO_CORTO' && c.formato !== 'LIGA') throw new ErrorRegla('La siguiente temporada solo existe en los formatos Liga y Apertura/Clausura.');
      const nuevaDivision = new Map();
      let movimientos = '';
      const k = c.config.ascensos == null ? 2 : c.config.ascensos;
      if (tieneDivisiones(c) && k > 0) {
        if (!m.terminado()) throw new ErrorRegla('Termine la temporada (hasta tener campeón) para saber quién asciende y quién desciende.');
        const n = c.fases[0].grupos.length, partes = [];
        for (let dv = 0; dv + 1 < n; dv++) {
          const arriba = m.tablaDeGrupo(0, dv).map(f => f.id), abajo = m.tablaDeGrupo(0, dv + 1).map(f => f.id);
          const cuantos = Math.min(k, Math.min(arriba.length, abajo.length) - 1); // cada división conserva al menos un equipo
          if (cuantos <= 0) continue;
          const bajan = arriba.slice(arriba.length - cuantos), suben = abajo.slice(0, cuantos);
          bajan.forEach(id => nuevaDivision.set(id, dv + 1));
          suben.forEach(id => nuevaDivision.set(id, dv));
          partes.push(`Suben a la ${nombreDivision(dv)}: ${suben.map(id => m.nombre(id)).join(', ')}. Bajan a la ${nombreDivision(dv + 1)}: ${bajan.map(id => m.nombre(id)).join(', ')}.`);
        }
        movimientos = partes.join(' ');
      }
      archivarActual();
      const copia = JSON.parse(JSON.stringify(c));
      Object.assign(copia, { nombre, partidos: [], fases: [], paso: 0, campeon: -1, semilla: Math.floor(Math.random() * 2147483647), codigo: nuevoCodigo(), fechaArchivo: null });
      copia.equipos = copia.equipos.filter(e => !e.retirado);
      copia.equipos.forEach(e => { if (nuevaDivision.has(e.id)) e.division = nuevaDivision.get(e.id); });
      usar(copia);
      return movimientos;
    },
    'equipo/division': d => {
      exigirLigaSinIniciar();
      const e = m.equipo(d.id);
      if (!e) throw new ErrorRegla('El equipo no existe.');
      if (!(d.division >= 0 && d.division < numeroDivisiones(c))) throw new ErrorRegla('Esa división no existe.');
      e.division = d.division;
    },
    'division/agregar': () => {
      exigirLigaSinIniciar();
      if (numeroDivisiones(c) >= MAXIMO_DIVISIONES) throw new ErrorRegla(`Se permiten como máximo ${MAXIMO_DIVISIONES} divisiones.`);
      c.config.divisiones = numeroDivisiones(c) + 1;
      return `Se creó la ${nombreDivision(c.config.divisiones - 1)}. Asígnele equipos.`;
    },
    'division/quitar': () => {
      exigirLigaSinIniciar();
      const n = numeroDivisiones(c);
      if (n <= 1) throw new ErrorRegla('La liga ya tiene una sola división.');
      const movidos = equiposDeDivision(c, n - 1);
      movidos.forEach(e => { e.division = n - 2; });
      c.config.divisiones = n - 1;
      return `Se quitó la ${nombreDivision(n - 1)}.` + (movidos.length ? ` Sus ${movidos.length} equipos pasaron a la ${nombreDivision(n - 2)}.` : '');
    },
    avanzar: () => {
      exigir();
      let mensaje;
      try { mensaje = m.avanzar(); } catch (e) { throw new ErrorRegla(e.message); }
      if (m.terminado()) archivarActual();
      return mensaje;
    },
    semilla: d => cargarDatosPrueba(d),
    'equipo/rapido': d => {
      exigir();
      exigirNoIniciado();
      const nombre = String(d.nombre || '').trim();
      if (!nombre) throw new ErrorRegla('Escriba el nombre del equipo.');
      const division = divisionConMenosEquipos(c);
      const nuevo = m.agregarEquipo(nombre);
      if (!nuevo) throw new ErrorRegla(`El equipo «${nombre}» ya existe.`);
      nuevo.division = division;
    },
    equipo: d => { exigir(); guardarEquipo(d); },
    'equipo/quitar': d => {
      exigir();
      const e = m.equipo(d.id);
      if (!e) throw new ErrorRegla('El equipo no existe.');
      return m.retirarEquipo(e);
    },
    partido: d => { exigir(); guardarPartido(d); },
    criterios: d => {
      exigir();
      if (!d.criterios.length) throw new ErrorRegla('Debe haber al menos un criterio de desempate.');
      c.config.criterios = d.criterios.slice();
    },
    archivar: () => archivarActual(),
    'historial/eliminar': d => {
      if (!Almacen.historial.eliminar(d.codigo)) throw new ErrorRegla('No se encontró ese campeonato en el historial.');
    },
    // Noticias (mismas rutas y mensajes que ApiControlador)
    'noticia/proponer': d => { noticias.proponer(d); return '¡Gracias! Su noticia quedó pendiente: se publicará cuando el administrador la apruebe.'; },
    'noticia/comentar': d => { noticias.comentar(d.id, d.autor, d.texto); return 'Comentario publicado.'; },
    'noticia/guardar': d => { noticias.publicar(d); return d.id == null ? 'Noticia publicada.' : 'Noticia guardada.'; },
    'noticia/aprobar': d => { noticias.aprobar(d.id); return 'Noticia aprobada: ya aparece en el feed.'; },
    'noticia/eliminar': d => { noticias.eliminar(d.id); return d.rechazo ? 'Propuesta rechazada.' : 'Noticia eliminada.'; },
    'comentario/eliminar': d => { noticias.eliminarComentario(d.noticia, d.id); return 'Comentario eliminado.'; }
  };

  /* ---------------------------------------------------------------- noticias */

  const noticias = crearNoticiasLocal(ErrorRegla);
  /** Lo único que un visitante puede crear: proponer noticias (quedan pendientes) y comentar. */
  const RUTAS_VISITANTE = new Set(['noticia/proponer', 'noticia/comentar']);

  /** Mismo JSON que Presentador.noticias en Java. «imagen» es la foto como data URL ('' = sin foto). */
  function noticiasJson() {
    const admin = !!(usuario && ROLES[usuario.rol].puedeEditar);
    const noticia = n => ({
      id: n.id, titulo: n.titulo, cuerpo: n.cuerpo, autor: n.autor, deVisitante: n.rolAutor === 'VISITANTE', fecha: n.fecha,
      imagen: n.imagenRuta || '', aprobado: n.aprobado, comentarios: n.comentarios.map(x => Object.assign({}, x))
    });
    return { publicadas: noticias.publicadas().map(noticia), pendientes: admin ? noticias.pendientes().map(noticia) : [], puedeModerar: admin };
  }

  const nombreArchivo = s => s.trim().replace(/[^a-zA-Z0-9-_]+/g, '_') || 'campeonato';

  return {
    modo: 'web',
    extensionRespaldo: '.json',
    async catalogo() {
      return {
        modo: 'web',
        formatos: Object.entries(FORMATOS).map(([clave, f]) => ({ clave, nombre: f.nombre, descripcion: f.descripcion })),
        criterios: Object.entries(CRITERIOS).map(([clave, texto]) => ({ clave, texto })),
        posiciones: GRUPOS_POSICIONES.flatMap(g => g.posiciones),
        gruposPosiciones: GRUPOS_POSICIONES.map(g => ({ grupo: g.grupo, posiciones: g.posiciones.slice() })),
        semillaMaximoEquipos: DatosPrueba.MAXIMO_EQUIPOS,
        configuracion: configuracionPorDefecto(),
        formaciones: FORMACIONES.slice(),
        formacionPorDefecto: FORMACION_POR_DEFECTO
      };
    },
    async sesion() { return { ok: true, sesion: sesion() }; },
    // Consultar es solo lectura: basta con haber iniciado sesión.
    async estado() {
      try { exigirSesion(); return estado(); } catch (e) { return fallo(e); }
    },
    async historial() {
      try { exigirSesion(); return historial(); } catch (e) { return fallo(e); }
    },
    async noticias() {
      try { exigirSesion(); return { ok: true, noticias: noticiasJson() }; } catch (e) { return fallo(e); }
    },
    /** En la web sola la foto ya es una «data URL»: se usa tal cual. */
    urlImagen: imagen => imagen || '',
    /** Detalle tipo Flashscore de un partido (mismo JSON que GET /api/partido/{id}). */
    async detalle(id) {
      try {
        exigirSesion();
        exigir();
        const p = c.partidos.find(x => x.id === id);
        if (!p) throw new ErrorRegla('El partido no existe.');
        const anteriores = Almacen.historial.listar().filter(x => x.datos && FORMATOS[x.datos.formato]).map(x => x.datos);
        return Object.assign({ ok: true, partido: partido(p) }, DetallePartido.calcular(c, anteriores, p));
      } catch (e) {
        return fallo(e);
      }
    },
    async ejecutar(ruta, cuerpo = {}) {
      const comando = comandos[ruta];
      if (!comando) return { ok: false, codigo: 'ERROR', error: 'Acción desconocida: ' + ruta };
      try {
        if (RUTAS_VISITANTE.has(ruta)) exigirSesion();                  // cualquiera con sesión: proponer y comentar
        else if (!RUTAS_PUBLICAS.has(ruta)) exigirAdministrador();      // toda acción que modifica datos pasa por aquí
        const mensaje = comando(cuerpo) || null;
        if (ruta === 'logout') return { ok: true };
        if (ruta.startsWith('noticia/') || ruta.startsWith('comentario/')) {
          return { ok: true, mensaje, estado: estado(), sesion: sesion(), noticias: noticiasJson() };
        }
        if (c && !RUTAS_PUBLICAS.has(ruta)) guardar();
        return { ok: true, mensaje, estado: estado(), sesion: sesion() };
      } catch (e) {
        return fallo(e);
      }
    },
    async respaldo() {
      exigirSesion();
      exigir();
      return { nombre: nombreArchivo(c.nombre) + '.json', contenido: new Blob([JSON.stringify(c, null, 2)], { type: 'application/json' }) };
    },
    async exportarHistorial() {
      exigirSesion();
      exigir();
      return { nombre: nombreArchivo(c.nombre) + '.txt', contenido: new Blob([textoHistorial()], { type: 'text/plain' }) };
    },
    async abrirRespaldo(archivo) {
      try { exigirAdministrador(); } catch (e) { return fallo(e); }
      try {
        const datos = JSON.parse(await archivo.text());
        if (!datos || !FORMATOS[datos.formato] || !Array.isArray(datos.equipos) || !Array.isArray(datos.partidos) || !Array.isArray(datos.fases)) {
          throw new Error('formato');
        }
        if (!datos.codigo) datos.codigo = nuevoCodigo();
        archivarActual();
        usar(datos);
        guardar();
        return { ok: true, mensaje: `Se abrió «${datos.nombre}».`, estado: estado() };
      } catch (e) {
        return { ok: false, codigo: 'ARCHIVO', error: 'El archivo no es un respaldo válido de AlphaSport.' };
      }
    },
    async salir() {}
  };
}
