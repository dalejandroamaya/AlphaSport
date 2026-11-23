'use strict';

/*
 * Pestaña «Alineaciones» del detalle del partido (gemela de VistaAlineaciones en el escritorio): una cancha
 * horizontal (el local a la izquierda ataca hacia la derecha y el visitante al revés) con la foto, el dorsal
 * y el nombre de los titulares. Debajo van la formación, el estado de la alineación, el cuerpo técnico, los
 * cambios y los suplentes. Solo dibuja el JSON de «alineaciones»; usa las ayudas de app.js (esc, fotos…).
 */

/** «Juan Carlos Pérez» -> «J. Pérez», para que quepa sobre la cancha (en el teléfono, solo «Pérez»). */
function apellidoHtml(nombre) {
  const partes = String(nombre).trim().split(/\s+/);
  return partes.length < 2 ? esc(nombre) : `<span class="inicial">${esc(partes[0].charAt(0))}. </span>${esc(partes[partes.length - 1])}`;
}

/** Un jugador en su puesto; «j» null = puesto por confirmar («?»). */
function fichaCanchaHtml(j, local, izquierda, arriba, p) {
  const clase = local ? 'ficha-local' : 'ficha-visitante';
  const salio = j && (p.cambios || []).find(x => x.sale === j.id);
  const titulo = j ? `#${j.dorsal} ${j.nombre} · ${j.posicion || ''}${salio ? ` · salió en el minuto ${salio.minuto}` : ''}` : 'Por confirmar';
  const dorsal = `<span class="ficha-dorsal ${clase}">${j ? j.dorsal : '?'}</span>`;
  // Con jugador: su foto tipo cédula (o «NN») con el dorsal en la esquina. El administrador la toca para cambiarla.
  const contenido = j ? `${fotoCedulaHtml(j.foto, j.nombre)}${dorsal}${salio ? `<span class="ficha-sale" aria-hidden="true">↓${minutoTexto(salio.minuto)}</span>` : ''}` : '';
  const foto = !j ? dorsal : puedeEditar()
    ? `<button type="button" class="ficha-foto ${clase}" data-foto-equipo="${local ? p.local : p.visitante}" data-foto-jugador="${j.id}"
        title="${j.foto ? 'Cambiar o quitar la foto' : 'Subir la foto'} de ${esc(j.nombre)}">${contenido}</button>`
    : `<span class="ficha-foto ${clase}">${contenido}</span>`;
  return `<div class="ficha-cancha" style="left:${(izquierda * 100).toFixed(2)}%;top:${(arriba * 100).toFixed(2)}%" title="${esc(titulo)}">
      ${foto}${j ? `<span class="ficha-nombre" aria-hidden="true">${apellidoHtml(j.nombre)}</span>` : ''}
      <span class="solo-lector">${esc(titulo)}</span></div>`;
}

/**
 * Las líneas de un equipo en su mitad: el portero junto a su arco y la última línea cerca del medio campo.
 * Cada línea viene de izquierda a derecha vista por el equipo: el local mira hacia la derecha (su izquierda
 * queda arriba) y el visitante hacia la izquierda (su izquierda queda abajo).
 */
function fichasEquipoHtml(al, local, p) {
  const ultima = Math.max(1, al.lineas.length - 1), arco = 0.06, frente = 0.437;
  return al.lineas.map((linea, i) => {
    const x = arco + (frente - arco) * i / ultima;
    return linea.map((j, k) => {
      const y = 0.02 + 0.96 * (k + 0.5) / linea.length;
      return fichaCanchaHtml(j, local, local ? x : 1 - x, local ? y : 1 - y, p);
    }).join('');
  }).join('');
}

/** Lo que va debajo de la cancha: formación, estado, cuerpo técnico, cambios y suplentes de un equipo. */
function datosAlineacionHtml(equipo, idEquipo, al, p) {
  const cambios = (p.cambios || []).filter(x => x.equipo === idEquipo);
  // Un suspendido para este partido no puede estar en el banco.
  const banco = (al.suplentes || []).filter(j => !(p.suspendidos || []).some(x => x.jugador === j.id));
  const titulares = al.lineas.flat().filter(Boolean);
  const suplente = j => {
    const entro = cambios.find(x => x.entra === j.id);
    return `<li class="suplente" title="${esc(`#${j.dorsal} ${j.nombre} · ${j.posicion || ''}`)}">${fotoCedulaHtml(j.foto, j.nombre, 'banco')}
      <span>#${j.dorsal} ${apellidoHtml(j.nombre)}</span>${entro ? `<b class="entra-txt">↑${minutoTexto(entro.minuto)}</b>` : ''}</li>`;
  };
  return `<section class="detalle-rotulo vidrio">
      <b>${esc(equipo)}</b>
      <p class="rotulo-linea"><span class="chip">Formación ${esc(al.formacion)}${al.formacionElegida ? '' : ' (por defecto)'}</span>
        <span class="sutil pequeno">${al.confirmada ? 'Alineación confirmada' : 'Alineación por confirmar'}</span></p>
      ${titulares.length ? `<div class="solo-telefono"><h4>Titulares</h4><p class="sutil pequeno">${titulares.map(j => esc(`#${j.dorsal} ${j.nombre}`)).join(' · ')}</p></div>` : ''}
      <h4>Cuerpo técnico</h4>
      <p class="${al.dt ? '' : 'sutil pequeno'}">${al.dt ? 'DT ' + esc(al.dt) : 'Sin director técnico registrado.'}</p>
      ${cambios.length ? `<h4>Cambios</h4><ol class="rotulo-cambios">${cambios.map(x => `<li><b class="entra-txt">${minutoTexto(x.minuto)}</b>
        <span><span>Entra #${x.entraDorsal} ${esc(x.entraNombre)}</span><small class="sale-txt">Sale #${x.saleDorsal} ${esc(x.saleNombre)}</small></span></li>`).join('')}</ol>` : ''}
      <h4>Suplentes</h4>
      ${banco.length ? `<ul class="suplentes">${banco.map(suplente).join('')}</ul>`
        : `<p class="sutil pequeno">${al.confirmada ? 'Sin suplentes en la plantilla.' : 'Se conocen al confirmar el XI inicial.'}</p>`}
    </section>`;
}

function alineacionesHtml(a, p) {
  const nota = !a.local.confirmada || !a.visitante.confirmada
    ? '<p class="sutil pequeno">Las casillas con «?» son puestos por confirmar: el XI inicial se registra en la previa del partido.</p>' : '';
  return `<div class="cancha-marco"><div class="cancha" role="group" aria-label="Alineaciones sobre la cancha: ${esc(p.localNombre)} a la izquierda y ${esc(p.visitanteNombre)} a la derecha">
        <span class="cancha-medio"></span><span class="cancha-circulo"></span>
        <span class="cancha-area izq"></span><span class="cancha-area der"></span>
        <span class="cancha-chica izq"></span><span class="cancha-chica der"></span>
        ${fichasEquipoHtml(a.local, true, p)}${fichasEquipoHtml(a.visitante, false, p)}
      </div></div>
    <div class="detalle-columnas">${datosAlineacionHtml(p.localNombre, p.local, a.local, p)}${datosAlineacionHtml(p.visitanteNombre, p.visitante, a.visitante, p)}</div>
    ${nota}`;
}
