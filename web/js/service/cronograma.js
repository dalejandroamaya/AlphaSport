'use strict';

/* Cronograma automático y fechas de los partidos, iguales a Cronograma.java: la web sola los usa al guardar. */
const Cronograma = {
  /** Hoy como AAAA-MM-DD (hora local). */
  hoy() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  },
  esFecha: f => /^\d{4}-\d{2}-\d{2}$/.test(f || ''),
  dma: f => f.split('-').reverse().join('/'),
  /** La fecha más temprana permitida: la creación del campeonato (o hoy). */
  minimo: c => (Cronograma.esFecha(c.fechaCreacion) ? c.fechaCreacion : Cronograma.hoy()),

  /** Ni antes de crear el campeonato ni, si se jugó, en el futuro. */
  validarFecha(c, datos, ErrorRegla) {
    if (!Cronograma.esFecha(datos.fecha)) return;
    if (Cronograma.esFecha(c.fechaCreacion) && datos.fecha < c.fechaCreacion) {
      throw new ErrorRegla(`La fecha del partido no puede ser anterior al ${Cronograma.dma(c.fechaCreacion)}, el día en que se creó el campeonato.`);
    }
    if ((datos.estado === 'JUGADO' || datos.estado === 'EN_JUEGO') && datos.fecha > Cronograma.hoy()) {
      throw new ErrorRegla(`Un partido jugado no puede tener una fecha futura (${Cronograma.dma(datos.fecha)}). Corrija la fecha o déjelo pendiente.`);
    }
  },

  /** Reparte los partidos pendientes: una jornada por día de juego, sin repetir equipo en el día. */
  programar(c, { inicio, dias, horas, sedes, todos }, ErrorRegla) {
    const minimo = Cronograma.minimo(c) > Cronograma.hoy() ? Cronograma.minimo(c) : Cronograma.hoy();
    if (!Cronograma.esFecha(inicio)) throw new ErrorRegla('Escoja la fecha de inicio.');
    if (inicio < minimo) throw new ErrorRegla(`La fecha de inicio no puede ser anterior al ${Cronograma.dma(minimo)}.`);
    const diasJuego = new Set((dias || []).map(Number));
    if (!diasJuego.size) throw new ErrorRegla('Marque al menos un día de juego.');
    const listaHoras = [];
    for (const h of horas || []) {
      const x = String(h).trim();
      if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(x)) throw new ErrorRegla(`La hora «${x}» no es válida: escríbala como 15:00.`);
      if (!listaHoras.includes(x)) listaHoras.push(x);
    }
    if (!listaHoras.length) throw new ErrorRegla('Escriba al menos una hora de juego, por ejemplo 15:00.');
    listaHoras.sort();
    const canchas = [...new Set((sedes || []).map(s => String(s).trim()).filter(Boolean))];
    const jornadas = new Map();
    c.partidos.filter(p => p.estado === 'PENDIENTE' && (todos || !Cronograma.esFecha(p.fecha)))
      .sort((a, b) => a.paso - b.paso || a.ronda - b.ronda || a.id - b.id)
      .forEach(p => { const k = p.paso + '-' + p.ronda; if (!jornadas.has(k)) jornadas.set(k, []); jornadas.get(k).push(p); });
    if (!jornadas.size) {
      throw new ErrorRegla(todos ? 'No hay partidos pendientes para programar.'
        : 'Todos los partidos pendientes ya tienen fecha. Marque «Reprogramar también los que ya tienen fecha» para cambiarlas.');
    }
    // Días como fechas locales (sin zona horaria): 1 = lunes … 7 = domingo.
    const aFecha = f => { const [a, m, d] = f.split('-').map(Number); return new Date(a, m - 1, d); };
    const texto = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const diaSemana = d => (d.getDay() === 0 ? 7 : d.getDay());
    const siguiente = d => { const x = new Date(d); while (!diasJuego.has(diaSemana(x))) x.setDate(x.getDate() + 1); return x; };
    const estadio = p => { const e = c.equipos.find(x => x.id === p.local); return e && e.estadio ? e.estadio.trim() : 'Por definir'; };
    const cupo = canchas.length ? listaHoras.length * canchas.length : Infinity;
    let dia = siguiente(aFecha(inicio)), primero = null, ultimo = null, programados = 0;
    for (const jornada of jornadas.values()) {
      let faltan = jornada.slice();
      while (faltan.length) {
        const juegan = new Set(), usoHora = new Map(), estadios = new Map(), quedan = [];
        let enElDia = 0;
        for (const p of faltan) {
          if (enElDia >= cupo || juegan.has(p.local) || juegan.has(p.visitante)) { quedan.push(p); continue; }
          let hora = null, sede = null;
          for (const h of listaHoras) {
            if (!canchas.length) {
              if (!(estadios.get(h) || new Set()).has(estadio(p))) { hora = h; sede = estadio(p); break; }
            } else if ((usoHora.get(h) || 0) < canchas.length) { hora = h; sede = canchas[usoHora.get(h) || 0]; break; }
          }
          if (!hora) { quedan.push(p); continue; }
          usoHora.set(hora, (usoHora.get(hora) || 0) + 1);
          if (!estadios.has(hora)) estadios.set(hora, new Set());
          estadios.get(hora).add(sede);
          Object.assign(p, { fecha: texto(dia), hora, sede });
          juegan.add(p.local); juegan.add(p.visitante);
          enElDia++; programados++;
          primero = primero || texto(dia); ultimo = texto(dia);
        }
        faltan = quedan;
        const otro = new Date(dia); otro.setDate(otro.getDate() + 1); dia = siguiente(otro);
      }
    }
    return `Se programaron ${programados} ${programados === 1 ? 'partido' : 'partidos'} del ${Cronograma.dma(primero)} al ${Cronograma.dma(ultimo)}. Puede ajustar cualquiera en su «Previa».`;
  }
};
