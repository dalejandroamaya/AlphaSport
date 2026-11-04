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
    const tabla = (texto, directos, repechaje, filas) => ({ texto, directos, repechaje, filas: filas.map(fila) });
    c.fases.forEach((f, i) => {
      if (f.tipo === 'LIGA') lista.push(tabla(f.nombre, corto ? cfg.clasificadosPlayoff : 1, 0, m.tablaDeFase(i)));
      if (f.tipo === 'SUIZO') lista.push(tabla(f.nombre, cfg.directosSuizo, cfg.repechajeSuizo, m.tablaDeFase(i)));
      if (f.tipo === 'GRUPOS') f.grupos.forEach((_, g) => lista.push(tabla(`${f.nombre} - Grupo ${letraGrupo(g)}`,
        corto ? 1 : cfg.clasificadosPorGrupo, 0, m.tablaDeGrupo(i, g))));
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
        partido: `${m.nombre(p.local)} vs ${m.nombre(p.visitante)}`, ronda: p.nombreRonda, fecha: p.fecha, hora: p.hora,
        equipo: m.nombre(t.equipo), jugador: t.nombre, dorsal: t.dorsal, tipo: t.tipo, fechas: t.fechas, pendientes
      }))
    };
  }

  function partido(p) {
    return {
      id: p.id, fase: p.fase, grupo: p.grupo || '', ronda: p.ronda, nombreRonda: p.nombreRonda, pierna: p.pierna || 0,
      llave: p.llave || '', eliminatorio: !!p.eliminatorio, local: p.local, visitante: p.visitante,
      localNombre: m.nombre(p.local), visitanteNombre: m.nombre(p.visitante), estado: p.estado, jugado: P.jugado(p),
      gl: p.gl, gv: p.gv, pl: p.pl, pv: p.pv, penales: P.tienePenales(p), ganador: P.jugado(p) ? P.ganador(p) : -1,
      fecha: p.fecha || 'Por definir', hora: p.hora || '', sede: p.sede || '', obs: p.obs || '',
      tarjetas: P.tarjetas(p).map(t => Object.assign({}, t)),
      suspendidos: m.suspendidosEn(p).map(t => ({ jugador: t.jugador, nombre: t.nombre, equipo: t.equipo, equipoNombre: m.nombre(t.equipo) }))
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
        puedeSiguiente: c.formato === 'TORNEO_CORTO'
      },
      equipos: c.equipos.map(e => ({
        id: e.id, nombre: e.nombre, estadio: e.estadio || '', retirado: !!e.retirado,
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
      campeonFicha: campeon ? {
        nombre: campeon.equipo.nombre, dt: campeon.equipo.dt ? campeon.equipo.dt.nombre : 'Sin asignar',
        pj: campeon.pj, g: campeon.g, e: campeon.e, p: campeon.p, gf: campeon.gf, gc: campeon.gc
      } : null
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
        fase: datos.fases[x.fase].nombre + (x.grupo ? ' · Grupo ' + x.grupo : ''), ronda: x.nombreRonda,
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
      if (f.tipo === 'GRUPOS') f.grupos.forEach((_, g) => { l.push('Grupo ' + letraGrupo(g) + ':'); tabla(m.tablaDeGrupo(i, g)); });
      l.push('RESULTADOS:');
      for (const p of m.partidosDeFase(i)) {
        l.push([(p.grupo ? 'Grupo ' + p.grupo + ' | ' : '') + p.nombreRonda, m.nombre(p.local), P.marcador(p), m.nombre(p.visitante), fechaTexto(p), ESTADOS[p.estado]].join('\t'));
      }
    });
    l.push('', 'TARJETAS:');
    for (const { tarjeta: t, pendientes } of m.listarTarjetas()) {
      l.push([m.nombre(t.equipo), `#${t.dorsal} ${t.nombre}`, t.tipo === 'ROJA' ? `Roja (${t.fechas} fecha/s)` : 'Amarilla',
        pendientes ? `le faltan ${pendientes} fecha(s)` : ''].join('\t'));
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
    if (!equipo) { exigirNoIniciado(); equipo = m.agregarEquipo(nombre); }
    equipo.nombre = nombre;
    equipo.estadio = String(d.estadio || '').trim();
    equipo.dt = d.dt && String(d.dt.nombre || '').trim()
      ? { nombre: d.dt.nombre.trim(), documento: String(d.dt.documento || '').trim(), nacionalidad: String(d.dt.nacionalidad || '').trim() } : null;
    equipo.jugadores = d.jugadores.map(j => ({ id: j.id || m.nuevoIdJugador(), nombre: j.nombre.trim(), documento: String(j.documento || '').trim(), dorsal: j.dorsal, posicion: j.posicion }));
  }

  function guardarPartido({ id, datos: d, accion, confirmado }) {
    const p = c.partidos.find(x => x.id === id);
    if (!p) throw new ErrorRegla('El partido no existe.');
    const datos = P.datos(p);
    if (d.estado === 'JUGADO') {
      datos.estado = 'JUGADO';
      Object.assign(datos, { gl: Math.max(0, d.gl | 0), gv: Math.max(0, d.gv | 0), pl: d.pl, pv: d.pv });
      P.ponerTarjetas(datos, d.tarjetas.map(t => Object.assign({}, t, { fechas: t.tipo === 'ROJA' ? Math.max(1, t.fechas | 0) : 0 })), p.local);
    } else {
      P.reiniciar(datos);
      datos.estado = d.estado;
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

  const comandos = {
    login: d => {
      if (!(String(d.usuario || '').trim() === 'admin' && d.clave === '123')) throw new ErrorRegla('Usuario o contraseña incorrectos.', 'LOGIN');
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
    siguiente: d => {
      exigir();
      const nombre = String(d.nombre || '').trim();
      if (!nombre) throw new ErrorRegla('Escriba el nombre del nuevo torneo.');
      archivarActual();
      const copia = JSON.parse(JSON.stringify(c));
      Object.assign(copia, { nombre, partidos: [], fases: [], paso: 0, campeon: -1, semilla: Math.floor(Math.random() * 2147483647), codigo: nuevoCodigo(), fechaArchivo: null });
      copia.equipos = copia.equipos.filter(e => !e.retirado);
      usar(copia);
    },
    avanzar: () => {
      exigir();
      let mensaje;
      try { mensaje = m.avanzar(); } catch (e) { throw new ErrorRegla(e.message); }
      if (m.terminado()) archivarActual();
      return mensaje;
    },
    'equipo/rapido': d => {
      exigir();
      exigirNoIniciado();
      const nombre = String(d.nombre || '').trim();
      if (!nombre) throw new ErrorRegla('Escriba el nombre del equipo.');
      if (!m.agregarEquipo(nombre)) throw new ErrorRegla(`El equipo «${nombre}» ya existe.`);
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
    }
  };

  const nombreArchivo = s => s.trim().replace(/[^a-zA-Z0-9-_]+/g, '_') || 'campeonato';

  return {
    modo: 'web',
    extensionRespaldo: '.json',
    async catalogo() {
      return {
        modo: 'web',
        formatos: Object.entries(FORMATOS).map(([clave, f]) => ({ clave, nombre: f.nombre, descripcion: f.descripcion })),
        criterios: Object.entries(CRITERIOS).map(([clave, texto]) => ({ clave, texto })),
        posiciones: ['Portero', 'Defensa', 'Mediocampista', 'Delantero'],
        configuracion: configuracionPorDefecto()
      };
    },
    async estado() { return estado(); },
    async historial() { return historial(); },
    async ejecutar(ruta, cuerpo = {}) {
      const comando = comandos[ruta];
      if (!comando) return { ok: false, codigo: 'ERROR', error: 'Acción desconocida: ' + ruta };
      try {
        const mensaje = comando(cuerpo) || null;
        if (c) guardar();
        return { ok: true, mensaje, estado: estado() };
      } catch (e) {
        return { ok: false, codigo: e.codigo || 'ERROR', error: e.message };
      }
    },
    async respaldo() {
      exigir();
      return { nombre: nombreArchivo(c.nombre) + '.json', contenido: new Blob([JSON.stringify(c, null, 2)], { type: 'application/json' }) };
    },
    async exportarHistorial() {
      exigir();
      return { nombre: nombreArchivo(c.nombre) + '.txt', contenido: new Blob([textoHistorial()], { type: 'text/plain' }) };
    },
    async abrirRespaldo(archivo) {
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
