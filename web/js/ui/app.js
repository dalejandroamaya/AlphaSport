'use strict';

/* Capa de interfaz: dibuja el estado que entrega el servicio y le envia las acciones del usuario. */

const CLAVE_SESION = 'alphasport.sesion';
const CLAVE_PESTANA = 'alphasport.pestana';
const ESTADOS_TEXTO = { PENDIENTE: 'Pendiente', JUGADO: 'Jugado', APLAZADO: 'Aplazado', SUSPENDIDO: 'Suspendido' };

let servicio = null;
let catalogo = null;
let E = { hay: false };

const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
const nombreCriterio = clave => (catalogo.criterios.find(x => x.clave === clave) || { texto: clave }).texto;
const equipoPorId = id => E.equipos.find(e => e.id === id);

function guardarPreferencia(clave, valor) {
  try { localStorage.setItem(clave, valor); } catch (e) { /* sin almacenamiento */ }
}

function leerPreferencia(clave) {
  try { return localStorage.getItem(clave); } catch (e) { return null; }
}

/* ---------------------------------------------------------------- avisos y dialogos */

let temporizadorAviso;
function aviso(texto) {
  if (!texto) return;
  const el = $('aviso');
  el.textContent = texto;
  el.classList.add('visible');
  clearTimeout(temporizadorAviso);
  temporizadorAviso = setTimeout(() => el.classList.remove('visible'), 3800);
}

function mensaje(titulo, contenido, opciones = {}) {
  return new Promise(resolver => {
    const dlg = $('dlgMensaje');
    $('msgTitulo').textContent = titulo;
    const caja = $('msgTexto');
    caja.textContent = contenido;
    let entrada = null;
    if (opciones.entrada !== undefined) {
      entrada = document.createElement('input');
      entrada.value = opciones.entrada;
      caja.append(entrada);
    }
    $('msgNo').hidden = !opciones.confirmar && opciones.entrada === undefined;
    $('msgSi').textContent = opciones.textoSi || 'Aceptar';
    let respondido = false;
    const responder = si => {
      if (respondido) return;
      respondido = true;
      if (dlg.open) dlg.close();
      resolver(entrada ? (si ? entrada.value.trim() : null) : si);
    };
    $('msgSi').onclick = e => { e.preventDefault(); responder(true); };
    $('msgNo').onclick = e => { e.preventDefault(); responder(false); };
    dlg.oncancel = () => responder(false);
    dlg.onclose = () => responder(false);
    if (entrada) entrada.onkeydown = e => { if (e.key === 'Enter') { e.preventDefault(); responder(true); } };
    dlg.showModal();
    if (entrada) entrada.select();
  });
}

/* Ejecuta una accion del servicio, actualiza la pantalla y devuelve la respuesta. */
async function accion(ruta, cuerpo, { silencioso = false } = {}) {
  const r = await servicio.ejecutar(ruta, cuerpo);
  if (r.ok) {
    E = r.estado;
    render();
    if (!silencioso) aviso(r.mensaje);
  }
  return r;
}

function descargar({ nombre, contenido }) {
  const url = URL.createObjectURL(contenido);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function fechaTexto(p) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(p.fecha || '');
  const fecha = m ? `${m[3]}/${m[2]}/${m[1]}` : (p.fecha || 'Por definir');
  return p.hora ? fecha + ' · ' + p.hora : fecha;
}

/* ---------------------------------------------------------------- sesion */

async function iniciar() {
  servicio = await crearServicio();
  catalogo = await servicio.catalogo();
  conectarEventos();
  $('jgPosicion').innerHTML = catalogo.posiciones.map(p => `<option>${esc(p)}</option>`).join('');
  $('chipModo').textContent = servicio.modo === 'java' ? '☕ Programa Java' : '🌐 Versión web';
  $('menuCerrar').hidden = servicio.modo !== 'java';
  if (leerPreferencia(CLAVE_SESION) === 'ok') mostrarApp();
  else $('login').hidden = false;
}

async function entrar(e) {
  e.preventDefault();
  const r = await servicio.ejecutar('login', { usuario: $('loginUsuario').value, clave: $('loginClave').value });
  $('loginError').hidden = r.ok;
  if (!r.ok) {
    $('loginError').textContent = r.error;
    return;
  }
  guardarPreferencia(CLAVE_SESION, 'ok');
  mostrarApp();
}

async function mostrarApp() {
  $('login').hidden = true;
  $('app').hidden = false;
  E = await servicio.estado();
  if (!E.hay) {
    abrirNuevo(true);
    return;
  }
  render();
  mostrarVista(leerPreferencia(CLAVE_PESTANA) || 'equipos');
}

/* ---------------------------------------------------------------- dibujo general */

function render() {
  if (!E.hay) return;
  const c = E.campeonato;
  $('titulo').textContent = c.nombre;
  document.title = c.nombre + ' · AlphaSport';
  $('chipFormato').textContent = c.formatoNombre;
  $('chipEquipos').textContent = c.equiposActivos + ' equipos';
  $('estado').textContent = c.estado;
  $('btnAvanzar').textContent = c.terminado ? 'Terminar e iniciar otro' : c.iniciado ? 'Avanzar ronda / fase' : 'Iniciar campeonato';
  $('btnAvanzar').classList.toggle('dorado', c.terminado);
  $('menuSiguiente').disabled = !c.puedeSiguiente;
  renderEquipos();
  renderPartidos();
  renderPosiciones();
  renderLlaves();
  renderSanciones();
  renderReglas();
}

function mostrarVista(nombre) {
  if (!$('vista-' + nombre)) nombre = 'equipos';
  document.querySelectorAll('.vista').forEach(v => { v.hidden = v.id !== 'vista-' + nombre; });
  document.querySelectorAll('.navegacion button').forEach(b => {
    b.classList.toggle('activa', b.dataset.vista === nombre);
    b.setAttribute('aria-current', b.dataset.vista === nombre ? 'page' : 'false');
  });
  guardarPreferencia(CLAVE_PESTANA, nombre);
}

/* ---------------------------------------------------------------- equipos */

function renderEquipos() {
  const iniciado = E.campeonato.iniciado;
  $('nombreRapido').disabled = iniciado;
  $('formRapido').querySelector('button').disabled = iniciado;
  $('btnEquipoCompleto').disabled = iniciado;
  $('ayudaEquipos').textContent = iniciado
    ? 'El campeonato ya inició: puede editar nombre, DT y jugadores, pero no agregar equipos nuevos.'
    : 'Registre los equipos (mínimo 2) y pulse «Iniciar campeonato» para generar el fixture automáticamente.';
  $('gridEquipos').innerHTML = E.equipos.length ? E.equipos.map((e, i) => `
    <article class="tarjeta-equipo vidrio ${e.retirado ? 'retirado' : ''}">
      <div class="escudo" style="--tono:${(e.id * 47) % 360}">${esc(e.nombre.slice(0, 2).toUpperCase())}</div>
      <div class="equipo-datos">
        <span class="sobretitulo">#${i + 1}${e.retirado ? ' · Retirado' : ''}</span>
        <h3>${esc(e.nombre)}</h3>
        <p class="sutil pequeno">DT: ${e.dt ? esc(e.dt.nombre) : 'Sin asignar'} · ${e.jugadores.length} jugadores</p>
        <p class="sutil pequeno">🏟️ ${esc(e.estadio) || 'Sin estadio'}</p>
      </div>
      <div class="equipo-acciones">
        <button class="btn mini" data-editar-equipo="${e.id}">Editar</button>
        ${e.retirado ? '' : `<button class="btn mini peligro" data-quitar-equipo="${e.id}">${iniciado ? 'Retirar' : 'Eliminar'}</button>`}
      </div>
    </article>`).join('')
    : '<div class="vacio vidrio">⚽ Todavía no hay equipos registrados.</div>';
}

let equipoEditando = null;
let jugadoresTemp = [];

function abrirEquipo(equipo) {
  equipoEditando = equipo;
  jugadoresTemp = equipo ? equipo.jugadores.map(j => Object.assign({}, j)) : [];
  $('eqTitulo').textContent = equipo ? 'Equipo: ' + equipo.nombre : 'Agregar equipo';
  $('eqNombre').value = equipo ? equipo.nombre : '';
  $('eqEstadio').value = equipo ? equipo.estadio : '';
  const dt = equipo && equipo.dt;
  $('dtNombre').value = dt ? dt.nombre : '';
  $('dtDocumento').value = dt ? dt.documento : '';
  $('dtNacionalidad').value = dt ? dt.nacionalidad : '';
  $('eqError').hidden = true;
  $('jgError').hidden = true;
  limpiarFormJugador();
  renderJugadores();
  $('dlgEquipo').showModal();
}

function limpiarFormJugador() {
  let dorsal = 1;
  while (jugadoresTemp.some(j => j.dorsal === dorsal) && dorsal < 99) dorsal++;
  $('jgDorsal').value = dorsal;
  $('jgNombre').value = '';
  $('jgDocumento').value = '';
}

function renderJugadores() {
  jugadoresTemp.sort((a, b) => a.dorsal - b.dorsal);
  $('tablaJugadores').innerHTML = jugadoresTemp.length
    ? jugadoresTemp.map((j, i) => `<tr><td class="num"><span class="dorsal">${j.dorsal}</span></td><td>${esc(j.nombre)}</td><td>${esc(j.documento)}</td>
        <td>${esc(j.posicion)}</td><td>${(j.amarillas ? `🟨${j.amarillas} ` : '') + (j.rojas ? `🟥${j.rojas} ` : '')
        + (j.pendientes ? `<span class="etiqueta suspendido">Suspendido ${j.pendientes}</span>` : '')}</td>
        <td class="acciones-fila"><button type="button" class="btn mini peligro" data-quitar-jugador="${i}">Quitar</button></td></tr>`).join('')
    : '<tr><td colspan="6" class="vacia">Sin jugadores todavía.</td></tr>';
}

function agregarJugador() {
  const dorsal = parseInt($('jgDorsal').value, 10);
  const nombre = $('jgNombre').value.trim();
  const error = !nombre ? 'Escriba el nombre del jugador.'
    : !(dorsal >= 1 && dorsal <= 99) ? 'El dorsal debe estar entre 1 y 99.'
      : jugadoresTemp.some(j => j.dorsal === dorsal) ? `El dorsal ${dorsal} ya está en uso.` : null;
  $('jgError').textContent = error || '';
  $('jgError').hidden = !error;
  if (error) return;
  jugadoresTemp.push({ id: 0, nombre, documento: $('jgDocumento').value.trim(), dorsal, posicion: $('jgPosicion').value });
  renderJugadores();
  limpiarFormJugador();
  $('jgNombre').focus();
}

async function guardarEquipo(e) {
  e.preventDefault();
  const r = await accion('equipo', {
    id: equipoEditando ? equipoEditando.id : null,
    nombre: $('eqNombre').value, estadio: $('eqEstadio').value,
    dt: { nombre: $('dtNombre').value, documento: $('dtDocumento').value, nacionalidad: $('dtNacionalidad').value },
    jugadores: jugadoresTemp.map(({ id, nombre, documento, dorsal, posicion }) => ({ id, nombre, documento, dorsal, posicion }))
  });
  $('eqError').hidden = r.ok;
  if (!r.ok) { $('eqError').textContent = r.error; return; }
  $('dlgEquipo').close();
  aviso('Equipo guardado.');
}

async function quitarEquipo(id) {
  const e = equipoPorId(id), iniciado = E.campeonato.iniciado;
  const texto = iniciado
    ? `¿Retirar al equipo «${e.nombre}»?\nSus partidos pendientes de la fase actual se darán por ganados 3-0 (W.O.) al rival.`
    : `¿Eliminar el equipo «${e.nombre}»?`;
  if (!await mensaje('Confirmar', texto, { confirmar: true, textoSi: iniciado ? 'Retirar' : 'Eliminar' })) return;
  const r = await accion('equipo/quitar', { id });
  if (!r.ok) mensaje('Error', r.error);
}

async function agregarRapido(e) {
  e.preventDefault();
  const nombre = $('nombreRapido').value.trim();
  if (!nombre) return;
  const r = await accion('equipo/rapido', { nombre }, { silencioso: true });
  if (!r.ok) { aviso(r.error); return; }
  $('nombreRapido').value = '';
  $('nombreRapido').focus();
}

/* ---------------------------------------------------------------- partidos */

function renderPartidos() {
  const selFase = $('filtroFase'), selEquipo = $('filtroEquipo');
  const fase = selFase.value, equipo = selEquipo.value;
  selFase.innerHTML = '<option value="todas">Todas las fases</option>' + E.fases.map((f, i) => `<option value="${i}">${esc(f.nombre)}</option>`).join('');
  selFase.value = [...selFase.options].some(o => o.value === fase) ? fase : 'todas';
  selEquipo.innerHTML = '<option value="todos">Todos los equipos</option>' + E.equipos.map(e => `<option value="${e.id}">${esc(e.nombre)}</option>`).join('');
  selEquipo.value = [...selEquipo.options].some(o => o.value === equipo) ? equipo : 'todos';
  dibujarListaPartidos();
}

function dibujarListaPartidos() {
  const fase = $('filtroFase').value, estado = $('filtroEstado').value, equipo = $('filtroEquipo').value;
  const lista = E.partidos.filter(p =>
    (fase === 'todas' || p.fase === Number(fase)) &&
    (equipo === 'todos' || p.local === Number(equipo) || p.visitante === Number(equipo)) &&
    (estado === 'todos' || (estado === 'pendientes' && !p.jugado) || (estado === 'jugados' && p.jugado) ||
      (estado === 'aplazados' && (p.estado === 'APLAZADO' || p.estado === 'SUSPENDIDO'))));
  const cont = $('listaPartidos');
  if (!lista.length) {
    cont.innerHTML = `<div class="vacio vidrio">${E.campeonato.iniciado ? 'No hay partidos con estos filtros.'
      : '📅 El fixture se genera automáticamente al pulsar «Iniciar campeonato».'}</div>`;
    return;
  }
  const bloques = new Map();
  for (const p of lista) {
    const titulo = contextoPartido(p);
    if (!bloques.has(titulo)) bloques.set(titulo, []);
    bloques.get(titulo).push(p);
  }
  cont.innerHTML = [...bloques].map(([titulo, ps]) => `
    <section class="bloque-ronda"><h3>${esc(titulo)}</h3><div class="partidos">${ps.map(htmlPartido).join('')}</div></section>`).join('');
}

const contextoPartido = p => [E.fases[p.fase].nombre, p.grupo ? 'Grupo ' + p.grupo : '', p.nombreRonda].filter(Boolean).join(' · ');

function htmlPartido(p) {
  const marcador = p.jugado
    ? `<b>${p.gl}</b><i>:</i><b>${p.gv}</b>${p.penales ? `<small>${p.pl}-${p.pv} pen</small>` : ''}` : '<span class="vs">VS</span>';
  return `<button class="partido vidrio ${p.jugado ? 'jugado' : ''}" data-partido="${p.id}" title="Registrar o editar el partido">
      <span class="partido-meta">${esc(fechaTexto(p))}<span class="etiqueta ${p.estado.toLowerCase()}">${ESTADOS_TEXTO[p.estado]}</span></span>
      <span class="local ${p.ganador === p.local ? 'ganador' : ''}">${esc(p.localNombre)}</span>
      <span class="resultado">${marcador}</span>
      <span class="visitante ${p.ganador === p.visitante ? 'ganador' : ''}">${esc(p.visitanteNombre)}</span>
      ${p.sede || p.obs ? `<span class="obs">${esc([p.sede, p.obs].filter(Boolean).join(' · '))}</span>` : ''}
    </button>`;
}

let partidoEditando = null;
let tarjetasTemp = [];

function abrirPartido(id) {
  const p = E.partidos.find(x => x.id === id);
  if (!p) return;
  partidoEditando = p;
  $('ptContexto').textContent = contextoPartido(p);
  $('ptLocal').textContent = p.localNombre;
  $('ptVisitante').textContent = p.visitanteNombre;
  $('ptEstado').value = p.estado === 'PENDIENTE' ? 'JUGADO' : p.estado;
  $('ptGL').value = p.gl;
  $('ptGV').value = p.gv;
  $('ptHayPenales').checked = p.penales;
  $('ptPL').value = Math.max(0, p.pl);
  $('ptPV').value = Math.max(0, p.pv);
  tarjetasTemp = p.tarjetas.map(t => Object.assign({}, t));
  $('tjEquipo').innerHTML = [[p.local, p.localNombre], [p.visitante, p.visitanteNombre]]
    .map(([idEq, nombre]) => `<option value="${idEq}">${esc(nombre)}</option>`).join('');
  $('tjTipo').value = 'AMARILLA';
  $('tjFechas').value = 1;
  llenarJugadoresTarjeta();
  renderTarjetasTemp();
  $('tjSuspendidos').textContent = p.suspendidos.length
    ? 'Suspendidos para este partido: ' + p.suspendidos.map(t => `${t.nombre} (${t.equipoNombre})`).join(', ')
    : 'Sin jugadores suspendidos para este partido.';
  $('tjSuspendidos').classList.toggle('hay', p.suspendidos.length > 0);
  $('ptFecha').value = /^\d{4}-\d{2}-\d{2}$/.test(p.fecha) ? p.fecha : '';
  $('ptHora').value = p.hora;
  $('ptSede').value = p.sede;
  $('ptObs').value = p.obs;
  $('ptPenales').hidden = !(p.eliminatorio && p.pierna !== 1);
  $('ptError').hidden = true;
  actualizarFormPartido();
  $('dlgPartido').showModal();
}

function actualizarFormPartido() {
  const jugado = $('ptEstado').value === 'JUGADO';
  ['ptGL', 'ptGV', 'ptHayPenales'].forEach(id => { $(id).disabled = !jugado; });
  $('ptTarjetas').disabled = !jugado;
  $('tjFilaFechas').hidden = $('tjTipo').value !== 'ROJA';
  const conPenales = jugado && $('ptHayPenales').checked;
  $('ptPL').disabled = !conPenales;
  $('ptPV').disabled = !conPenales;
}

function llenarJugadoresTarjeta() {
  const equipo = equipoPorId(Number($('tjEquipo').value));
  const jugadores = equipo ? equipo.jugadores.filter(j => j.id).sort((a, b) => a.dorsal - b.dorsal) : [];
  $('tjJugador').innerHTML = jugadores.length
    ? jugadores.map(j => `<option value="${j.id}">#${j.dorsal} ${esc(j.nombre)}</option>`).join('')
    : '<option value="">(sin jugadores registrados)</option>';
}

function renderTarjetasTemp() {
  $('tjLista').innerHTML = tarjetasTemp.map((t, i) => {
    const equipo = equipoPorId(t.equipo);
    return `<li><span>${t.tipo === 'ROJA' ? '🟥' : '🟨'} #${t.dorsal} ${esc(t.nombre)} · ${esc(equipo ? equipo.nombre : '')}
      ${t.tipo === 'ROJA' ? ` · ${t.fechas} fecha${t.fechas === 1 ? '' : 's'} fuera` : ''}</span>
      <button type="button" class="btn mini peligro" data-quitar-tarjeta="${i}">Quitar</button></li>`;
  }).join('') || '<li class="sutil"><span>Sin tarjetas en este partido.</span></li>';
}

function errorPartido(texto) {
  $('ptError').textContent = texto;
  $('ptError').hidden = !texto;
}

async function agregarTarjeta() {
  const idEquipo = Number($('tjEquipo').value);
  const jugador = (equipoPorId(idEquipo).jugadores || []).find(j => j.id === Number($('tjJugador').value));
  if (!jugador) { errorPartido('Este equipo no tiene jugadores registrados. Agréguelos en la sección Equipos.'); return; }
  errorPartido('');
  let tipo = $('tjTipo').value;
  let fechas = Math.max(1, parseInt($('tjFechas').value, 10) || 1);
  const delJugador = tarjetasTemp.filter(t => t.jugador === jugador.id);
  const dlg = $('dlgPartido');
  if (partidoEditando.suspendidos.some(t => t.jugador === jugador.id)) {
    dlg.close();
    const seguir = await mensaje('Jugador suspendido', `${jugador.nombre} estaba suspendido para este partido. ¿Registrar la tarjeta de todas formas?`, { confirmar: true });
    dlg.showModal();
    if (!seguir) return;
  }
  if (tipo === 'ROJA' && delJugador.some(t => t.tipo === 'ROJA')) { errorPartido(`${jugador.nombre} ya fue expulsado en este partido.`); return; }
  if (tipo === 'AMARILLA' && delJugador.some(t => t.tipo === 'AMARILLA')) {
    dlg.close();
    const roja = await mensaje('Doble amarilla', `${jugador.nombre} ya tiene una amarilla en este partido. ¿Registrar la segunda como ROJA (expulsión, 1 fecha)?`, { confirmar: true, textoSi: 'Sí, roja' });
    dlg.showModal();
    if (roja) { tipo = 'ROJA'; fechas = 1; }
  }
  tarjetasTemp.push({ equipo: idEquipo, jugador: jugador.id, nombre: jugador.nombre, dorsal: jugador.dorsal, tipo, fechas: tipo === 'ROJA' ? fechas : 0 });
  renderTarjetasTemp();
}

const entero = id => Math.max(0, parseInt($(id).value, 10) || 0);

function datosFormPartido() {
  const penales = !$('ptPenales').hidden && $('ptHayPenales').checked;
  return {
    estado: $('ptEstado').value, gl: entero('ptGL'), gv: entero('ptGV'),
    pl: penales ? entero('ptPL') : SIN_PENALES, pv: penales ? entero('ptPV') : SIN_PENALES,
    tarjetas: tarjetasTemp.map(t => Object.assign({}, t)),
    fecha: $('ptFecha').value, hora: $('ptHora').value, sede: $('ptSede').value, obs: $('ptObs').value
  };
}

async function guardarPartido(accionPartido) {
  const cuerpo = { id: partidoEditando.id, datos: datosFormPartido(), accion: accionPartido, confirmado: false };
  let r = await accion('partido', cuerpo, { silencioso: true });
  if (!r.ok && r.codigo === 'CONFIRMAR') {
    $('dlgPartido').close();
    if (!await mensaje('El cambio afecta rondas posteriores', r.error, { confirmar: true, textoSi: 'Sí, continuar' })) return;
    cuerpo.confirmado = true;
    r = await accion('partido', cuerpo, { silencioso: true });
  }
  if (!r.ok) {
    if (!$('dlgPartido').open) $('dlgPartido').showModal();
    errorPartido(r.error);
    if (r.codigo === 'PENALES') {
      $('ptHayPenales').checked = true;
      actualizarFormPartido();
      $('ptPL').focus();
    }
    return;
  }
  if ($('dlgPartido').open) $('dlgPartido').close();
  aviso('Partido guardado.');
}

/* ---------------------------------------------------------------- posiciones */

function renderPosiciones() {
  const sel = $('selectorTabla'), anterior = sel.value;
  sel.innerHTML = E.tablas.map((t, k) => `<option value="${k}">${esc(t.texto)}</option>`).join('');
  sel.value = anterior && Number(anterior) < E.tablas.length ? anterior : '0';
  dibujarTabla();
}

function dibujarTabla() {
  const t = E.tablas[Number($('selectorTabla').value) || 0];
  $('tablaPosiciones').innerHTML = t.filas.length ? t.filas.map((f, i) => {
    const clase = i < t.directos ? 'clasifica' : i < t.directos + t.repechaje ? 'repechaje' : '';
    return `<tr class="${clase}"><td class="num"><span class="pos">${i + 1}</span></td><td><b>${esc(f.nombre)}</b>${f.retirado ? ' <span class="etiqueta retirado">retirado</span>' : ''}</td>
      <td class="num">${f.pj}</td><td class="num">${f.g}</td><td class="num">${f.e}</td><td class="num">${f.p}</td>
      <td class="num">${f.gf}</td><td class="num">${f.gc}</td><td class="num">${f.dg > 0 ? '+' + f.dg : f.dg}</td>
      <td class="num">${f.ta}</td><td class="num">${f.tr}</td><td class="num puntos">${f.pts}</td></tr>`;
  }).join('') : '<tr><td colspan="12" class="vacia">Sin equipos.</td></tr>';
  let leyenda = E.leyenda;
  if (t.directos) leyenda += ' Verde: clasifica.';
  if (t.repechaje) leyenda += ' Ámbar: repechaje.';
  $('leyendaPosiciones').textContent = leyenda;
}

/* ---------------------------------------------------------------- llaves */

function renderLlaves() {
  let html = E.campeonato.terminado ? `<div class="campeon-caja"><span class="trofeo">🏆</span><span class="sobretitulo">Campeón</span><b>${esc(E.campeonato.campeonNombre)}</b></div>` : '';
  const lado = l => `<div class="lado ${l.gana ? 'gana' : ''}"><span>${esc(l.nombre)}</span><span class="goles">${esc(l.goles)}</span></div>`;
  const columnas = cols => `<div class="llave">${cols.map(col => `<div class="columna-ronda"><h4>${esc(col.nombre)}</h4>
      ${col.cruces.map(cruce => `<div class="cruce">${cruce.map(lado).join('')}</div>`).join('')}</div>`).join('')}</div>`;
  for (const ll of E.llaves) {
    html += `<section class="llave-fase vidrio"><h3>${esc(ll.nombre)}</h3>
      ${ll.secciones.map(s => (s.titulo ? `<h4 class="titulo-seccion">${esc(s.titulo)}</h4>` : '') + columnas(s.columnas)).join('')}
      ${ll.vivos ? `<div class="vivos"><div><b>Llave de ganadores (0 derrotas):</b> ${esc(ll.vivos.ganadores.join(', ') || '—')}</div>
        <div><b>Llave de perdedores (1 derrota):</b> ${esc(ll.vivos.perdedores.join(', ') || '—')}</div></div>` : ''}</section>`;
  }
  if (!E.llaves.length) html += '<div class="vacio vidrio">Todavía no hay llaves de eliminación. Aparecen aquí cuando el campeonato llega a una fase de eliminación directa.</div>';
  $('contenedorLlaves').innerHTML = html;
}

/* ---------------------------------------------------------------- sanciones */

function renderSanciones() {
  const filtro = $('filtroSanciones').value, s = E.sanciones;
  $('resumenSanciones').textContent = s.resumen;
  const lista = s.registros.filter(r => filtro === 'todas' || (filtro === 'rojas' && r.tipo === 'ROJA') || (filtro === 'pendientes' && r.pendientes > 0));
  $('tablaSanciones').innerHTML = lista.length ? lista.map(r => {
    const roja = r.tipo === 'ROJA';
    const estado = !roja ? '—' : r.pendientes > 0 ? `<span class="etiqueta suspendido">Le falta(n) ${r.pendientes}</span>` : '<span class="etiqueta jugado">Cumplida</span>';
    return `<tr><td data-label="Partido">${esc(r.partido)}<br><span class="sutil pequeno">${esc(r.ronda)}</span></td>
      <td data-label="Fecha">${esc(fechaTexto(r))}</td><td data-label="Equipo">${esc(r.equipo)}</td>
      <td data-label="Jugador">#${r.dorsal} ${esc(r.jugador)}</td><td data-label="Tarjeta">${roja ? '🟥 Roja' : '🟨 Amarilla'}</td>
      <td data-label="Fechas" class="num">${roja ? r.fechas : '—'}</td><td data-label="Estado">${estado}</td></tr>`;
  }).join('') : '<tr><td colspan="7" class="vacia">No hay tarjetas registradas. Se agregan al editar un partido.</td></tr>';
}

/* ---------------------------------------------------------------- reglas y desempates */

function editorCriterios(contenedor) {
  let lista = [];
  const dibujar = () => {
    const libres = catalogo.criterios.map(x => x.clave).filter(c => !lista.includes(c));
    contenedor.innerHTML = `<ol class="criterios">${lista.map((c, i) => `
      <li><span class="orden">${i + 1}</span><span>${esc(nombreCriterio(c))}</span>
        <button type="button" class="btn mini fantasma" data-mover="${i}" data-dir="-1" aria-label="Subir" ${i === 0 ? 'disabled' : ''}>▲</button>
        <button type="button" class="btn mini fantasma" data-mover="${i}" data-dir="1" aria-label="Bajar" ${i === lista.length - 1 ? 'disabled' : ''}>▼</button>
        <button type="button" class="btn mini peligro" data-quitar="${i}" aria-label="Quitar">✕</button></li>`).join('')}</ol>
      ${libres.length ? `<div class="fila-form"><select>${libres.map(c => `<option value="${c}">${esc(nombreCriterio(c))}</option>`).join('')}</select>
        <button type="button" class="btn" data-agregar>Agregar</button></div>` : ''}`;
  };
  contenedor.onclick = e => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.dataset.mover !== undefined) {
      const i = Number(b.dataset.mover), j = i + Number(b.dataset.dir);
      [lista[i], lista[j]] = [lista[j], lista[i]];
    } else if (b.dataset.quitar !== undefined) {
      lista.splice(Number(b.dataset.quitar), 1);
    } else if (b.hasAttribute('data-agregar')) {
      lista.push(contenedor.querySelector('select').value);
    }
    dibujar();
  };
  return { obtener: () => lista.slice(), poner: l => { lista = l.slice(); dibujar(); } };
}

let editorReglas, editorNuevo;

function renderReglas() {
  const c = E.campeonato;
  $('reglasFormato').textContent = c.formatoNombre;
  $('reglasDescripcion').textContent = c.formatoDescripcion;
  $('reglasLista').innerHTML = c.reglas.map(r => `<li>${esc(r)}</li>`).join('');
  editorReglas.poner(c.criterios);
}

async function aplicarCriterios() {
  const criterios = editorReglas.obtener();
  if (E.fases.length > 1 && !await mensaje('Confirmar',
    'Las tablas se recalcularán con el nuevo orden, pero las fases que ya se generaron no cambian. ¿Aplicar de todas formas?', { confirmar: true })) return;
  const r = await accion('criterios', { criterios }, { silencioso: true });
  if (!r.ok) { mensaje('Error', r.error); return; }
  aviso('Criterios de desempate actualizados.');
}

/* ---------------------------------------------------------------- nuevo campeonato */

let formatoElegido = 'LIGA';
let nuevoObligatorio = false;

function abrirNuevo(obligatorio) {
  nuevoObligatorio = obligatorio;
  $('nvCancelar').hidden = obligatorio;
  const previos = !obligatorio && E.hay ? E.equipos.filter(e => !e.retirado).length : 0;
  $('filaMismosEquipos').hidden = previos === 0;
  $('nvMismosEquipos').checked = previos > 0;
  $('textoMismosEquipos').textContent = `Usar los mismos ${previos} equipos de «${E.hay ? E.campeonato.nombre : ''}» (con su DT y jugadores)`;
  const cfg = catalogo.configuracion;
  $('nvNombre').value = '';
  $('nvVictoria').value = cfg.puntosVictoria;
  $('nvEmpate').value = cfg.puntosEmpate;
  $('nvTamGrupo').value = cfg.tamanoGrupo;
  $('nvClasGrupo').value = cfg.clasificadosPorGrupo;
  $('nvClasPlayoff').value = cfg.clasificadosPlayoff;
  $('nvTipoPlayoff').value = cfg.tipoPlayoff;
  $('nvRondas').value = cfg.rondasSuizo;
  $('nvDirectos').value = cfg.directosSuizo;
  $('nvRepechaje').value = cfg.repechajeSuizo;
  $('nvIdaVuelta').checked = cfg.idaYVuelta;
  $('nvElimIdaVuelta').checked = cfg.eliminatoriaIdaYVuelta;
  $('nvFinalUnica').checked = cfg.finalUnica;
  $('nvVentaja').checked = cfg.ventajaDeportiva;
  $('nvReinicio').checked = cfg.granFinalConReinicio;
  $('nvSorteo').checked = cfg.sorteoAleatorio;
  editorNuevo.poner(cfg.criterios);
  $('tarjetasFormato').innerHTML = catalogo.formatos.map(f => `
    <button type="button" class="tarjeta-formato" role="radio" data-formato="${f.clave}" aria-checked="${f.clave === formatoElegido}">
      <b>${esc(f.nombre)}</b><span>${esc(f.descripcion)}</span></button>`).join('');
  actualizarOpcionesNuevo();
  $('dlgNuevo').showModal();
}

function actualizarOpcionesNuevo() {
  document.querySelectorAll('.tarjeta-formato').forEach(t => t.setAttribute('aria-checked', t.dataset.formato === formatoElegido));
  document.querySelectorAll('#formNuevo [data-para]').forEach(el => { el.hidden = !el.dataset.para.split(' ').includes(formatoElegido); });
  if (!$('nvElimIdaVuelta').checked) $('filaFinalUnica').hidden = true;
}

async function crearDesdeFormulario(e) {
  e.preventDefault();
  const num = (id, def) => { const v = parseInt($(id).value, 10); return Number.isNaN(v) ? def : v; };
  const cfg = catalogo.configuracion;
  const config = {
    puntosVictoria: num('nvVictoria', cfg.puntosVictoria), puntosEmpate: num('nvEmpate', cfg.puntosEmpate),
    idaYVuelta: $('nvIdaVuelta').checked, eliminatoriaIdaYVuelta: $('nvElimIdaVuelta').checked,
    finalUnica: !$('nvElimIdaVuelta').checked || $('nvFinalUnica').checked, sorteoAleatorio: $('nvSorteo').checked,
    tamanoGrupo: num('nvTamGrupo', cfg.tamanoGrupo), clasificadosPorGrupo: num('nvClasGrupo', cfg.clasificadosPorGrupo),
    clasificadosPlayoff: num('nvClasPlayoff', cfg.clasificadosPlayoff), tipoPlayoff: $('nvTipoPlayoff').value,
    ventajaDeportiva: $('nvVentaja').checked, rondasSuizo: num('nvRondas', cfg.rondasSuizo),
    directosSuizo: num('nvDirectos', cfg.directosSuizo), repechajeSuizo: num('nvRepechaje', cfg.repechajeSuizo),
    granFinalConReinicio: $('nvReinicio').checked, criterios: editorNuevo.obtener()
  };
  const mismos = !$('filaMismosEquipos').hidden && $('nvMismosEquipos').checked;
  const r = await accion('nuevo', { nombre: $('nvNombre').value, formato: formatoElegido, config, mismosEquipos: mismos }, { silencioso: true });
  if (!r.ok) { mensaje('Error', r.error); return; }
  $('dlgNuevo').close();
  mostrarVista('equipos');
  aviso(mismos ? `Campeonato creado con ${E.equipos.length} equipos. El anterior quedó en el historial.` : 'Campeonato creado. Ahora registre los equipos.');
}

/* ---------------------------------------------------------------- menu archivo */

async function accionMenu(nombre) {
  $('menuArchivo').hidden = true;
  $('btnMenu').setAttribute('aria-expanded', 'false');
  try {
    switch (nombre) {
      case 'nuevo':
        if (await mensaje('Nuevo campeonato', 'El campeonato actual se reemplazará por uno nuevo. Si ya tiene partidos jugados se guardará en el historial (como «sin terminar» si no ha terminado). ¿Continuar?', { confirmar: true })) abrirNuevo(false);
        break;
      case 'siguiente': {
        const nombreNuevo = await mensaje('Siguiente torneo corto', 'Se creará un torneo nuevo con los mismos equipos, plantillas y reglas.\nNombre del nuevo torneo:',
          { entrada: E.campeonato.nombre.replace('Apertura', 'Clausura') });
        if (!nombreNuevo) return;
        const r = await accion('siguiente', { nombre: nombreNuevo }, { silencioso: true });
        if (!r.ok) { mensaje('Error', r.error); return; }
        aviso(`Se creó «${nombreNuevo}». El torneo anterior quedó en el historial.`);
        break;
      }
      case 'exportar':
        descargar(await servicio.respaldo());
        break;
      case 'importar':
        $('archivoImportar').accept = servicio.extensionRespaldo;
        $('archivoImportar').value = '';
        $('archivoImportar').click();
        break;
      case 'historialTxt':
        descargar(await servicio.exportarHistorial());
        break;
      case 'historial':
        await verHistorial();
        break;
      case 'salir':
        guardarPreferencia(CLAVE_SESION, '');
        $('app').hidden = true;
        $('login').hidden = false;
        $('loginClave').value = '';
        break;
      case 'cerrar':
        if (await mensaje('Cerrar programa', 'Se detendrá el programa AlphaSport. Los datos ya están guardados. ¿Cerrar?', { confirmar: true, textoSi: 'Cerrar' })) {
          await servicio.salir();
          document.body.innerHTML = '<div class="despedida"><img src="img/balon.svg" alt=""><h1>AlphaSport se cerró</h1><p>Ya puede cerrar esta pestaña.</p></div>';
        }
        break;
    }
  } catch (e) {
    mensaje('Error', e.message);
  }
}

async function abrirRespaldo(archivo) {
  const r = await servicio.abrirRespaldo(archivo);
  if (!r.ok) { mensaje('Error', r.error); return; }
  E = r.estado;
  render();
  aviso(r.mensaje);
}

/* ---------------------------------------------------------------- historial */

let historialDatos = { lista: [], palmares: [] };
let historialSeleccion = 0;
let historialPestana = 'clasificacion';

async function verHistorial() {
  historialDatos = await servicio.historial();
  historialSeleccion = 0;
  dibujarHistorial();
  mostrarPestanaHistorial('campeonatos');
  $('dlgHistorial').showModal();
}

function mostrarPestanaHistorial(nombre) {
  document.querySelectorAll('[data-hs]').forEach(b => b.classList.toggle('activa', b.dataset.hs === nombre));
  $('hs-campeonatos').hidden = nombre !== 'campeonatos';
  $('hs-palmares').hidden = nombre !== 'palmares';
}

function dibujarHistorial() {
  const lista = historialDatos.lista;
  $('hsVacio').textContent = lista.length ? `${lista.length} campeonato(s). Seleccione uno para ver sus resultados.`
    : 'Todavía no hay campeonatos en el historial. Se guardan solos al terminar un campeonato.';
  $('hsLista').innerHTML = lista.map((h, i) => `<li><button type="button" data-hist="${i}" aria-current="${i === historialSeleccion}">
      <b>${esc(h.nombre)}</b><small>${esc(h.formato)}</small>
      <small>${esc(h.fecha || '')} · ${h.campeon ? '🏆 ' + esc(h.campeon) : 'Sin terminar'}</small></button></li>`).join('');
  $('hsDetalle').hidden = !lista.length;
  if (lista.length) dibujarDetalleHistorial();
  $('hsPalmares').innerHTML = historialDatos.palmares.map(f => `<tr><td><b>${esc(f.equipo)}</b></td><td class="num">${f.titulos}</td>
      <td class="num">${f.sub}</td><td class="num">${f.podios}</td><td class="num">${f.participaciones}</td><td class="num">${f.pj}</td>
      <td class="num">${f.g}</td><td class="num">${f.e}</td><td class="num">${f.p}</td><td class="num">${f.gf}</td>
      <td class="num">${f.gc}</td><td class="num">${f.pts}</td></tr>`).join('') || '<tr><td colspan="12" class="vacia">Sin datos todavía.</td></tr>';
}

function dibujarDetalleHistorial() {
  const h = historialDatos.lista[historialSeleccion];
  const clases = ['oro', 'plata', 'bronce'], puestos = ['Campeón', 'Subcampeón', 'Tercer lugar'];
  $('hsPodio').innerHTML = h.podio.map((f, i) => `<div class="puesto ${clases[i]}">
      <span>${h.terminado ? puestos[i] : (i + 1) + '.º (sin terminar)'}</span><b>${esc(f.nombre)}</b><span>DT: ${esc(f.dt)}</span></div>`).join('');
  $('hsPremios').innerHTML = h.premios.map(p => `<div class="premio"><span>${esc(p.titulo)}</span><b>${esc(p.valor)}</b><span>${esc(p.detalle)}</span></div>`).join('');
  document.querySelectorAll('[data-hsd]').forEach(b => b.classList.toggle('activa', b.dataset.hsd === historialPestana));
  if (historialPestana === 'clasificacion') {
    $('hsCabecera').innerHTML = '<tr><th class="num">Pos</th><th>Equipo</th><th class="num">PJ</th><th class="num">G</th><th class="num">E</th><th class="num">P</th><th class="num">GF</th><th class="num">GC</th><th class="num">DG</th><th class="num">TA</th><th class="num">TR</th><th class="num">Pts</th></tr>';
    $('hsCuerpo').innerHTML = h.clasificacion.map((f, i) => `<tr><td class="num">${i + 1}</td><td><b>${esc(f.nombre)}</b></td>
      <td class="num">${f.pj}</td><td class="num">${f.g}</td><td class="num">${f.e}</td><td class="num">${f.p}</td><td class="num">${f.gf}</td>
      <td class="num">${f.gc}</td><td class="num">${f.dg}</td><td class="num">${f.ta}</td><td class="num">${f.tr}</td><td class="num">${f.pts}</td></tr>`).join('');
  } else if (historialPestana === 'resultados') {
    $('hsCabecera').innerHTML = '<tr><th>Fase</th><th>Ronda</th><th>Local</th><th>Marcador</th><th>Visitante</th><th>Estado</th></tr>';
    $('hsCuerpo').innerHTML = h.resultados.map(x => `<tr><td>${esc(x.fase)}</td><td>${esc(x.ronda)}</td><td>${esc(x.local)}</td>
      <td>${esc(x.marcador)}</td><td>${esc(x.visitante)}</td><td>${esc(x.estado)}</td></tr>`).join('');
  } else {
    $('hsCabecera').innerHTML = '<tr><th>Equipo</th><th>Jugador</th><th>Tarjeta</th><th>Partido</th></tr>';
    $('hsCuerpo').innerHTML = h.tarjetas.map(t => `<tr><td>${esc(t.equipo)}</td><td>${esc(t.jugador)}</td>
      <td>${t.tipo === 'ROJA' ? `🟥 Roja (${t.fechas} fecha/s)` : '🟨 Amarilla'}</td><td>${esc(t.partido)}</td></tr>`).join('')
      || '<tr><td colspan="4" class="vacia">Sin tarjetas.</td></tr>';
  }
}

async function eliminarDelHistorial() {
  const h = historialDatos.lista[historialSeleccion];
  if (!h) return;
  $('dlgHistorial').close();
  if (await mensaje('Eliminar del historial', `¿Eliminar «${h.nombre}» del historial? Sus resultados dejarán de contar en el palmarés.`, { confirmar: true, textoSi: 'Eliminar' })) {
    const r = await servicio.ejecutar('historial/eliminar', { codigo: h.codigo });
    if (!r.ok) mensaje('Error', r.error);
  }
  await verHistorial();
}

function descargarPremios() {
  const h = historialDatos.lista[historialSeleccion];
  if (!h) return;
  descargar({ nombre: (h.nombre.trim().replace(/[^a-zA-Z0-9-_]+/g, '_') || 'campeonato') + '_premios.txt',
    contenido: new Blob([h.texto.replace(/\r?\n/g, '\r\n')], { type: 'text/plain' }) });
}

/* ---------------------------------------------------------------- avanzar y campeon */

async function avanzar() {
  if (E.campeonato.terminado) {
    await servicio.ejecutar('archivar');
    abrirNuevo(false);
    return;
  }
  const iniciaba = !E.campeonato.iniciado;
  const r = await accion('avanzar', {}, { silencioso: true });
  if (!r.ok) { mensaje('No se puede avanzar', r.error); return; }
  if (E.campeonato.terminado) {
    mostrarVista(E.llaves.length ? 'llaves' : 'posiciones');
    await verCampeon();
    aviso('El campeonato quedó guardado en el historial. Use «Terminar e iniciar otro» para el siguiente.');
  } else {
    if (iniciaba) mostrarVista('partidos');
    aviso(r.mensaje);
  }
}

function verCampeon() {
  const f = E.campeonFicha;
  if (!f) return mensaje('Campeón', 'El campeonato aún no termina.\n' + E.campeonato.estado);
  return mensaje('🏆 Campeón del torneo', `${f.nombre}\n\nDirector técnico: ${f.dt}\n` +
    `Jugados: ${f.pj}   Ganados: ${f.g}   Empatados: ${f.e}   Perdidos: ${f.p}\nGoles a favor: ${f.gf}   Goles en contra: ${f.gc}`);
}

/* ---------------------------------------------------------------- eventos */

function conectarEventos() {
  editorReglas = editorCriterios($('criteriosReglas'));
  editorNuevo = editorCriterios($('criteriosNuevo'));
  $('formLogin').addEventListener('submit', entrar);
  document.querySelector('.navegacion').addEventListener('click', e => {
    const b = e.target.closest('button[data-vista]');
    if (b) mostrarVista(b.dataset.vista);
  });
  $('btnAvanzar').addEventListener('click', avanzar);
  $('btnCampeon').addEventListener('click', verCampeon);
  $('btnHistorial').addEventListener('click', verHistorial);
  $('hsCerrar').addEventListener('click', () => $('dlgHistorial').close());
  document.querySelectorAll('[data-hs]').forEach(b => b.addEventListener('click', () => mostrarPestanaHistorial(b.dataset.hs)));
  document.querySelectorAll('[data-hsd]').forEach(b => b.addEventListener('click', () => { historialPestana = b.dataset.hsd; dibujarDetalleHistorial(); }));
  $('hsLista').addEventListener('click', e => {
    const b = e.target.closest('[data-hist]');
    if (b) { historialSeleccion = Number(b.dataset.hist); dibujarHistorial(); }
  });
  $('hsEliminar').addEventListener('click', eliminarDelHistorial);
  $('hsDescargar').addEventListener('click', descargarPremios);

  $('btnMenu').addEventListener('click', e => {
    e.stopPropagation();
    const abrir = $('menuArchivo').hidden;
    $('menuArchivo').hidden = !abrir;
    $('btnMenu').setAttribute('aria-expanded', String(abrir));
  });
  $('menuArchivo').addEventListener('click', e => {
    const b = e.target.closest('button[data-accion]');
    if (b && !b.disabled) accionMenu(b.dataset.accion);
  });
  document.addEventListener('click', e => { if (!e.target.closest('.menu')) $('menuArchivo').hidden = true; });
  $('archivoImportar').addEventListener('change', e => { if (e.target.files[0]) abrirRespaldo(e.target.files[0]); });

  $('formRapido').addEventListener('submit', agregarRapido);
  $('btnEquipoCompleto').addEventListener('click', () => abrirEquipo(null));
  $('gridEquipos').addEventListener('click', e => {
    const editar = e.target.closest('[data-editar-equipo]'), quitar = e.target.closest('[data-quitar-equipo]');
    if (editar) abrirEquipo(equipoPorId(Number(editar.dataset.editarEquipo)));
    if (quitar) quitarEquipo(Number(quitar.dataset.quitarEquipo));
  });
  $('formEquipo').addEventListener('submit', guardarEquipo);
  $('eqCancelar').addEventListener('click', () => $('dlgEquipo').close());
  $('jgAgregar').addEventListener('click', agregarJugador);
  $('jgNombre').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); agregarJugador(); } });
  $('tablaJugadores').addEventListener('click', e => {
    const b = e.target.closest('[data-quitar-jugador]');
    if (b) { jugadoresTemp.splice(Number(b.dataset.quitarJugador), 1); renderJugadores(); }
  });

  ['filtroFase', 'filtroEstado', 'filtroEquipo'].forEach(id => $(id).addEventListener('change', dibujarListaPartidos));
  $('listaPartidos').addEventListener('click', e => {
    const b = e.target.closest('[data-partido]');
    if (b) abrirPartido(Number(b.dataset.partido));
  });
  $('ptEstado').addEventListener('change', actualizarFormPartido);
  $('ptHayPenales').addEventListener('change', actualizarFormPartido);
  $('tjEquipo').addEventListener('change', llenarJugadoresTarjeta);
  $('tjTipo').addEventListener('change', actualizarFormPartido);
  $('tjAgregar').addEventListener('click', agregarTarjeta);
  $('tjLista').addEventListener('click', e => {
    const b = e.target.closest('[data-quitar-tarjeta]');
    if (b) { tarjetasTemp.splice(Number(b.dataset.quitarTarjeta), 1); renderTarjetasTemp(); }
  });
  $('formPartido').addEventListener('submit', e => { e.preventDefault(); guardarPartido('guardar'); });
  $('ptCancelar').addEventListener('click', () => $('dlgPartido').close());
  $('ptWoLocal').addEventListener('click', () => guardarPartido('woLocal'));
  $('ptWoVisitante').addEventListener('click', () => guardarPartido('woVisitante'));
  $('ptBorrar').addEventListener('click', () => guardarPartido('borrar'));
  $('filtroSanciones').addEventListener('change', renderSanciones);

  $('selectorTabla').addEventListener('change', dibujarTabla);
  $('btnAplicarCriterios').addEventListener('click', aplicarCriterios);

  $('tarjetasFormato').addEventListener('click', e => {
    const t = e.target.closest('[data-formato]');
    if (t) { formatoElegido = t.dataset.formato; actualizarOpcionesNuevo(); }
  });
  $('nvElimIdaVuelta').addEventListener('change', actualizarOpcionesNuevo);
  $('formNuevo').addEventListener('submit', crearDesdeFormulario);
  $('nvCancelar').addEventListener('click', () => $('dlgNuevo').close());
  $('dlgNuevo').addEventListener('cancel', e => { if (nuevoObligatorio) e.preventDefault(); });
}

iniciar();
