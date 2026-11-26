'use strict';

/*
 * [REQ-07] Sección «Votaciones» del visitante (gemela de VistaVotaciones en el escritorio): los partidos terminados
 * con la votación abierta. En cada uno se elige la nota (1 a 10) de los jugadores que se quiera y se envían todas
 * juntas. Cuando vence el tiempo el partido sale solo de la lista. No muestra notas, para no influir en el voto.
 * Aquí están también el tiempo de votación en el editor del partido (administrador) y la pestaña «Calificaciones»
 * del detalle, que solo muestra la nota final. Usa las ayudas de app.js.
 */

/* ---------------------------------------------------------------- textos del tiempo */

/** «23/11 a las 18:00» */
function horaVotacion(ms) {
  const f = new Date(ms), dos = n => String(n).padStart(2, '0');
  return `${dos(f.getDate())}/${dos(f.getMonth() + 1)} a las ${dos(f.getHours())}:${dos(f.getMinutes())}`;
}

/** «quedan 2 d 3 h», «quedan 23 h 5 min» o «quedan 8 min» */
function restanteVotacion(cierraEn) {
  const minutos = Math.max(0, Math.ceil((cierraEn - Date.now()) / 60000)), horas = Math.floor(minutos / 60), dias = Math.floor(horas / 24);
  if (dias > 1) return `quedan ${dias} d ${horas % 24} h`;
  if (horas > 0) return `quedan ${horas} h ${minutos % 60} min`;
  return minutos === 1 ? 'queda 1 min' : `quedan ${minutos} min`;
}

/* ---------------------------------------------------------------- Votaciones (visitante) */

let votacionesAbiertas = [];
/** Notas elegidas que todavía no se envían: partido → (jugador → nota). Se conservan al redibujar. */
const notasElegidas = new Map();
const votacionesDesplegadas = new Set();
const erroresVotacion = new Map();
let primeraVotacion = true;

/** Otra sesión: sin las notas a medio elegir de la anterior. */
function olvidarVotaciones() {
  notasElegidas.clear();
  votacionesDesplegadas.clear();
  erroresVotacion.clear();
  primeraVotacion = true;
}

/** Pide al servicio las votaciones abiertas y dibuja la sección. */
async function renderVotaciones() {
  const r = await servicio.votaciones();
  if (!r.ok) {
    if (r.codigo === 'LOGIN') volverAlLogin('Su sesión terminó. Inicie sesión de nuevo.');
    else $('listaVotaciones').innerHTML = vacioHtml(esc(r.error));
    return;
  }
  votacionesAbiertas = r.votaciones;
  if (primeraVotacion && votacionesAbiertas.length === 1) votacionesDesplegadas.add(votacionesAbiertas[0].partido.id);
  primeraVotacion = false;
  dibujarVotaciones();
}

function dibujarVotaciones() {
  $('listaVotaciones').innerHTML = votacionesAbiertas.length ? votacionesAbiertas.map(tarjetaVotacionHtml).join('')
    : vacioHtml('No hay votaciones abiertas. Cuando termine un partido aparecerá aquí para que califique a los jugadores.');
}

/** «Votaciones 2» en el menú: cuántos partidos puede votar el visitante ahora. */
function contarVotaciones() {
  const cuantas = E.hay ? E.partidos.filter(p => p.votacion && p.votacion.abierta).length : 0;
  $('cuentaVotaciones').textContent = cuantas;
  $('cuentaVotaciones').hidden = cuantas === 0;
  // Sin votaciones abiertas la sección solo tendría un aviso vacío: se oculta del menú del Aficionado.
  const nav = document.querySelector('.navegacion button[data-vista="votaciones"]');
  if (nav && puedeVer('votaciones')) nav.hidden = cuantas === 0 && vistaActual() !== 'votaciones';
}

function tarjetaVotacionHtml({ partido: p, calificaciones: c }) {
  const todos = c.local.concat(c.visitante), votados = todos.filter(j => j.miVoto > 0).length, completo = votados === todos.length;
  const abierta = votacionesDesplegadas.has(p.id), elegidas = notasElegidas.get(p.id) || new Map(), error = erroresVotacion.get(p.id);
  const columna = (equipo, jugadores) => `<div class="calificacion-columna"><h4>${esc(equipo)}</h4>
      ${jugadores.length ? `<ol class="lista-calificaciones">${jugadores.map(j => filaVotoHtml(p.id, j, elegidas.get(j.id))).join('')}</ol>`
        : '<p class="sutil">No se registró quién jugó por este equipo.</p>'}</div>`;
  return `<article class="tarjeta-votacion vidrio">
      <header><div><span class="sutil pequeno">${esc(contextoPartido(p))}</span>
          <h3>${esc(p.localNombre)} <b>${p.gl} - ${p.gv}</b> ${esc(p.visitanteNombre)}</h3>
          <span class="sutil pequeno">${completo ? 'Ya calificó a todos los jugadores' : `${votados} de ${todos.length} jugadores calificados`}</span></div>
        <span class="chip chip-votacion" data-cierra="${p.votacion.cierraEn}">${restanteVotacion(p.votacion.cierraEn)}</span>
        <button type="button" class="btn mini ${abierta || completo ? 'fantasma' : 'primario'}" data-desplegar="${p.id}" aria-expanded="${abierta}">${abierta ? 'Ocultar' : completo ? 'Ver mis votos' : 'Calificar'}</button></header>
      ${abierta ? `<div class="detalle-columnas">${columna(p.localNombre, c.local)}${columna(p.visitanteNombre, c.visitante)}</div>
        ${completo ? '' : `<footer>${error ? `<p class="error" role="alert">${esc(error)}</p>` : ''}
          <p class="sutil pequeno">Elija la nota de los jugadores que quiera calificar. Cada jugador se califica una sola vez.</p>
          <button type="button" class="btn primario" data-enviar-votos="${p.id}" ${elegidas.size ? '' : 'disabled'}>${textoEnviar(elegidas.size)}</button></footer>`}` : ''}
    </article>`;
}

const textoEnviar = n => (n === 0 ? 'Enviar calificaciones' : n === 1 ? 'Enviar 1 calificación' : `Enviar ${n} calificaciones`);

/** La nota que se elige (1 a 10) o, si este dispositivo ya votó por el jugador, su voto. */
function filaVotoHtml(idPartido, j, elegida) {
  const control = j.miVoto ? `<span class="chip chip-voto">Su voto: ${j.miVoto}</span>`
    : `<select data-nota-partido="${idPartido}" data-nota-jugador="${j.id}" aria-label="Nota del 1 al 10 para ${esc(j.nombre)}"><option value="">–</option>${
      [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(v => `<option ${v === elegida ? 'selected' : ''}>${v}</option>`).join('')}</select>`;
  return `<li class="fila-calificacion ${j.miVoto ? 'fila-votada' : ''}">${fotoCedulaHtml(j.foto, j.nombre, 'fila')}
      <div class="calificacion-textos"><span class="calificacion-nombre">#${j.dorsal} ${esc(j.nombre)}</span>
        <span class="sutil pequeno">${esc(j.posicion || '')}${j.titular ? '' : ' · entró desde el banco'}</span></div>${control}</li>`;
}

async function enviarVotos(idPartido) {
  const votos = [...(notasElegidas.get(idPartido) || new Map())].map(([jugador, valor]) => ({ jugador, valor }));
  const r = await accion('calificacion/votar', { partido: idPartido, dispositivo: Almacen.dispositivo(), votos }, { silencioso: true });
  if (r.codigo === 'LOGIN') return;
  if (r.ok) {
    notasElegidas.delete(idPartido);
    erroresVotacion.delete(idPartido);
    aviso(r.mensaje);
  } else {
    erroresVotacion.set(idPartido, r.error);
    aviso(r.error); // si la votación cerró, el partido ya no está en la lista para mostrarlo
  }
  await refrescarVotaciones();
}

/** Vuelve a pedir el estado y las votaciones (por ejemplo, cuando vence una). */
async function refrescarVotaciones() {
  const estado = await servicio.estado();
  if (estado.ok === false) return;
  E = estado;
  render(); // con la sección abierta también vuelve a pedir las votaciones
}

/** Mientras la sección está en pantalla, el tiempo que queda avanza y los partidos vencidos salen de la lista. */
function avanzarRelojVotaciones() {
  if (!S || $('vista-votaciones').hidden || !votacionesAbiertas.length) return;
  if (votacionesAbiertas.some(v => v.partido.votacion.cierraEn <= Date.now())) {
    refrescarVotaciones();
    return;
  }
  document.querySelectorAll('[data-cierra]').forEach(el => { el.textContent = restanteVotacion(Number(el.dataset.cierra)); });
}

/* ---------------------------------------------------------------- tiempo de votación en el editor (administrador) */

/** Prepara el grupo «Votación del público» del editor con el partido que se abre. */
function abrirEditorVotacion(p) {
  const reglas = catalogo.votacion, horas = p.votacion.horas, otro = !reglas.opciones.includes(horas);
  $('vtTiempo').innerHTML = reglas.opciones.map(h => `<option value="${h}">${h} horas</option>`).join('') + '<option value="otro">Otro tiempo…</option>';
  $('vtTiempo').value = otro ? 'otro' : String(horas);
  $('vtHoras').min = reglas.minimo;
  $('vtHoras').max = reglas.maximo;
  $('vtHoras').value = horas;
  $('vtRango').textContent = `horas (de ${reglas.minimo} a ${reglas.maximo})`;
  actualizarEditorVotacion();
  dibujarEstadoVotacion(p);
}

function actualizarEditorVotacion() {
  $('vtOtro').hidden = $('vtTiempo').value !== 'otro';
}

/** Horas elegidas; null si lo escrito no sirve. */
function horasEditorVotacion() {
  if ($('vtTiempo').value !== 'otro') return Number($('vtTiempo').value);
  const texto = $('vtHoras').value.trim(), horas = Number(texto);
  return texto !== '' && Number.isInteger(horas) && horas >= catalogo.votacion.minimo && horas <= catalogo.votacion.maximo ? horas : null;
}

function dibujarEstadoVotacion(p) {
  const v = p.votacion, votos = plural(v.votos, '1 voto', v.votos + ' votos');
  $('vtEstado').textContent = !p.jugado ? 'Se abre cuando el partido quede «Jugado».'
    : p.walkover ? 'Partido definido por W.O.: no tiene votación.'
    : !v.cierraEn ? 'Partido jugado antes de existir las votaciones: no tiene votación.'
    : v.abierta ? `Abierta: ${restanteVotacion(v.cierraEn)} (cierra el ${horaVotacion(v.cierraEn)}). Lleva ${votos}.`
    : v.cierraEn > Date.now() ? 'No se registró quién jugó: no hay a quién calificar.'
    : `Cerrada el ${horaVotacion(v.cierraEn)} con ${votos}. Para abrirla de nuevo, elija otro tiempo y guarde.`;
  $('vtEstado').classList.toggle('votacion-abierta', v.abierta);
  $('vtCerrar').hidden = !v.abierta;
  // Sin votación posible solo se explica por qué.
  $('vtControles').hidden = p.jugado && (p.walkover || !v.cierraEn);
}

async function cerrarVotacion() {
  const id = partidoEditando && partidoEditando.id;
  if (id == null || !await mensaje('Cerrar la votación',
    'Los visitantes ya no podrán calificar este partido y las notas finales quedarán definitivas. ¿Cerrarla ahora?',
    { confirmar: true, textoSi: 'Cerrar votación' })) return;
  const r = await accion('votacion/cerrar', { partido: id }, { silencioso: true });
  if (!r.ok) errorPartido(r.error);
  else aviso(r.mensaje);
  const p = E.partidos.find(x => x.id === id);
  if (p) dibujarEstadoVotacion(p);
}

/* ---------------------------------------------------------------- pestaña «Calificaciones» del detalle (nota final) */

const notaTexto = v => Number(v || 0).toFixed(1);
/** Verde desde 7.0, rojo por debajo de 5.0 y neutra en medio (igual que en el escritorio). */
const claseNota = v => (v >= 7 ? 'nota-alta' : v < 5 ? 'nota-baja' : 'nota-media');

/** «2 G · 1 A · 1 TA» (goles, asistencias, tarjetas; el portero, valla invicta o goles recibidos). */
function accionesTexto(j) {
  const partes = [];
  if (j.goles) partes.push(j.goles + ' G');
  if (j.asistencias) partes.push(j.asistencias + ' A');
  if (j.amarillas) partes.push(j.amarillas + ' TA');
  if (j.rojas) partes.push(j.rojas + ' TR');
  if (j.portero) partes.push(j.golesRecibidos ? j.golesRecibidos + ' GR' : 'valla invicta');
  return partes.join(' · ');
}

/** Nota final de cada jugador y MVP. Aquí no se vota: «Ir a votar» lleva al visitante a «Votaciones». */
function calificacionesHtml(c, p) {
  if (!c || !c.disponible) return vacioHtml((c && c.motivo) || 'Las calificaciones aparecen cuando el partido termina.');
  const abierta = c.votacion === 'ABIERTA';
  const estadoTexto = abierta ? `Nota final provisional: la votación del público cierra el ${horaVotacion(c.cierraEn)}.`
    : c.votacion === 'CERRADA' ? 'Nota final definitiva: promedio del sistema y los votos del público.'
    : 'Nota final del sistema: este partido se jugó antes de existir las votaciones.';
  const mvp = c.local.concat(c.visitante).find(j => j.id === c.mvp);
  const fila = j => `<li class="fila-calificacion ${j.mvp ? 'fila-mvp' : ''}">${fotoCedulaHtml(j.foto, j.nombre, 'fila')}
      <div class="calificacion-textos">
        <span class="calificacion-nombre">#${j.dorsal} ${esc(j.nombre)}${j.portero ? ' <span class="chip chip-portero">POR</span>' : ''}${j.mvp ? ' <span class="chip chip-mvp">★ MVP</span>' : ''}</span>
        ${accionesTexto(j) ? `<span class="sutil pequeno">${esc(accionesTexto(j))}</span>` : ''}</div>
      <span class="nota ${claseNota(j.notaFinal)}"><span class="solo-lector">Nota final </span>${notaTexto(j.notaFinal)}</span></li>`;
  const columna = (equipo, jugadores) => {
    const titulares = jugadores.filter(j => j.titular), banco = jugadores.filter(j => !j.titular);
    return `<div class="calificacion-columna"><h3 class="ficha-titulo">${esc(equipo)}</h3>
      ${jugadores.length ? `<ol class="lista-calificaciones">${titulares.map(fila).join('')}</ol>
        ${banco.length ? `<p class="sutil pequeno">Ingresaron desde el banco</p><ol class="lista-calificaciones">${banco.map(fila).join('')}</ol>` : ''}`
        : '<p class="sutil">No se registró quién jugó por este equipo.</p>'}</div>`;
  };
  return `<div class="calificacion-cabecera"><p class="calificacion-estado ${abierta ? 'calificacion-abierta' : 'calificacion-cerrada'}">${esc(estadoTexto)}</p>
      ${abierta && !puedeEditar() ? '<button type="button" class="btn primario" data-ir-a-votar>Ir a votar</button>' : ''}</div>
    ${mvp ? `<div class="tarjeta-mvp vidrio">${fotoCedulaHtml(mvp.foto, mvp.nombre)}
        <div><span class="mvp-titulo">★ MVP del partido</span><b class="mvp-nombre">#${mvp.dorsal} ${esc(mvp.nombre)}</b>
          <span class="sutil pequeno">${esc(mvp.equipo === p.local ? p.localNombre : p.visitanteNombre)}${accionesTexto(mvp) ? ' · ' + esc(accionesTexto(mvp)) : ''}</span></div>
        <span class="nota nota-mvp">${notaTexto(mvp.notaFinal)}</span></div>` : ''}
    <div class="detalle-columnas">${columna(p.localNombre, c.local)}${columna(p.visitanteNombre, c.visitante)}</div>`;
}

/* ---------------------------------------------------------------- eventos */

function conectarVotaciones() {
  $('vista-votaciones').addEventListener('click', e => {
    const desplegar = e.target.closest('[data-desplegar]'), enviar = e.target.closest('[data-enviar-votos]');
    if (desplegar) {
      const id = Number(desplegar.dataset.desplegar);
      if (!votacionesDesplegadas.delete(id)) votacionesDesplegadas.add(id);
      dibujarVotaciones();
      document.querySelector(`[data-desplegar="${id}"]`).focus();
    }
    if (enviar) {
      enviar.disabled = true; // un doble clic no envía dos veces
      enviarVotos(Number(enviar.dataset.enviarVotos));
    }
  });
  $('vista-votaciones').addEventListener('change', e => {
    const s = e.target.closest('[data-nota-jugador]');
    if (!s) return;
    const id = Number(s.dataset.notaPartido), elegidas = notasElegidas.get(id) || new Map();
    if (s.value) elegidas.set(Number(s.dataset.notaJugador), Number(s.value));
    else elegidas.delete(Number(s.dataset.notaJugador));
    notasElegidas.set(id, elegidas);
    const enviar = document.querySelector(`[data-enviar-votos="${id}"]`);
    enviar.textContent = textoEnviar(elegidas.size);
    enviar.disabled = elegidas.size === 0;
  });
  setInterval(avanzarRelojVotaciones, 15000);
  $('vtTiempo').addEventListener('change', () => {
    actualizarEditorVotacion();
    if ($('vtTiempo').value === 'otro') $('vtHoras').select();
  });
  $('vtCerrar').addEventListener('click', cerrarVotacion);
  $('dpPanel').addEventListener('click', e => {
    if (!e.target.closest('[data-ir-a-votar]')) return;
    if (detalleActual) votacionesDesplegadas.add(detalleActual.partido.id);
    $('dlgDetalle').close();
    mostrarVista('votaciones');
  });
}
