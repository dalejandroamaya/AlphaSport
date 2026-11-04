'use strict';

/* Reglas del torneo (web): fixture, tablas, desempates, llaves, sanciones y premios. Mismas reglas que el servicio Java. */

function azar(semilla) {
  let a = semilla >>> 0;
  return function () {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function barajar(lista, rnd) {
  for (let i = lista.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [lista[i], lista[j]] = [lista[j], lista[i]];
  }
  return lista;
}

/* ------------------------------------------------------------------ */
/*  Partido                                                            */
/* ------------------------------------------------------------------ */

const Fixture = {
  // Metodo del circulo, igual que GeneradorFixture.todosContraTodos en Java
  todosContraTodos(ids, idaYVuelta) {
    const equipos = ids.slice();
    if (equipos.length % 2 !== 0) equipos.push(LIBRE);
    const n = equipos.length;
    const jornadas = [];
    for (let ronda = 0; ronda < n - 1; ronda++) {
      const jornada = [];
      for (let i = 0; i < n / 2; i++) {
        const a = equipos[i], b = equipos[n - 1 - i];
        if (a === LIBRE || b === LIBRE) continue;
        const aEsLocal = i === 0 ? ronda % 2 === 0 : i % 2 === 0;
        jornada.push(aEsLocal ? [a, b] : [b, a]);
      }
      jornadas.push(jornada);
      equipos.splice(1, 0, equipos.pop());
    }
    if (idaYVuelta) {
      const ida = jornadas.length;
      for (let r = 0; r < ida; r++) jornadas.push(jornadas[r].map(([a, b]) => [b, a]));
    }
    return jornadas;
  },
  potenciaDeDos(n) { let p = 1; while (p < n) p *= 2; return p; },
  // [1, 8, 4, 5, 2, 7, 3, 6] para P = 8
  ordenSembrado(p) {
    let orden = [1];
    if (p === 1) return orden;
    orden = [1, 2];
    let tam = 2;
    while (tam < p) {
      tam *= 2;
      orden = orden.flatMap(s => [s, tam + 1 - s]);
    }
    return orden;
  },
  cuadroDesdeSiembra(sembrados) {
    const p = Fixture.potenciaDeDos(Math.max(2, sembrados.length));
    return Fixture.ordenSembrado(p).map(s => (s <= sembrados.length ? sembrados[s - 1] : LIBRE));
  },
  nombreRonda(n) {
    return { 2: 'Final', 4: 'Semifinales', 8: 'Cuartos de final', 16: 'Octavos de final', 32: 'Dieciseisavos de final' }[n]
      || 'Ronda de ' + n;
  }
};

/* ------------------------------------------------------------------ */
/*  Tabla de posiciones con desempates configurables                  */
/* ------------------------------------------------------------------ */
class TablaPosiciones {
  constructor(c) { this.c = c; }

  calcular(partidos, ids) {
    const filas = new Map();
    const lista = [];
    for (const id of ids) {
      const equipo = this.c.equipos.find(e => e.id === id);
      if (!equipo) continue;
      const f = { equipo, id, grupo: '', pj: 0, g: 0, e: 0, p: 0, gf: 0, gc: 0, pts: 0, ta: 0, tr: 0 };
      filas.set(id, f);
      lista.push(f);
    }
    const { puntosVictoria: pv, puntosEmpate: pe } = this.c.config;
    for (const p of partidos) {
      const l = filas.get(p.local), v = filas.get(p.visitante);
      if (!P.jugado(p) || !l || !v) continue;
      l.pj++; v.pj++;
      l.gf += p.gl; l.gc += p.gv; v.gf += p.gv; v.gc += p.gl;
      l.ta += p.al; l.tr += p.rl; v.ta += p.av; v.tr += p.rv;
      if (p.gl > p.gv) { l.g++; l.pts += pv; v.p++; }
      else if (p.gl < p.gv) { v.g++; v.pts += pv; l.p++; }
      else { l.e++; v.e++; l.pts += pe; v.pts += pe; }
    }
    return this.ordenar(lista, partidos, true);
  }

  ordenar(lista, partidos, permitirDirecto) {
    lista.sort((a, b) => b.pts - a.pts);
    const resultado = [];
    for (const bloque of this.bloques(lista, f => [f.pts])) {
      resultado.push(...this.desempatar(bloque, 0, partidos, permitirDirecto));
    }
    return resultado;
  }

  // separa en grupos consecutivos con la misma clave
  bloques(lista, clave) {
    const out = [];
    for (const f of lista) {
      const ultimo = out[out.length - 1];
      if (ultimo && comparar(clave(ultimo[0]), clave(f)) === 0) ultimo.push(f);
      else out.push([f]);
    }
    return out;
  }

  desempatar(empatados, indice, partidos, permitirDirecto) {
    if (empatados.length <= 1) return empatados;
    const criterios = this.c.config.criterios;
    if (indice >= criterios.length) {
      return empatados.sort((a, b) => this.sorteo(b) - this.sorteo(a));
    }
    const criterio = criterios[indice];
    if (criterio === 'ENFRENTAMIENTO_DIRECTO' && !permitirDirecto) {
      return this.desempatar(empatados, indice + 1, partidos, permitirDirecto);
    }
    const claves = new Map();
    if (criterio === 'ENFRENTAMIENTO_DIRECTO') {
      const directo = this.minitabla(empatados, partidos);
      for (const f of empatados) claves.set(f.id, directo.get(f.id));
    } else {
      for (const f of empatados) claves.set(f.id, [this.valor(f, criterio)]);
    }
    empatados.sort((a, b) => comparar(claves.get(b.id), claves.get(a.id)));
    const resultado = [];
    for (const sub of this.bloques(empatados, f => claves.get(f.id))) {
      resultado.push(...this.desempatar(sub, indice + 1, partidos, permitirDirecto));
    }
    return resultado;
  }

  valor(f, criterio) {
    switch (criterio) {
      case 'DIFERENCIA_GOLES': return f.gf - f.gc;
      case 'GOLES_A_FAVOR': return f.gf;
      case 'PARTIDOS_GANADOS': return f.g;
      case 'FAIR_PLAY': return -(f.ta + 3 * f.tr);
      case 'SORTEO': return this.sorteo(f);
      default: return 0;
    }
  }

  // puntos, diferencia y goles solo en los partidos entre los empatados
  minitabla(empatados, partidos) {
    const { puntosVictoria: pv, puntosEmpate: pe } = this.c.config;
    const m = new Map(empatados.map(f => [f.id, [0, 0, 0]]));
    for (const p of partidos) {
      if (!P.jugado(p) || !m.has(p.local) || !m.has(p.visitante)) continue;
      const l = m.get(p.local), v = m.get(p.visitante);
      l[1] += p.gl - p.gv; l[2] += p.gl; v[1] += p.gv - p.gl; v[2] += p.gv;
      if (p.gl > p.gv) l[0] += pv; else if (p.gv > p.gl) v[0] += pv; else { l[0] += pe; v[0] += pe; }
    }
    return m;
  }

  // numero fijo por equipo: el sorteo no cambia cada vez que se refresca la tabla
  sorteo(f) { return Math.floor(azar(this.c.semilla * 31 + f.id * 7919)() * 1000000); }
}

function comparar(a, b) {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i] - b[i];
  return 0;
}

/* ------------------------------------------------------------------ */
/*  Formatos: deciden QUE fases tiene el torneo                        */
/* ------------------------------------------------------------------ */
const Formatos = {
  LIGA: {
    validar: () => null,
    primera: (c, m) => m.faseLiga(c.config.idaYVuelta ? 'Liga (ida y vuelta)' : 'Liga (una vuelta)',
      m.sembrar(m.activos()), c.config.idaYVuelta),
    siguiente: () => null
  },
  ELIMINACION_DIRECTA: {
    validar: () => null,
    primera: (c, m) => m.faseEliminatoria('Eliminación directa', m.sembrar(m.activos()),
      c.config.eliminatoriaIdaYVuelta, c.config.finalUnica, false, 0),
    siguiente: () => null
  },
  GRUPOS_Y_ELIMINATORIA: {
    cantidadGrupos: (n, t) => Math.max(1, Math.ceil(n / t)),
    validar(c) {
      const n = c.equipos.filter(e => !e.retirado).length, cfg = c.config;
      if (cfg.tamanoGrupo < 2) return 'Cada grupo debe tener al menos 2 equipos.';
      const g = this.cantidadGrupos(n, cfg.tamanoGrupo), minimo = Math.floor(n / g);
      if (minimo < 2) return `Con ${n} equipos no alcanza para grupos de ${cfg.tamanoGrupo}.`;
      if (cfg.clasificadosPorGrupo < 1 || cfg.clasificadosPorGrupo >= minimo)
        return `Los clasificados por grupo deben ser entre 1 y ${minimo - 1} (hay grupos de ${minimo} equipos).`;
      if (g * cfg.clasificadosPorGrupo < 2) return 'Deben clasificar al menos 2 equipos a la fase final.';
      return null;
    },
    primera(c, m) {
      const g = this.cantidadGrupos(m.activos().length, c.config.tamanoGrupo);
      return m.faseGrupos('Fase de grupos', m.repartirPorBombos(m.sembrar(m.activos()), g), c.config.idaYVuelta);
    },
    siguiente(c, m, indice) {
      if (indice !== 0) return null;
      const fase = c.fases[0], k = c.config.clasificadosPorGrupo;
      const tablas = fase.grupos.map((_, g) => m.tablaDeGrupo(0, g));
      const grupoDe = new Map();
      tablas.forEach(t => t.forEach(f => grupoDe.set(f.id, f.grupo)));
      const nivel = pos => m.tabla.ordenar(tablas.filter(t => pos < t.length).map(t => t[pos]),
        m.partidosDeFase(0), false).map(f => f.id);
      const sembrados = [];
      for (let pos = 0; pos < k; pos++) sembrados.push(...nivel(pos));
      const llave = Fixture.potenciaDeDos(sembrados.length);
      if (sembrados.length < llave) {
        // completar con los mejores de la siguiente posicion ("mejores terceros")
        for (const id of nivel(k)) if (sembrados.length < llave) sembrados.push(id);
      }
      const ko = m.faseEliminatoria('Fase final', sembrados, c.config.eliminatoriaIdaYVuelta, c.config.finalUnica, false, 0);
      m.evitarCrucesMismoGrupo(ko, grupoDe);
      return ko;
    }
  },
  TORNEO_CORTO: {
    cuadrangulares: c => c.config.tipoPlayoff === 'CUADRANGULARES',
    validar(c) {
      const n = c.equipos.filter(e => !e.retirado).length, q = c.config.clasificadosPlayoff;
      if (q < 2 || q > n) return `Los clasificados a la liguilla deben ser entre 2 y ${n}.`;
      if (this.cuadrangulares(c) && q < 4) return 'Los cuadrangulares necesitan al menos 4 clasificados.';
      return null;
    },
    primera: (c, m) => m.faseLiga('Fase regular', m.sembrar(m.activos()), c.config.idaYVuelta),
    siguiente(c, m, indice) {
      const cfg = c.config;
      if (indice === 0) {
        const clasificados = m.tablaDeFase(0).map(f => f.id).slice(0, cfg.clasificadosPlayoff);
        if (this.cuadrangulares(c)) {
          return m.faseGrupos('Cuadrangulares', m.repartirEnSerpentina(clasificados, 2), cfg.eliminatoriaIdaYVuelta);
        }
        return m.faseEliminatoria('Liguilla', clasificados, cfg.eliminatoriaIdaYVuelta, cfg.finalUnica, cfg.ventajaDeportiva, 0);
      }
      if (indice === 1 && this.cuadrangulares(c)) {
        const finalistas = [m.tablaDeGrupo(1, 0)[0].id, m.tablaDeGrupo(1, 1)[0].id];
        return m.faseEliminatoria('Final', finalistas, cfg.eliminatoriaIdaYVuelta, cfg.finalUnica, false, 0);
      }
      return null;
    }
  },
  SUIZO: {
    validar(c) {
      const n = c.equipos.filter(e => !e.retirado).length, cfg = c.config;
      const d = cfg.directosSuizo, r = cfg.repechajeSuizo;
      if (n < 3) return 'El sistema suizo necesita al menos 3 equipos.';
      if (cfg.rondasSuizo < 1) return 'Debe haber al menos 1 ronda.';
      if (d < 0 || r < 0 || r % 2 !== 0) return 'Los equipos del repechaje deben ser un número par.';
      if (d + r > n) return `Directos + repechaje (${d + r}) supera los ${n} equipos.`;
      if (d + r / 2 < 2) return 'A la fase final deben llegar al menos 2 equipos.';
      return null;
    },
    primera: (c, m) => m.faseSuiza('Fase de liga (suizo)', m.sembrar(m.activos()), c.config.rondasSuizo),
    siguiente(c, m, indice) {
      const cfg = c.config, d = cfg.directosSuizo, r = cfg.repechajeSuizo;
      const tabla = m.tablaDeFase(0).map(f => f.id);
      if (indice === 0 && r > 0) {
        return m.faseEliminatoria('Repechaje', tabla.slice(d, d + r), cfg.eliminatoriaIdaYVuelta, false, false, 1);
      }
      if (indice === 0 || (indice === 1 && r > 0)) {
        const sembrados = tabla.slice(0, d);
        if (r > 0) {
          const ganadores = m.ganadoresDeRonda(c.fases[1], 1, 1).sort((a, b) => tabla.indexOf(a) - tabla.indexOf(b));
          sembrados.push(...ganadores);
        }
        return m.faseEliminatoria('Fase final', sembrados, cfg.eliminatoriaIdaYVuelta, cfg.finalUnica, false, 0);
      }
      return null;
    }
  },
  DOBLE_ELIMINACION: {
    validar: c => (c.equipos.filter(e => !e.retirado).length < 3 ? 'La doble eliminación necesita al menos 3 equipos.' : null),
    primera: (c, m) => m.faseDoble('Doble eliminación', m.sembrar(m.activos()), c.config.granFinalConReinicio),
    siguiente: () => null
  }
};

/* ------------------------------------------------------------------ */
/*  Motor: sabe COMO se juega cada tipo de fase                        */
/* ------------------------------------------------------------------ */
class Motor {
  constructor(c) {
    this.c = c;
    this.tabla = new TablaPosiciones(c);
    this.formato = Formatos[c.formato];
  }

  // ---------- utilidades ----------
  equipo(id) { return this.c.equipos.find(e => e.id === id); }
  nombre(id) { return id === LIBRE ? '(libre)' : (this.equipo(id) || { nombre: '?' }).nombre; }
  activos() { return this.c.equipos.filter(e => !e.retirado); }
  partidosDeFase(i) { return this.c.partidos.filter(p => p.fase === i); }
  iniciado() { return this.c.fases.length > 0; }
  terminado() { return this.c.campeon !== -1; }
  ultimaRonda(i) { return this.partidosDeFase(i).reduce((m, p) => Math.max(m, p.ronda), 0); }
  contarPendientes(i) { return this.partidosDeFase(i).filter(p => !P.jugado(p)).length; }

  agregarEquipo(nombre) {
    nombre = nombre.trim();
    if (!nombre || this.c.equipos.some(e => e.nombre.toLowerCase() === nombre.toLowerCase())) return null;
    const e = { id: this.c.sigEquipo++, nombre, estadio: '', retirado: false, dt: null, jugadores: [] };
    this.c.equipos.push(e);
    return e;
  }

  // id unico de jugador (no cambia aunque se edite el dorsal; lo usan las tarjetas)
  nuevoIdJugador() {
    let max = 0;
    for (const e of this.c.equipos) for (const j of e.jugadores) max = Math.max(max, j.id || 0);
    this.c.sigJugador = Math.max(this.c.sigJugador || 1, max + 1);
    return this.c.sigJugador++;
  }

  // ---------- tarjetas y suspensiones ----------
  // Una roja con N fechas deja al jugador fuera de los siguientes N partidos de su
  // equipo (orden del fixture). Una fecha se cumple cuando ese partido ya se jugo.
  partidosSiguientes(desde, idEquipo) {
    return this.c.partidos.filter(p => p.id > desde.id && P.involucra(p, idEquipo)).sort((a, b) => a.id - b.id);
  }

  fechasPendientes(partidoRoja, roja) {
    const cumplidas = this.partidosSiguientes(partidoRoja, roja.equipo).slice(0, roja.fechas).filter(P.jugado).length;
    return roja.fechas - cumplidas;
  }

  listarTarjetas() {
    const lista = [];
    for (const p of this.c.partidos) {
      if (!P.jugado(p)) continue;
      for (const t of P.tarjetas(p)) lista.push({ partido: p, tarjeta: t, pendientes: t.tipo === 'ROJA' ? this.fechasPendientes(p, t) : 0 });
    }
    return lista;
  }

  suspendidosEn(objetivo) {
    const out = [];
    for (const p of this.c.partidos) {
      if (!P.jugado(p) || p.id >= objetivo.id) continue;
      for (const t of P.tarjetas(p)) {
        if (t.tipo !== 'ROJA' || !P.involucra(objetivo, t.equipo)) continue;
        if (this.partidosSiguientes(p, t.equipo).slice(0, t.fechas).some(x => x.id === objetivo.id)) out.push(t);
      }
    }
    return out;
  }

  fechasPendientesDe(idJugador) {
    return this.listarTarjetas().filter(r => r.tarjeta.jugador === idJugador).reduce((s, r) => s + r.pendientes, 0);
  }

  contarTarjetas(idJugador, tipo) {
    return this.listarTarjetas().filter(r => r.tarjeta.jugador === idJugador && r.tarjeta.tipo === tipo).length;
  }

  // ---------- iniciar y avanzar ----------
  iniciar() {
    if (this.iniciado()) throw new Error('El campeonato ya fue iniciado.');
    if (this.activos().length < 2) throw new Error('Debe registrar al menos 2 equipos.');
    const error = this.formato.validar(this.c);
    if (error) throw new Error(error);
    const primera = this.formato.primera(this.c, this);
    this.agregarFase(primera);
    return `Campeonato iniciado. Se generó: ${primera.nombre}.`;
  }

  avanzar() {
    if (!this.iniciado()) return this.iniciar();
    if (this.terminado()) throw new Error('El campeonato ya terminó. Campeón: ' + this.nombre(this.c.campeon));
    const i = this.c.fases.length - 1, fase = this.c.fases[i];
    const pendientes = this.contarPendientes(i);
    if (pendientes > 0) {
      throw new Error(`Faltan ${pendientes} partido(s) por jugar en la fase actual. Los aplazados o suspendidos deben reprogramarse y jugarse (o darse por W.O.).`);
    }
    const error = this.validarRondaDefinida(fase, i);
    if (error) throw new Error(error);
    if (this.generarSiguienteRonda(fase, i)) return `Se generó la siguiente ronda de ${fase.nombre}.`;
    const siguiente = this.formato.siguiente(this.c, this, i);
    if (!siguiente) {
      const g = this.ganadorDeFase(i);
      if (g === -1) throw new Error('No se pudo determinar el campeón.');
      this.c.campeon = g;
      return '¡El campeonato terminó! Campeón: ' + this.nombre(g);
    }
    this.agregarFase(siguiente);
    return `Comienza la fase: ${siguiente.nombre}.`;
  }

  describirEstado() {
    if (!this.iniciado()) return 'Sin iniciar: registre los equipos y pulse «Iniciar campeonato».';
    if (this.terminado()) return 'Terminado. Campeón: ' + this.nombre(this.c.campeon);
    const i = this.c.fases.length - 1, fase = this.c.fases[i], pend = this.contarPendientes(i);
    let texto = 'Fase actual: ' + fase.nombre;
    if (fase.tipo !== 'LIGA' && fase.tipo !== 'GRUPOS') texto += ' · ronda ' + this.ultimaRonda(i);
    return texto + (pend > 0 ? ` · ${pend} partido(s) pendiente(s)` : ' · completa, pulse «Avanzar»');
  }

  agregarFase(fase) {
    this.c.paso++;
    fase.pasoInicio = this.c.paso;
    this.c.fases.push(fase);
    const i = this.c.fases.length - 1;
    switch (fase.tipo) {
      case 'LIGA': this.generarTodosContraTodos(i, fase.participantes, '', fase.idaYVuelta); break;
      case 'GRUPOS': fase.grupos.forEach((g, k) => this.generarTodosContraTodos(i, g, letraGrupo(k), fase.idaYVuelta)); break;
      case 'SUIZO': this.generarRondaSuiza(fase, i, 1); break;
      case 'ELIMINATORIA': this.generarRondaEliminatoria(fase, i, 1); break;
      case 'DOBLE_ELIMINACION': this.generarRondaDoble(fase, i, 1); break;
    }
  }

  generarSiguienteRonda(fase, i) {
    const r = this.ultimaRonda(i);
    switch (fase.tipo) {
      case 'SUIZO':
        if (r < fase.rondasSuizo) { this.c.paso++; this.generarRondaSuiza(fase, i, r + 1); return true; }
        return false;
      case 'ELIMINATORIA':
        if (fase.maxRondas > 0 && r >= fase.maxRondas) return false;
        if (this.ganadoresDeRonda(fase, i, r).length < 2) return false;
        this.c.paso++; this.generarRondaEliminatoria(fase, i, r + 1); return true;
      case 'DOBLE_ELIMINACION':
        if (this.campeonDoble(fase, i) !== -1) return false;
        this.c.paso++; this.generarRondaDoble(fase, i, r + 1); return true;
      default: return false;
    }
  }

  validarRondaDefinida(fase, i) {
    const r = this.ultimaRonda(i);
    for (const p of this.partidosDeFase(i)) {
      if (p.ronda !== r || !p.eliminatorio) continue;
      const sinGanador = fase.tipo === 'ELIMINATORIA'
        ? this.ganadorSerie(fase, i, p.serie, this.esFinal(fase, i, r)) === -1
        : P.ganador(p) === -1;
      if (sinGanador) {
        return `La llave ${this.nombre(p.local)} vs ${this.nombre(p.visitante)} terminó empatada. Edite el partido y registre la definición por penales.`;
      }
    }
    return null;
  }

  nuevoPartido(fase, local, visitante, ronda, nombreRonda) {
    const eq = this.equipo(local);
    const p = {
      id: this.c.sigPartido++, paso: this.c.paso, fase, grupo: '', ronda, nombreRonda,
      serie: '', pierna: 0, llave: '', eliminatorio: false, local, visitante,
      estado: 'PENDIENTE', gl: 0, gv: 0, pl: SIN_PENALES, pv: SIN_PENALES, al: 0, rl: 0, av: 0, rv: 0, tarjetas: [],
      fecha: 'Por definir', hora: '', sede: eq && eq.estadio ? eq.estadio : '', obs: ''
    };
    this.c.partidos.push(p);
    return p;
  }

  // ---------- creacion de fases ----------
  // al azar o por orden de registro (el primero registrado queda como cabeza de serie)
  sembrar(equipos) {
    const lista = equipos.slice();
    if (this.c.config.sorteoAleatorio) barajar(lista, azar(this.c.semilla));
    return lista.map(e => e.id);
  }

  faseBase(nombre, tipo, participantes) {
    return { nombre, tipo, pasoInicio: 0, idaYVuelta: false, finalUnica: true, ventaja: false, maxRondas: 0,
      rondasSuizo: 0, conReinicio: false, participantes: participantes.slice(), grupos: [], cuadro: [] };
  }

  faseLiga(nombre, ids, idaYVuelta) {
    return Object.assign(this.faseBase(nombre, 'LIGA', ids), { idaYVuelta });
  }

  faseGrupos(nombre, grupos, idaYVuelta) {
    return Object.assign(this.faseBase(nombre, 'GRUPOS', grupos.flat()), { grupos, idaYVuelta });
  }

  // sorteo por bombos: cada grupo recibe un equipo de cada bombo
  repartirPorBombos(sembrados, cantidad) {
    const grupos = Array.from({ length: cantidad }, () => []);
    const rnd = azar(this.c.semilla + 17);
    for (let inicio = 0; inicio < sembrados.length; inicio += cantidad) {
      const bombo = sembrados.slice(inicio, inicio + cantidad);
      if (this.c.config.sorteoAleatorio) barajar(bombo, rnd);
      bombo.forEach((id, k) => grupos[k].push(id));
    }
    return grupos;
  }

  // 8 equipos en 2 grupos: A = 1,4,5,8  B = 2,3,6,7
  repartirEnSerpentina(sembrados, cantidad) {
    const grupos = Array.from({ length: cantidad }, () => []);
    sembrados.forEach((id, i) => {
      const vuelta = Math.floor(i / cantidad), pos = i % cantidad;
      grupos[vuelta % 2 === 0 ? pos : cantidad - 1 - pos].push(id);
    });
    return grupos;
  }

  faseEliminatoria(nombre, sembrados, idaYVuelta, finalUnica, ventaja, maxRondas) {
    return Object.assign(this.faseBase(nombre, 'ELIMINATORIA', sembrados),
      { cuadro: Fixture.cuadroDesdeSiembra(sembrados), idaYVuelta, finalUnica, ventaja, maxRondas });
  }

  evitarCrucesMismoGrupo(fase, grupoDe) {
    const c = fase.cuadro;
    for (let i = 0; i + 1 < c.length; i += 2) {
      const a = c[i], b = c[i + 1];
      if (a === LIBRE || b === LIBRE || grupoDe.get(a) !== grupoDe.get(b)) continue;
      for (let j = 0; j + 1 < c.length; j += 2) {
        const x = c[j], y = c[j + 1];
        if (j === i || x === LIBRE || y === LIBRE) continue;
        if (grupoDe.get(a) !== grupoDe.get(y) && grupoDe.get(x) !== grupoDe.get(b)) {
          c[i + 1] = y; c[j + 1] = b; break;
        }
      }
    }
  }

  faseSuiza(nombre, sembrados, rondas) {
    return Object.assign(this.faseBase(nombre, 'SUIZO', sembrados),
      { rondasSuizo: Math.max(1, Math.min(rondas, sembrados.length - 1)) });
  }

  faseDoble(nombre, sembrados, conReinicio) {
    return Object.assign(this.faseBase(nombre, 'DOBLE_ELIMINACION', sembrados),
      { cuadro: Fixture.cuadroDesdeSiembra(sembrados), conReinicio });
  }

  // ---------- tablas ----------
  tablaDeFase(i) { return this.tabla.calcular(this.partidosDeFase(i), this.c.fases[i].participantes); }

  tablaDeGrupo(i, g) {
    const letra = letraGrupo(g);
    const filas = this.tabla.calcular(this.partidosDeFase(i).filter(p => p.grupo === letra), this.c.fases[i].grupos[g]);
    filas.forEach(f => { f.grupo = letra; });
    return filas;
  }

  tablaGeneral() { return this.tabla.calcular(this.c.partidos, this.c.equipos.map(e => e.id)); }

  ganadorDeFase(i) {
    const fase = this.c.fases[i];
    switch (fase.tipo) {
      case 'LIGA': case 'SUIZO': return this.tablaDeFase(i)[0].id;
      case 'GRUPOS': return this.tablaDeGrupo(i, 0)[0].id;
      case 'ELIMINATORIA': return this.ganadorEliminatoria(fase, i);
      case 'DOBLE_ELIMINACION': return this.campeonDoble(fase, i);
      default: return -1;
    }
  }

  // ---------- todos contra todos ----------
  generarTodosContraTodos(i, ids, grupo, idaYVuelta) {
    Fixture.todosContraTodos(ids, idaYVuelta).forEach((jornada, j) => {
      for (const [l, v] of jornada) this.nuevoPartido(i, l, v, j + 1, 'Jornada ' + (j + 1)).grupo = grupo;
    });
  }

  // ---------- eliminacion directa ----------
  static claveSerie(i, r, n) { return `F${i}-R${r}-S${n}`; }

  entrantesDeRonda(fase, i, r) { return r <= 1 ? fase.cuadro.slice() : this.ganadoresDeRonda(fase, i, r - 1); }

  ganadoresDeRonda(fase, i, r) {
    const e = this.entrantesDeRonda(fase, i, r), out = [], esFinal = e.length === 2;
    for (let k = 0; k + 1 < e.length; k += 2) {
      const a = e[k], b = e[k + 1];
      if (a === LIBRE) out.push(b);
      else if (b === LIBRE) out.push(a);
      else if (a === -1 || b === -1) out.push(-1);
      else out.push(this.ganadorSerie(fase, i, Motor.claveSerie(i, r, k / 2), esFinal));
    }
    return out;
  }

  esFinal(fase, i, r) { return this.entrantesDeRonda(fase, i, r).length === 2; }

  ganadorEliminatoria(fase, i) {
    let e = fase.cuadro, r = 1;
    while (e.length > 1) {
      e = this.ganadoresDeRonda(fase, i, r);
      r++;
      if (fase.maxRondas > 0 && r > fase.maxRondas) break;
    }
    return e.length === 1 ? e[0] : -1;
  }

  partidosDeSerie(clave) { return this.c.partidos.filter(p => p.serie === clave).sort((a, b) => a.pierna - b.pierna); }

  ganadorSerie(fase, i, clave, esFinal) {
    const serie = this.partidosDeSerie(clave);
    if (!serie.length || serie.some(p => !P.jugado(p))) return -1;
    const a = serie[0].local, b = serie[0].visitante;
    let ga = 0, gb = 0;
    for (const p of serie) { ga += P.golesDe(p, a); gb += P.golesDe(p, b); }
    if (ga !== gb) return ga > gb ? a : b;
    const ultimo = serie[serie.length - 1];
    if (P.tienePenales(ultimo) && ultimo.pl !== ultimo.pv) return ultimo.pl > ultimo.pv ? ultimo.local : ultimo.visitante;
    if (fase.ventaja && !esFinal) return this.mejorSembrado(fase, a, b);
    return -1;
  }

  // partido eliminatorio unico empatado, o vuelta con el global empatado
  requierePenales(p, gl, gv) {
    if (!p.eliminatorio || p.pierna === 1) return false;
    const fase = this.c.fases[p.fase];
    if (p.pierna === 2) {
      const ida = this.partidosDeSerie(p.serie)[0];
      if (!P.jugado(ida)) return false;
      if (gl + P.golesDe(ida, p.local) !== gv + P.golesDe(ida, p.visitante)) return false;
    } else if (gl !== gv) return false;
    const esFinal = fase.tipo === 'ELIMINATORIA' && this.esFinal(fase, p.fase, p.ronda);
    return !(fase.ventaja && !esFinal);
  }

  mejorSembrado(fase, a, b) {
    const pa = fase.participantes.indexOf(a), pb = fase.participantes.indexOf(b);
    if (pa === -1) return b;
    if (pb === -1) return a;
    return pa < pb ? a : b;
  }

  generarRondaEliminatoria(fase, i, r) {
    const e = this.entrantesDeRonda(fase, i, r), n = e.length;
    const nombre = fase.maxRondas === 1 ? fase.nombre : Fixture.nombreRonda(n);
    const idaYVuelta = fase.idaYVuelta && !(n === 2 && fase.finalUnica);
    for (let k = 0; k + 1 < n; k += 2) {
      const a = e[k], b = e[k + 1];
      if (a === LIBRE || b === LIBRE) continue;
      const mejor = this.mejorSembrado(fase, a, b), peor = mejor === a ? b : a;
      const clave = Motor.claveSerie(i, r, k / 2);
      if (idaYVuelta) {
        // el mejor sembrado define de local en la vuelta
        const ida = this.nuevoPartido(i, peor, mejor, r, nombre + ' (ida)');
        const vuelta = this.nuevoPartido(i, mejor, peor, r, nombre + ' (vuelta)');
        Object.assign(ida, { pierna: 1, serie: clave, eliminatorio: true });
        Object.assign(vuelta, { pierna: 2, serie: clave, eliminatorio: true });
      } else {
        Object.assign(this.nuevoPartido(i, mejor, peor, r, nombre), { serie: clave, eliminatorio: true });
      }
    }
  }

  // ---------- doble eliminacion ----------
  derrotas(fase, i) {
    const d = new Map(fase.participantes.map(id => [id, 0]));
    for (const p of this.partidosDeFase(i)) {
      const g = P.ganador(p);
      if (g !== -1) { const per = P.rival(p, g); d.set(per, d.get(per) + 1); }
    }
    return d;
  }

  vivosDoble(fase, i, perdidas) {
    const d = this.derrotas(fase, i);
    return fase.cuadro.filter(id => id !== LIBRE && !this.equipo(id).retirado && d.get(id) === perdidas);
  }

  campeonDoble(fase, i) {
    const g = this.vivosDoble(fase, i, 0), p = this.vivosDoble(fase, i, 1);
    if (g.length + p.length === 1) return g.length ? g[0] : p[0];
    if (!fase.conReinicio) {
      const gf = this.partidosDeFase(i).find(x => x.llave === LLAVE_GRAN_FINAL && P.ganador(x) !== -1);
      if (gf) return P.ganador(gf);
    }
    return -1;
  }

  generarRondaDoble(fase, i, r) {
    const ganadores = this.vivosDoble(fase, i, 0), perdedores = this.vivosDoble(fase, i, 1);
    const crear = (l, v, nombre, llave) => Object.assign(this.nuevoPartido(i, l, v, r, nombre), { llave, eliminatorio: true });
    if (ganadores.length === 1 && perdedores.length === 1) {
      crear(ganadores[0], perdedores[0], 'Gran final', LLAVE_GRAN_FINAL); return;
    }
    if (ganadores.length === 0 && perdedores.length === 2) {
      crear(perdedores[0], perdedores[1], 'Gran final (desempate)', LLAVE_GRAN_FINAL); return;
    }
    if (ganadores.length > 1) {
      const nombre = ganadores.length === 2 ? 'Final de ganadores' : `Ronda ${r} - Ganadores`;
      const orden = r === 1 ? fase.cuadro : ganadores;
      for (let k = 0; k + 1 < orden.length; k += 2) {
        if (orden[k] !== LIBRE && orden[k + 1] !== LIBRE) crear(orden[k], orden[k + 1], nombre, LLAVE_GANADORES);
      }
    }
    if (perdedores.length > 1) {
      // los que cayeron antes contra los que acaban de caer
      const caida = new Map();
      for (const p of this.partidosDeFase(i)) {
        const g = P.ganador(p);
        if (g !== -1) { const per = P.rival(p, g); if (!caida.has(per) || caida.get(per) > p.ronda) caida.set(per, p.ronda); }
      }
      perdedores.sort((x, y) => (caida.get(x) - caida.get(y)) || (fase.cuadro.indexOf(x) - fase.cuadro.indexOf(y)));
      const nombre = perdedores.length === 2 && ganadores.length <= 1 ? 'Final de perdedores' : `Ronda ${r} - Perdedores`;
      const n = perdedores.length;
      for (let k = 0; k < Math.floor(n / 2); k++) crear(perdedores[k], perdedores[n - 1 - k], nombre, LLAVE_PERDEDORES);
    }
  }

  // ---------- sistema suizo ----------
  generarRondaSuiza(fase, i, r) {
    const activos = fase.participantes.filter(id => !this.equipo(id).retirado);
    const partidos = this.partidosDeFase(i);
    let orden = r === 1 ? activos.slice() : this.tabla.calcular(partidos, activos).map(f => f.id);
    const jugados = new Map(orden.map(id => [id, 0])), localias = new Map(orden.map(id => [id, 0]));
    const enfrentados = new Set();
    for (const p of partidos) {
      jugados.set(p.local, (jugados.get(p.local) || 0) + 1);
      jugados.set(p.visitante, (jugados.get(p.visitante) || 0) + 1);
      localias.set(p.local, (localias.get(p.local) || 0) + 1);
      enfrentados.add(claveCruce(p.local, p.visitante));
    }
    if (orden.length % 2 === 1) {
      // descansa el peor ubicado que todavia no haya descansado
      let descansa = orden[orden.length - 1];
      for (let k = orden.length - 1; k >= 0; k--) if (jugados.get(orden[k]) === r - 1) { descansa = orden[k]; break; }
      orden = orden.filter(id => id !== descansa);
    }
    let cruces;
    if (r === 1) {
      const mitad = orden.length / 2;
      cruces = orden.slice(0, mitad).map((id, k) => [id, orden[k + mitad]]);
    } else {
      this.contador = 0;
      cruces = this.emparejar(orden, enfrentados);
      if (!cruces) {
        cruces = [];
        for (let k = 0; k + 1 < orden.length; k += 2) cruces.push([orden[k], orden[k + 1]]);
      }
    }
    for (let [l, v] of cruces) {
      if (localias.get(v) < localias.get(l)) [l, v] = [v, l];
      this.nuevoPartido(i, l, v, r, 'Ronda ' + r);
    }
  }

  // empareja con el mejor ubicado posible sin repetir rival (backtracking)
  emparejar(pendientes, enfrentados) {
    if (!pendientes.length) return [];
    if (++this.contador > 200000) return null;
    const a = pendientes[0];
    for (let j = 1; j < pendientes.length; j++) {
      const b = pendientes[j];
      if (enfrentados.has(claveCruce(a, b))) continue;
      const sol = this.emparejar(pendientes.filter(x => x !== a && x !== b), enfrentados);
      if (sol) return [[a, b], ...sol];
    }
    return null;
  }

  // ---------- edicion de partidos y equipos ----------
  huella(p) {
    const fase = this.c.fases[p.fase];
    switch (fase.tipo) {
      case 'LIGA': case 'SUIZO': return this.tablaDeFase(p.fase).map(f => f.id).join(',');
      case 'GRUPOS': return fase.grupos.map((_, g) => this.tablaDeGrupo(p.fase, g).map(f => f.id).join(',')).join('|');
      case 'ELIMINATORIA': return String(this.ganadorSerie(fase, p.fase, p.serie, this.esFinal(fase, p.fase, p.ronda)));
      default: return String(P.ganador(p));
    }
  }

  // true si el cambio altera quien avanza y ya hay rondas generadas despues
  afectaRondasPosteriores(p, datos) {
    if (p.paso >= this.c.paso) return false;
    const respaldo = P.datos(p);
    const antes = this.huella(p);
    Object.assign(p, datos);
    const despues = this.huella(p);
    Object.assign(p, respaldo);
    return antes !== despues;
  }

  aplicarEdicion(p, datos, deshacer) {
    if (deshacer) this.deshacerDesdePaso(p.paso);
    Object.assign(p, datos);
    if (this.terminado() && p.fase === this.c.fases.length - 1) this.c.campeon = -1;
  }

  deshacerDesdePaso(paso) {
    this.c.partidos = this.c.partidos.filter(p => p.paso <= paso);
    this.c.fases = this.c.fases.filter(f => f.pasoInicio <= paso);
    this.c.paso = paso;
    this.c.campeon = -1;
  }

  static walkover(datos, partido, idGanador, motivo) {
    P.reiniciar(datos);
    datos.estado = 'JUGADO';
    if (partido.local === idGanador) datos.gl = 3; else datos.gv = 3;
    datos.obs = motivo;
  }

  retirarEquipo(e) {
    if (!this.iniciado()) {
      this.c.equipos = this.c.equipos.filter(x => x !== e);
      return `Equipo «${e.nombre}» eliminado.`;
    }
    e.retirado = true;
    const actual = this.c.fases.length - 1;
    let n = 0;
    for (const p of this.c.partidos) {
      if (!P.jugado(p) && P.involucra(p, e.id) && p.fase === actual) {
        Motor.walkover(p, p, P.rival(p, e.id), 'W.O. por retiro de ' + e.nombre);
        n++;
      }
    }
    return `Equipo «${e.nombre}» retirado. ${n} partido(s) pendiente(s) se dieron por W.O. 3-0 a favor del rival.`;
  }
}

function letraGrupo(i) { return String.fromCharCode(65 + i); }
function claveCruce(a, b) { return Math.min(a, b) + '-' + Math.max(a, b); }

function resumenReglas(c) {
  const cfg = c.config, f = c.formato, l = [];
  l.push(`Puntos: victoria ${cfg.puntosVictoria}, empate ${cfg.puntosEmpate}`);
  const elim = () => {
    let t = cfg.eliminatoriaIdaYVuelta ? 'Eliminatorias a ida y vuelta' : 'Eliminatorias a partido único';
    if (cfg.eliminatoriaIdaYVuelta) t += cfg.finalUnica ? ' (final a partido único)' : ' (final también a ida y vuelta)';
    l.push(t);
    l.push(cfg.sorteoAleatorio ? 'Cruces por sorteo' : 'Cruces según orden de registro');
  };
  if (f === 'LIGA') l.push(cfg.idaYVuelta ? 'A ida y vuelta (doble round-robin)' : 'A una sola vuelta');
  if (f === 'ELIMINACION_DIRECTA') elim();
  if (f === 'GRUPOS_Y_ELIMINATORIA') {
    l.push(`Grupos de ${cfg.tamanoGrupo} equipos, clasifican ${cfg.clasificadosPorGrupo} por grupo`);
    l.push(cfg.idaYVuelta ? 'Grupos a ida y vuelta' : 'Grupos a una vuelta');
    elim();
  }
  if (f === 'TORNEO_CORTO') {
    l.push(cfg.idaYVuelta ? 'Fase regular a ida y vuelta' : 'Fase regular a una vuelta');
    l.push(`Clasifican ${cfg.clasificadosPlayoff} a ${cfg.tipoPlayoff === 'CUADRANGULARES' ? 'cuadrangulares' : 'liguilla (llave)'}`);
    elim();
    if (cfg.ventajaDeportiva) l.push('Empate en el global: avanza el mejor ubicado en la tabla');
  }
  if (f === 'SUIZO') {
    l.push(`${cfg.rondasSuizo} rondas. Pasan directo los ${cfg.directosSuizo} primeros` +
      (cfg.repechajeSuizo > 0 ? `; del ${cfg.directosSuizo + 1} al ${cfg.directosSuizo + cfg.repechajeSuizo} juegan repechaje` : ''));
    elim();
  }
  if (f === 'DOBLE_ELIMINACION') {
    l.push(cfg.granFinalConReinicio ? 'Gran final con reinicio (si gana el de perdedores se juega otro partido)' : 'Gran final a un solo partido');
  }
  return l;
}

// Para poder probar el motor con Node.js (node pruebas.js) sin navegador
/* ------------------------------------------------------------------ */
/*  Historial: resumen, premios y palmares (igual que ResumenCampeonato */
/*  y Palmares en Java)                                                 */
/* ------------------------------------------------------------------ */
function resumenCampeonato(c) {
  const m = new Motor(c);
  const general = m.tablaGeneral();
  const posGeneral = new Map(general.map((f, i) => [f.id, i]));

  // {ultima fase en la que participo, que tan lejos llego en ella}
  const clave = id => {
    let ultima = -1;
    c.fases.forEach((f, i) => { if (f.participantes.includes(id)) ultima = i; });
    if (ultima === -1) return [-1, 0];
    const fase = c.fases[ultima];
    let avance;
    if (fase.tipo === 'ELIMINATORIA' || fase.tipo === 'DOBLE_ELIMINACION') {
      avance = m.partidosDeFase(ultima).filter(p => P.involucra(p, id)).reduce((x, p) => Math.max(x, p.ronda), 0);
    } else if (fase.tipo === 'GRUPOS') {
      avance = -Math.min(...fase.grupos.map((_, g) => {
        const pos = m.tablaDeGrupo(ultima, g).map(f => f.id).indexOf(id);
        return pos === -1 ? 99 : pos;
      }));
    } else {
      avance = -m.tablaDeFase(ultima).map(f => f.id).indexOf(id);
    }
    return [ultima, avance];
  };
  const claves = new Map(general.map(f => [f.id, clave(f.id)]));
  const clasificacion = general.slice().sort((a, b) => {
    if (a.id === c.campeon) return -1;
    if (b.id === c.campeon) return 1;
    return comparar(claves.get(b.id), claves.get(a.id)) || posGeneral.get(a.id) - posGeneral.get(b.id);
  });

  const conPartidos = general.filter(f => f.pj > 0);
  const mejor = (lista, mejorQue) => lista.reduce((x, f) => (x === null || mejorQue(f, x) ? f : x), null);
  const jugados = c.partidos.filter(P.jugado);
  const goles = jugados.reduce((s, p) => s + p.gl + p.gv, 0);
  const premios = {
    goleador: mejor(conPartidos, (a, b) => a.gf > b.gf),
    valla: mejor(conPartidos, (a, b) => a.gc * b.pj < b.gc * a.pj),          // menos goles recibidos por partido
    fairPlay: mejor(conPartidos, (a, b) => (a.ta + 3 * a.tr) * b.pj < (b.ta + 3 * b.tr) * a.pj),
    goleada: mejor(jugados, (a, b) => Math.abs(a.gl - a.gv) > Math.abs(b.gl - b.gv))
  };

  const l = [];
  l.push('ALPHASPORT - RESUMEN DEL CAMPEONATO', '', 'CAMPEONATO: ' + c.nombre, 'Formato: ' + FORMATOS[c.formato].nombre);
  if (c.fechaArchivo) l.push('Fecha: ' + c.fechaArchivo);
  l.push('Estado: ' + (m.terminado() ? 'Terminado' : 'Sin terminar'), '', '=========== PODIO ===========');
  if (!m.terminado()) l.push('(el campeonato no terminó: se muestran los primeros de la clasificación)');
  ['Campeón', 'Subcampeón', 'Tercer lugar'].forEach((puesto, i) => {
    const f = clasificacion[i];
    if (f) l.push(`${(puesto + ':').padEnd(14)} ${f.equipo.nombre}  (DT: ${f.equipo.dt ? f.equipo.dt.nombre : 'Sin asignar'})`);
  });
  l.push('', '=========== PREMIOS ===========');
  if (premios.goleador) l.push(`Equipo más goleador:  ${premios.goleador.equipo.nombre} (${premios.goleador.gf} goles)`);
  if (premios.valla) l.push(`Valla menos vencida:  ${premios.valla.equipo.nombre} (${premios.valla.gc} goles recibidos en ${premios.valla.pj} partidos)`);
  if (premios.fairPlay) l.push(`Premio Fair Play:     ${premios.fairPlay.equipo.nombre} (${premios.fairPlay.ta} amarillas, ${premios.fairPlay.tr} rojas)`);
  const g = premios.goleada;
  if (g) l.push(`Mayor goleada:        ${m.nombre(g.local)} ${g.gl} - ${g.gv} ${m.nombre(g.visitante)} (${g.nombreRonda})`);
  l.push('', '=========== CIFRAS ===========',
    `Equipos: ${c.equipos.length}   Partidos jugados: ${jugados.length}   Goles: ${goles}` +
    (jugados.length ? `   Promedio: ${(goles / jugados.length).toFixed(2)} goles por partido` : ''),
    '', '=========== CLASIFICACIÓN FINAL ===========');
  clasificacion.forEach((f, i) => l.push(`${String(i + 1).padStart(2)}. ${f.equipo.nombre.padEnd(22)} PJ ${f.pj}  G ${f.g}  E ${f.e}  P ${f.p}  GF ${f.gf}  GC ${f.gc}  Pts ${f.pts}`));

  return { clasificacion, premios, jugados: jugados.length, goles, terminado: m.terminado(), texto: l.join('\n') };
}

// suma de todos los campeonatos del historial; los equipos se reconocen por su nombre
function palmares(campeonatos) {
  const filas = new Map();
  for (const c of campeonatos) {
    const r = resumenCampeonato(c);
    r.clasificacion.forEach((f, i) => {
      const k = f.equipo.nombre.trim().toLowerCase();
      if (!filas.has(k)) filas.set(k, { equipo: f.equipo.nombre, titulos: 0, sub: 0, podios: 0, participaciones: 0, pj: 0, g: 0, e: 0, p: 0, gf: 0, gc: 0, pts: 0 });
      const x = filas.get(k);
      x.participaciones++;
      for (const campo of ['pj', 'g', 'e', 'p', 'gf', 'gc', 'pts']) x[campo] += f[campo];
      if (r.terminado) { // solo los torneos terminados reparten titulos
        if (i === 0) x.titulos++;
        else if (i === 1) x.sub++;
        if (i < 3) x.podios++;
      }
    });
  }
  return [...filas.values()].sort((a, b) => (b.titulos - a.titulos) || (b.sub - a.sub) || (b.podios - a.podios) || (b.pts - a.pts));
}

// Copia los equipos (con DT y plantilla) a un campeonato nuevo
function importarEquipos(destino, origen) {
  const m = new Motor(destino);
  for (const viejo of origen) {
    if (viejo.retirado) continue;
    const e = m.agregarEquipo(viejo.nombre);
    if (!e) continue;
    e.estadio = viejo.estadio || '';
    e.dt = viejo.dt ? Object.assign({}, viejo.dt) : null;
    e.jugadores = viejo.jugadores.map(j => Object.assign({}, j, { id: 0 }));
    for (const j of e.jugadores) j.id = m.nuevoIdJugador();
  }
}

