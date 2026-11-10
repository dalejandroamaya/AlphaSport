'use strict';

/* Capa de interfaz: dibuja el estado que entrega el servicio y le envia las acciones del usuario. */

const CLAVE_SESION = 'alphasport.sesion';
const CLAVE_PESTANA = 'alphasport.pestana';
const ESTADOS_TEXTO = { PENDIENTE: 'Pendiente', EN_JUEGO: 'En juego', JUGADO: 'Jugado', APLAZADO: 'Aplazado', SUSPENDIDO: 'Suspendido' };

let servicio = null;
let catalogo = null;
let E = { hay: false };

const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
const ICONO = {
  estadio: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10c0-2 4-4 9-4s9 2 9 4v7c0 2-4 4-9 4s-9-2-9-4z"/><path d="M3 10c0 2 4 4 9 4s9-2 9-4"/></svg>',
  persona: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>',
  arriba: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 15 6-6 6 6"/></svg>',
  abajo: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>',
  quitar: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  cambio: '<svg class="ico ico-cambio" viewBox="0 0 24 24" aria-hidden="true"><path class="entra" d="M7 20V6M3 10l4-4 4 4"/><path class="sale" d="M17 4v14M13 14l4 4 4-4"/></svg>'
};
/** Tarjeta de árbitro dibujada (rectángulo amarillo o rojo) con su nombre en texto. */
const tarjetaHtml = (tipo, texto) => `<span class="tarjeta-ico ${tipo === 'ROJA' ? 'roja' : 'amarilla'}" aria-hidden="true"></span>${texto}`;
const vacioHtml = texto => `<div class="vacio vidrio"><img src="img/balon.svg" alt=""><span>${texto}</span></div>`;
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
  $('jgPosicion').innerHTML = catalogo.gruposPosiciones
    ? catalogo.gruposPosiciones.map(g => `<optgroup label="${esc(g.grupo)}">${g.posiciones.map(p => `<option>${esc(p)}</option>`).join('')}</optgroup>`).join('')
    : catalogo.posiciones.map(p => `<option>${esc(p)}</option>`).join('');
  // El chip solo informa cuando la web la sirve el programa Java; en la versión web sola es ruido.
  $('chipModo').textContent = '☕ Programa Java';
  $('chipModo').hidden = servicio.modo !== 'java';
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
  // El punto solo late mientras el campeonato está en juego.
  $('estado').classList.toggle('en-juego', c.iniciado && !c.terminado);
  $('estado').classList.toggle('sin-iniciar', !c.iniciado);
  $('estado').classList.toggle('terminado', c.terminado);
  // El dorado se reserva para los logros: la cabecera se vuelve dorada solo al coronar al campeón.
  $('sobreEstado').textContent = c.terminado ? 'Terminado' : c.iniciado ? 'En curso' : 'Por iniciar';
  document.querySelector('.marcador-principal').classList.toggle('terminado', c.terminado);
  $('btnCampeon').hidden = !c.terminado;
  $('btnAvanzar').textContent = c.terminado ? 'Terminar e iniciar otro' : c.iniciado ? 'Cerrar ronda y avanzar' : 'Iniciar campeonato';
  $('btnAvanzar').classList.toggle('dorado', c.terminado);
  $('menuSiguiente').disabled = !c.puedeSiguiente;
  renderEquipos();
  renderPartidos();
  renderPosiciones();
  renderLlaves();
  renderSanciones();
  renderFicha();
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
  // Con el campeonato iniciado los datos de prueba solo completan plantillas vacías.
  $('btnDatosPrueba').disabled = iniciado && E.equipos.every(e => e.retirado || e.jugadores.length);
  $('ayudaEquipos').textContent = iniciado
    ? 'El campeonato ya inició: puede editar nombre, DT y jugadores, pero no agregar equipos nuevos.'
    : 'Registre los equipos (mínimo 2) y pulse «Iniciar campeonato» para generar el fixture automáticamente.';
  $('gridEquipos').innerHTML = E.equipos.length ? E.equipos.map((e, i) => `
    <article class="tarjeta-equipo vidrio ${e.retirado ? 'retirado' : ''}">
      <div class="escudo" style="--tono:${(e.id * 47) % 360}">${esc(e.nombre.slice(0, 2).toUpperCase())}</div>
      <div class="equipo-datos">
        <h3>${esc(e.nombre)}${e.retirado ? ' <span class="etiqueta retirado">Retirado</span>' : ''}</h3>
        <p class="sutil pequeno">${ICONO.persona}DT: ${e.dt ? esc(e.dt.nombre) : 'Sin asignar'} · ${e.jugadores.length === 1 ? '1 jugador' : e.jugadores.length + ' jugadores'}</p>
        <p class="sutil pequeno">${ICONO.estadio}${esc(e.estadio) || 'Sin estadio'}</p>
      </div>
      <div class="equipo-acciones">
        <button class="btn mini" data-editar-equipo="${e.id}">Editar</button>
        ${e.retirado ? '' : `<button class="btn mini peligro" data-quitar-equipo="${e.id}">${iniciado ? 'Retirar' : 'Eliminar'}</button>`}
      </div>
    </article>`).join('')
    : vacioHtml('Todavía no hay equipos. Escriba un nombre arriba y pulse «Agregar».');
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
        <td>${esc(j.posicion)}</td><td>${(j.amarillas ? tarjetaHtml('AMARILLA', `<span class="solo-lector">Amarillas: </span>${j.amarillas} `) : '') + (j.rojas ? tarjetaHtml('ROJA', `<span class="solo-lector">Rojas: </span>${j.rojas} `) : '')
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
    cont.innerHTML = vacioHtml(E.campeonato.iniciado ? 'No hay partidos con estos filtros.'
      : 'El fixture se genera automáticamente al pulsar «Iniciar campeonato».');
    return;
  }
  const dibujar = cargaRapida ? htmlPartidoRapido : htmlPartido;
  const bloques = new Map();
  for (const p of lista) {
    const titulo = contextoPartido(p);
    if (!bloques.has(titulo)) bloques.set(titulo, []);
    bloques.get(titulo).push(p);
  }
  cont.innerHTML = [...bloques].map(([titulo, ps]) => `
    <section class="bloque-ronda"><h3>${esc(titulo)}</h3><div class="partidos">${ps.map(dibujar).join('')}</div></section>`).join('');
}

const fechaDefinida = p => /^\d{4}-\d{2}-\d{2}$/.test(p.fecha || '');
/** Línea superior de la tarjeta: la fecha solo si existe (no repetir «Por definir» en cada partido). */
const metaPartido = p => `<span class="partido-meta"><span>${fechaDefinida(p) ? esc(fechaTexto(p)) : ''}</span>
  <span class="etiqueta ${p.estado.toLowerCase()}">${ESTADOS_TEXTO[p.estado]}</span></span>`;

/* ---------------------------------------------------------------- carga rápida de resultados */

let cargaRapida = false;

function htmlPartidoRapido(p) {
  const editable = p.estado === 'PENDIENTE' || p.estado === 'JUGADO';
  const valor = v => p.jugado ? v : '';
  return `<div class="partido rapido vidrio ${p.jugado ? 'jugado' : ''}" data-rapido="${p.id}">
      ${metaPartido(p)}
      <span class="local ${p.ganador === p.local ? 'ganador' : ''}">${esc(p.localNombre)}</span>
      <span class="rapido-goles">
        <input type="number" min="0" max="99" inputmode="numeric" placeholder="–" value="${valor(p.gl)}" data-lado="gl" aria-label="Goles de ${esc(p.localNombre)}" ${editable ? '' : 'disabled'}>
        <span aria-hidden="true">:</span>
        <input type="number" min="0" max="99" inputmode="numeric" placeholder="–" value="${valor(p.gv)}" data-lado="gv" aria-label="Goles de ${esc(p.visitanteNombre)}" ${editable ? '' : 'disabled'}>
      </span>
      <span class="visitante ${p.ganador === p.visitante ? 'ganador' : ''}">${esc(p.visitanteNombre)}</span>
      <span class="rapido-acciones">
        <span class="error" role="alert" hidden></span>
        <span class="sutil pequeno">${editable ? '' : 'Reprograme este partido desde el detalle.'}</span>
        <span>
          <button type="button" class="btn mini fantasma" data-detalle="${p.id}">Detalle</button>
          <button type="button" class="btn mini primario" data-guardar-rapido="${p.id}" ${editable ? '' : 'disabled'}>Guardar</button>
        </span>
      </span>
    </div>`;
}

async function guardarRapido(id) {
  const tarjeta = document.querySelector(`[data-rapido="${id}"]`);
  const p = E.partidos.find(x => x.id === id);
  if (!tarjeta || !p) return;
  const gl = tarjeta.querySelector('[data-lado="gl"]'), gv = tarjeta.querySelector('[data-lado="gv"]');
  const error = tarjeta.querySelector('.error');
  const falta = gl.value.trim() === '' ? gl : gv.value.trim() === '' ? gv : null;
  if (falta) {
    error.textContent = 'Escriba los dos marcadores (0 si no hubo goles).';
    error.hidden = false;
    falta.focus();
    return;
  }
  const datos = {
    estado: 'JUGADO', gl: Math.max(0, parseInt(gl.value, 10) || 0), gv: Math.max(0, parseInt(gv.value, 10) || 0),
    pl: p.penales ? p.pl : SIN_PENALES, pv: p.penales ? p.pv : SIN_PENALES, tarjetas: p.tarjetas.map(t => Object.assign({}, t)),
    fecha: fechaDefinida(p) ? p.fecha : '', hora: p.hora, sede: p.sede, obs: p.obs
  };
  const cuerpo = { id, datos, accion: 'guardar', confirmado: false };
  let r = await accion('partido', cuerpo, { silencioso: true });
  if (!r.ok && r.codigo === 'CONFIRMAR') {
    if (!await mensaje('El cambio afecta rondas posteriores', r.error, { confirmar: true, textoSi: 'Sí, continuar' })) return;
    cuerpo.confirmado = true;
    r = await accion('partido', cuerpo, { silencioso: true });
  }
  if (!r.ok) {
    if (r.codigo === 'PENALES') {
      // Un empate en eliminatoria necesita los penales: se completan en el detalle.
      abrirPartido(id);
      $('ptGL').value = datos.gl; $('ptGV').value = datos.gv;
      $('ptEstado').value = 'JUGADO';
      $('ptHayPenales').checked = true;
      actualizarFormPartido();
      errorPartido(r.error);
      $('ptPL').focus();
      return;
    }
    const t = document.querySelector(`[data-rapido="${id}"] .error`);
    if (t) { t.textContent = r.error; t.hidden = false; }
    return;
  }
  aviso(`Resultado guardado: ${p.localNombre} ${datos.gl}-${datos.gv} ${p.visitanteNombre}.`);
  // Tras guardar, el cursor pasa al siguiente partido sin resultado para cargar la jornada de corrido.
  const siguiente = document.querySelector('[data-rapido]:not(.jugado) input[data-lado="gl"]:not(:disabled)');
  if (siguiente) siguiente.focus();
}

function alternarCargaRapida() {
  cargaRapida = !cargaRapida;
  $('btnRapido').setAttribute('aria-pressed', String(cargaRapida));
  dibujarListaPartidos();
}

const contextoPartido = p => [E.fases[p.fase].nombre, p.grupo ? 'Grupo ' + p.grupo : '', p.nombreRonda].filter(Boolean).join(' · ');

/** Resumen corto de lo que pasó en el partido: «2 cambios · 3 amarillas · 1 roja». */
function incidenciasTexto(p) {
  const am = p.tarjetas.filter(t => t.tipo !== 'ROJA').length, ro = p.tarjetas.length - am, cb = (p.cambios || []).length;
  return [cb ? plural(cb, '1 cambio', cb + ' cambios') : '', am ? plural(am, '1 amarilla', am + ' amarillas') : '',
    ro ? plural(ro, '1 roja', ro + ' rojas') : ''].filter(Boolean).join(' · ');
}

const conMarcador = p => p.jugado || p.estado === 'EN_JUEGO';

function htmlPartido(p) {
  const marcador = conMarcador(p)
    ? `<b>${p.gl}</b><i>:</i><b>${p.gv}</b>${p.penales ? `<small>${p.pl}-${p.pv} pen</small>` : ''}` : '<span class="vs">VS</span>';
  const incidencias = conMarcador(p) ? incidenciasTexto(p) : '';
  return `<button class="partido vidrio ${p.jugado ? 'jugado' : ''} ${p.estado === 'EN_JUEGO' ? 'en-vivo' : ''}" data-partido="${p.id}" title="Registrar o editar el partido">
      ${metaPartido(p)}
      <span class="local ${p.ganador === p.local ? 'ganador' : ''}">${esc(p.localNombre)}</span>
      <span class="resultado">${marcador}</span>
      <span class="visitante ${p.ganador === p.visitante ? 'ganador' : ''}">${esc(p.visitanteNombre)}</span>
      ${p.sede || p.obs ? `<span class="obs">${esc([p.sede, p.obs].filter(Boolean).join(' · '))}</span>` : ''}
      ${incidencias ? `<span class="incidencias">${esc(incidencias)}</span>` : ''}
    </button>`;
}

let partidoEditando = null;
let tarjetasTemp = [];
let cambiosTemp = [];
/** XI inicial que se está editando: id del equipo → Set con los ids de los titulares. */
let titularesTemp = new Map();
const TITULARES = 11;

function abrirPartido(id) {
  const p = E.partidos.find(x => x.id === id);
  if (!p) return;
  partidoEditando = p;
  $('ptContexto').textContent = contextoPartido(p);
  $('ptLocal').textContent = p.localNombre;
  $('ptVisitante').textContent = p.visitanteNombre;
  // Un partido sin jugar abre con el marcador vacío: así nunca se guarda un 0:0 que no ocurrió.
  $('ptEstado').value = p.estado;
  // Un partido «En juego» conserva el marcador que lleva.
  $('ptGL').value = conMarcador(p) ? p.gl : '';
  $('ptGV').value = conMarcador(p) ? p.gv : '';
  marcarGolesInvalidos(false);
  $('ptBorrar').hidden = !conMarcador(p);
  $('ptHayPenales').checked = p.penales;
  $('ptPL').value = Math.max(0, p.pl);
  $('ptPV').value = Math.max(0, p.pv);
  tarjetasTemp = p.tarjetas.map(t => Object.assign({}, t));
  $('tjEquipo').innerHTML = [[p.local, p.localNombre], [p.visitante, p.visitanteNombre]]
    .map(([idEq, nombre]) => `<option value="${idEq}">${esc(nombre)}</option>`).join('');
  $('tjTipo').value = 'AMARILLA';
  $('tjFechas').value = 1;
  $('tjMinuto').value = '';
  llenarJugadoresTarjeta();
  renderTarjetasTemp();
  cambiosTemp = (p.cambios || []).map(x => Object.assign({}, x));
  titularesTemp = new Map([[p.local, new Set(p.titularesLocal || [])], [p.visitante, new Set(p.titularesVisitante || [])]]);
  renderXI();
  $('cbEquipo').innerHTML = $('tjEquipo').innerHTML;
  $('cbMinuto').value = '';
  llenarJugadoresCambio();
  renderCambiosTemp();
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
  const estado = $('ptEstado').value;
  const jugado = estado === 'JUGADO', enJuego = estado === 'EN_JUEGO';
  // En un partido pendiente se puede escribir el marcador directamente; al hacerlo pasa a «Jugado».
  ['ptGL', 'ptGV'].forEach(id => { $(id).disabled = !(jugado || enJuego || estado === 'PENDIENTE'); });
  $('ptHayPenales').disabled = !jugado;
  $('ptGuardar').textContent = enJuego ? 'Guardar en vivo' : !jugado ? 'Guardar'
    : partidoEditando && partidoEditando.jugado ? 'Guardar resultado' : 'Registrar resultado';
  // Tarjetas y cambios se anotan mientras se juega («En juego») o después, con el resultado final.
  $('ptTarjetas').disabled = !(jugado || enJuego);
  $('ptCambios').disabled = !(jugado || enJuego);
  $('ptXI').disabled = !(jugado || enJuego);
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

const minutoTexto = m => m > 0 ? m + '\u2032' : '';
const porMinuto = (a, b) => (a.minuto || 999) - (b.minuto || 999);

function renderTarjetasTemp() {
  tarjetasTemp.sort(porMinuto);
  $('tjLista').innerHTML = tarjetasTemp.map((t, i) => {
    const equipo = equipoPorId(t.equipo);
    return `<li><span>${t.minuto ? `<b class="minuto">${minutoTexto(t.minuto)}</b>` : ''}${tarjetaHtml(t.tipo, `<span class="solo-lector">${t.tipo === 'ROJA' ? 'Roja' : 'Amarilla'}: </span>`)}#${t.dorsal} ${esc(t.nombre)} · ${esc(equipo ? equipo.nombre : '')}
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
  const minuto = $('tjMinuto').value.trim() === '' ? 0 : parseInt($('tjMinuto').value, 10);
  if (!(minuto >= 0 && minuto <= 130)) { errorPartido('El minuto de la tarjeta debe estar entre 1 y 130 (o vacío si no se sabe).'); $('tjMinuto').focus(); return; }
  const delJugador = tarjetasTemp.filter(t => t.jugador === jugador.id);
  // Las confirmaciones se abren encima del partido, sin cerrarlo: el foco vuelve a «Agregar» al responder.
  if (partidoEditando.suspendidos.some(t => t.jugador === jugador.id)) {
    const seguir = await mensaje('Jugador suspendido', `${jugador.nombre} estaba suspendido para este partido. ¿Registrar la tarjeta de todas formas?`, { confirmar: true, textoSi: 'Registrar tarjeta' });
    if (!seguir) return;
  }
  if (tipo === 'ROJA' && delJugador.some(t => t.tipo === 'ROJA')) { errorPartido(`${jugador.nombre} ya fue expulsado en este partido.`); return; }
  if (tipo === 'AMARILLA' && delJugador.some(t => t.tipo === 'AMARILLA')) {
    const roja = await mensaje('Doble amarilla', `${jugador.nombre} ya tiene una amarilla en este partido. ¿Registrar la segunda como ROJA (expulsión, 1 fecha)?`, { confirmar: true, textoSi: 'Sí, roja' });
    if (roja) { tipo = 'ROJA'; fechas = 1; }
  }
  tarjetasTemp.push({ equipo: idEquipo, jugador: jugador.id, nombre: jugador.nombre, dorsal: jugador.dorsal, tipo, fechas: tipo === 'ROJA' ? fechas : 0, minuto });
  renderTarjetasTemp();
  $('tjAgregar').focus();
}

/* ---- cambios del partido ---- */

/** Quién ya salió y quién ya entró en los cambios anotados de un equipo. */
function movimientos(idEquipo) {
  const delEquipo = cambiosTemp.filter(x => x.equipo === idEquipo);
  return { salieron: new Set(delEquipo.map(x => x.sale)), entraron: new Set(delEquipo.map(x => x.entra)) };
}

const plantillaDe = idEquipo => {
  const e = equipoPorId(idEquipo);
  return e ? e.jugadores.filter(j => j.id).sort((a, b) => a.dorsal - b.dorsal) : [];
};
/** Cuántos titulares necesita un equipo: 11, o toda la plantilla si tiene menos. */
const necesariosXI = idEquipo => Math.min(TITULARES, plantillaDe(idEquipo).length);

function renderXI() {
  const p = partidoEditando;
  $('xiEquipos').innerHTML = [[p.local, p.localNombre], [p.visitante, p.visitanteNombre]].map(([idEq, nombre]) => {
    const elegidos = titularesTemp.get(idEq), jugadores = plantillaDe(idEq), necesarios = necesariosXI(idEq);
    const usados = new Set(cambiosTemp.filter(x => x.equipo === idEq).map(x => x.sale));
    const completo = elegidos.size === necesarios;
    const lista = jugadores.length ? jugadores.map(j => {
      const suspendido = p.suspendidos.some(t => t.jugador === j.id);
      const marcado = elegidos.has(j.id);
      // No se puede desmarcar a un titular que ya salió en un cambio, ni marcar más de los necesarios.
      const bloqueado = (marcado && usados.has(j.id)) || (!marcado && (completo || suspendido));
      return `<label class="xi-jugador ${marcado ? 'titular' : ''} ${suspendido ? 'suspendido' : ''}" title="${suspendido ? 'Suspendido para este partido' : ''}">
        <input type="checkbox" data-xi-equipo="${idEq}" value="${j.id}" ${marcado ? 'checked' : ''} ${bloqueado ? 'disabled' : ''}>
        <b>${j.dorsal}</b><span>${esc(j.nombre)}<small>${esc(j.posicion || '')}${suspendido ? ' · suspendido' : ''}</small></span></label>`;
    }).join('') : '<p class="sutil">Este equipo no tiene jugadores registrados. Agréguelos en Equipos.</p>';
    return `<div class="xi-equipo">
      <div class="xi-cabecera"><b>${esc(nombre)}</b><span class="etiqueta ${completo ? 'jugado' : 'pendiente'}">${elegidos.size}/${necesarios}</span>
        <button type="button" class="btn mini fantasma" data-xi-rapido="${idEq}" ${jugadores.length ? '' : 'disabled'}>Dorsales más bajos</button>
        <button type="button" class="btn mini fantasma" data-xi-limpiar="${idEq}" ${elegidos.size && !usados.size ? '' : 'disabled'}>Limpiar</button></div>
      <div class="xi-lista">${lista}</div></div>`;
  }).join('');
}

function alMarcarTitular(e) {
  const caja = e.target.closest('[data-xi-equipo]');
  if (!caja) return;
  const elegidos = titularesTemp.get(Number(caja.dataset.xiEquipo));
  if (caja.checked) elegidos.add(Number(caja.value)); else elegidos.delete(Number(caja.value));
  renderXI();
  llenarJugadoresCambio();
}

function accionXI(e) {
  const rapido = e.target.closest('[data-xi-rapido]'), limpiar = e.target.closest('[data-xi-limpiar]');
  if (!rapido && !limpiar) return;
  const idEq = Number((rapido || limpiar).dataset.xiRapido || (rapido || limpiar).dataset.xiLimpiar);
  if (limpiar) titularesTemp.set(idEq, new Set());
  else {
    // Los once dorsales más bajos que no estén suspendidos (sirve con los datos de prueba: 1 al 11).
    const libres = plantillaDe(idEq).filter(j => !partidoEditando.suspendidos.some(t => t.jugador === j.id));
    titularesTemp.set(idEq, new Set(libres.slice(0, necesariosXI(idEq)).map(j => j.id)));
  }
  renderXI();
  llenarJugadoresCambio();
}

function llenarJugadoresCambio() {
  const idEquipo = Number($('cbEquipo').value);
  const jugadores = plantillaDe(idEquipo);
  const { salieron, entraron } = movimientos(idEquipo);
  const titulares = titularesTemp.get(idEquipo) || new Set();
  const opcion = j => `<option value="${j.id}">#${j.dorsal} ${esc(j.nombre)}${j.posicion ? ' · ' + esc(j.posicion) : ''}</option>`;
  const listo = titulares.size === necesariosXI(idEquipo) && titulares.size > 0;
  const vacio = `<option value="">${listo ? '(sin jugadores disponibles)' : '(primero escoja el XI inicial)'}</option>`;
  // Sale: quien está en la cancha (titulares que no han salido y suplentes que ya entraron).
  const pueden = listo ? jugadores.filter(j => (titulares.has(j.id) || entraron.has(j.id)) && !salieron.has(j.id)) : [];
  const antes = $('cbSale').value;
  $('cbSale').innerHTML = pueden.length ? pueden.map(opcion).join('') : vacio;
  if (pueden.some(j => String(j.id) === antes)) $('cbSale').value = antes;
  const sale = Number($('cbSale').value);
  // Entra: solo suplentes que todavía no han entrado.
  const entran = listo ? jugadores.filter(j => !titulares.has(j.id) && !entraron.has(j.id) && !salieron.has(j.id)) : [];
  const antesEntra = $('cbEntra').value;
  $('cbEntra').innerHTML = entran.length ? entran.map(opcion).join('') : vacio;
  if (entran.some(j => String(j.id) === antesEntra)) $('cbEntra').value = antesEntra;
}

function renderCambiosTemp() {
  cambiosTemp.sort(porMinuto);
  $('cbLista').innerHTML = cambiosTemp.map((x, i) => {
    const equipo = equipoPorId(x.equipo);
    return `<li><span><b class="minuto">${minutoTexto(x.minuto)}</b>${ICONO.cambio}
      <span class="sale-txt">Sale #${x.saleDorsal} ${esc(x.saleNombre)}</span> · <span class="entra-txt">Entra #${x.entraDorsal} ${esc(x.entraNombre)}</span>
      · ${esc(equipo ? equipo.nombre : '')}</span>
      <button type="button" class="btn mini peligro" data-quitar-cambio="${i}">Quitar</button></li>`;
  }).join('') || '<li class="sutil"><span>Sin cambios en este partido.</span></li>';
}

function agregarCambio() {
  const idEquipo = Number($('cbEquipo').value);
  const equipo = equipoPorId(idEquipo);
  const sale = equipo && equipo.jugadores.find(j => j.id === Number($('cbSale').value));
  const entra = equipo && equipo.jugadores.find(j => j.id === Number($('cbEntra').value));
  const minuto = parseInt($('cbMinuto').value, 10);
  if (!(minuto >= 1 && minuto <= 130)) { errorPartido('Escriba el minuto del cambio (entre 1 y 130).'); $('cbMinuto').focus(); return; }
  const titulares = titularesTemp.get(idEquipo);
  if (titulares.size !== necesariosXI(idEquipo) || !titulares.size) {
    errorPartido(`Antes de registrar cambios escoja el XI inicial de ${equipo.nombre} (${titulares.size}/${necesariosXI(idEquipo)}).`);
    $('xiEquipos').scrollIntoView({ block: 'nearest' });
    return;
  }
  if (!sale || !entra) { errorPartido('Escoja el jugador que sale y el que entra. Si no aparecen, registre la plantilla en Equipos.'); return; }
  const { salieron, entraron } = movimientos(idEquipo);
  if (salieron.has(sale.id)) { errorPartido(`${sale.nombre} ya salió en otro cambio.`); return; }
  if (salieron.has(entra.id) || entraron.has(entra.id)) { errorPartido(`${entra.nombre} ya participó en otro cambio y no puede volver a entrar.`); return; }
  errorPartido('');
  cambiosTemp.push({ equipo: idEquipo, minuto, sale: sale.id, saleNombre: sale.nombre, saleDorsal: sale.dorsal, entra: entra.id, entraNombre: entra.nombre, entraDorsal: entra.dorsal });
  renderCambiosTemp();
  renderXI();
  llenarJugadoresCambio();
  $('cbSale').focus();
}

const entero = id => Math.max(0, parseInt($(id).value, 10) || 0);

function marcarGolesInvalidos(invalidos) {
  ['ptGL', 'ptGV'].forEach(id => {
    const vacio = $(id).value.trim() === '';
    $(id).setAttribute('aria-invalid', invalidos && vacio ? 'true' : 'false');
  });
}

function alEscribirGoles() {
  if ($('ptEstado').value === 'PENDIENTE' && ($('ptGL').value !== '' || $('ptGV').value !== '')) {
    $('ptEstado').value = 'JUGADO';
    actualizarFormPartido();
  }
  if ($('ptGL').getAttribute('aria-invalid') === 'true' || $('ptGV').getAttribute('aria-invalid') === 'true') {
    marcarGolesInvalidos(true);
    if ($('ptGL').value !== '' && $('ptGV').value !== '') errorPartido('');
  }
}

/** Un resultado «Jugado» necesita los dos marcadores escritos. */
function golesCompletos() {
  if ($('ptEstado').value !== 'JUGADO') return true;
  const faltaLocal = $('ptGL').value.trim() === '', faltaVisitante = $('ptGV').value.trim() === '';
  if (!faltaLocal && !faltaVisitante) return true;
  marcarGolesInvalidos(true);
  errorPartido(faltaLocal && faltaVisitante
    ? 'Escriba los goles de los dos equipos para registrar el resultado, o cambie el estado a «Pendiente».'
    : `Falta el marcador de ${faltaLocal ? partidoEditando.localNombre : partidoEditando.visitanteNombre}. Escriba 0 si no marcó goles.`);
  $(faltaLocal ? 'ptGL' : 'ptGV').focus();
  return false;
}

function datosFormPartido() {
  const penales = !$('ptPenales').hidden && $('ptHayPenales').checked;
  return {
    estado: $('ptEstado').value, gl: entero('ptGL'), gv: entero('ptGV'),
    pl: penales ? entero('ptPL') : SIN_PENALES, pv: penales ? entero('ptPV') : SIN_PENALES,
    tarjetas: tarjetasTemp.map(t => Object.assign({}, t)),
    cambios: cambiosTemp.map(x => Object.assign({}, x)),
    titularesLocal: [...titularesTemp.get(partidoEditando.local)],
    titularesVisitante: [...titularesTemp.get(partidoEditando.visitante)],
    fecha: $('ptFecha').value, hora: $('ptHora').value, sede: $('ptSede').value, obs: $('ptObs').value
  };
}

/** Un XI empezado tiene que quedar completo (o vacío) antes de guardar. */
function xiCompletos() {
  for (const [idEq, elegidos] of titularesTemp) {
    const necesarios = necesariosXI(idEq);
    if (elegidos.size && elegidos.size !== necesarios) {
      errorPartido(`El XI inicial de ${equipoPorId(idEq).nombre} tiene ${elegidos.size} de ${necesarios} jugadores. Complételo o pulse «Limpiar».`);
      $('xiEquipos').scrollIntoView({ block: 'nearest' });
      return false;
    }
  }
  return true;
}

async function guardarPartido(accionPartido) {
  if (accionPartido === 'guardar' && !golesCompletos()) return;
  if (accionPartido === 'guardar' && ['JUGADO', 'EN_JUEGO'].includes($('ptEstado').value) && !xiCompletos()) return;
  const cuerpo = { id: partidoEditando.id, datos: datosFormPartido(), accion: accionPartido, confirmado: false };
  let r = await accion('partido', cuerpo, { silencioso: true });
  if (!r.ok && r.codigo === 'CONFIRMAR') {
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
    let clase = i < t.directos ? 'clasifica' : i < t.directos + t.repechaje ? 'repechaje' : '';
    if (i === 0 && f.pj > 0) clase += ' lider';
    return `<tr class="${clase}"><td class="num"><span class="pos">${i + 1}</span></td><th scope="row"><b>${esc(f.nombre)}</b>${f.retirado ? ' <span class="etiqueta retirado">retirado</span>' : ''}</th>
      <td class="num">${f.pj}</td><td class="num extra">${f.g}</td><td class="num extra">${f.e}</td><td class="num extra">${f.p}</td>
      <td class="num extra">${f.gf}</td><td class="num extra">${f.gc}</td><td class="num">${f.dg > 0 ? '+' + f.dg : f.dg}</td>
      <td class="num extra">${f.ta}</td><td class="num extra">${f.tr}</td><td class="num puntos">${f.pts}</td></tr>`;
  }).join('') : '<tr><td colspan="12" class="vacia">Sin equipos.</td></tr>';
  let leyenda = E.leyenda;
  if (t.directos) leyenda += ' Verde: clasifica.';
  if (t.repechaje) leyenda += ' Ámbar: repechaje.';
  if (t.filas.length && t.filas[0].pj > 0) leyenda += ' Dorado: líder.';
  $('leyendaPosiciones').textContent = leyenda;
}

/* ---------------------------------------------------------------- llaves */

function renderLlaves() {
  let html = E.campeonato.terminado ? `<div class="campeon-caja"><img class="trofeo" src="img/trofeo.svg" alt=""><div><b>${esc(E.campeonato.campeonNombre)}</b><span>Campeón del torneo</span></div></div>` : '';
  const lado = l => `<div class="lado ${l.gana ? 'gana' : ''}"><span>${esc(l.nombre)}</span><span class="goles">${esc(l.goles)}</span></div>`;
  const columnas = cols => `<div class="llave">${cols.map(col => `<div class="columna-ronda"><h4>${esc(col.nombre)}</h4>
      ${col.cruces.map(cruce => `<div class="cruce">${cruce.map(lado).join('')}</div>`).join('')}</div>`).join('')}</div>`;
  for (const ll of E.llaves) {
    html += `<section class="llave-fase vidrio"><h3>${esc(ll.nombre)}</h3>
      ${ll.secciones.map(s => (s.titulo ? `<h4 class="titulo-seccion">${esc(s.titulo)}</h4>` : '') + columnas(s.columnas)).join('')}
      ${ll.vivos ? `<div class="vivos"><div><b>Llave de ganadores (0 derrotas):</b> ${esc(ll.vivos.ganadores.join(', ') || '—')}</div>
        <div><b>Llave de perdedores (1 derrota):</b> ${esc(ll.vivos.perdedores.join(', ') || '—')}</div></div>` : ''}</section>`;
  }
  if (!E.llaves.length) html += vacioHtml('Todavía no hay llaves de eliminación. Aparecen aquí cuando el campeonato llega a una fase de eliminación directa.');
  $('contenedorLlaves').innerHTML = html;
}

/* ---------------------------------------------------------------- sanciones */

function renderSanciones() {
  const filtro = $('filtroSanciones').value, s = E.sanciones;
  $('resumenSanciones').textContent = s.resumen;
  const lista = s.registros.filter(r => filtro === 'todas' || (filtro === 'rojas' && r.tipo === 'ROJA') || (filtro === 'pendientes' && r.pendientes > 0));
  $('tablaSanciones').innerHTML = lista.length ? lista.map(r => {
    const roja = r.tipo === 'ROJA';
    const estado = !roja ? '—' : r.pendientes > 0
      ? `<span class="etiqueta suspendido">${r.pendientes === 1 ? 'Falta 1 fecha' : `Faltan ${r.pendientes} fechas`}</span>` : '<span class="etiqueta jugado">Cumplida</span>';
    return `<tr><td data-label="Partido">${esc(r.partido)}<br><span class="sutil pequeno">${esc(r.ronda)}</span></td>
      <td data-label="Fecha">${esc(fechaTexto(r))}</td><td data-label="Equipo">${esc(r.equipo)}</td>
      <td data-label="Jugador">#${r.dorsal} ${esc(r.jugador)}</td><td data-label="Tarjeta">${tarjetaHtml(r.tipo, roja ? 'Roja' : 'Amarilla')}</td>
      <td data-label="Fechas" class="num">${roja ? r.fechas : '—'}</td><td data-label="Estado">${estado}</td></tr>`;
  }).join('') : '<tr><td colspan="7" class="vacia">No hay tarjetas registradas. Se agregan al editar un partido.</td></tr>';
}

/* ---------------------------------------------------------------- ficha del partido */

let fichaElegida = null;

function renderFicha() {
  const sel = $('fichaPartido');
  const conDatos = E.partidos.filter(conMarcador);
  if (!conDatos.length) {
    sel.hidden = true;
    $('fichaContenido').innerHTML = vacioHtml('Todavía no hay partidos jugados ni en juego. Al registrar un resultado, sus cambios y tarjetas aparecen aquí.');
    return;
  }
  sel.hidden = false;
  sel.innerHTML = conDatos.map(p => `<option value="${p.id}">${p.estado === 'EN_JUEGO' ? 'En juego · ' : ''}${esc(p.localNombre)} ${p.gl}-${p.gv} ${esc(p.visitanteNombre)} · ${esc(p.nombreRonda || contextoPartido(p))}</option>`).join('');
  // Por defecto, el partido en juego o el último jugado.
  if (!conDatos.some(p => p.id === fichaElegida)) {
    const vivo = conDatos.find(p => p.estado === 'EN_JUEGO');
    fichaElegida = (vivo || conDatos[conDatos.length - 1]).id;
  }
  sel.value = String(fichaElegida);
  dibujarFicha(E.partidos.find(p => p.id === fichaElegida));
}

/** XI inicial de cada equipo con quién salió y quién entró (y en qué minuto). */
function alineacionesHtml(p) {
  const cambios = p.cambios || [];
  if (!(p.titularesLocal || []).length && !(p.titularesVisitante || []).length) return '';
  const columna = (idEq, nombre, ids) => {
    const plantilla = plantillaDe(idEq);
    const jugador = id => plantilla.find(j => j.id === id);
    const filas = ids.map(jugador).filter(Boolean).sort((a, b) => a.dorsal - b.dorsal).map(j => {
      const salio = cambios.find(x => x.sale === j.id);
      return `<li><b>${j.dorsal}</b><span>${esc(j.nombre)}<small>${esc(j.posicion || '')}</small></span>${salio ? `<em class="sale-txt">${ICONO.cambio}${minutoTexto(salio.minuto)}</em>` : ''}</li>`;
    }).join('');
    const entraron = cambios.filter(x => x.equipo === idEq).map(x => `<li class="entro"><b>${x.entraDorsal}</b><span>${esc(x.entraNombre)}<small>por #${x.saleDorsal}</small></span><em class="entra-txt">${minutoTexto(x.minuto)}</em></li>`).join('');
    return `<div class="alineacion"><h4>${esc(nombre)}</h4>${ids.length ? `<ol>${filas}</ol>` : '<p class="sutil pequeno">Sin XI registrado.</p>'}
      ${entraron ? `<h5>Entraron</h5><ol>${entraron}</ol>` : ''}</div>`;
  };
  return `<h3 class="ficha-titulo">XI inicial</h3><div class="alineaciones">${columna(p.local, p.localNombre, p.titularesLocal || [])}${columna(p.visitante, p.visitanteNombre, p.titularesVisitante || [])}</div>`;
}

function dibujarFicha(p) {
  const cambios = p.cambios || [];
  const am = id => p.tarjetas.filter(t => t.equipo === id && t.tipo !== 'ROJA').length;
  const ro = id => p.tarjetas.filter(t => t.equipo === id && t.tipo === 'ROJA').length;
  const cb = id => cambios.filter(x => x.equipo === id).length;
  const cifra = (titulo, l, v) => {
    const total = l + v, pl = total ? Math.round(l / total * 100) : 0;
    return `<div class="cifra-duelo"><b>${l}</b><span>${titulo}</span><b>${v}</b>
      <div class="barra-duelo" aria-hidden="true"><i style="width:${pl}%"></i><i style="width:${total ? 100 - pl : 0}%"></i></div></div>`;
  };
  const eventos = [
    ...cambios.map(x => ({ minuto: x.minuto, equipo: x.equipo, html: `${ICONO.cambio}<span><span class="entra-txt">Entra #${x.entraDorsal} ${esc(x.entraNombre)}</span><br><span class="sale-txt">Sale #${x.saleDorsal} ${esc(x.saleNombre)}</span></span>` })),
    ...p.tarjetas.map(t => ({ minuto: t.minuto || 0, equipo: t.equipo, html: `${tarjetaHtml(t.tipo, '')}<span>#${t.dorsal} ${esc(t.nombre)}<br><span class="sutil">${t.tipo === 'ROJA' ? `Roja · ${plural(t.fechas, '1 fecha', t.fechas + ' fechas')} fuera` : 'Amarilla'}</span></span>` }))
  ].sort(porMinuto);
  const fila = e => {
    const local = e.equipo === p.local;
    const celda = `<div class="evento"><span class="solo-lector">${esc(local ? p.localNombre : p.visitanteNombre)}: </span>${e.html}</div>`;
    return `<li class="${local ? 'de-local' : 'de-visitante'}">${local ? celda : '<div></div>'}<span class="minuto-eje">${e.minuto ? minutoTexto(e.minuto) : '–'}</span>${local ? '<div></div>' : celda}</li>`;
  };
  const meta = [contextoPartido(p), fechaDefinida(p) ? fechaTexto(p) : '', p.sede].filter(Boolean).map(esc).join(' · ');
  $('fichaContenido').innerHTML = `
    <article class="ficha vidrio ${p.estado === 'EN_JUEGO' ? 'en-vivo' : ''}">
      <header class="ficha-cabecera">
        <span class="partido-meta"><span>${meta}</span><span class="etiqueta ${p.estado.toLowerCase()}">${ESTADOS_TEXTO[p.estado]}</span></span>
        <div class="ficha-marcador">
          <span class="equipo-nombre local ${p.ganador === p.local ? 'ganador' : ''}">${esc(p.localNombre)}</span>
          <span class="goles"><b>${p.gl}</b><i>:</i><b>${p.gv}</b>${p.penales ? `<small>${p.pl}-${p.pv} pen</small>` : ''}</span>
          <span class="equipo-nombre ${p.ganador === p.visitante ? 'ganador' : ''}">${esc(p.visitanteNombre)}</span>
        </div>
      </header>
      <div class="ficha-cifras">
        ${cifra('Cambios', cb(p.local), cb(p.visitante))}
        ${cifra('Amarillas', am(p.local), am(p.visitante))}
        ${cifra('Rojas', ro(p.local), ro(p.visitante))}
      </div>
      ${alineacionesHtml(p)}
      <h3 class="ficha-titulo">Minuto a minuto</h3>
      ${eventos.length ? `<ol class="linea-tiempo">${eventos.map(fila).join('')}</ol>`
        : '<p class="sutil ficha-vacia">No se registraron cambios ni tarjetas en este partido. Se anotan al abrir el partido en Partidos.</p>'}
      <div class="acciones-izq"><button type="button" class="btn mini fantasma" data-editar-ficha="${p.id}">Editar partido</button></div>
    </article>`;
}

/* ---------------------------------------------------------------- datos de prueba */

const semillaAlAzar = () => Math.floor(Math.random() * 90000) + 10000;

function abrirDatosPrueba() {
  const iniciado = E.campeonato.iniciado;
  $('smSemilla').value = semillaAlAzar();
  $('smCantidad').value = iniciado ? 0 : 8;
  $('smCantidad').disabled = iniciado;
  $('smCantidad').max = catalogo.semillaMaximoEquipos || 32;
  $('smCompletar').checked = true;
  const vacios = E.equipos.filter(e => !e.retirado && !e.jugadores.length).length;
  $('smAviso').textContent = iniciado
    ? `El campeonato ya inició: solo se pueden completar plantillas (${plural(vacios, '1 equipo sin jugadores', vacios + ' equipos sin jugadores')}).`
    : vacios ? `Hay ${plural(vacios, '1 equipo', vacios + ' equipos')} sin jugadores.` : '';
  $('smError').hidden = true;
  $('dlgSemilla').showModal();
  $('smSemilla').select();
}

async function cargarDatosPrueba(e) {
  e.preventDefault();
  const semilla = parseInt($('smSemilla').value, 10);
  if (!(semilla >= 0)) { $('smError').textContent = 'Escriba una semilla (un número entero).'; $('smError').hidden = false; return; }
  const cantidad = Math.max(0, parseInt($('smCantidad').value, 10) || 0);
  const r = await accion('semilla', { semilla, cantidad, completar: $('smCompletar').checked }, { silencioso: true });
  if (!r.ok) { $('smError').textContent = r.error; $('smError').hidden = false; return; }
  $('dlgSemilla').close();
  aviso(r.mensaje);
}

/* ---------------------------------------------------------------- reglas y desempates */

function editorCriterios(contenedor) {
  let lista = [];
  const dibujar = () => {
    const libres = catalogo.criterios.map(x => x.clave).filter(c => !lista.includes(c));
    contenedor.innerHTML = `<ol class="criterios">${lista.map((c, i) => `
      <li><span class="orden">${i + 1}</span><span>${esc(nombreCriterio(c))}</span>
        <button type="button" class="btn mini fantasma icono" data-mover="${i}" data-dir="-1" aria-label="Subir «${esc(nombreCriterio(c))}»" ${i === 0 ? 'disabled' : ''}>${ICONO.arriba}</button>
        <button type="button" class="btn mini fantasma icono" data-mover="${i}" data-dir="1" aria-label="Bajar «${esc(nombreCriterio(c))}»" ${i === lista.length - 1 ? 'disabled' : ''}>${ICONO.abajo}</button>
        <button type="button" class="btn mini peligro icono" data-quitar="${i}" aria-label="Quitar «${esc(nombreCriterio(c))}»">${ICONO.quitar}</button></li>`).join('')}</ol>
      ${libres.length ? `<div class="fila-form"><select aria-label="Criterio para agregar">${libres.map(c => `<option value="${c}">${esc(nombreCriterio(c))}</option>`).join('')}</select>
        <button type="button" class="btn" data-agregar>Agregar</button></div>` : ''}`;
  };
  // Tras redibujar, el foco vuelve al control equivalente para no perderse con teclado o lector de pantalla.
  const enfocar = selectores => {
    for (const s of selectores) {
      const el = contenedor.querySelector(s);
      if (el && !el.disabled) { el.focus(); return; }
    }
  };
  contenedor.onclick = e => {
    const b = e.target.closest('button');
    if (!b) return;
    let destino = [];
    if (b.dataset.mover !== undefined) {
      const i = Number(b.dataset.mover), dir = Number(b.dataset.dir), j = i + dir;
      [lista[i], lista[j]] = [lista[j], lista[i]];
      destino = [`[data-mover="${j}"][data-dir="${dir}"]`, `[data-mover="${j}"][data-dir="${-dir}"]`];
    } else if (b.dataset.quitar !== undefined) {
      const i = Number(b.dataset.quitar);
      lista.splice(i, 1);
      destino = [`[data-quitar="${i}"]`, `[data-quitar="${i - 1}"]`, '[data-agregar]'];
    } else if (b.hasAttribute('data-agregar')) {
      lista.push(contenedor.querySelector('select').value);
      destino = ['select', `[data-quitar="${lista.length - 1}"]`];
    }
    dibujar();
    enfocar(destino);
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
let cambiandoFormato = false;

function abrirNuevo(obligatorio, cambiar = false) {
  nuevoObligatorio = obligatorio;
  cambiandoFormato = cambiar;
  $('nvCancelar').hidden = obligatorio;
  $('nvTitulo').textContent = cambiar ? 'Cambiar formato del campeonato' : 'Nuevo campeonato';
  $('nvCrear').textContent = cambiar ? 'Aplicar nuevo formato' : 'Crear campeonato';
  const previos = !obligatorio && !cambiar && E.hay ? E.equipos.filter(e => !e.retirado).length : 0;
  $('filaMismosEquipos').hidden = previos === 0;
  $('nvMismosEquipos').checked = previos > 0;
  $('textoMismosEquipos').textContent = `Usar los mismos ${previos} equipos de «${E.hay ? E.campeonato.nombre : ''}» (con su DT y jugadores)`;
  const cfg = catalogo.configuracion;
  $('nvNombre').value = cambiar ? E.campeonato.nombre : '';
  if (cambiar) formatoElegido = E.campeonato.formato;
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

/** Grupo de formatos como radios reales: una sola parada de Tab y flechas para cambiar de opción. */
function teclaFormatos(e) {
  const tarjetas = [...document.querySelectorAll('.tarjeta-formato')];
  const actual = tarjetas.findIndex(t => t.dataset.formato === formatoElegido);
  const pasos = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
  let destino = null;
  if (e.key in pasos) destino = (actual + pasos[e.key] + tarjetas.length) % tarjetas.length;
  else if (e.key === 'Home') destino = 0;
  else if (e.key === 'End') destino = tarjetas.length - 1;
  if (destino === null) return;
  e.preventDefault();
  formatoElegido = tarjetas[destino].dataset.formato;
  actualizarOpcionesNuevo();
  tarjetas[destino].focus();
}

function actualizarOpcionesNuevo() {
  document.querySelectorAll('.tarjeta-formato').forEach(t => {
    const elegida = t.dataset.formato === formatoElegido;
    t.setAttribute('aria-checked', elegida);
    t.tabIndex = elegida ? 0 : -1;
  });
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
  if (cambiandoFormato) {
    const r = await accion('cambiarFormato', { nombre: $('nvNombre').value, formato: formatoElegido, config }, { silencioso: true });
    if (!r.ok) { mensaje('Error', r.error); return; }
    $('dlgNuevo').close();
    mostrarVista('equipos');
    aviso(`Formato cambiado a «${E.campeonato.formatoNombre}». Se conservaron los ${E.equipos.length} equipos.`);
    return;
  }
  const mismos = !$('filaMismosEquipos').hidden && $('nvMismosEquipos').checked;
  const r = await accion('nuevo', { nombre: $('nvNombre').value, formato: formatoElegido, config, mismosEquipos: mismos }, { silencioso: true });
  if (!r.ok) { mensaje('Error', r.error); return; }
  $('dlgNuevo').close();
  mostrarVista('equipos');
  aviso(mismos ? `Campeonato creado con ${E.equipos.length} equipos. El anterior quedó en el historial.` : 'Campeonato creado. Ahora registre los equipos.');
}

/* ---------------------------------------------------------------- menu archivo */

/* Menú Archivo con el patrón de menú WAI-ARIA: Esc cierra, flechas recorren, Inicio/Fin saltan, Tab sale. */
const itemsMenu = () => [...$('menuArchivo').querySelectorAll('[role=menuitem]')].filter(b => !b.hidden && !b.disabled);

function abrirMenu(enfocar = 'primero') {
  $('menuArchivo').hidden = false;
  $('btnMenu').setAttribute('aria-expanded', 'true');
  const items = itemsMenu();
  if (items.length) (enfocar === 'ultimo' ? items[items.length - 1] : items[0]).focus();
}

function cerrarMenu(devolverFoco = false) {
  if ($('menuArchivo').hidden) return;
  $('menuArchivo').hidden = true;
  $('btnMenu').setAttribute('aria-expanded', 'false');
  if (devolverFoco) $('btnMenu').focus();
}

function teclaMenu(e) {
  const items = itemsMenu(), i = items.indexOf(document.activeElement);
  const ir = k => { e.preventDefault(); items[(k + items.length) % items.length].focus(); };
  switch (e.key) {
    case 'ArrowDown': ir(i + 1); break;
    case 'ArrowUp': ir(i - 1); break;
    case 'Home': ir(0); break;
    case 'End': ir(items.length - 1); break;
    case 'Escape': e.preventDefault(); cerrarMenu(true); break;
    case 'Tab': cerrarMenu(); break;
  }
}

async function accionMenu(nombre) {
  cerrarMenu(true);
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
      case 'cambiarFormato': {
        const texto = E.campeonato.iniciado
          ? 'Se conservarán los equipos con su DT y jugadores, pero se borrarán el fixture y todos los resultados del campeonato actual. ¿Continuar?'
          : 'Puede escoger otro formato y otras reglas. Los equipos registrados se conservan. ¿Continuar?';
        if (await mensaje('Cambiar formato', texto, { confirmar: true, textoSi: 'Continuar' })) abrirNuevo(false, true);
        break;
      }
      case 'cancelar':
        if (await mensaje('Cancelar campeonato', `Se descartará «${E.campeonato.nombre}» por completo (equipos, partidos y resultados) y no quedará en el historial. Esta acción no se puede deshacer. ¿Cancelar el campeonato?`,
          { confirmar: true, textoSi: 'Sí, cancelarlo' })) {
          const r = await servicio.ejecutar('cancelar');
          if (!r.ok) { mensaje('Error', r.error); return; }
          E = r.estado;
          abrirNuevo(true);
        }
        break;
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
          document.body.innerHTML = '<div class="despedida"><img src="img/logo.png" alt=""><h1>AlphaSport se cerró</h1><p>Ya puede cerrar esta pestaña.</p></div>';
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
  if (!$('dlgHistorial').open) $('dlgHistorial').showModal();
}

function mostrarPestanaHistorial(nombre) {
  document.querySelectorAll('[data-hs]').forEach(b => b.classList.toggle('activa', b.dataset.hs === nombre));
  $('hs-campeonatos').hidden = nombre !== 'campeonatos';
  $('hs-palmares').hidden = nombre !== 'palmares';
}

function dibujarHistorial() {
  const lista = historialDatos.lista;
  $('hsVacio').textContent = lista.length ? `${lista.length === 1 ? '1 campeonato' : lista.length + ' campeonatos'}. Seleccione uno para ver sus resultados.`
    : 'Todavía no hay campeonatos en el historial. Se guardan solos al terminar un campeonato.';
  $('hsLista').innerHTML = lista.map((h, i) => `<li><button type="button" data-hist="${i}" aria-current="${i === historialSeleccion}">
      <b>${esc(h.nombre)}</b><small>${esc(h.formato)}</small>
      <small>${esc(h.fecha || '')} · ${h.campeon ? 'Campeón: ' + esc(h.campeon) : 'Sin terminar'}</small></button></li>`).join('');
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
      <td>${tarjetaHtml(t.tipo, t.tipo === 'ROJA' ? `Roja (${t.fechas === 1 ? '1 fecha' : t.fechas + ' fechas'})` : 'Amarilla')}</td><td>${esc(t.partido)}</td></tr>`).join('')
      || '<tr><td colspan="4" class="vacia">Sin tarjetas.</td></tr>';
  }
}

async function eliminarDelHistorial() {
  const h = historialDatos.lista[historialSeleccion];
  if (!h) return;
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

/* Pantalla del campeón: el clímax del torneo, con trofeo, focos del estadio y confeti dorado. */
function lanzarConfeti() {
  const caja = $('cpConfeti');
  caja.innerHTML = '';
  const colores = ['#f6c453', '#ffe08a', '#1fe08a', '#ffffff', '#d9a02a'];
  for (let i = 0; i < 46; i++) {
    const pieza = document.createElement('i');
    pieza.style.left = Math.random() * 100 + '%';
    pieza.style.background = colores[i % colores.length];
    pieza.style.animationDuration = (2.4 + Math.random() * 2.2) + 's';
    pieza.style.animationDelay = (Math.random() * .9) + 's';
    pieza.style.transform = `rotate(${Math.random() * 360}deg)`;
    caja.append(pieza);
  }
}

function verCampeon() {
  const f = E.campeonFicha;
  if (!f) return mensaje('Campeón', 'El campeonato aún no termina.\n' + E.campeonato.estado);
  $('cpNombre').textContent = f.nombre;
  $('cpTorneo').textContent = 'Campeón de ' + E.campeonato.nombre;
  $('cpDt').textContent = 'Director técnico: ' + f.dt;
  [['cpPj', f.pj], ['cpG', f.g], ['cpE', f.e], ['cpP', f.p], ['cpGf', f.gf], ['cpGc', f.gc]].forEach(([id, v]) => { $(id).textContent = v; });
  lanzarConfeti();
  const dlg = $('dlgCampeon');
  // Mientras se celebra, los focos del estadio se vuelven dorados.
  document.body.classList.add('coronacion');
  return new Promise(resolver => {
    dlg.onclose = () => { document.body.classList.remove('coronacion'); resolver(true); };
    dlg.showModal();
    $('cpCerrar').focus();
  });
}

/* ---------------------------------------------------------------- eventos */

function alternarTema() {
  const claro = document.documentElement.dataset.tema !== 'claro';
  if (claro) document.documentElement.dataset.tema = 'claro';
  else delete document.documentElement.dataset.tema;
  guardarPreferencia('alphasport.tema', claro ? 'claro' : 'oscuro');
}

function conectarEventos() {
  document.querySelectorAll('[data-tema-alternar]').forEach(b => b.addEventListener('click', alternarTema));
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
    if ($('menuArchivo').hidden) abrirMenu(); else cerrarMenu();
  });
  $('btnMenu').addEventListener('keydown', e => {
    if (e.key === 'ArrowDown') { e.preventDefault(); abrirMenu('primero'); }
    if (e.key === 'ArrowUp') { e.preventDefault(); abrirMenu('ultimo'); }
  });
  $('menuArchivo').addEventListener('keydown', teclaMenu);
  $('menuArchivo').addEventListener('click', e => {
    const b = e.target.closest('button[data-accion]');
    if (b && !b.disabled) accionMenu(b.dataset.accion);
  });
  document.addEventListener('click', e => { if (!e.target.closest('.menu')) cerrarMenu(); });

  $('cpCerrar').addEventListener('click', () => $('dlgCampeon').close());
  $('cpTabla').addEventListener('click', () => { $('dlgCampeon').close(); mostrarVista('posiciones'); });
  $('btnRapido').addEventListener('click', alternarCargaRapida);
  $('btnColumnas').addEventListener('click', () => {
    const todas = $('tablaPos').classList.toggle('todas');
    $('btnColumnas').setAttribute('aria-pressed', String(todas));
    $('btnColumnas').textContent = todas ? 'Ver lo esencial' : 'Ver todas las columnas';
  });
  $('tarjetasFormato').addEventListener('keydown', teclaFormatos);
  $('archivoImportar').addEventListener('change', e => { if (e.target.files[0]) abrirRespaldo(e.target.files[0]); });

  $('formRapido').addEventListener('submit', agregarRapido);
  $('btnEquipoCompleto').addEventListener('click', () => abrirEquipo(null));
  $('btnDatosPrueba').addEventListener('click', abrirDatosPrueba);
  $('formSemilla').addEventListener('submit', cargarDatosPrueba);
  $('smCancelar').addEventListener('click', () => $('dlgSemilla').close());
  $('smOtra').addEventListener('click', () => { $('smSemilla').value = semillaAlAzar(); });
  $('fichaPartido').addEventListener('change', () => { fichaElegida = Number($('fichaPartido').value); renderFicha(); });
  $('fichaContenido').addEventListener('click', e => {
    const b = e.target.closest('[data-editar-ficha]');
    if (b) abrirPartido(Number(b.dataset.editarFicha));
  });
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
    const b = e.target.closest('[data-partido]'), detalle = e.target.closest('[data-detalle]'), rapido = e.target.closest('[data-guardar-rapido]');
    if (b) abrirPartido(Number(b.dataset.partido));
    if (detalle) abrirPartido(Number(detalle.dataset.detalle));
    if (rapido) guardarRapido(Number(rapido.dataset.guardarRapido));
  });
  // En carga rápida, Enter en el gol local salta al visitante, y Enter en el visitante guarda.
  $('listaPartidos').addEventListener('keydown', e => {
    const input = e.target.closest('[data-rapido] input');
    if (!input || e.key !== 'Enter') return;
    e.preventDefault();
    const tarjeta = input.closest('[data-rapido]');
    if (input.dataset.lado === 'gl') tarjeta.querySelector('[data-lado="gv"]').focus();
    else guardarRapido(Number(tarjeta.dataset.rapido));
  });
  $('ptEstado').addEventListener('change', actualizarFormPartido);
  $('ptHayPenales').addEventListener('change', actualizarFormPartido);
  $('ptGL').addEventListener('input', alEscribirGoles);
  $('ptGV').addEventListener('input', alEscribirGoles);
  $('tjEquipo').addEventListener('change', llenarJugadoresTarjeta);
  $('tjTipo').addEventListener('change', actualizarFormPartido);
  $('tjAgregar').addEventListener('click', agregarTarjeta);
  $('tjLista').addEventListener('click', e => {
    const b = e.target.closest('[data-quitar-tarjeta]');
    if (b) { tarjetasTemp.splice(Number(b.dataset.quitarTarjeta), 1); renderTarjetasTemp(); }
  });
  $('cbEquipo').addEventListener('change', llenarJugadoresCambio);
  $('cbSale').addEventListener('change', llenarJugadoresCambio);
  $('cbAgregar').addEventListener('click', agregarCambio);
  $('cbMinuto').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('cbSale').focus(); } });
  $('cbLista').addEventListener('click', e => {
    const b = e.target.closest('[data-quitar-cambio]');
    if (b) { cambiosTemp.splice(Number(b.dataset.quitarCambio), 1); renderCambiosTemp(); renderXI(); llenarJugadoresCambio(); }
  });
  $('xiEquipos').addEventListener('change', alMarcarTitular);
  $('xiEquipos').addEventListener('click', accionXI);
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
