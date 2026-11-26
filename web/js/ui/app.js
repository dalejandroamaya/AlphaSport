'use strict';

/* Capa de interfaz: dibuja el estado que entrega el servicio y le envia las acciones del usuario. */

const CLAVE_PESTANA = 'alphasport.pestana';
const ESTADOS_TEXTO = { PENDIENTE: 'Pendiente', EN_JUEGO: 'En juego', JUGADO: 'Jugado', APLAZADO: 'Aplazado', SUSPENDIDO: 'Suspendido' };
/** Vistas que el visitante no ve (son de gestión), la que solo ve el visitante [REQ-07] y vista con la que entra. */
const VISTAS_SOLO_ADMIN = ['equipos', 'reglas']; // «campeonatos»: Mis campeonatos (Organizador) o Campeonatos activos (Aficionado)
const VISTAS_SOLO_VISITANTE = ['votaciones'];
const VISTA_INICIAL_VISITANTE = 'partidos';

let servicio = null;
let catalogo = null;
let E = { hay: false };
/** Sesión abierta: {usuario, rol, rolNombre, puedeEditar}; null si nadie ha entrado. */
let S = null;

/* La regla real está en el servicio (Java o JavaScript): aquí solo se ocultan los controles. */
const puedeEditar = () => !!(S && S.puedeEditar);
/** Si el formato no tiene llaves (Liga), la sección «Llaves» no se muestra a nadie. */
const sinLlaves = () => E.hay && E.campeonato.usaLlaves === false;
const puedeVer = vista => (vista !== 'llaves' || !sinLlaves()) && !(puedeEditar() ? VISTAS_SOLO_VISITANTE : VISTAS_SOLO_ADMIN).includes(vista);

const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
const ICONO = {
  estadio: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10c0-2 4-4 9-4s9 2 9 4v7c0 2-4 4-9 4s-9-2-9-4z"/><path d="M3 10c0 2 4 4 9 4s9-2 9-4"/></svg>',
  persona: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>',
  arriba: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 15 6-6 6 6"/></svg>',
  abajo: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>',
  quitar: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  gol: '<svg class="ico ico-gol" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m12 7 3.5 2.6-1.3 4.1H9.8L8.5 9.6Z"/></svg>',
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
    $('msgNo').textContent = opciones.textoNo || 'Cancelar';
    dlg.classList.toggle('advertencia-dlg', !!opciones.advertencia);
    // Con dos caminos (textoNo), cerrar sin elegir (Esc) devuelve null en lugar de false.
    const alCerrar = opciones.textoNo ? null : false;
    let respondido = false;
    const responder = si => {
      if (respondido) return;
      respondido = true;
      if (dlg.open) dlg.close();
      resolver(entrada ? (si ? entrada.value.trim() : null) : si);
    };
    $('msgSi').onclick = e => { e.preventDefault(); responder(true); };
    $('msgNo').onclick = e => { e.preventDefault(); responder(false); };
    dlg.oncancel = () => responder(alCerrar);
    dlg.onclose = () => responder(alCerrar);
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
    if (r.sesion !== undefined) S = r.sesion;
    render();
    if (!silencioso) aviso(r.mensaje);
  } else if (r.codigo === 'LOGIN') {
    // La sesión se perdió (por ejemplo, se reinició el programa Java): se vuelve a pedir.
    volverAlLogin('Su sesión terminó. Inicie sesión de nuevo.');
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
  actualizarReglasClave(); // lista de reglas de la contraseña y «Crear cuenta» desactivado
  $('jgPosicion').innerHTML = catalogo.gruposPosiciones
    ? catalogo.gruposPosiciones.map(g => `<optgroup label="${esc(g.grupo)}">${g.posiciones.map(p => `<option>${esc(p)}</option>`).join('')}</optgroup>`).join('')
    : catalogo.posiciones.map(p => `<option>${esc(p)}</option>`).join('');
  const formaciones = catalogo.formaciones || [];
  const opciones = '<option value="">Sin definir</option>' + formaciones.map(f => `<option>${esc(f)}</option>`).join('');
  $('fmLocal').innerHTML = opciones;
  $('fmVisitante').innerHTML = opciones;
  // La sesión la guarda el servicio: si sigue abierta se entra directo.
  const r = await servicio.sesion();
  S = r && r.ok ? r.sesion : null;
  if (S) mostrarApp();
  else {
    $('login').hidden = false;
    mostrarAcceso('entrar');
  }
}

async function entrar(e) {
  e.preventDefault();
  await iniciarSesion('login', { usuario: $('loginUsuario').value, clave: $('loginClave').value });
}

/** Crea la cuenta y entra. Valida aquí con las mismas reglas que el servicio, que igual vuelve a validar. */
async function registrarse(e) {
  e.preventDefault();
  const tipo = document.querySelector('input[name="tipoCuenta"]:checked');
  const cuerpo = {
    tipo: tipo ? tipo.value : '', nombre: $('regNombre').value, usuario: $('regUsuario').value,
    correo: $('regCorreo').value, clave: $('regClave').value, confirmacion: $('regConfirmacion').value
  };
  try {
    Cuentas.validarRegistro(cuerpo);
  } catch (error) {
    mostrarErrorAcceso('registroError', error.message);
    return;
  }
  await iniciarSesion('registro', cuerpo, 'registroError');
}

/**
 * Marca en vivo las 5 reglas de la contraseña (cumplida / pendiente) y deja «Crear cuenta» desactivado hasta
 * cumplirlas todas. Solo en el registro: el inicio de sesión no revisa la fuerza (las cuentas antiguas entran).
 */
function actualizarReglasClave() {
  const reglas = Cuentas.reglasClave($('regClave').value);
  $('reglasClave').innerHTML = reglas.map(r =>
    `<li class="${r.cumple ? 'cumplida' : 'pendiente'}">${esc(r.texto)}<span class="sr-only">: ${r.cumple ? 'cumplida' : 'pendiente'}</span></li>`).join('');
  document.querySelector('#formRegistro button[type=submit]').disabled = !reglas.every(r => r.cumple);
}

async function iniciarSesion(ruta, cuerpo, idError = 'loginError') {
  const boton = document.querySelector(`#${idError === 'loginError' ? 'formLogin' : 'formRegistro'} button[type=submit]`);
  boton.disabled = true; // el hash de la clave tarda un momento: evita enviar dos veces
  let r;
  try {
    r = await servicio.ejecutar(ruta, cuerpo);
  } finally {
    if (ruta === 'registro') actualizarReglasClave(); // sigue desactivado si la clave no cumple
    else boton.disabled = false;
  }
  if (!r.ok) {
    mostrarErrorAcceso(idError, r.error);
    return;
  }
  olvidarVotaciones(); // otra sesión: sin las notas a medio elegir de la anterior
  S = r.sesion;
  limpiarAcceso();
  mostrarApp();
  if (r.mensaje) aviso(r.mensaje);
}

function mostrarErrorAcceso(id, texto) {
  $(id).textContent = texto || '';
  $(id).hidden = !texto;
}

/** Borra las contraseñas escritas y los errores de las dos pestañas de acceso. */
function limpiarAcceso() {
  ['loginClave', 'regClave', 'regConfirmacion'].forEach(id => { $(id).value = ''; });
  mostrarErrorAcceso('loginError', '');
  mostrarErrorAcceso('registroError', '');
  actualizarReglasClave();
}

/** Muestra «Iniciar sesión» o «Registrarse». */
function mostrarAcceso(cual) {
  const registro = cual === 'registro';
  $('login').classList.toggle('registrando', registro); // al registrarse saludan los lobitos que salen del panel
  $('formLogin').hidden = registro;
  $('formRegistro').hidden = !registro;
  $('tabEntrar').classList.toggle('activa', !registro);
  $('tabRegistro').classList.toggle('activa', registro);
  $('tabEntrar').setAttribute('aria-selected', String(!registro));
  $('tabRegistro').setAttribute('aria-selected', String(registro));
  $('avisoCuentasLocales').hidden = !servicio || servicio.modo === 'java';
  (registro ? document.querySelector('input[name="tipoCuenta"]') : $('loginUsuario')).focus();
}

/*
 * Lobito que patea: cada vuelta de su animación (aparece, patea, desaparece) sale en otro sitio, alternando el
 * lado del panel y siempre pateando hacia afuera. Si no hay espacio a los lados (celular), sale arriba del panel.
 */
(function lobitoQuePatea() {
  const lobito = document.querySelector('.lobito-patea'), login = $('login');
  if (!lobito || !window.ResizeObserver) return;
  let lado = 'der';
  const ubicar = () => {
    const t = document.querySelector('.login-tarjeta').getBoundingClientRect(), base = login.getBoundingClientRect();
    const ancho = 146, alto = 150, margen = 28, vw = document.documentElement.clientWidth, vh = innerHeight;
    const zonas = { izq: [margen, t.left - ancho - margen], der: [t.right + margen, vw - ancho - margen] };
    const libres = Object.keys(zonas).filter(k => zonas[k][1] >= zonas[k][0]);
    lobito.classList.toggle('arriba', !libres.length);
    if (!libres.length) { // sin espacio a los lados: pequeño, arriba del panel
      lobito.style.left = (vw / 2 - 48 - base.left) + 'px';
      lobito.style.top = (t.top - 100 - base.top) + 'px';
      lobito.classList.remove('espejo');
      return;
    }
    const otro = lado === 'izq' ? 'der' : 'izq';
    lado = libres.includes(otro) ? otro : libres[0];
    const [x0, x1] = zonas[lado];
    lobito.style.left = (x0 + Math.random() * (x1 - x0) - base.left) + 'px';
    lobito.style.top = (60 + Math.random() * Math.max(0, vh - alto - 120) - base.top) + 'px';
    lobito.classList.toggle('espejo', lado === 'izq'); // a la izquierda mira (y patea) hacia la izquierda
  };
  lobito.addEventListener('animationiteration', e => { if (e.target === lobito) ubicar(); });
  new ResizeObserver(() => {
    if (!login.offsetHeight) return;
    ubicar();
    lobito.classList.add('activo');
  }).observe(login);
})();

function volverAlLogin(motivo) {
  S = null;
  document.querySelectorAll('dialog[open]').forEach(d => d.close());
  $('app').hidden = true;
  $('login').hidden = false;
  limpiarAcceso();
  mostrarAcceso('entrar');
  mostrarErrorAcceso('loginError', motivo);
}

async function cerrarSesion() {
  await servicio.ejecutar('logout');
  pestanaNoticias = null; // el próximo administrador entra a lo que necesite atención
  pestanaPartidos = 'previa';
  volverAlLogin();
}

/** Muestra u oculta lo que depende del rol: controles de edición, vistas y el chip del rol. */
function aplicarPermisos() {
  const admin = puedeEditar();
  document.querySelectorAll('[data-solo-admin]').forEach(el => { el.hidden = !admin; });
  // Sin campeonato no hay nada que ver fuera de Partidos (que muestra el aviso).
  document.querySelectorAll('.navegacion button[data-vista]').forEach(b => {
    // (las noticias no dependen del campeonato: siguen disponibles)
    b.hidden = !puedeVer(b.dataset.vista) || (!E.hay && !['campeonatos', 'partidos', 'noticias'].includes(b.dataset.vista));
  });
  // El Aficionado elige qué campeonato seguir entre los activos; el Organizador gestiona los suyos.
  $('navCampeonatos').textContent = admin ? 'Mis campeonatos' : 'Campeonatos activos';
  $('tituloCampeonatos').textContent = admin ? 'Mis campeonatos' : 'Campeonatos activos';
  // Perfil bajo el logo: inicial y «Bienvenido» con el usuario que eligió al registrarse.
  $('perfil').hidden = !S;
  if (S) {
    $('perfilUsuario').textContent = S.usuario;
    $('perfilInicial').textContent = (S.usuario || '?').charAt(0).toUpperCase();
    $('perfil').title = S.nombre && S.nombre !== S.usuario ? S.nombre : '';
  }
  $('chipRol').textContent = S ? S.rolNombre + (admin ? '' : ' · solo lectura') : '';
  $('chipRol').hidden = !S;
  $('chipLectura').hidden = !S || admin;
  $('btnAvanzar').hidden = !admin || !E.hay; // sin campeonato elegido no hay nada que iniciar
  // «Cerrar programa» solo en el programa local (publicado en internet no se cierra).
  $('menuCerrar').hidden = servicio.modo !== 'java' || !admin || !['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname);
  ajustarNavMovil();
  if (!admin && cargaRapida) {
    cargaRapida = false;
    $('btnRapido').setAttribute('aria-pressed', 'false');
  }
}

/*
 * En vivo (solo con el servidor Java, que es el que comparte la base entre computadores): cada pocos segundos
 * se pide el estado —el servidor solo recarga si cambió la versión del campeonato— y se redibuja únicamente si
 * otro equipo cambió algo. Se pausa con la pestaña oculta, con un diálogo abierto o mientras se escribe.
 */
const ACTUALIZAR_CADA_MS = 5000;
let revisando = false;
async function revisarCambios() {
  if (servicio.modo !== 'java' || !S || document.hidden || revisando || $('app').hidden) return;
  const escribiendo = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement && document.activeElement.tagName);
  if (escribiendo || document.querySelector('dialog[open]')) return;
  revisando = true;
  try {
    const nuevo = await servicio.estado();
    if (nuevo && nuevo.ok !== false && JSON.stringify(nuevo) !== JSON.stringify(E)) {
      E = nuevo;
      render();
      aviso('Actualizado con los cambios de otro equipo.');
    }
  } catch (e) {
    // sin conexión por un momento: se intenta de nuevo en la próxima vuelta
  } finally {
    revisando = false;
  }
}
setInterval(revisarCambios, ACTUALIZAR_CADA_MS);

async function mostrarApp() {
  $('login').hidden = true;
  $('app').hidden = false;
  aplicarPermisos();
  E = await servicio.estado();
  if (E.ok === false) { volverAlLogin(E.codigo === 'LOGIN' ? '' : E.error); return; }
  if (!E.hay) {
    renderSinCampeonato();
    return;
  }
  render();
  // El Aficionado empieza en «Campeonatos activos» para elegir cuál seguir, aunque haya uno solo.
  mostrarVista(puedeEditar() ? leerPreferencia(CLAVE_PESTANA) || 'equipos' : 'campeonatos');
}

/**
 * Sin campeonato elegido. El Organizador va a «Mis campeonatos» (elegir uno o crear el primero); el Aficionado,
 * que no crea campeonatos, solo ve el aviso.
 */
function renderSinCampeonato() {
  const organizador = puedeEditar();
  $('titulo').textContent = organizador ? 'Mis campeonatos' : 'Campeonatos activos';
  document.title = 'AlphaSport · Gestor de campeonatos';
  ['sobreEstado', 'chipFormato', 'chipEquipos'].forEach(id => { $(id).hidden = true; });
  $('filaSelector').hidden = true;
  $('estado').textContent = organizador ? 'Elija el campeonato que quiere gestionar o cree uno nuevo.' : 'Elija el campeonato que quiere seguir.';
  $('btnCampeon').hidden = true;
  // Si estaba en Noticias se queda ahí (las generales se siguen viendo); si no, a su vista de inicio.
  const enNoticias = !$('vista-noticias').hidden;
  if (organizador) {
    aplicarPermisos();
    mostrarVista(enNoticias ? 'noticias' : 'campeonatos');
    return;
  }
  document.querySelector('#vista-partidos .filtros').hidden = true;
  $('ayudaPartidos').textContent = 'Calendario y resultados del campeonato en curso.';
  $('pestanasPartidos').hidden = true;
  $('ayudaPestanaPartidos').hidden = true;
  $('listaPartidos').innerHTML = vacioHtml('Elija un campeonato en «Campeonatos activos».');
  aplicarPermisos();
  mostrarVista(enNoticias ? 'noticias' : 'campeonatos');
}

/* ---------------------------------------------------------------- mis campeonatos (Organizador) */

/** Lo último que respondió la consulta «campeonatos»: los del usuario y los que no tienen organizador. */
let misCampeonatos = { campeonatos: [], sinOrganizador: [] };

async function cargarCampeonatos() {
  const r = await servicio.campeonatos();
  if (r && r.ok) misCampeonatos = r;
  return misCampeonatos;
}

/** Tarjetas de «Mis campeonatos»: los suyos (el que gestiona ahora, resaltado) y los que puede tomar. */
async function renderCampeonatos() {
  await cargarCampeonatos();
  const actual = E.hay ? E.campeonato.codigo : null, lista = misCampeonatos.campeonatos || [], sin = misCampeonatos.sinOrganizador || [];
  if (!puedeEditar()) { // Aficionado: todos los campeonatos en curso, para elegir cuál seguir
    $('ayudaCampeonatos').hidden = false;
    $('ayudaCampeonatos').textContent = lista.length ? 'Elija el campeonato que quiere seguir. Puede cambiar cuando quiera desde aquí.' : '';
    $('listaCampeonatos').innerHTML = lista.length ? lista.map(x => tarjetaCampeonatoHtml(x, x.codigo === actual, false, true)).join('')
      : vacioHtml('Todavía no hay campeonatos en curso. Vuelva más tarde.');
    $('bloqueSinOrganizador').hidden = true;
    return;
  }
  $('ayudaCampeonatos').textContent = 'Cada campeonato tiene sus propios equipos, partidos y resultados. Elija cuál gestionar.';
  $('ayudaCampeonatos').hidden = !lista.length;
  // Con campeonatos, lo principal es gestionarlos (la cabecera); crear otro es secundario. Sin ninguno, crear es lo principal.
  $('btnNuevoCampeonato').classList.toggle('primario', !lista.length);
  $('listaCampeonatos').innerHTML = lista.length ? lista.map(x => tarjetaCampeonatoHtml(x, x.codigo === actual)).join('')
    : `<div class="vacio vidrio"><img src="img/balon.svg" alt=""><span>Todavía no tiene campeonatos. Cree el primero: después registra sus equipos y lo inicia.</span>
       <button type="button" class="btn primario" data-crear-campeonato>Crear mi primer campeonato</button></div>`;
  $('bloqueSinOrganizador').hidden = !sin.length;
  $('listaSinOrganizador').innerHTML = sin.map(x => tarjetaCampeonatoHtml(x, false, true)).join('');
}

function tarjetaCampeonatoHtml(x, actual, paraTomar = false, paraSeguir = false) {
  const pct = x.partidos ? Math.round(100 * x.partidosJugados / x.partidos) : 0, codigo = esc(x.codigo);
  const acciones = paraSeguir
    ? `<button type="button" class="btn${actual ? '' : ' primario'}" data-gestionar="${codigo}">${actual ? 'Seguir viendo' : 'Entrar'}</button>`
    : paraTomar
    ? `<button type="button" class="btn primario" data-tomar="${codigo}">Tomar y gestionar</button>`
    : `<button type="button" class="btn" data-gestionar="${codigo}">${actual ? 'Seguir gestionando' : 'Gestionar'}</button>
       <span class="acciones-tarjeta">
         <button type="button" class="btn mini fantasma" data-terminar="${codigo}"${x.partidosJugados ? '' : ' disabled title="Se puede terminar cuando tenga partidos jugados"'}>Terminar…</button>
         <button type="button" class="btn mini peligro" data-eliminar="${codigo}">Eliminar…</button>
       </span>`;
  return `<article class="tarjeta-campeonato vidrio${actual ? ' actual' : ''}" aria-label="${esc(x.nombre)}">
    <header><h3>${esc(x.nombre)}</h3>${actual ? `<span class="chip chip-actual">${paraSeguir ? 'Viendo' : 'Gestionando'}</span>` : ''}</header>
    <div class="chips"><span class="chip">${esc(x.formatoNombre)}</span><span class="chip">${esc(x.estado)}</span><span class="chip">${x.equipos} equipos</span></div>
    <p class="sutil">${esc(x.fase)}</p>
    <div class="progreso" role="progressbar" aria-label="Partidos jugados" aria-valuemin="0" aria-valuemax="${x.partidos}" aria-valuenow="${x.partidosJugados}"><span style="width:${pct}%"></span></div>
    <p class="sutil pequeno">${x.partidos ? `${x.partidosJugados} de ${x.partidos} partidos jugados` : 'Todavía sin fixture'}</p>
    ${paraSeguir && x.organizador ? `<p class="sutil pequeno">Organiza: ${esc(x.organizador)}</p>` : ''}
    <footer>${acciones}</footer>
  </article>`;
}

/** Pasa a gestionar ese campeonato (o a seguirlo, en la fase del Aficionado). */
async function gestionarCampeonato(codigo, vista = 'equipos') {
  const r = await accion('campeonato/elegir', { codigo }, { silencioso: true });
  if (!r.ok) { mensaje('Error', r.error); return; }
  mostrarVista(vista);
}

async function clicCampeonatos(e) {
  const b = e.target.closest('button');
  if (!b) return;
  if (b.hasAttribute('data-crear-campeonato')) { abrirNuevo(false); return; }
  if (b.dataset.gestionar) { gestionarCampeonato(b.dataset.gestionar, puedeEditar() ? 'equipos' : VISTA_INICIAL_VISITANTE); return; }
  const x = [...misCampeonatos.campeonatos, ...(misCampeonatos.sinOrganizador || [])].find(y => y.codigo === (b.dataset.tomar || b.dataset.terminar || b.dataset.eliminar));
  if (!x) return;
  if (b.dataset.tomar) {
    const r = await accion('campeonato/tomar', { codigo: x.codigo });
    if (!r.ok) { mensaje('Error', r.error); return; }
    mostrarVista('equipos');
  } else if (b.dataset.terminar) {
    await terminarCampeonato(x);
  } else if (b.dataset.eliminar) {
    await eliminarCampeonato(x);
  }
}

/** Termina un campeonato: pasa al historial y deja de estar en curso; los demás siguen igual. */
async function terminarCampeonato(x) {
  const texto = `«${x.nombre}» pasará al historial con sus resultados (${x.estado === 'Terminado' ? 'con su campeón' : 'como «sin terminar»'}) `
    + 'y dejará de estar en curso. Sus otros campeonatos siguen igual. ¿Terminarlo?';
  if (!await mensaje('Terminar campeonato', texto, { confirmar: true, textoSi: 'Sí, terminarlo' })) return;
  const r = await accion('campeonato/terminar', { codigo: x.codigo });
  if (!r.ok) { mensaje('Error', r.error); return; }
  mostrarVista('campeonatos');
}

/** Elimina un campeonato sin guardarlo en el historial (se elige y se cancela, como desde el menú Archivo). */
async function eliminarCampeonato(x) {
  const texto = `Se descartará «${x.nombre}» por completo (equipos, partidos y resultados) y no quedará en el historial. `
    + 'Esta acción no se puede deshacer. ¿Eliminarlo?';
  if (!await mensaje('Eliminar campeonato', texto, { confirmar: true, textoSi: 'Sí, eliminarlo' })) return;
  if (!E.hay || E.campeonato.codigo !== x.codigo) {
    const r = await accion('campeonato/elegir', { codigo: x.codigo }, { silencioso: true });
    if (!r.ok) { mensaje('Error', r.error); return; }
  }
  const r = await accion('cancelar', {});
  if (!r.ok) { mensaje('Error', r.error); return; }
  aviso(`Se eliminó «${x.nombre}».`);
  mostrarVista('campeonatos');
}

/** Selector de la cabecera: solo para el Organizador y solo si tiene más de un campeonato. */
async function actualizarSelector() {
  if (!puedeEditar() || !E.hay) { $('filaSelector').hidden = true; return; }
  const lista = (await cargarCampeonatos()).campeonatos || [];
  $('selCampeonato').innerHTML = lista.map(x => `<option value="${esc(x.codigo)}"${E.hay && x.codigo === E.campeonato.codigo ? ' selected' : ''}>${esc(x.nombre)}</option>`).join('');
  $('filaSelector').hidden = lista.length < 2 || !E.hay;
}

/* ---------------------------------------------------------------- dibujo general */

function render() {
  if (!E.hay) { renderSinCampeonato(); return; } // se termino, se elimino o hay que elegir uno
  const c = E.campeonato;
  aplicarPermisos();
  actualizarSelector(); // el Organizador con varios campeonatos cambia de uno a otro desde la cabecera
  ['sobreEstado', 'chipFormato', 'chipEquipos'].forEach(id => { $(id).hidden = false; });
  document.querySelector('#vista-partidos .filtros').hidden = false;
  // El administrador gestiona los partidos (previa y resultados); el visitante solo los consulta.
  const admin = puedeEditar();
  $('navPartidos').textContent = admin ? 'Gestionar partidos' : 'Partidos';
  $('tituloPartidos').textContent = admin ? 'Gestionar partidos' : 'Partidos';
  $('ayudaPartidos').textContent = admin ? 'Prepare cada partido en la «Previa» y, cuando se juegue, cargue lo ocurrido en «Registrar resultados».'
    : 'Calendario y resultados. Toque cualquier partido para ver la previa, el cara a cara, la racha y las alineaciones.';
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
  $('btnAvanzar').textContent = c.terminado ? textoTerminado(c) : c.iniciado ? 'Cerrar ronda y avanzar' : 'Iniciar campeonato';
  $('btnAvanzar').classList.toggle('dorado', c.terminado);
  $('menuSiguiente').disabled = !c.puedeSiguiente;
  $('menuSiguiente').textContent = c.textoSiguiente || 'Siguiente torneo corto (Clausura)…';
  // Sin llaves no hay sección «Llaves»: si estaba abierta se pasa a Posiciones.
  const llavesAbierta = !$('vista-llaves').hidden;
  if (llavesAbierta && !puedeVer('llaves')) mostrarVista('posiciones');
  renderEquipos();
  renderPartidos();
  renderPosiciones();
  renderLlaves();
  renderEstadisticas();
  renderReglas();
  contarVotaciones();
  if (!$('vista-votaciones').hidden) renderVotaciones();
  if (!$('vista-noticias').hidden) renderNoticias();
}

/*
 * Celular: la barra inferior no alcanza para todas las secciones. Quedan a mano las 4 que más usa cada rol
 * (el Organizador carga resultados al borde de la cancha; el Aficionado sigue partidos y vota) y el resto
 * se abre con «Más». En pantallas grandes no cambia nada: es la barra lateral de siempre.
 */
const NAV_PRINCIPALES = {
  organizador: ['partidos', 'posiciones', 'equipos', 'campeonatos'],
  aficionado: ['campeonatos', 'partidos', 'posiciones', 'noticias']
};
const NAV_CORTOS = { campeonatos: 'Torneos', partidos: 'Partidos', sanciones: 'Estadísticas' };
function ajustarNavMovil() {
  const nav = document.querySelector('.navegacion');
  const visibles = [...nav.querySelectorAll('button[data-vista]')].filter(b => !b.hidden);
  const nombres = visibles.map(b => b.dataset.vista);
  const preferidas = NAV_PRINCIPALES[puedeEditar() ? 'organizador' : 'aficionado'].filter(v => nombres.includes(v));
  const orden = [...new Set([...preferidas, ...nombres])];
  const caben = orden.length <= 5; // con 5 o menos no hace falta «Más»
  visibles.forEach(b => {
    const i = orden.indexOf(b.dataset.vista);
    b.style.setProperty('--orden-movil', i);
    b.classList.toggle('nav-extra', !caben && i >= 4);
    // En la barra del celular los nombres largos usan una etiqueta corta; el nombre completo queda para el lector de pantalla.
    const texto = b.querySelector('span');
    if (texto && NAV_CORTOS[b.dataset.vista]) {
      texto.dataset.corto = NAV_CORTOS[b.dataset.vista];
      b.setAttribute('aria-label', texto.textContent.trim());
    }
  });
  $('navMas').hidden = caben;
  cerrarNavMas();
  marcarNavMas(document.querySelector('.navegacion button[data-vista].activa')?.dataset.vista);
}
/** «Más» se ve activo cuando la sección abierta está dentro de su panel. */
function marcarNavMas(nombre) {
  const b = nombre && document.querySelector(`.navegacion button[data-vista="${nombre}"]`);
  $('navMas').classList.toggle('activa', !!(b && b.classList.contains('nav-extra')));
}
function cerrarNavMas() {
  document.querySelector('.navegacion').classList.remove('abierta');
  $('navMas').setAttribute('aria-expanded', 'false');
}

function mostrarVista(nombre) {
  if (!$('vista-' + nombre)) nombre = 'equipos';
  if (!puedeVer(nombre)) nombre = VISTA_INICIAL_VISITANTE; // el visitante no ve Equipos ni Reglas
  // Sin campeonato elegido las demás vistas tendrían datos viejos: el Organizador va a «Mis campeonatos».
  if (!E.hay && !['noticias', 'campeonatos'].includes(nombre)) nombre = puedeVer('campeonatos') ? 'campeonatos' : 'partidos';
  document.querySelectorAll('.vista').forEach(v => { v.hidden = v.id !== 'vista-' + nombre; });
  document.querySelectorAll('.navegacion button[data-vista]').forEach(b => {
    b.classList.toggle('activa', b.dataset.vista === nombre);
    b.setAttribute('aria-current', b.dataset.vista === nombre ? 'page' : 'false');
  });
  guardarPreferencia(CLAVE_PESTANA, nombre);
  marcarNavMas(nombre);
  if (nombre === 'votaciones') renderVotaciones();
  if (nombre === 'noticias') renderNoticias(); // las noticias se piden al servicio al abrir la sección
  if (nombre === 'campeonatos') renderCampeonatos();
}

/* ---------------------------------------------------------------- equipos */

/* ---------------------------------------------------------------- fotos de jugadores y escudos */

/** Persona «NN» (sin foto), con la misma proporción 3:4 de la foto tipo cédula. */
const SILUETA_NN = `<svg class="nn" viewBox="0 0 30 40" aria-hidden="true"><rect width="30" height="40" rx="3" class="nn-fondo"/>
  <circle cx="15" cy="13.5" r="6.5" class="nn-figura"/><path d="M3.5 40c0-8 5-12.5 11.5-12.5S26.5 32 26.5 40z" class="nn-figura"/>
  <text x="15" y="37.5" text-anchor="middle" class="nn-texto">NN</text></svg>`;

/** Foto tipo cédula de un jugador, o la persona «NN» si no tiene. */
const fotoCedulaHtml = (foto, nombre, clase = '') => foto
  ? `<img class="foto-cedula ${clase}" src="${esc(servicio.urlFoto(foto))}" alt="Foto de ${esc(nombre)}" loading="lazy">`
  : `<span class="foto-cedula nn-caja ${clase}" role="img" aria-label="${esc(nombre)}: sin foto">${SILUETA_NN}</span>`;

/** Escudo del equipo: su imagen o, si no tiene, el escudo con las iniciales. */
function escudoEquipoHtml(e, clase = '') {
  const id = e ? e.id : 0, nombre = e ? e.nombre || '?' : '?';
  return e && e.escudo
    ? `<div class="escudo con-imagen ${clase}"><img src="${esc(servicio.urlFoto(e.escudo))}" alt="Escudo de ${esc(nombre)}"></div>`
    : `<div class="escudo ${clase}" style="--tono:${(id * 47) % 360}" aria-hidden="true">${esc(String(nombre).slice(0, 2).toUpperCase())}</div>`;
}

/** Abre el selector de archivos del sistema; devuelve el archivo o null si se cancela. */
function elegirImagen() {
  return new Promise(resolver => {
    const entrada = document.createElement('input');
    entrada.type = 'file';
    entrada.accept = 'image/jpeg,image/png,image/gif,image/webp';
    let listo = false;
    const fin = archivo => { if (!listo) { listo = true; resolver(archivo); } };
    entrada.addEventListener('change', () => fin(entrada.files[0] || null));
    entrada.addEventListener('cancel', () => fin(null));
    entrada.click();
  });
}

/** Abre la imagen ya derecha (las fotos de celular traen su giro en el EXIF). */
async function abrirImagen(archivo) {
  try {
    return await createImageBitmap(archivo, { imageOrientation: 'from-image' });
  } catch (e) {
    const url = URL.createObjectURL(archivo);
    try {
      return await new Promise((ok, mal) => { const i = new Image(); i.onload = () => ok(i); i.onerror = mal; i.src = url; });
    } catch (e2) {
      throw new Error('No se pudo abrir la imagen: use una foto JPG o PNG.');
    } finally {
      setTimeout(() => URL.revokeObjectURL(url), 0);
    }
  }
}

/** Dibuja reduciendo a la mitad varias veces antes del último paso (más nítido que de golpe). */
function dibujarReducida(ctx, img, sx, sy, sw, sh, dx, dy, dw, dh) {
  let fuente = img, x = sx, y = sy, w = sw, h = sh;
  while (w / 2 >= dw && h / 2 >= dh) {
    const lienzo = document.createElement('canvas');
    lienzo.width = Math.max(1, Math.round(w / 2));
    lienzo.height = Math.max(1, Math.round(h / 2));
    const c = lienzo.getContext('2d');
    c.imageSmoothingQuality = 'high';
    c.drawImage(fuente, x, y, w, h, 0, 0, lienzo.width, lienzo.height);
    fuente = lienzo; x = 0; y = 0; w = lienzo.width; h = lienzo.height;
  }
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(fuente, x, y, w, h, dx, dy, dw, dh);
}

/**
 * Deja la imagen en su formato fijo (igual que FotosService en Java):
 *  - jugador: foto tipo cédula 3:4, recortada al centro con la cara arriba, 240x320 JPG;
 *  - escudo: completo (sin recortar) en un cuadrado transparente de 256x256 PNG.
 */
async function prepararImagen(archivo, tipo) {
  if (!/^image\/(jpeg|png|gif|webp)$/.test(archivo.type)) throw new Error('El archivo no es una imagen JPG, PNG, GIF o WEBP.');
  if (archivo.size > 15 * 1024 * 1024) throw new Error('La foto es muy pesada (máximo 15 MB).');
  const img = await abrirImagen(archivo);
  const w = img.width, h = img.height;
  if (!w || !h) throw new Error('La imagen está vacía.');
  if (w * h > 40e6) throw new Error('La foto es demasiado grande (máximo 40 megapíxeles).');
  const lienzo = document.createElement('canvas');
  const ctx = lienzo.getContext('2d');
  if (tipo === 'escudo') {
    lienzo.width = lienzo.height = 256;
    const caja = 240, escala = Math.min(caja / w, caja / h);
    const dw = Math.max(1, Math.round(w * escala)), dh = Math.max(1, Math.round(h * escala));
    dibujarReducida(ctx, img, 0, 0, w, h, (256 - dw) / 2, (256 - dh) / 2, dw, dh);
    return lienzo.toDataURL('image/png');
  }
  lienzo.width = 240;
  lienzo.height = 320;
  const proporcion = 240 / 320;
  let cx = 0, cy = 0, cw = w, ch = h;
  if (w / h > proporcion) { cw = Math.max(1, Math.round(h * proporcion)); cx = (w - cw) / 2; }
  else { ch = Math.max(1, Math.round(w / proporcion)); cy = (h - ch) * 0.3; }
  ctx.fillStyle = '#fff'; // las PNG con transparencia quedan sobre fondo blanco, como una cédula
  ctx.fillRect(0, 0, 240, 320);
  dibujarReducida(ctx, img, cx, cy, cw, ch, 0, 0, 240, 320);
  return lienzo.toDataURL('image/jpeg', 0.88);
}

/** Elige un archivo y lo deja en su formato. Devuelve la data URL, o null si se canceló o falló (con aviso). */
async function pedirImagen(tipo) {
  const archivo = await elegirImagen();
  if (!archivo) return null;
  try {
    return await prepararImagen(archivo, tipo);
  } catch (e) {
    aviso(e.message);
    return null;
  }
}

/**
 * Menú pequeño junto a la foto o el escudo tocado: «Cambiar» y, si ya tiene, «Eliminar».
 * Devuelve 'cambiar', 'quitar' o null. Se dibuja dentro del diálogo abierto para quedar encima.
 */
let menuFotoAbierto = null;
function menuFoto(ancla, tieneFoto, cosa) {
  if (menuFotoAbierto) menuFotoAbierto(null);
  return new Promise(resolver => {
    const menu = document.createElement('div');
    menu.className = 'menu-foto vidrio';
    menu.setAttribute('role', 'menu');
    menu.innerHTML = `<button type="button" role="menuitem" data-op="cambiar">${tieneFoto ? 'Cambiar' : 'Subir'} ${cosa}</button>`
      + (tieneFoto ? `<button type="button" role="menuitem" class="opcion-peligro" data-op="quitar">Eliminar ${cosa}</button>` : '');
    const dialogo = ancla.closest('dialog[open]');
    (dialogo || document.body).append(menu);
    const r = ancla.getBoundingClientRect(), mw = menu.offsetWidth, mh = menu.offsetHeight;
    // Alineado con la foto tocada y sin salirse del diálogo (ni de la ventana).
    const caja = dialogo ? dialogo.getBoundingClientRect() : { left: 0, right: innerWidth };
    const izq = Math.max(caja.left, 0) + 8, der = Math.min(caja.right, innerWidth) - 8;
    menu.style.left = Math.max(izq, Math.min(der - mw, r.left)) + 'px';
    menu.style.top = (r.bottom + mh + 8 > innerHeight ? Math.max(8, r.top - mh - 6) : r.bottom + 6) + 'px';
    const cerrar = op => {
      menuFotoAbierto = null;
      document.removeEventListener('pointerdown', fuera, true);
      document.removeEventListener('keydown', tecla, true);
      menu.remove();
      resolver(op);
    };
    const fuera = e => { if (!menu.contains(e.target)) cerrar(null); };
    const tecla = e => { if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); cerrar(null); ancla.focus(); } };
    menu.addEventListener('click', e => { const b = e.target.closest('[data-op]'); if (b) cerrar(b.dataset.op); });
    setTimeout(() => {
      document.addEventListener('pointerdown', fuera, true);
      document.addEventListener('keydown', tecla, true);
    });
    menuFotoAbierto = cerrar;
    menu.querySelector('button').focus({ preventScroll: true }); // sin desplazar el diálogo hasta el final
  });
}

/** Cambiar o quitar una foto ya guardada (jugador o escudo) desde donde se toque. */
async function editarFotoGuardada(ancla, tipo, actual, enviar) {
  const op = actual ? await menuFoto(ancla, true, tipo === 'escudo' ? 'escudo' : 'foto') : 'cambiar';
  if (!op) return false;
  let imagen = '';
  if (op === 'cambiar') {
    imagen = await pedirImagen(tipo);
    if (!imagen) return false;
  }
  const r = await enviar(imagen);
  if (!r.ok) { if (r.codigo !== 'LOGIN') mensaje('No se pudo guardar', r.error); return false; }
  aviso(r.mensaje);
  return true;
}

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
  renderBarraDivisiones();
  const divisiones = E.campeonato.divisiones || [];
  const conDivisiones = divisiones.length > 1;
  // Antes de iniciar, el administrador mueve cada equipo a su división con un selector en la tarjeta.
  const moverDivision = e => conDivisiones && !iniciado && !e.retirado ? `<label class="campo campo-division">División
      <select data-division-equipo="${e.id}">${divisiones.map(d => `<option value="${d.indice}" ${d.indice === e.division ? 'selected' : ''}>${esc(d.nombre)}</option>`).join('')}</select></label>` : '';
  const tarjeta = e => `
    <article class="tarjeta-equipo vidrio ${e.retirado ? 'retirado' : ''}">
      ${puedeEditar() && !e.retirado
        ? `<button type="button" class="escudo-boton" data-escudo-equipo="${e.id}" title="Cambiar o quitar el escudo de ${esc(e.nombre)}">${escudoEquipoHtml(e)}</button>`
        : escudoEquipoHtml(e)}
      <div class="equipo-datos">
        <h3>${esc(e.nombre)}${e.retirado ? ' <span class="etiqueta retirado">Retirado</span>' : ''}${marcaMovimiento(e)}</h3>
        <p class="sutil pequeno">${ICONO.persona}DT: ${e.dt ? esc(e.dt.nombre) : 'Sin asignar'} · ${e.jugadores.length === 1 ? '1 jugador' : e.jugadores.length + ' jugadores'}</p>
        <p class="sutil pequeno">${ICONO.estadio}${esc(e.estadio) || 'Sin estadio'}</p>
      </div>
      <div class="equipo-acciones">
        ${moverDivision(e)}
        <button class="btn mini" data-editar-equipo="${e.id}">Editar</button>
        ${e.retirado ? '' : `<button class="btn mini peligro" data-quitar-equipo="${e.id}">${iniciado ? 'Retirar' : 'Eliminar'}</button>`}
      </div>
    </article>`;
  if (!E.equipos.length) {
    $('gridEquipos').innerHTML = vacioHtml('Todavía no hay equipos. Escriba un nombre arriba y pulse «Agregar».');
    return;
  }
  // Constancia del paso de temporada: quién subió y quién bajó.
  const movimientos = E.campeonato.resumenMovimientos
    ? `<p class="sutil aviso-movimientos">Ascensos y descensos de «${esc(E.campeonato.movimientos[0].temporadaAnterior)}»: ${esc(E.campeonato.resumenMovimientos)}</p>` : '';
  // Liga con divisiones: un bloque por división (Liga A, Liga B...).
  $('gridEquipos').classList.toggle('por-divisiones', conDivisiones);
  $('gridEquipos').innerHTML = movimientos + (conDivisiones
    ? divisiones.map(d => {
      const equipos = E.equipos.filter(e => e.division === d.indice);
      return `<section class="bloque-division"><h3>${esc(d.nombre)} <span class="sutil pequeno">${plural(equipos.length, '1 equipo', equipos.length + ' equipos')}</span></h3>
        <div class="grid-equipos">${equipos.map(tarjeta).join('') || '<p class="sutil">Sin equipos en esta división.</p>'}</div></section>`;
    }).join('')
    : E.equipos.map(tarjeta).join(''));
}

/** «↑ Ascendió» o «↓ Descendió» si el equipo empezó esta temporada en otra división. */
function marcaMovimiento(e) {
  const mv = (E.campeonato.movimientos || []).find(x => x.equipo === e.id);
  return mv ? ` <span class="etiqueta ${mv.ascenso ? 'jugado' : 'retirado'}" title="${mv.ascenso ? 'Subió' : 'Bajó'} de la ${esc(mv.desde)} a la ${esc(mv.hasta)}: terminó ${mv.puesto}° en «${esc(mv.temporadaAnterior)}».">${mv.ascenso ? '↑ Ascendió' : '↓ Descendió'}</span>` : '';
}

/** Barra de divisiones (solo administrador, formato Liga): ver, agregar y quitar divisiones antes de iniciar. */
function renderBarraDivisiones() {
  const c = E.campeonato, barra = $('barraDivisiones');
  barra.hidden = !(puedeEditar() && c.esLiga);
  if (barra.hidden) return;
  const divisiones = c.divisiones || [];
  const bloqueada = c.iniciado;
  barra.innerHTML = `<div><b>Divisiones</b>
      <p class="sutil pequeno">${divisiones.length > 1
        ? `Cada división es una liga aparte; el campeón sale de la ${esc(divisiones[0].nombre)}.${c.ascensos ? ` En la siguiente temporada suben y bajan ${c.ascensos} por división.` : ''}`
        : 'Liga de una sola división. Agregue divisiones para tener Liga A, Liga B, Liga C…'}${bloqueada ? ' Ya inició: los cambios de división se hacen en la siguiente temporada.' : ''}</p></div>
    <div class="chips">${divisiones.map(d => `<span class="chip">${esc(d.nombre)} · ${d.equipos}</span>`).join('')}</div>
    <div class="acciones-divisiones">
      <button type="button" class="btn mini" data-division-accion="agregar" ${bloqueada || divisiones.length >= c.maximoDivisiones ? 'disabled' : ''}>+ Agregar división</button>
      <button type="button" class="btn mini fantasma" data-division-accion="quitar" ${bloqueada || divisiones.length <= 1 ? 'disabled' : ''}>Quitar la última</button>
    </div>`;
}

async function accionDivision(ruta, cuerpo) {
  const r = await accion(ruta, cuerpo, { silencioso: true });
  if (!r.ok) { mensaje('Divisiones', r.error); return; }
  aviso(r.mensaje || 'Equipo movido de división.');
}

let equipoEditando = null;
let jugadoresTemp = [];

/** Escudo y foto que se están editando en el diálogo del equipo (se guardan con «Guardar equipo»). */
let escudoTemp = '';
let fotoNuevaJugador = '';

function renderEscudoTemp() {
  const e = { id: equipoEditando ? equipoEditando.id : 0, nombre: $('eqNombre').value.trim() || '?', escudo: escudoTemp };
  $('eqEscudo').innerHTML = escudoEquipoHtml(e, 'grande');
  $('eqEscudo').title = escudoTemp ? 'Cambiar o quitar el escudo' : 'Subir el escudo';
  $('eqSubirEscudo').textContent = escudoTemp ? 'Cambiar escudo' : 'Subir escudo';
}

function renderFotoNueva() {
  $('jgFoto').innerHTML = fotoCedulaHtml(fotoNuevaJugador, $('jgNombre').value.trim() || 'Jugador nuevo', 'mini')
    + `<span>${fotoNuevaJugador ? 'Cambiar foto' : 'Foto'}</span>`;
}

async function tocarEscudoDialogo(ancla) {
  const op = escudoTemp ? await menuFoto(ancla, true, 'escudo') : 'cambiar';
  if (op === 'quitar') escudoTemp = '';
  if (op === 'cambiar') { const img = await pedirImagen('escudo'); if (img) escudoTemp = img; }
  renderEscudoTemp();
}

async function tocarFotoNueva(ancla) {
  const op = fotoNuevaJugador ? await menuFoto(ancla, true, 'foto') : 'cambiar';
  if (op === 'quitar') fotoNuevaJugador = '';
  if (op === 'cambiar') { const img = await pedirImagen('jugador'); if (img) fotoNuevaJugador = img; }
  renderFotoNueva();
}

async function tocarFotoFila(ancla, i) {
  const j = jugadoresTemp[i];
  if (!j) return;
  const op = j.foto ? await menuFoto(ancla, true, 'foto') : 'cambiar';
  if (op === 'quitar') j.foto = '';
  if (op === 'cambiar') { const img = await pedirImagen('jugador'); if (img) j.foto = img; }
  renderJugadores();
}

function abrirEquipo(equipo) {
  equipoEditando = equipo;
  jugadoresTemp = equipo ? equipo.jugadores.map(j => Object.assign({}, j)) : [];
  escudoTemp = equipo ? equipo.escudo || '' : '';
  fotoNuevaJugador = '';
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
  renderEscudoTemp();
  renderFotoNueva();
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
  jugadoresTemp.sort(porLinea); // Portería, Defensa, Mediocampo, Ataque
  $('tablaJugadores').innerHTML = jugadoresTemp.length
    ? jugadoresTemp.map((j, i) => `<tr><td><button type="button" class="foto-boton-fila" data-foto-fila="${i}" title="${j.foto ? 'Cambiar o quitar la foto' : 'Subir la foto'} de ${esc(j.nombre)}">${fotoCedulaHtml(j.foto, j.nombre, 'mini')}</button></td>
        <td class="num"><span class="dorsal">${j.dorsal}</span></td><td>${esc(j.nombre)}</td><td>${esc(j.documento)}</td>
        <td>${esc(j.posicion)}</td><td>${(j.amarillas ? tarjetaHtml('AMARILLA', `<span class="solo-lector">Amarillas: </span>${j.amarillas} `) : '') + (j.rojas ? tarjetaHtml('ROJA', `<span class="solo-lector">Rojas: </span>${j.rojas} `) : '')
        + (j.pendientes ? `<span class="etiqueta suspendido">Suspendido ${j.pendientes}</span>` : '')}</td>
        <td class="acciones-fila"><button type="button" class="btn mini peligro" data-quitar-jugador="${i}">Quitar</button></td></tr>`).join('')
    : '<tr><td colspan="7" class="vacia">Sin jugadores todavía.</td></tr>';
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
  jugadoresTemp.push({ id: 0, nombre, documento: $('jgDocumento').value.trim(), dorsal, posicion: $('jgPosicion').value, foto: fotoNuevaJugador });
  fotoNuevaJugador = '';
  renderJugadores();
  limpiarFormJugador();
  renderFotoNueva();
  $('jgNombre').focus();
}

async function guardarEquipo(e) {
  e.preventDefault();
  const r = await accion('equipo', {
    id: equipoEditando ? equipoEditando.id : null,
    nombre: $('eqNombre').value, estadio: $('eqEstadio').value,
    dt: { nombre: $('dtNombre').value, documento: $('dtDocumento').value, nacionalidad: $('dtNacionalidad').value },
    jugadores: jugadoresTemp.map(({ id, nombre, documento, dorsal, posicion, foto }) => ({ id, nombre, documento, dorsal, posicion, foto: foto || '' })),
    escudo: escudoTemp
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
  mostrarPestanaPartidos(pestanaPartidos); // marca la pestaña activa y dibuja la lista
}

/** Pestaña de «Gestionar partidos» (administrador): «previa» o «resultados». Se conserva al redibujar. */
let pestanaPartidos = 'previa';

function mostrarPestanaPartidos(nombre) {
  pestanaPartidos = nombre;
  document.querySelectorAll('[data-pp]').forEach(b => {
    const activa = b.dataset.pp === nombre;
    b.classList.toggle('activa', activa);
    b.setAttribute('aria-selected', String(activa));
    b.tabIndex = activa ? 0 : -1;
  });
  dibujarListaPartidos();
}

function dibujarListaPartidos() {
  const fase = $('filtroFase').value, estado = $('filtroEstado').value, equipo = $('filtroEquipo').value;
  const admin = puedeEditar(), previa = admin && pestanaPartidos === 'previa';
  // Previa: solo los partidos que todavía no se juegan (el filtro de estado no aplica).
  $('pestanasPartidos').hidden = !admin;
  $('filtroEstado').hidden = previa;
  $('btnRapido').hidden = !admin || previa;
  $('ayudaPestanaPartidos').hidden = !admin;
  $('ayudaPestanaPartidos').textContent = previa
    ? 'Partidos del fixture que todavía no se juegan. Defina fecha, hora, sede, formación y XI titular de cada equipo.'
    : 'Elija el partido que se jugó (o se está jugando) para cargar el marcador o el W.O., los goles, las tarjetas y los cambios.';
  const faseYEquipo = p => (fase === 'todas' || p.fase === Number(fase)) &&
    (equipo === 'todos' || p.local === Number(equipo) || p.visitante === Number(equipo));
  $('cuentaPrevia').textContent = E.partidos.filter(p => !p.jugado && faseYEquipo(p)).length;
  const lista = E.partidos.filter(p => faseYEquipo(p) && (previa ? !p.jugado
    : estado === 'todos' || (estado === 'pendientes' && !p.jugado) || (estado === 'jugados' && p.jugado) ||
      (estado === 'aplazados' && (p.estado === 'APLAZADO' || p.estado === 'SUSPENDIDO'))));
  const cont = $('listaPartidos');
  if (!lista.length) {
    cont.innerHTML = vacioHtml(!E.campeonato.iniciado ? 'El fixture se genera automáticamente al pulsar «Iniciar campeonato».'
      : previa && !E.partidos.some(p => !p.jugado) ? 'Todos los partidos ya se jugaron.'
      : previa ? 'No hay partidos por jugar con estos filtros.' : 'No hay partidos con estos filtros.');
    return;
  }
  const dibujar = cargaRapida && !previa ? htmlPartidoRapido : p => htmlPartido(p, previa);
  const bloques = new Map();
  for (const p of lista) {
    const titulo = contextoPartido(p);
    if (!bloques.has(titulo)) bloques.set(titulo, []);
    bloques.get(titulo).push(p);
  }
  cont.innerHTML = [...bloques].map(([titulo, ps]) => `
    <section class="bloque-ronda"><h3>${esc(titulo)}</h3><div class="partidos">${ps.map(dibujar).join('')}</div></section>`).join('');
}

/** Escudo pequeño junto al nombre del equipo en la fila del partido. */
const escudoFila = (id, nombre) => escudoEquipoHtml(E.equipos.find(e => e.id === id) || { id, nombre }, 'mini');

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
      <span class="local ${p.ganador === p.local ? 'ganador' : ''}">${escudoFila(p.local, p.localNombre)}<span class="nombre-equipo">${esc(p.localNombre)}</span></span>
      <span class="rapido-goles">
        <input type="number" min="0" max="99" inputmode="numeric" placeholder="–" value="${valor(p.gl)}" data-lado="gl" aria-label="Goles de ${esc(p.localNombre)}" ${editable ? '' : 'disabled'}>
        <span aria-hidden="true">:</span>
        <input type="number" min="0" max="99" inputmode="numeric" placeholder="–" value="${valor(p.gv)}" data-lado="gv" aria-label="Goles de ${esc(p.visitanteNombre)}" ${editable ? '' : 'disabled'}>
      </span>
      <span class="visitante ${p.ganador === p.visitante ? 'ganador' : ''}">${escudoFila(p.visitante, p.visitanteNombre)}<span class="nombre-equipo">${esc(p.visitanteNombre)}</span></span>
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
      abrirPartido(id, false);
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

const contextoPartido = p => [E.fases[p.fase].nombre, p.grupoNombre || (p.grupo ? 'Grupo ' + p.grupo : ''), p.nombreRonda].filter(Boolean).join(' · ');

/** Resumen corto de lo que pasó en el partido: «2 cambios · 3 amarillas · 1 roja». */
function incidenciasTexto(p) {
  const am = p.tarjetas.filter(t => t.tipo !== 'ROJA').length, ro = p.tarjetas.length - am, cb = (p.cambios || []).length;
  return [cb ? plural(cb, '1 cambio', cb + ' cambios') : '', am ? plural(am, '1 amarilla', am + ' amarillas') : '',
    ro ? plural(ro, '1 roja', ro + ' rojas') : ''].filter(Boolean).join(' · ');
}

const conMarcador = p => p.jugado || p.estado === 'EN_JUEGO';

/** Fichas de la previa: formación y XI de cada equipo («4-4-2 · XI 11/11» o «Sin formación · XI 3/11»). */
function fichasPreviaHtml(p) {
  let todoListo = true;
  const fichas = [[p.local, p.titularesLocal, p.formacionLocal], [p.visitante, p.titularesVisitante, p.formacionVisitante]].map(([idEq, xi, formacion]) => {
    const necesarios = necesariosXI(idEq), elegidos = (xi || []).length;
    const listo = necesarios > 0 && elegidos === necesarios;
    todoListo = todoListo && listo && !!formacion;
    return `<span class="etiqueta ${listo ? 'jugado' : 'pendiente'}">${esc(formacion || 'Sin formación')} · XI ${elegidos}/${necesarios}</span>`;
  }).join('');
  return `<span class="fichas-previa">${fichas}<small class="sutil">${todoListo ? 'Previa lista' : 'Previa incompleta'}</small></span>`;
}

function htmlPartido(p, previa = false) {
  const marcador = conMarcador(p)
    ? `<b>${p.gl}</b><i>:</i><b>${p.gv}</b>${p.penales ? `<small>${p.pl}-${p.pv} pen</small>` : ''}` : '<span class="vs">VS</span>';
  const incidencias = conMarcador(p) && !previa ? incidenciasTexto(p) : '';
  const titulo = !puedeEditar() ? 'Ver la previa, el cara a cara, la racha y las alineaciones'
    : previa ? 'Preparar la previa: fecha, sede, formación y XI' : 'Registrar o editar el resultado';
  return `<button class="partido vidrio ${p.jugado ? 'jugado' : ''} ${p.estado === 'EN_JUEGO' ? 'en-vivo' : ''}" data-partido="${p.id}" title="${titulo}">
      ${metaPartido(p)}
      <span class="local ${p.ganador === p.local ? 'ganador' : ''}">${escudoFila(p.local, p.localNombre)}<span class="nombre-equipo">${esc(p.localNombre)}</span></span>
      <span class="resultado">${marcador}</span>
      <span class="visitante ${p.ganador === p.visitante ? 'ganador' : ''}">${escudoFila(p.visitante, p.visitanteNombre)}<span class="nombre-equipo">${esc(p.visitanteNombre)}</span></span>
      ${p.sede || p.obs ? `<span class="obs">${esc([p.sede, p.obs].filter(Boolean).join(' · '))}</span>` : ''}
      ${incidencias ? `<span class="incidencias">${esc(incidencias)}</span>` : ''}
      ${previa ? fichasPreviaHtml(p) : ''}
    </button>`;
}

let partidoEditando = null;
let tarjetasTemp = [];
let cambiosTemp = [];
/** XI inicial que se está editando: id del equipo → Set con los ids de los titulares. */
let titularesTemp = new Map();
// Los límites del partido (titulares y minuto máximo) los da el servicio en el catálogo: son de ReglasPartido.
const reglasPartido = () => catalogo.reglasPartido;

/** Administrador: editor de la previa o del resultado (según la pestaña). Visitante: detalle del partido. */
function abrirSegunRol(id) {
  if (!puedeEditar()) abrirDetalle(id);
  else if (pestanaPartidos === 'previa') abrirPartido(id, true);
  else abrirResultado(id);
}

/** Lo que le falta a la previa: XI completo y formación de cada equipo, fecha y hora, y sede. [] = completa. */
function faltantesPrevia(p) {
  const lados = [[p.local, p.localNombre, p.titularesLocal, p.formacionLocal], [p.visitante, p.visitanteNombre, p.titularesVisitante, p.formacionVisitante]];
  const falta = [];
  for (const [idEq, nombre, xi] of lados) {
    const necesarios = necesariosXI(idEq);
    if (necesarios > 0 && (xi || []).length !== necesarios) falta.push('el XI titular de ' + nombre);
  }
  for (const [, nombre, , formacion] of lados) if (!formacion) falta.push('la formación de ' + nombre);
  if (!fechaDefinida(p) || !p.hora) falta.push('la fecha y la hora');
  if (!String(p.sede || '').trim()) falta.push('la sede');
  return falta;
}

/** Nada de la previa: ni XI, ni formación, ni fecha. */
const previaVacia = p => !(p.titularesLocal || []).length && !(p.titularesVisitante || []).length
  && !p.formacionLocal && !p.formacionVisitante && !fechaDefinida(p);

/** «a, b y c» */
const enumerar = partes => partes.length <= 1 ? partes.join('') : partes.slice(0, -1).join(', ') + ' y ' + partes[partes.length - 1];

/**
 * «Registrar resultados» de un partido sin marcador cuya previa no está completa: se advierte antes de
 * abrir el editor. «Ir a registrar previa» lleva a la pestaña Previa con ese partido abierto; «Continuar de
 * todas formas» abre el resultado (goles y tarjetas usan la plantilla completa). Cerrar con Esc no abre nada.
 */
async function abrirResultado(id) {
  const p = E.partidos.find(x => x.id === id);
  if (!p) return;
  const falta = conMarcador(p) ? [] : faltantesPrevia(p);
  if (falta.length) {
    const texto = previaVacia(p)
      ? 'Aún no ha registrado la previa de este partido (alineaciones, formación y datos logísticos). ¿Está seguro de que desea registrar el resultado directamente?'
      : `La previa de este partido está incompleta: falta ${enumerar(falta)}. ¿Está seguro de que desea registrar el resultado directamente?`;
    const eleccion = await mensaje('⚠ Atención: previa sin registrar', texto,
      { confirmar: true, textoSi: 'Ir a registrar previa', textoNo: 'Continuar de todas formas', advertencia: true });
    if (eleccion === null) return;
    if (eleccion) {
      mostrarPestanaPartidos('previa');
      abrirPartido(id, true);
      return;
    }
  }
  abrirPartido(id, false);
}

/** Paso del editor abierto: true = «Previa del partido», false = «Registrar resultados». */
let editandoPrevia = false;

const TEXTO_ESTADO_EDITOR = { JUGADO: 'Jugado (resultado final)', EN_JUEGO: 'En juego (en vivo)', PENDIENTE: 'Pendiente', APLAZADO: 'Aplazado', SUSPENDIDO: 'Suspendido' };

/**
 * Editor del partido en dos pasos:
 *  - previa = true: fecha, hora, sede, formación y XI titular (lo que se decide antes del pitazo).
 *  - previa = false: marcador o W.O., goles con asistencia, tarjetas y cambios. El XI de la previa ya viene cargado.
 * Los dos envían el partido completo con la misma acción «partido»: lo que no se muestra se reenvía tal
 * como estaba, así ninguno borra lo que guardó el otro. Sin indicar el paso: un partido sin jugar abre su previa.
 */
function abrirPartido(id, previa) {
  const p = E.partidos.find(x => x.id === id);
  if (!p || !puedeEditar()) return;
  partidoEditando = p;
  editandoPrevia = previa == null ? !conMarcador(p) : previa;
  // XI de la previa: se carga primero porque las listas de goles, tarjetas y cambios lo usan.
  titularesTemp = new Map([[p.local, new Set(p.titularesLocal || [])], [p.visitante, new Set(p.titularesVisitante || [])]]);
  const claves = !editandoPrevia ? ['JUGADO', 'EN_JUEGO'] : p.estado === 'EN_JUEGO' ? ['EN_JUEGO'] : ['PENDIENTE', 'APLAZADO', 'SUSPENDIDO'];
  $('ptEstado').innerHTML = claves.map(k => `<option value="${k}">${TEXTO_ESTADO_EDITOR[k]}</option>`).join('');
  // Formación táctica de cada entrenador: se puede elegir en cualquier estado del partido.
  $('fmLocalNombre').textContent = p.localNombre;
  $('fmVisitanteNombre').textContent = p.visitanteNombre;
  $('fmLocal').value = p.formacionLocal || '';
  $('fmVisitante').value = p.formacionVisitante || '';
  $('ptContexto').textContent = (editandoPrevia ? 'Previa del partido · ' : 'Registrar resultado · ') + contextoPartido(p);
  $('ptLocal').textContent = p.localNombre;
  $('ptVisitante').textContent = p.visitanteNombre;
  // Un partido sin jugar abre con el marcador vacío: así nunca se guarda un 0:0 que no ocurrió.
  $('ptEstado').value = claves.includes(p.estado) ? p.estado : claves[0];
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
  golesTemp = (p.goles || []).map(g => Object.assign({}, g));
  $('glEquipo').innerHTML = $('tjEquipo').innerHTML;
  $('glEnContra').checked = false;
  $('glMinuto').value = '';
  llenarJugadoresGol();
  renderGolesTemp();
  cambiosTemp = (p.cambios || []).map(x => Object.assign({}, x));
  renderXI(true); // al abrir el partido se construye la lista; luego solo se actualiza
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
  $('ptPenales').hidden = editandoPrevia || !(p.eliminatorio && p.pierna !== 1);
  $('ptError').hidden = true;
  // Cada paso muestra solo lo suyo.
  $('ptLed').hidden = editandoPrevia;
  $('ptVs').hidden = !editandoPrevia;
  $('ptAyudaPrevia').hidden = !editandoPrevia;
  $('ptFilaEstado').hidden = editandoPrevia && p.estado === 'EN_JUEGO';
  $('ptProgramacion').hidden = !editandoPrevia;
  ['ptGoles', 'ptTarjetas', 'ptCambios', 'ptResumenPrevia'].forEach(x => { $(x).hidden = editandoPrevia; });
  ['ptWoLocal', 'ptWoVisitante'].forEach(x => { $(x).hidden = editandoPrevia; });
  if (editandoPrevia) $('ptBorrar').hidden = true;
  // En «Registrar resultados» la alineación de la previa queda plegada si ya está completa.
  const xiCompleto = [p.local, p.visitante].every(xiListo);
  plegarAlineacion(!editandoPrevia && xiCompleto);
  $('ptSinXI').hidden = editandoPrevia || xiCompleto;
  abrirEditorVotacion(p); // [REQ-07] cuánto dura la votación del público
  actualizarResumenPrevia();
  actualizarFormPartido();
  $('dlgPartido').showModal();
}

/** El XI de un equipo está completo (11, o toda la plantilla si tiene menos). */
const xiListo = idEq => necesariosXI(idEq) > 0 && titularesTemp.get(idEq).size === necesariosXI(idEq);

function plegarAlineacion(plegada) {
  $('ptAlineacion').hidden = plegada;
  $('ptPlegar').textContent = plegada ? 'Corregir alineación' : 'Ocultar';
  $('ptPlegar').setAttribute('aria-expanded', String(!plegada));
}

/** «Local: 4-4-2 · XI 11/11» y «Visitante: sin formación · XI 0/11», uno por línea. */
function actualizarResumenPrevia() {
  const p = partidoEditando;
  if (!p) return;
  $('ptResumenTexto').textContent = [[p.local, p.localNombre, 'fmLocal'], [p.visitante, p.visitanteNombre, 'fmVisitante']]
    .map(([idEq, nombre, sel]) => {
      const necesarios = necesariosXI(idEq);
      return `${nombre}: ${$(sel).value || 'sin formación'} · XI ${necesarios ? titularesTemp.get(idEq).size + '/' + necesarios : 'sin plantilla'}`;
    }).join('\n'); // un equipo por línea (.resumen-previa p usa white-space: pre-line)
}

/** Para goles y tarjetas: primero los titulares de la previa (marcados), después los suplentes. */
function convocadosDe(idEq) {
  const xi = titularesTemp.get(idEq) || new Set();
  return plantillaDe(idEq).slice().sort((a, b) => (xi.has(a.id) ? 0 : 1) - (xi.has(b.id) ? 0 : 1));
}
const textoConvocado = (idEq, j) => `#${j.dorsal} ${esc(j.nombre)}${j.posicion ? ' · ' + esc(j.posicion) : ''}${(titularesTemp.get(idEq) || new Set()).has(j.id) ? ' · titular' : ''}`;

function actualizarFormPartido() {
  const estado = $('ptEstado').value;
  const jugado = estado === 'JUGADO', enJuego = estado === 'EN_JUEGO';
  ['ptGL', 'ptGV'].forEach(id => { $(id).disabled = !(jugado || enJuego); });
  $('ptHayPenales').disabled = !jugado;
  $('ptGuardar').textContent = editandoPrevia ? 'Guardar previa' : enJuego ? 'Guardar en vivo'
    : partidoEditando && partidoEditando.jugado ? 'Guardar resultado' : 'Registrar resultado';
  // Tarjetas y cambios se anotan mientras se juega («En juego») o después, con el resultado final.
  $('ptTarjetas').disabled = !(jugado || enJuego);
  $('ptGoles').disabled = !(jugado || enJuego);
  $('ptCambios').disabled = !(jugado || enJuego);
  $('tjFilaFechas').hidden = $('tjTipo').value !== 'ROJA';
  const conPenales = jugado && $('ptHayPenales').checked;
  $('ptPL').disabled = !conPenales;
  $('ptPV').disabled = !conPenales;
}

function llenarJugadoresTarjeta() {
  const idEq = Number($('tjEquipo').value), jugadores = convocadosDe(idEq);
  $('tjJugador').innerHTML = jugadores.length
    ? jugadores.map(j => `<option value="${j.id}">${textoConvocado(idEq, j)}</option>`).join('')
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
  if (!(minuto >= 0 && minuto <= reglasPartido().minutoMaximo)) { errorPartido(`El minuto de la tarjeta debe estar entre 1 y ${reglasPartido().minutoMaximo} (o vacío si no se sabe).`); $('tjMinuto').focus(); return; }
  const delJugador = tarjetasTemp.filter(t => t.jugador === jugador.id);
  // Las confirmaciones se abren encima del partido, sin cerrarlo: el foco vuelve a «Agregar» al responder.
  if (partidoEditando.suspendidos.some(t => t.jugador === jugador.id)) {
    const seguir = await mensaje('Jugador suspendido', `${jugador.nombre} estaba suspendido para este partido. ¿Registrar la tarjeta de todas formas?`, { confirmar: true, textoSi: 'Registrar tarjeta' });
    if (!seguir) return;
  }
  if (tipo === 'AMARILLA' && delJugador.some(t => t.tipo === 'AMARILLA')) {
    const roja = await mensaje('Doble amarilla', `${jugador.nombre} ya tiene una amarilla en este partido. ¿Registrar la segunda como ROJA (expulsión, 1 fecha)?`, { confirmar: true, textoSi: 'Sí, roja' });
    if (roja) { tipo = 'ROJA'; fechas = 1; }
  }
  tarjetasTemp.push({ equipo: idEquipo, jugador: jugador.id, nombre: jugador.nombre, dorsal: jugador.dorsal, tipo, fechas: tipo === 'ROJA' ? fechas : 0, minuto });
  renderTarjetasTemp();
  $('tjAgregar').focus();
}

/* ---- goles por jugador (alimentan goleadores y asistencias) ---- */

let golesTemp = [];

function llenarJugadoresGol() {
  const p = partidoEditando, idEq = Number($('glEquipo').value), contra = $('glEnContra').checked;
  // Un autogol lo marca un jugador del rival; sin asistencia.
  const idAutor = contra ? (idEq === p.local ? p.visitante : p.local) : idEq;
  const autores = convocadosDe(idAutor);
  const opcion = idEquipo => j => `<option value="${j.id}">${textoConvocado(idEquipo, j)}</option>`;
  $('glAutor').innerHTML = autores.length ? autores.map(opcion(idAutor)).join('') : '<option value="">(sin jugadores registrados)</option>';
  $('glAsistente').innerHTML = '<option value="0">(sin asistencia)</option>' + (contra ? '' : convocadosDe(idEq).map(opcion(idEq)).join(''));
  $('glAsistente').disabled = contra;
}

function renderGolesTemp() {
  golesTemp.sort(porMinuto);
  $('glLista').innerHTML = golesTemp.map((g, i) => {
    const equipo = equipoPorId(g.equipo);
    return `<li><span>${g.minuto ? `<b class="minuto">${minutoTexto(g.minuto)}</b>` : ''}${ICONO.gol}#${g.autorDorsal} ${esc(g.autorNombre)}${g.enContra ? ' (en contra)' : ''}
      ${g.asistente ? ` · asistencia de #${g.asistenteDorsal} ${esc(g.asistenteNombre)}` : ''} · ${esc(equipo ? equipo.nombre : '')}</span>
      <button type="button" class="btn mini peligro" data-quitar-gol="${i}">Quitar</button></li>`;
  }).join('') || '<li class="sutil"><span>Sin goles registrados por jugador.</span></li>';
}

function agregarGol() {
  const p = partidoEditando, idEq = Number($('glEquipo').value), contra = $('glEnContra').checked;
  const equipoAutor = equipoPorId(contra ? (idEq === p.local ? p.visitante : p.local) : idEq);
  const autor = equipoAutor && equipoAutor.jugadores.find(j => j.id === Number($('glAutor').value));
  if (!autor) { errorPartido('Ese equipo no tiene jugadores registrados. Agréguelos en la sección Equipos.'); return; }
  const minuto = $('glMinuto').value.trim() === '' ? 0 : parseInt($('glMinuto').value, 10);
  if (!(minuto >= 0 && minuto <= reglasPartido().minutoMaximo)) { errorPartido(`El minuto del gol debe estar entre 1 y ${reglasPartido().minutoMaximo} (o vacío si no se sabe).`); $('glMinuto').focus(); return; }
  const asistente = contra ? null : (equipoPorId(idEq).jugadores.find(j => j.id === Number($('glAsistente').value)) || null);
  errorPartido('');
  golesTemp.push({
    equipo: idEq, minuto, enContra: contra, autor: autor.id, autorNombre: autor.nombre, autorDorsal: autor.dorsal,
    asistente: asistente ? asistente.id : 0, asistenteNombre: asistente ? asistente.nombre : '', asistenteDorsal: asistente ? asistente.dorsal : 0
  });
  renderGolesTemp();
  $('glMinuto').value = '';
  $('glMinuto').focus();
}

/* ---- cambios del partido ---- */

/** Quién ya salió y quién ya entró en los cambios anotados de un equipo. */
function movimientos(idEquipo) {
  const delEquipo = cambiosTemp.filter(x => x.equipo === idEquipo);
  return { salieron: new Set(delEquipo.map(x => x.sale)), entraron: new Set(delEquipo.map(x => x.entra)) };
}

const plantillaDe = idEquipo => {
  const e = equipoPorId(idEquipo);
  return e ? e.jugadores.filter(j => j.id).sort(porLinea) : [];
};
/** Cuántos titulares necesita un equipo: 11, o toda la plantilla si tiene menos. */
const necesariosXI = idEquipo => Math.min(reglasPartido().titulares, plantillaDe(idEquipo).length);

/**
 * XI inicial. La lista se construye UNA vez al abrir el partido (construir = true); después solo
 * se actualizan las casillas que ya existen. Antes se volvía a escribir todo con innerHTML en cada
 * clic: la casilla con el foco desaparecía y la lista (que tiene su propio scroll) volvía arriba.
 */
function renderXI(construir = false) {
  const p = partidoEditando;
  const cont = $('xiEquipos');
  if (!construir && cont.dataset.partido === String(p.id)) { actualizarXI(); return; }
  cont.dataset.partido = String(p.id);
  cont.innerHTML = [[p.local, p.localNombre], [p.visitante, p.visitanteNombre]].map(([idEq, nombre]) => {
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

/** Pone cada casilla, el contador y los botones en su estado actual sin crear ni quitar elementos. */
function actualizarXI() {
  const p = partidoEditando;
  for (const idEq of [p.local, p.visitante]) {
    const elegidos = titularesTemp.get(idEq), necesarios = necesariosXI(idEq);
    const usados = new Set(cambiosTemp.filter(x => x.equipo === idEq).map(x => x.sale));
    const completo = elegidos.size === necesarios;
    document.querySelectorAll(`#xiEquipos [data-xi-equipo="${idEq}"]`).forEach(caja => {
      const id = Number(caja.value), marcado = elegidos.has(id);
      const suspendido = p.suspendidos.some(t => t.jugador === id);
      caja.checked = marcado;
      // No se puede desmarcar a un titular que ya salió en un cambio, ni marcar más de los necesarios.
      caja.disabled = (marcado && usados.has(id)) || (!marcado && (completo || suspendido));
      caja.closest('.xi-jugador').classList.toggle('titular', marcado);
    });
    const cuenta = document.querySelector(`#xiEquipos [data-xi-rapido="${idEq}"]`).previousElementSibling;
    cuenta.textContent = `${elegidos.size}/${necesarios}`;
    cuenta.classList.toggle('jugado', completo);
    cuenta.classList.toggle('pendiente', !completo);
    document.querySelector(`#xiEquipos [data-xi-limpiar="${idEq}"]`).disabled = !(elegidos.size && !usados.size);
  }
  actualizarResumenPrevia();
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
  if (!(minuto >= 1 && minuto <= reglasPartido().minutoMaximo)) { errorPartido(`Escriba el minuto del cambio (entre 1 y ${reglasPartido().minutoMaximo}).`); $('cbMinuto').focus(); return; }
  if (!sale || !entra) { errorPartido('Escoja el jugador que sale y el que entra. Si no aparecen, registre la plantilla en Equipos.'); return; }
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
    ? 'Escriba los goles de los dos equipos para registrar el resultado (o use «En juego» si todavía se está jugando).'
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
    goles: golesTemp.map(g => Object.assign({}, g)),
    cambios: cambiosTemp.map(x => Object.assign({}, x)),
    titularesLocal: [...titularesTemp.get(partidoEditando.local)],
    titularesVisitante: [...titularesTemp.get(partidoEditando.visitante)],
    formacionLocal: $('fmLocal').value, formacionVisitante: $('fmVisitante').value,
    fecha: $('ptFecha').value, hora: $('ptHora').value, sede: $('ptSede').value, obs: $('ptObs').value,
    horasVotacion: horasEditorVotacion()
  };
}

async function guardarPartido(accionPartido) {
  if (accionPartido === 'guardar' && !golesCompletos()) return;
  if (accionPartido === 'guardar' && horasEditorVotacion() === null) {
    errorPartido(`El tiempo de votación debe estar entre ${catalogo.votacion.minimo} y ${catalogo.votacion.maximo} horas.`);
    $('vtHoras').focus();
    return;
  }
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
  aviso(editandoPrevia ? 'Previa guardada: el visitante ya la ve en el detalle del partido.' : 'Partido guardado.');
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
    if (t.descenso && i >= t.filas.length - t.descenso) clase += ' desciende'; // liga con divisiones
    if (i === 0 && f.pj > 0) clase += ' lider';
    return `<tr class="${clase}"><td class="num"><span class="pos">${i + 1}</span></td><th scope="row"><b>${esc(f.nombre)}</b>${f.retirado ? ' <span class="etiqueta retirado">retirado</span>' : ''}</th>
      <td class="num">${f.pj}</td><td class="num extra">${f.g}</td><td class="num extra">${f.e}</td><td class="num extra">${f.p}</td>
      <td class="num extra">${f.gf}</td><td class="num extra">${f.gc}</td><td class="num">${f.dg > 0 ? '+' + f.dg : f.dg}</td>
      <td class="num extra">${f.ta}</td><td class="num extra">${f.tr}</td><td class="num puntos">${f.pts}</td></tr>`;
  }).join('') : '<tr><td colspan="12" class="vacia">Sin equipos.</td></tr>';
  let leyenda = E.leyenda;
  if (t.directos) leyenda += t.division ? ' Verde: asciende.' : ' Verde: clasifica.';
  if (t.descenso) leyenda += ' Rojo: desciende.';
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

/* ---------------------------------------------------------------- estadísticas globales del campeonato */

let pestanaEstadisticas = 'goleadores';

/**
 * Estadísticas de TODO el campeonato: goleadores, asistencias, valla menos vencida y juego limpio.
 * Las de un partido (goles, cambios y tarjetas minuto a minuto) están en su detalle, pestaña «Resumen».
 * Administrador: «Corregir» abre el editor del partido. Visitante: solo lectura («Ver partido» abre el detalle).
 */
function renderEstadisticas() {
  const est = E.estadisticas || { goleadores: [], asistidores: [], goles: [], golesSinAutor: 0, valla: [], fairPlay: [] };
  const admin = puedeEditar();
  $('ayudaEstadisticas').textContent = admin
    ? 'Datos de todo el campeonato. Use «Corregir» en los registros de goles y tarjetas para abrir su partido.'
    : 'Datos de todo el campeonato. Las estadísticas de cada partido están en su detalle: tóquelo en Partidos.';
  document.querySelectorAll('[data-est]').forEach(b => {
    const activa = b.dataset.est === pestanaEstadisticas;
    b.classList.toggle('activa', activa);
    b.setAttribute('aria-selected', String(activa));
  });
  document.querySelectorAll('.panel-estadistica').forEach(panel => { panel.hidden = panel.id !== 'est-' + pestanaEstadisticas; });
  const jugador = f => `<span class="dorsal">${f.dorsal}</span> ${esc(f.nombre)}`;
  const vacia = (columnas, texto) => `<tr><td colspan="${columnas}" class="vacia">${texto}</td></tr>`;
  const pos = i => `<td class="num"><span class="pos">${i + 1}</span></td>`;
  const abrir = id => `<button type="button" class="btn mini fantasma" data-abrir-partido="${id}">${admin ? 'Corregir' : 'Ver partido'}</button>`;

  $('tablaGoleadores').innerHTML = est.goleadores.map((f, i) => `<tr>${pos(i)}<th scope="row">${jugador(f)}</th>
      <td>${esc(f.equipoNombre)}</td><td class="num puntos">${f.goles}</td><td class="num">${f.asistencias}</td></tr>`).join('')
    || vacia(5, 'Todavía no hay goles con autor. Se registran al editar un partido jugado.');
  const sinAutor = est.golesSinAutor;
  $('golesSinAutor').textContent = sinAutor > 0
    ? `${sinAutor === 1 ? '1 gol del marcador todavía no tiene' : sinAutor + ' goles del marcador todavía no tienen'} autor registrado${admin ? ': regístrelos en el editor del partido.' : '.'}` : '';
  $('tablaRegistroGoles').innerHTML = est.goles.map(g => `<tr>
      <td data-label="Partido">${esc(g.partidoTexto)}${g.ronda ? `<br><span class="sutil pequeno">${esc(g.ronda)}</span>` : ''}</td>
      <td data-label="Minuto" class="num">${g.minuto ? minutoTexto(g.minuto) : '–'}</td><td data-label="Equipo">${esc(g.equipoNombre)}</td>
      <td data-label="Gol">#${g.autorDorsal} ${esc(g.autorNombre)}${g.enContra ? ' <span class="sutil">(en contra)</span>' : ''}</td>
      <td data-label="Asistencia">${g.asistente ? `#${g.asistenteDorsal} ${esc(g.asistenteNombre)}` : '—'}</td>
      <td class="acciones-fila">${abrir(g.partido)}</td></tr>`).join('') || vacia(6, 'Sin goles registrados.');

  $('tablaAsistencias').innerHTML = est.asistidores.map((f, i) => `<tr>${pos(i)}<th scope="row">${jugador(f)}</th>
      <td>${esc(f.equipoNombre)}</td><td class="num puntos">${f.asistencias}</td><td class="num">${f.goles}</td></tr>`).join('')
    || vacia(5, 'Todavía no hay asistencias registradas. Se anotan junto con cada gol.');

  $('tablaValla').innerHTML = est.valla.map((f, i) => `<tr>${pos(i)}<th scope="row"><b>${esc(f.nombre)}</b></th>
      <td class="num">${f.pj}</td><td class="num">${f.gc}</td><td class="num puntos">${f.promedio}</td><td class="num">${f.invictas}</td></tr>`).join('')
    || vacia(6, 'Todavía no hay partidos jugados.');

  $('tablaFairPlay').innerHTML = est.fairPlay.map((f, i) => `<tr>${pos(i)}<th scope="row"><b>${esc(f.nombre)}</b></th>
      <td class="num">${f.pj}</td><td class="num">${f.ta}</td><td class="num">${f.tr}</td><td class="num">${f.puntos}</td><td class="num puntos">${f.promedio}</td></tr>`).join('')
    || vacia(7, 'Todavía no hay partidos jugados.');

  renderSanciones();
}

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
      <td data-label="Fechas" class="num">${roja ? r.fechas : '—'}</td><td data-label="Estado">${estado}</td>
      <td class="acciones-fila"><button type="button" class="btn mini fantasma" data-abrir-partido="${r.idPartido}">${puedeEditar() ? 'Corregir' : 'Ver partido'}</button></td></tr>`;
  }).join('') : '<tr><td colspan="8" class="vacia">No hay tarjetas registradas. Se agregan al editar un partido.</td></tr>';
}

/* ---------------------------------------------------------------- resumen de un partido (pestaña del detalle) */

/** Fila «2  Cambios  0» con una barra a cada lado, como en las transmisiones. */
function cifraDueloHtml(titulo, l, v) {
  const total = l + v, pl = total ? Math.round(l / total * 100) : 0;
  return `<div class="cifra-duelo"><b>${l}</b><span>${titulo}</span><b>${v}</b>
    <div class="barra-duelo" aria-hidden="true"><i style="width:${pl}%"></i><i style="width:${total ? 100 - pl : 0}%"></i></div></div>`;
}

/**
 * Estadísticas de ESTE partido: cifras por equipo y los goles, cambios y tarjetas minuto a minuto.
 * (Antes estaban en la vista general de Estadísticas; ahora solo viven en el detalle.)
 */
function resumenPartidoHtml(p) {
  if (!conMarcador(p)) return vacioHtml('El partido todavía no se juega. Aquí aparecerán los goles, los cambios y las tarjetas.');
  const cambios = p.cambios || [], goles = p.goles || [];
  const am = id => p.tarjetas.filter(t => t.equipo === id && t.tipo !== 'ROJA').length;
  const ro = id => p.tarjetas.filter(t => t.equipo === id && t.tipo === 'ROJA').length;
  const cb = id => cambios.filter(x => x.equipo === id).length;
  const eventos = [
    ...goles.map(g => ({ minuto: g.minuto || 0, equipo: g.equipo, html: `${ICONO.gol}<span><b>#${g.autorDorsal} ${esc(g.autorNombre)}</b>${g.enContra ? ' <span class="sutil">(en contra)</span>' : ''}${g.asistente ? `<br><span class="sutil">Asistencia: #${g.asistenteDorsal} ${esc(g.asistenteNombre)}</span>` : ''}</span>` })),
    ...cambios.map(x => ({ minuto: x.minuto, equipo: x.equipo, html: `${ICONO.cambio}<span><span class="entra-txt">Entra #${x.entraDorsal} ${esc(x.entraNombre)}</span><br><span class="sale-txt">Sale #${x.saleDorsal} ${esc(x.saleNombre)}</span></span>` })),
    ...p.tarjetas.map(t => ({ minuto: t.minuto || 0, equipo: t.equipo, html: `${tarjetaHtml(t.tipo, '')}<span>#${t.dorsal} ${esc(t.nombre)}<br><span class="sutil">${t.tipo === 'ROJA' ? `Roja · ${plural(t.fechas, '1 fecha', t.fechas + ' fechas')} fuera` : 'Amarilla'}</span></span>` }))
  ].sort(porMinuto);
  const fila = e => {
    const local = e.equipo === p.local;
    const celda = `<div class="evento"><span class="solo-lector">${esc(local ? p.localNombre : p.visitanteNombre)}: </span>${e.html}</div>`;
    return `<li class="${local ? 'de-local' : 'de-visitante'}">${local ? celda : '<div></div>'}<span class="minuto-eje">${e.minuto ? minutoTexto(e.minuto) : '–'}</span>${local ? '<div></div>' : celda}</li>`;
  };
  return `<div class="ficha-cifras">
      ${cifraDueloHtml('Goles', p.gl, p.gv)}
      ${cifraDueloHtml('Cambios', cb(p.local), cb(p.visitante))}
      ${cifraDueloHtml('Amarillas', am(p.local), am(p.visitante))}
      ${cifraDueloHtml('Rojas', ro(p.local), ro(p.visitante))}
    </div>
    <h3 class="ficha-titulo">Minuto a minuto</h3>
    ${eventos.length ? `<ol class="linea-tiempo">${eventos.map(fila).join('')}</ol>`
      : '<p class="sutil ficha-vacia">No se registraron goles, cambios ni tarjetas en este partido.</p>'}`;
}

/* ---------------------------------------------------------------- detalle del partido */

let detalleActual = null;
let detallePestana = 'resumen';

/** Pide el detalle al servicio (resumen, cara a cara, racha y alineaciones) y lo muestra. Es de solo lectura. */
async function abrirDetalle(id, pestana = 'resumen') {
  const r = await servicio.detalle(id);
  if (!r.ok) {
    if (r.codigo === 'LOGIN') volverAlLogin('Su sesión terminó. Inicie sesión de nuevo.');
    else mensaje('No se pudo abrir el partido', r.error);
    return;
  }
  detalleActual = r;
  detallePestana = pestana;
  const p = r.partido;
  $('dpContexto').textContent = contextoPartido(p);
  $('dpCabecera').innerHTML = cabeceraDetalle(p);
  dibujarDetalle();
  if (!$('dlgDetalle').open) $('dlgDetalle').showModal();
  $('dpCerrar').focus();
}

/** Escudo de la cabecera del detalle: el administrador lo toca para cambiarlo o quitarlo. */
function escudoHtml(id, nombre) {
  const e = equipoPorId(id) || { id, nombre, escudo: '' };
  return puedeEditar()
    ? `<button type="button" class="escudo-boton" data-escudo-detalle="${id}" title="Cambiar o quitar el escudo de ${esc(nombre)}">${escudoEquipoHtml(e)}</button>`
    : escudoEquipoHtml(e);
}

function cabeceraDetalle(p) {
  const marcador = conMarcador(p)
    ? `<span class="goles"><b>${p.gl}</b><i>:</i><b>${p.gv}</b>${p.penales ? `<small>${p.pl}-${p.pv} pen</small>` : ''}</span>`
    : '<span class="goles vs">VS</span>';
  const meta = [fechaDefinida(p) ? fechaTexto(p) : 'Fecha por definir', p.sede].filter(Boolean).map(esc).join(' · ');
  return `<div class="ficha-marcador detalle-marcador">
      <div class="detalle-equipo">${escudoHtml(p.local, p.localNombre)}<span class="equipo-nombre ${p.ganador === p.local ? 'ganador' : ''}">${esc(p.localNombre)}</span></div>
      <div class="detalle-centro">${marcador}<span class="etiqueta ${p.estado.toLowerCase()}">${ESTADOS_TEXTO[p.estado]}</span></div>
      <div class="detalle-equipo">${escudoHtml(p.visitante, p.visitanteNombre)}<span class="equipo-nombre ${p.ganador === p.visitante ? 'ganador' : ''}">${esc(p.visitanteNombre)}</span></div>
    </div>
    <p class="sutil pequeno centro detalle-meta">${meta}</p>`;
}

function dibujarDetalle() {
  document.querySelectorAll('[data-dp]').forEach(b => {
    const activa = b.dataset.dp === detallePestana;
    b.classList.toggle('activa', activa);
    b.setAttribute('aria-selected', String(activa));
  });
  const d = detalleActual, p = d.partido;
  const pestanas = {
    resumen: () => resumenPartidoHtml(p),
    calificaciones: () => calificacionesHtml(d.calificaciones, p),
    caraACara: () => caraACaraHtml(d.caraACara, p),
    racha: () => rachaHtml(d.racha, p),
    alineaciones: () => alineacionesHtml(d.alineaciones, p)
  };
  // En «Alineaciones» el marcador se hace más bajo: así la cancha completa cabe sin desplazarse.
  $('dlgDetalle').classList.toggle('con-cancha', detallePestana === 'alineaciones');
  $('dpPanel').innerHTML = (pestanas[detallePestana] || pestanas.resumen)();
}

/** Cuadro de color con la letra (V/E/D), para no depender solo del color. */
const formaHtml = r => `<span class="forma forma-${esc(r)}" title="${{ V: 'Victoria', E: 'Empate', D: 'Derrota' }[r] || ''}">${esc(r || '?')}</span>`;

function fechaCorta(fecha) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha || '');
  return m ? `${m[3]}/${m[2]}/${m[1]} · ` : '';
}

/** Un partido anterior; en la racha lleva delante el cuadro V/E/D. */
function filaPrevia(r, resultado) {
  const gana = lado => r.ganador && r.ganador.trim().toLowerCase() === String(lado).trim().toLowerCase();
  return `<li class="detalle-fila vidrio">
      ${resultado ? formaHtml(resultado) : ''}
      <div><span class="sutil pequeno">${esc(fechaCorta(r.fecha) + r.competicion + (r.ronda ? ' · ' + r.ronda : ''))}</span>
        <div class="detalle-fila-marcador"><span class="${gana(r.local) ? 'ganador' : ''}">${esc(r.local)}</span>
          <b>${r.gl} - ${r.gv}</b><span class="${gana(r.visitante) ? 'ganador' : ''}">${esc(r.visitante)}</span></div></div></li>`;
}

function caraACaraHtml(h, p) {
  if (!h.partidos.length) return vacioHtml('Estos equipos no se han enfrentado antes.');
  const cifra = (valor, texto, clase) => `<div class="detalle-cifra vidrio ${clase}"><b>${valor}</b><span class="sutil pequeno">${esc(texto)}</span></div>`;
  return `<div class="detalle-cifras">
      ${cifra(h.victoriasLocal, 'Victorias ' + p.localNombre, 'victoria')}
      ${cifra(h.empates, 'Empates', 'empate')}
      ${cifra(h.victoriasVisitante, 'Victorias ' + p.visitanteNombre, 'victoria')}
    </div>
    <h3 class="ficha-titulo">Últimos enfrentamientos</h3>
    <ol class="detalle-lista">${h.partidos.map(r => filaPrevia(r, null)).join('')}</ol>`;
}

function rachaHtml(r, p) {
  const columna = (equipo, partidos) => `<div class="detalle-racha">
      <h3 class="ficha-titulo">${esc(equipo)}</h3>
      ${partidos.length ? `<div class="racha-cuadros">${partidos.map(x => formaHtml(x.resultado)).join('')}</div>
        <ol class="detalle-lista">${partidos.map(x => filaPrevia(x, x.resultado)).join('')}</ol>`
      : '<p class="sutil">Sin partidos jugados todavía.</p>'}
    </div>`;
  return `<div class="detalle-columnas">${columna(p.localNombre, r.local)}${columna(p.visitanteNombre, r.visitante)}</div>`;
}

/* ---------------------------------------------------------------- noticias */

let N = { publicadas: [], pendientes: [], puedeModerar: false };

/** «2026-11-15 18:40» -> «15/11/2026 · 18:40». */
function fechaNoticia(f) {
  const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}:\d{2})$/.exec(f || '');
  return m ? `${m[3]}/${m[2]}/${m[1]} · ${m[4]}` : (f || '');
}

/**
 * Administrador: crea, edita y elimina noticias y modera las propuestas de los visitantes.
 * Visitante: lee, comenta y propone noticias (quedan pendientes hasta que se aprueban).
 */
async function renderNoticias(datos) {
  if (!datos) {
    const r = await servicio.noticias();
    if (!r.ok) { $('listaNoticias').innerHTML = vacioHtml(esc(r.error)); return; }
    datos = r.noticias;
  }
  N = datos;
  const admin = puedeEditar();
  $('ayudaNoticias').textContent = admin
    ? 'Publique noticias oficiales y apruebe o rechace las que envían los visitantes.'
    : 'Noticias del torneo. Puede comentar o proponer una noticia: el administrador la revisa antes de publicarla.';
  $('btnNuevaNoticia').textContent = admin ? '+ Nueva noticia' : 'Proponer una noticia';
  if (admin) {
    // Administrador: «Pendientes por aprobar» y «Publicadas y oficiales» en pestañas separadas.
    $('listaPendientes').innerHTML = N.pendientes.map(n => tarjetaNoticiaHtml(n, true)).join('')
      || vacioHtml('No hay aportes de visitantes por revisar.');
    $('cuentaPendientes').textContent = N.pendientes.length;
    $('cuentaPublicadas').textContent = N.publicadas.length;
    $('tabPendientes').classList.toggle('con-pendientes', N.pendientes.length > 0);
    if (!pestanaNoticias) pestanaNoticias = N.pendientes.length ? 'pendientes' : 'publicadas'; // al entrar: lo que necesita atención
  } else {
    pestanaNoticias = 'publicadas'; // el visitante solo ve el feed público
    $('listaPendientes').innerHTML = ''; // y no queda nada del panel de moderación en la página
  }
  $('listaNoticias').innerHTML = N.publicadas.map(n => tarjetaNoticiaHtml(n, false)).join('')
    || vacioHtml('Todavía no hay noticias publicadas.' + (admin ? ' Use «+ Nueva noticia» para crear la primera.' : ' ¡Proponga la primera!'));
  $('pestanasNoticias').hidden = !admin;
  $('ayudaPublicadas').hidden = !admin;
  mostrarPestanaNoticias(pestanaNoticias);
}

/** «pendientes» o «publicadas» (se conserva al redibujar: al aprobar una, el administrador sigue revisando). */
let pestanaNoticias = null;

function mostrarPestanaNoticias(nombre) {
  pestanaNoticias = nombre;
  document.querySelectorAll('[data-nt]').forEach(b => {
    const activa = b.dataset.nt === nombre;
    b.classList.toggle('activa', activa);
    b.setAttribute('aria-selected', String(activa));
    b.tabIndex = activa ? 0 : -1;
  });
  $('panelPendientes').hidden = nombre !== 'pendientes';
  $('panelPublicadas').hidden = nombre !== 'publicadas';
}

function tarjetaNoticiaHtml(n, pendiente) {
  const admin = puedeEditar(), url = servicio.urlImagen(n.imagen);
  const acciones = admin && pendiente
    ? `<button type="button" class="btn mini primario" data-aprobar="${n.id}">Aprobar</button>
       <button type="button" class="btn mini peligro" data-rechazar="${n.id}">Rechazar y eliminar</button>
       <button type="button" class="btn mini fantasma" data-editar-noticia="${n.id}">Editar antes de publicar</button>`
    : admin ? `<button type="button" class="btn mini fantasma" data-editar-noticia="${n.id}">Editar</button>
       <button type="button" class="btn mini peligro" data-eliminar-noticia="${n.id}">Eliminar</button>` : '';
  const comentarios = pendiente ? '' : `<section class="comentarios">
      <h4>Comentarios (${n.comentarios.length})</h4>
      <ul class="lista-comentarios">${n.comentarios.map(c => `<li><div><span class="sutil pequeno">${esc(c.autor)} · ${esc(fechaNoticia(c.fecha))}</span><p>${esc(c.texto)}</p></div>
        ${admin ? `<button type="button" class="btn mini peligro" data-quitar-comentario="${c.id}" data-noticia="${n.id}">Quitar</button>` : ''}</li>`).join('')}</ul>
      <form class="form-comentario" data-comentar="${n.id}">
        <input name="autor" maxlength="40" placeholder="${admin ? 'Administración' : 'Su nombre (opcional)'}" aria-label="Su nombre">
        <input name="texto" maxlength="500" required placeholder="Escriba un comentario…" aria-label="Comentario">
        <button class="btn">Comentar</button>
      </form>
    </section>`;
  return `<article class="tarjeta-noticia vidrio ${url ? 'con-foto' : ''}">
      ${url ? `<img class="noticia-foto" src="${esc(url)}" alt="Foto de la noticia: ${esc(n.titulo)}" loading="lazy">` : ''}
      <div class="noticia-contenido">
        <h3>${esc(n.titulo)}</h3>
        <p class="sutil pequeno noticia-meta">Por ${esc(n.autor)} · ${esc(fechaNoticia(n.fecha))}${pendiente ? ' <span class="etiqueta pendiente">Pendiente de aprobación</span>'
          : n.deVisitante ? ` <span class="chip">${admin ? 'Aporte de visitante · aprobado' : 'Aporte de visitante'}</span>`
          : admin ? ' <span class="chip">Oficial</span>' : ''}</p>
        <p class="noticia-cuerpo">${esc(n.cuerpo)}</p>
        ${acciones ? `<div class="acciones-izq">${acciones}</div>` : ''}
      </div>
      ${comentarios}
    </article>`;
}

async function accionNoticia(ruta, cuerpo) {
  const r = await servicio.ejecutar(ruta, cuerpo);
  if (!r.ok) {
    if (r.codigo === 'LOGIN') volverAlLogin('Su sesión terminó. Inicie sesión de nuevo.');
    else mensaje('Noticias', r.error);
    return r;
  }
  renderNoticias(r.noticias);
  aviso(r.mensaje);
  return r;
}

/* ---- crear, editar o proponer una noticia ---- */

let noticiaEditando = null, noticiaPropuesta = false, fotoNueva = '', quitarFoto = false;

function abrirNoticia(noticia, propuesta) {
  noticiaEditando = noticia;
  noticiaPropuesta = propuesta;
  fotoNueva = '';
  quitarFoto = false;
  $('ntTitulo').textContent = propuesta ? 'Proponer una noticia' : noticia ? 'Editar noticia' : 'Nueva noticia';
  $('ntGuardar').textContent = propuesta ? 'Enviar para aprobación' : noticia ? 'Guardar y publicar' : 'Publicar';
  $('ntTituloNoticia').value = noticia ? noticia.titulo : '';
  $('ntCuerpo').value = noticia ? noticia.cuerpo : '';
  $('ntAutor').value = '';
  $('ntFilaAutor').hidden = !propuesta;
  $('ntAvisoPropuesta').hidden = !propuesta;
  $('ntArchivo').value = '';
  mostrarFotoNoticia(noticia && noticia.imagen ? servicio.urlImagen(noticia.imagen) : '');
  $('ntError').hidden = true;
  $('dlgNoticia').showModal();
  $('ntTituloNoticia').focus();
}

function mostrarFotoNoticia(url) {
  $('ntVista').hidden = !url;
  if (url) $('ntVista').src = url; else $('ntVista').removeAttribute('src');
  $('ntSinFoto').hidden = !!url;
  $('ntQuitarFoto').disabled = !url;
}

/**
 * Reduce la foto en el navegador (máximo 1280 px, JPEG) antes de enviarla: así no llena el
 * almacenamiento del navegador ni pesa en la petición. Devuelve una «data URL».
 */
function reducirFoto(archivo) {
  return new Promise((resolver, rechazar) => {
    if (!/^image\/(jpeg|png|gif|webp)$/.test(archivo.type)) { rechazar(new Error('Escoja una imagen JPG, PNG, GIF o WEBP.')); return; }
    if (archivo.size > 15 * 1024 * 1024) { rechazar(new Error('La foto es muy pesada (máximo 15 MB antes de reducirla).')); return; }
    const url = URL.createObjectURL(archivo), img = new Image();
    img.onload = () => {
      const escala = Math.min(1, 1280 / Math.max(img.width, img.height));
      const lienzo = document.createElement('canvas');
      lienzo.width = Math.round(img.width * escala);
      lienzo.height = Math.round(img.height * escala);
      lienzo.getContext('2d').drawImage(img, 0, 0, lienzo.width, lienzo.height);
      URL.revokeObjectURL(url);
      resolver(lienzo.toDataURL('image/jpeg', 0.82));
    };
    img.onerror = () => { URL.revokeObjectURL(url); rechazar(new Error('No se pudo leer esa imagen.')); };
    img.src = url;
  });
}

async function alElegirFoto() {
  const archivo = $('ntArchivo').files[0];
  if (!archivo) return;
  try {
    fotoNueva = await reducirFoto(archivo);
    quitarFoto = false;
    mostrarFotoNoticia(fotoNueva);
    $('ntError').hidden = true;
  } catch (e) {
    $('ntError').textContent = e.message;
    $('ntError').hidden = false;
  }
}

async function guardarNoticia(e) {
  e.preventDefault();
  const cuerpo = { titulo: $('ntTituloNoticia').value, cuerpo: $('ntCuerpo').value, autor: $('ntAutor').value, imagen: fotoNueva, quitarImagen: quitarFoto };
  if (noticiaEditando) cuerpo.id = noticiaEditando.id;
  const r = await servicio.ejecutar(noticiaPropuesta ? 'noticia/proponer' : 'noticia/guardar', cuerpo);
  if (!r.ok) { $('ntError').textContent = r.error; $('ntError').hidden = false; return; }
  $('dlgNoticia').close();
  renderNoticias(r.noticias);
  if (noticiaPropuesta) mensaje('Noticia enviada', r.mensaje); else aviso(r.mensaje);
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
  // Al cambiar el formato de una liga se conservan sus divisiones.
  $('nvDivisiones').value = cambiar && E.campeonato.esLiga ? E.campeonato.divisiones.length : (cfg.divisiones || 1);
  $('nvAscensos').value = cambiar && E.campeonato.esLiga ? E.campeonato.ascensos : (cfg.ascensos == null ? 2 : cfg.ascensos);
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
    granFinalConReinicio: $('nvReinicio').checked, criterios: editorNuevo.obtener(),
    divisiones: formatoElegido === 'LIGA' ? Math.max(1, Math.min(6, num('nvDivisiones', 1))) : 1,
    ascensos: Math.max(0, num('nvAscensos', 2))
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
  if (mismos && r.mensaje) { // los mismos equipos de una liga terminada: se aplicaron los ascensos y descensos
    mensaje('Ascensos y descensos', `Se creó el campeonato. Se aplicaron los ascensos y descensos de la temporada anterior.\n\n${r.mensaje}`);
    return;
  }
  aviso(mismos ? `Campeonato creado con ${E.equipos.length} equipos. Los demás siguen en «Mis campeonatos».` : 'Campeonato creado. Ahora registre los equipos.');
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
        abrirNuevo(false); // uno más: el actual sigue en curso y está en «Mis campeonatos»
        break;
      case 'siguiente': {
        const c = E.campeonato, liga = c.esLiga, conDivisiones = (c.divisiones || []).length > 1;
        // Nombre sugerido: «Apertura» -> «Clausura»; en una liga, sube el número final («Temporada 1» -> «Temporada 2»).
        const sugerido = liga ? (/\d+$/.test(c.nombre) ? c.nombre.replace(/\d+$/, n => String(Number(n) + 1)) : c.nombre + ' 2')
          : c.nombre.replace('Apertura', 'Clausura');
        const texto = liga
          ? `Se creará la nueva temporada con los mismos equipos, plantillas y reglas.${conDivisiones && c.ascensos ? `\nSuben los ${c.ascensos} primeros y bajan los ${c.ascensos} últimos de cada división.` : ''}\nNombre de la nueva temporada:`
          : 'Se creará un torneo nuevo con los mismos equipos, plantillas y reglas.\nNombre del nuevo torneo:';
        const nombreNuevo = await mensaje(liga ? 'Nueva temporada' : 'Siguiente torneo corto', texto, { entrada: sugerido });
        if (!nombreNuevo) return;
        const r = await accion('siguiente', { nombre: nombreNuevo }, { silencioso: true });
        if (!r.ok) { mensaje('Error', r.error); return; }
        mostrarVista('equipos');
        if (r.mensaje) mensaje('Ascensos y descensos', `Se creó «${nombreNuevo}». El torneo anterior quedó en el historial.\n\n${r.mensaje}`);
        else aviso(`Se creó «${nombreNuevo}». El torneo anterior quedó en el historial.`);
        break;
      }
      case 'cambiarFormato': {
        const texto = E.campeonato.iniciado
          ? 'Se conservarán los equipos con su DT y jugadores, pero se borrarán el fixture y todos los resultados del campeonato actual. ¿Continuar?'
          : 'Puede escoger otro formato y otras reglas. Los equipos registrados se conservan. ¿Continuar?';
        if (await mensaje('Cambiar formato', texto, { confirmar: true, textoSi: 'Continuar' })) abrirNuevo(false, true);
        break;
      }
      case 'terminar':
        await terminarCampeonato({ codigo: E.campeonato.codigo, nombre: E.campeonato.nombre, estado: E.campeonato.terminado ? 'Terminado' : 'En curso' });
        break;
      case 'cancelar':
        await eliminarCampeonato({ codigo: E.campeonato.codigo, nombre: E.campeonato.nombre });
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
        await cerrarSesion();
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

/** Botón principal con el campeonato terminado: en una liga o torneo corto sigue la temporada siguiente. */
const textoTerminado = c => (c.puedeSiguiente ? 'Iniciar la siguiente temporada' : 'Terminar e iniciar otro');

async function avanzar() {
  if (E.campeonato.terminado) {
    if (E.campeonato.puedeSiguiente) { accionMenu('siguiente'); return; } // aplica los ascensos y descensos
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
    aviso(`El campeonato quedó guardado en el historial. Use «${textoTerminado(E.campeonato)}» para el siguiente.`);
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
  // Liga con divisiones: el campeón de cada división por separado (Liga A, Liga B, Liga C...).
  const porDivision = E.campeonesDivision || [];
  $('cpDivisiones').hidden = porDivision.length < 2;
  if (porDivision.length > 1) {
    $('cpTorneo').textContent = `Campeón de la ${porDivision[0].division} · ${E.campeonato.nombre}`;
    $('cpDivisiones').innerHTML = '<h3>Campeones por división</h3><ul>' + porDivision.map(c => `<li>
        <span class="division">${esc(c.division)}</span><span class="equipo"><b>${esc(c.nombre)}</b><small>DT: ${esc(c.dt)}</small></span>
        <span class="cifras">${c.pts} pts · ${c.pj} PJ · ${c.g}G ${c.e}E ${c.p}P</span></li>`).join('') + '</ul>';
  }
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
  // Al salir del correo se explica qué regla no cumple (el mismo texto que da el servicio al crear la cuenta).
  $('regCorreo').addEventListener('blur', () => {
    if ($('regCorreo').value.trim()) mostrarErrorAcceso('registroError', Cuentas.problemaCorreo(Cuentas.normalizar($('regCorreo').value)));
  });
  $('formRegistro').addEventListener('submit', registrarse);
  $('regClave').addEventListener('input', actualizarReglasClave);
  // Mis campeonatos (Organizador): crear, gestionar, terminar, eliminar o tomar; y cambiar desde la cabecera.
  $('btnNuevoCampeonato').addEventListener('click', () => abrirNuevo(false));
  $('listaCampeonatos').addEventListener('click', clicCampeonatos);
  $('listaSinOrganizador').addEventListener('click', clicCampeonatos);
  $('selCampeonato').addEventListener('change', async () => {
    const vista = (document.querySelector('.vista:not([hidden])') || { id: 'vista-equipos' }).id.replace('vista-', '');
    await gestionarCampeonato($('selCampeonato').value, vista);
  });
  document.querySelectorAll('[data-acceso]').forEach(b => b.addEventListener('click', () => mostrarAcceso(b.dataset.acceso)));
  // Detalle del partido (cara a cara, racha y alineaciones)
  document.querySelectorAll('[data-dp]').forEach(b => b.addEventListener('click', () => { detallePestana = b.dataset.dp; dibujarDetalle(); }));
  conectarVotaciones(); // [REQ-07] votaciones.js
  $('dpPanel').addEventListener('click', async e => {
    // Fotos en la alineación: el administrador toca al jugador para cambiar o quitar su foto.
    const ficha = e.target.closest('[data-foto-jugador]');
    if (ficha && detalleActual) {
      const idEquipo = Number(ficha.dataset.fotoEquipo), idJugador = Number(ficha.dataset.fotoJugador);
      const j = (equipoPorId(idEquipo) || { jugadores: [] }).jugadores.find(x => x.id === idJugador);
      const hecho = await editarFotoGuardada(ficha, 'jugador', j && j.foto,
        imagen => accion('jugador/foto', { equipo: idEquipo, jugador: idJugador, imagen }, { silencioso: true }));
      if (hecho) abrirDetalle(detalleActual.partido.id, 'alineaciones');
    }
  });
  $('dpCabecera').addEventListener('click', async e => {
    const escudo = e.target.closest('[data-escudo-detalle]');
    if (!escudo || !detalleActual) return;
    const eq = equipoPorId(Number(escudo.dataset.escudoDetalle));
    if (!eq) return;
    const hecho = await editarFotoGuardada(escudo, 'escudo', eq.escudo, imagen => accion('equipo/escudo', { id: eq.id, imagen }, { silencioso: true }));
    if (hecho) abrirDetalle(detalleActual.partido.id, detallePestana);
  });
  $('dlgDetalle').querySelector('.detalle-pestanas').addEventListener('keydown', e => {
    const pestanas = [...document.querySelectorAll('[data-dp]')], i = pestanas.findIndex(b => b.dataset.dp === detallePestana);
    const paso = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    if (!paso) return;
    e.preventDefault();
    const destino = pestanas[(i + paso + pestanas.length) % pestanas.length];
    detallePestana = destino.dataset.dp;
    dibujarDetalle();
    destino.focus();
  });
  $('dpCerrar').addEventListener('click', () => $('dlgDetalle').close());
  $('dpX').addEventListener('click', () => $('dlgDetalle').close());
  $('dpEditar').addEventListener('click', () => {
    const id = detalleActual && detalleActual.partido.id;
    $('dlgDetalle').close();
    if (id != null) abrirPartido(id);
  });
  $('ptVerDetalle').addEventListener('click', () => {
    const id = partidoEditando && partidoEditando.id;
    $('dlgPartido').close();
    if (id != null) abrirDetalle(id);
  });
  document.querySelector('.navegacion').addEventListener('click', e => {
    const b = e.target.closest('button[data-vista]');
    if (b) { cerrarNavMas(); mostrarVista(b.dataset.vista); }
  });
  $('navMas').addEventListener('click', () => {
    const abierta = document.querySelector('.navegacion').classList.toggle('abierta');
    $('navMas').setAttribute('aria-expanded', String(abierta));
  });
  // El panel de «Más» se cierra con Escape o tocando fuera de la barra.
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && document.querySelector('.navegacion.abierta')) { cerrarNavMas(); $('navMas').focus(); }
  });
  document.addEventListener('pointerdown', e => {
    if (document.querySelector('.navegacion.abierta') && !e.target.closest('.barra-lateral')) cerrarNavMas();
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
  // Estadísticas del campeonato: pestañas y «Corregir» / «Ver partido» en los registros.
  document.querySelectorAll('[data-est]').forEach(b => b.addEventListener('click', () => { pestanaEstadisticas = b.dataset.est; renderEstadisticas(); }));
  $('vista-sanciones').addEventListener('click', e => {
    const b = e.target.closest('[data-abrir-partido]');
    if (b) abrirSegunRol(Number(b.dataset.abrirPartido));
  });
  // Goles en el editor del partido
  $('glEquipo').addEventListener('change', llenarJugadoresGol);
  $('glEnContra').addEventListener('change', llenarJugadoresGol);
  $('glAgregar').addEventListener('click', agregarGol);
  $('glMinuto').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('glAutor').focus(); } });
  $('glLista').addEventListener('click', e => {
    const b = e.target.closest('[data-quitar-gol]');
    if (b) { golesTemp.splice(Number(b.dataset.quitarGol), 1); renderGolesTemp(); }
  });
  // Noticias: nueva / proponer, moderación, edición, comentarios
  $('btnNuevaNoticia').addEventListener('click', () => abrirNoticia(null, !puedeEditar()));
  // Pestañas del administrador (clic y flechas del teclado)
  document.querySelectorAll('[data-nt]').forEach(b => b.addEventListener('click', () => mostrarPestanaNoticias(b.dataset.nt)));
  $('pestanasNoticias').addEventListener('keydown', e => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    e.preventDefault();
    const otra = pestanaNoticias === 'pendientes' ? 'publicadas' : 'pendientes';
    mostrarPestanaNoticias(otra);
    document.querySelector(`[data-nt="${otra}"]`).focus();
  });
  $('formNoticia').addEventListener('submit', guardarNoticia);
  $('ntCancelar').addEventListener('click', () => $('dlgNoticia').close());
  $('ntArchivo').addEventListener('change', alElegirFoto);
  $('ntQuitarFoto').addEventListener('click', () => { fotoNueva = ''; quitarFoto = true; $('ntArchivo').value = ''; mostrarFotoNoticia(''); });
  $('vista-noticias').addEventListener('click', async e => {
    const b = e.target.closest('button');
    if (!b) return;
    const todas = [...N.publicadas, ...N.pendientes], buscar = id => todas.find(n => n.id === Number(id));
    if (b.dataset.aprobar) accionNoticia('noticia/aprobar', { id: Number(b.dataset.aprobar) });
    if (b.dataset.editarNoticia) abrirNoticia(buscar(b.dataset.editarNoticia), false);
    if (b.dataset.rechazar) {
      const n = buscar(b.dataset.rechazar);
      if (await mensaje('Rechazar propuesta', `¿Rechazar «${n.titulo}»? Se borra la propuesta y su foto.`, { confirmar: true, textoSi: 'Rechazar' })) {
        accionNoticia('noticia/eliminar', { id: n.id, rechazo: true });
      }
    }
    if (b.dataset.eliminarNoticia) {
      const n = buscar(b.dataset.eliminarNoticia);
      if (await mensaje('Eliminar noticia', `¿Eliminar «${n.titulo}» con su foto y sus comentarios?`, { confirmar: true, textoSi: 'Eliminar' })) {
        accionNoticia('noticia/eliminar', { id: n.id });
      }
    }
    if (b.dataset.quitarComentario) accionNoticia('comentario/eliminar', { noticia: Number(b.dataset.noticia), id: Number(b.dataset.quitarComentario) });
  });
  $('vista-noticias').addEventListener('submit', e => {
    const f = e.target.closest('[data-comentar]');
    if (!f) return;
    e.preventDefault();
    const autor = f.elements.autor.value || (puedeEditar() ? 'Administración' : '');
    accionNoticia('noticia/comentar', { id: Number(f.dataset.comentar), autor, texto: f.elements.texto.value });
  });

  // Divisiones de la liga: agregar / quitar y mover un equipo de división
  $('barraDivisiones').addEventListener('click', e => {
    const b = e.target.closest('[data-division-accion]');
    if (b) accionDivision('division/' + b.dataset.divisionAccion, {});
  });
  $('gridEquipos').addEventListener('change', e => {
    const s = e.target.closest('[data-division-equipo]');
    if (s) accionDivision('equipo/division', { id: Number(s.dataset.divisionEquipo), division: Number(s.value) });
  });
  $('gridEquipos').addEventListener('click', e => {
    const escudo = e.target.closest('[data-escudo-equipo]');
    if (escudo) {
      const eq = equipoPorId(Number(escudo.dataset.escudoEquipo));
      editarFotoGuardada(escudo, 'escudo', eq.escudo, imagen => accion('equipo/escudo', { id: eq.id, imagen }, { silencioso: true }));
      return;
    }
    const editar = e.target.closest('[data-editar-equipo]'), quitar = e.target.closest('[data-quitar-equipo]');
    if (editar) abrirEquipo(equipoPorId(Number(editar.dataset.editarEquipo)));
    if (quitar) quitarEquipo(Number(quitar.dataset.quitarEquipo));
  });
  $('formEquipo').addEventListener('submit', guardarEquipo);
  $('eqEscudo').addEventListener('click', e => tocarEscudoDialogo(e.currentTarget));
  $('eqSubirEscudo').addEventListener('click', async () => { const img = await pedirImagen('escudo'); if (img) { escudoTemp = img; renderEscudoTemp(); } });
  $('eqNombre').addEventListener('input', () => { if (!escudoTemp) renderEscudoTemp(); });
  $('jgFoto').addEventListener('click', e => tocarFotoNueva(e.currentTarget));
  $('jgNombre').addEventListener('input', renderFotoNueva);
  $('eqCancelar').addEventListener('click', () => $('dlgEquipo').close());
  $('jgAgregar').addEventListener('click', agregarJugador);
  $('jgNombre').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); agregarJugador(); } });
  $('tablaJugadores').addEventListener('click', e => {
    const f = e.target.closest('[data-foto-fila]');
    if (f) { tocarFotoFila(f, Number(f.dataset.fotoFila)); return; }
    const b = e.target.closest('[data-quitar-jugador]');
    if (b) { jugadoresTemp.splice(Number(b.dataset.quitarJugador), 1); renderJugadores(); }
  });

  ['filtroFase', 'filtroEstado', 'filtroEquipo'].forEach(id => $(id).addEventListener('change', dibujarListaPartidos));
  $('listaPartidos').addEventListener('click', e => {
    const b = e.target.closest('[data-partido]'), detalle = e.target.closest('[data-detalle]'), rapido = e.target.closest('[data-guardar-rapido]');
    if (b) abrirSegunRol(Number(b.dataset.partido));
    if (detalle) abrirResultado(Number(detalle.dataset.detalle));
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
  // Gestionar partidos: pestañas «Previa del partido» y «Registrar resultados» (con flechas, como Noticias).
  $('pestanasPartidos').addEventListener('click', e => {
    const b = e.target.closest('[data-pp]');
    if (b) mostrarPestanaPartidos(b.dataset.pp);
  });
  $('pestanasPartidos').addEventListener('keydown', e => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    e.preventDefault();
    const otra = pestanaPartidos === 'previa' ? 'resultados' : 'previa';
    mostrarPestanaPartidos(otra);
    document.querySelector(`[data-pp="${otra}"]`).focus();
  });
  $('ptPlegar').addEventListener('click', () => plegarAlineacion(!$('ptAlineacion').hidden));
  ['fmLocal', 'fmVisitante'].forEach(id => $(id).addEventListener('change', actualizarResumenPrevia));
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