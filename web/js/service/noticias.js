'use strict';

/*
 * Noticias y comentarios (versión web sola). Mismas reglas que service.NoticiasService y
 * service.FiltroPalabras en Java:
 * - el administrador crea, edita, aprueba y elimina; lo suyo sale publicado (aprobado = true);
 * - el visitante propone (aprobado = false, queda pendiente) y comenta las publicadas;
 * - los comentarios pasan por el filtro anti-obscenidades antes de guardarse.
 * Las noticias viven en un arreglo en memoria; Almacen las conserva en el navegador.
 */

/** Filtro anti-obscenidades: cada palabra de la lista negra se cambia por «***». */
const FiltroPalabras = (() => {
  const REEMPLAZO = '***';
  // Lista negra (minúsculas, sin tildes). Es la misma de FiltroPalabras.java.
  const PROHIBIDAS = ['idiota', 'imbecil', 'estupido', 'estupida', 'pendejo', 'pendeja', 'marica', 'maricon', 'malparido', 'malparida',
    'hijueputa', 'hijodeputa', 'gonorrea', 'puta', 'puto', 'mierda', 'verga', 'careverga', 'cabron', 'cabrona',
    'huevon', 'guevon', 'perra', 'zorra', 'tarado', 'tarada', 'mamon', 'malnacido', 'pirobo', 'culo', 'hp', 'hdp'];
  // Cada letra puede repetirse pero no faltar («p+e+r+r+a+» reconoce «perraaa» y nunca «pera»);
  // se acepta el plural y, en las palabras largas, cualquier final («malparidos»).
  const escapar = ch => ch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const PATRONES = PROHIBIDAS.map(raiz => new RegExp('^' + [...raiz].map(ch => escapar(ch) + '+').join('') + (raiz.length >= 6 ? '.*' : '(e?s)?') + '$'));
  const LETRAS = { 0: 'o', 1: 'i', 3: 'e', 4: 'a', '@': 'a', 5: 's', $: 's', 7: 't' };
  const normalizar = palabra => [...palabra.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')].map(ch => LETRAS[ch] || ch).join('');
  const esProhibida = palabra => { const p = normalizar(palabra); return PATRONES.some(r => r.test(p)); };
  const sanear = texto => String(texto || '').replace(/[\p{L}\p{N}@$]+/gu, w => (esProhibida(w) ? REEMPLAZO : w));
  return { sanear, esProhibida, PROHIBIDAS };
})();

function crearNoticiasLocal(ErrorRegla) {
  const MAXIMO_TITULO = 120, MAXIMO_CUERPO = 4000, MAXIMO_COMENTARIO = 500, MAXIMO_NOMBRE = 40;
  const MAXIMO_IMAGEN = 5 * 1024 * 1024;
  let lista = Almacen.noticias.cargar();
  let sigNoticia = lista.reduce((m, n) => Math.max(m, n.id + 1), 1);
  let sigComentario = lista.reduce((m, n) => n.comentarios.reduce((x, c) => Math.max(x, c.id + 1), m), 1);

  const ahora = () => { const d = new Date(), dos = x => String(x).padStart(2, '0'); return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())} ${dos(d.getHours())}:${dos(d.getMinutes())}`; };
  const masReciente = (a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id;
  /** Se ve en ese campeonato: es suya o es general (sin campeonato). campeonato null = todas. */
  const delCampeonato = (n, campeonato) => campeonato == null || !n.campeonato || n.campeonato === campeonato;
  const buscar = (id, campeonato) => {
    const n = lista.find(x => x.id === id && delCampeonato(x, campeonato));
    if (!n) throw new ErrorRegla('La noticia no existe (quizá ya fue eliminada).');
    return n;
  };
  /** Para moderar: tiene que ser de este campeonato (las generales no se moderan desde un campeonato). */
  const buscarParaModerar = (id, campeonato) => {
    const n = buscar(id, campeonato);
    if (campeonato != null && n.campeonato !== campeonato) throw new ErrorRegla('Esa noticia no es de este campeonato: solo se moderan las del campeonato que gestiona.', 'PERMISO');
    return n;
  };

  function guardar() {
    try {
      Almacen.noticias.guardar(lista);
    } catch (e) {
      lista = Almacen.noticias.cargar(); // se deshace el cambio
      throw new ErrorRegla('El navegador no tiene espacio para guardar esto. Use una foto más liviana o elimine noticias viejas.');
    }
  }

  function validarTitulo(t) {
    t = String(t || '').trim();
    if (t.length < 3) throw new ErrorRegla('Escriba un título (al menos 3 caracteres).');
    if (t.length > MAXIMO_TITULO) throw new ErrorRegla(`El título puede tener hasta ${MAXIMO_TITULO} caracteres.`);
    return t;
  }
  function validarCuerpo(c) {
    c = String(c || '').trim();
    if (c.length < 10) throw new ErrorRegla('Escriba el texto de la noticia (al menos 10 caracteres).');
    if (c.length > MAXIMO_CUERPO) throw new ErrorRegla(`La noticia puede tener hasta ${MAXIMO_CUERPO} caracteres.`);
    return c;
  }
  const nombre = (n, porDefecto) => (String(n || '').trim() || porDefecto).slice(0, MAXIMO_NOMBRE);

  /** Foto como «data URL»; solo JPG, PNG, GIF o WEBP de verdad (se revisan los primeros bytes). */
  function validarImagen(dataUrl) {
    if (!dataUrl) return '';
    const m = /^data:image\/[a-z+]+;base64,([A-Za-z0-9+/=]+)$/.exec(dataUrl);
    if (!m) throw new ErrorRegla('La foto no tiene un formato válido.');
    if (m[1].length * 0.75 > MAXIMO_IMAGEN) throw new ErrorRegla('La foto es muy pesada (máximo 5 MB).');
    const b = atob(m[1].slice(0, 16)), c = i => b.charCodeAt(i);
    const valida = (c(0) === 0xFF && c(1) === 0xD8) || (c(0) === 0x89 && b.slice(1, 4) === 'PNG') || b.startsWith('GIF') || (b.startsWith('RIFF') && b.slice(8, 12) === 'WEBP');
    if (!valida) throw new ErrorRegla('El archivo no es una imagen JPG, PNG, GIF o WEBP.');
    return dataUrl;
  }

  /*
   * Cada noticia es de un campeonato (su código, como NoticiasService.de en Java): el feed de un campeonato
   * muestra las suyas y las generales; las nuevas quedan en él y solo se moderan las suyas. campeonato null = todas.
   */
  return {
    publicadas: campeonato => lista.filter(n => n.aprobado && delCampeonato(n, campeonato)).sort(masReciente),
    pendientes: campeonato => lista.filter(n => !n.aprobado && delCampeonato(n, campeonato)).sort(masReciente),
    /** Visitante: queda pendiente (aprobado = false) hasta que el administrador la apruebe. */
    proponer(d, campeonato) {
      const n = { id: sigNoticia, titulo: validarTitulo(d.titulo), cuerpo: validarCuerpo(d.cuerpo), autor: nombre(d.autor, 'Visitante'),
        rolAutor: 'AFICIONADO', fecha: ahora(), imagenRuta: validarImagen(d.imagen), aprobado: false, comentarios: [], campeonato: campeonato || null };
      lista.push(n);
      guardar();
      sigNoticia++;
    },
    /** Administrador: crea (sin id) o edita; queda publicada. */
    publicar(d, campeonato) {
      const t = validarTitulo(d.titulo), c = validarCuerpo(d.cuerpo), foto = validarImagen(d.imagen);
      let n;
      if (d.id == null) {
        n = { id: sigNoticia++, titulo: t, cuerpo: c, autor: 'Administración', rolAutor: 'ORGANIZADOR', fecha: ahora(), imagenRuta: '', aprobado: true,
          comentarios: [], campeonato: campeonato || null };
        lista.push(n);
      } else {
        n = buscarParaModerar(d.id, campeonato);
        Object.assign(n, { titulo: t, cuerpo: c, aprobado: true }); // editar una propuesta también la publica
      }
      if (foto || d.quitarImagen) n.imagenRuta = foto;
      guardar();
    },
    aprobar(id, campeonato) { buscarParaModerar(id, campeonato).aprobado = true; guardar(); },
    eliminar(id, campeonato) { buscarParaModerar(id, campeonato); lista = lista.filter(n => n.id !== id); guardar(); },
    comentar(id, autor, texto, campeonato) {
      const n = buscar(id, campeonato);
      if (!n.aprobado) throw new ErrorRegla('Solo se puede comentar una noticia publicada.');
      const limpio = String(texto || '').trim();
      if (!limpio) throw new ErrorRegla('Escriba el comentario.');
      if (limpio.length > MAXIMO_COMENTARIO) throw new ErrorRegla(`El comentario puede tener hasta ${MAXIMO_COMENTARIO} caracteres.`);
      n.comentarios.push({ id: sigComentario++, autor: FiltroPalabras.sanear(nombre(autor, 'Anónimo')), texto: FiltroPalabras.sanear(limpio), fecha: ahora() });
      guardar();
    },
    eliminarComentario(idNoticia, idComentario, campeonato) {
      const n = buscarParaModerar(idNoticia, campeonato), antes = n.comentarios.length;
      n.comentarios = n.comentarios.filter(c => c.id !== idComentario);
      if (n.comentarios.length === antes) throw new ErrorRegla('El comentario no existe.');
      guardar();
    }
  };
}
