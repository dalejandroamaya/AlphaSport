'use strict';

/*
 * Cuentas de usuario: las reglas del registro (gemelo de AutenticacionService en Java) y, para la web sola,
 * el hash de las claves con PBKDF2-SHA256 de WebCrypto. La pantalla valida con las mismas reglas antes de
 * enviar; el servicio (Java o servicioLocal.js) vuelve a validar siempre.
 * En la web sola no hay servidor: las cuentas se guardan en este navegador (Almacen.cuentas) y no sirven en
 * otro navegador ni en el programa Java.
 */
const Cuentas = (() => {
  const USUARIO_MINIMO = 4, USUARIO_MAXIMO = 20, NOMBRE_MINIMO = 2, NOMBRE_MAXIMO = 80;
  const CORREO_MAXIMO = 254, CORREO_LOCAL_MAXIMO = 64, CORREO_ETIQUETA_MAXIMA = 63;
  const CLAVE_MINIMA = 8, CLAVE_MAXIMA = 128;
  /** Signos permitidos en el usuario y nombres que nadie puede tomar (igual que Java). */
  const SIGNOS_USUARIO = '._-';
  const USUARIOS_RESERVADOS = ['admin', 'administrador', 'root', 'organizador', 'aficionado', 'visitante', 'soporte'];
  /** La ayuda bajo el campo «Usuario»: el mismo texto que AutenticacionService.AYUDA_USUARIO. */
  const AYUDA_USUARIO = 'De 4 a 20 caracteres: letras a-z sin tilde, números y . _ -. Empieza con letra, '
    + 'no termina en . _ -, sin dos signos seguidos, no solo números. No se permiten: admin, administrador, root, '
    + 'organizador, aficionado, visitante ni soporte.';
  const ROLES_REGISTRO = ['ORGANIZADOR', 'AFICIONADO'];
  /** Menos que en Java (600 000) para que el navegador no se congele; el número va dentro del hash. */
  const ITERACIONES = 100000;

  /** Usuario o correo como se guardan: sin espacios alrededor y en minúsculas. */
  const normalizar = texto => String(texto == null ? '' : texto).trim().toLowerCase();

  /**
   * Recorre la clave carácter por carácter (igual que AutenticacionService en Java) y anota qué tiene:
   * mayúscula, minúscula, número, carácter especial (ni letra, ni número, ni espacio), espacios y caracteres
   * de control. Devuelve las 5 reglas con su estado para la lista que se marca mientras se escribe.
   */
  function revisarClave(clave) {
    const c = typeof clave === 'string' ? clave : '';
    let mayuscula = false, minuscula = false, numero = false, especial = false, espacios = false, control = false;
    for (const ch of c) {
      if (/[\p{Z}\t\n\v\f\r\u001c-\u001f]/u.test(ch)) espacios = true;
      else if (/\p{Cc}/u.test(ch)) control = true;
      else if (/\p{Lu}/u.test(ch)) mayuscula = true;
      else if (/\p{Ll}/u.test(ch)) minuscula = true;
      else if (/\p{Nd}/u.test(ch)) numero = true;
      else if (!/\p{L}/u.test(ch)) especial = true;
    }
    const reglas = [
      { id: 'largo', texto: `Entre ${CLAVE_MINIMA} y ${CLAVE_MAXIMA} caracteres`, falta: `al menos ${CLAVE_MINIMA} caracteres`,
        cumple: c.length >= CLAVE_MINIMA && c.length <= CLAVE_MAXIMA },
      { id: 'mayuscula', texto: 'Una letra mayúscula', falta: 'una mayúscula', cumple: mayuscula },
      { id: 'minuscula', texto: 'Una letra minúscula', falta: 'una minúscula', cumple: minuscula },
      { id: 'numero', texto: 'Un número', falta: 'un número', cumple: numero },
      { id: 'especial', texto: 'Un carácter especial (por ejemplo ! # $ % *)', falta: 'un carácter especial', cumple: especial }
    ];
    return { reglas, espacios, control, larga: c.length > CLAVE_MAXIMA };
  }

  /** Las 5 reglas de la contraseña y si esta clave las cumple. */
  const reglasClave = clave => revisarClave(clave).reglas;

  /** «a», «a y b», «a, b y c». */
  const enLista = cosas => cosas.length === 1 ? cosas[0] : `${cosas.slice(0, -1).join(', ')} y ${cosas[cosas.length - 1]}`;

  /**
   * Por qué no sirve la clave, nombrando todo lo que falta (por ejemplo «Falta: un número y un carácter
   * especial.»), o null si sirve. No revisa la confirmación. El mismo texto que Java.
   */
  function problemaClave(clave) {
    const r = revisarClave(clave);
    const partes = [];
    if (r.larga) partes.push(`La contraseña no puede tener más de ${CLAVE_MAXIMA} caracteres.`);
    if (r.espacios) partes.push('La contraseña no puede tener espacios.');
    if (r.control) partes.push('La contraseña no puede tener caracteres de control.');
    const faltan = r.reglas.filter((regla, i) => !regla.cumple && !(r.larga && i === 0)).map(regla => regla.falta);
    if (faltan.length) partes.push(`Falta: ${enLista(faltan)}.`);
    return partes.length ? partes.join(' ') : null;
  }

  const letraONumero = c => (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');

  /** Las 7 reglas del nombre de usuario y si las cumple (mismo orden y textos que Java). */
  function reglasUsuario(texto) {
    const u = normalizar(texto);
    const chars = [...u];
    const signo = c => SIGNOS_USUARIO.includes(c);
    const caracteres = chars.length > 0 && chars.every(c => letraONumero(c) || signo(c));
    const seguidos = chars.some((c, i) => i > 0 && signo(c) && signo(chars[i - 1]));
    const soloNumeros = chars.length > 0 && chars.every(c => c >= '0' && c <= '9');
    return [
      { id: 'largo', texto: `De ${USUARIO_MINIMO} a ${USUARIO_MAXIMO} caracteres`,
        falta: `El usuario debe tener de ${USUARIO_MINIMO} a ${USUARIO_MAXIMO} caracteres.`, cumple: u.length >= USUARIO_MINIMO && u.length <= USUARIO_MAXIMO },
      { id: 'caracteres', texto: 'Solo letras a-z sin tilde, números y . _ -',
        falta: 'El usuario solo puede tener letras a-z sin tilde, números y los signos . _ -', cumple: caracteres },
      { id: 'inicio', texto: 'Empieza con una letra', falta: 'El usuario debe empezar con una letra.', cumple: /^[a-z]/.test(u) },
      { id: 'final', texto: 'No termina en . _ -', falta: 'El usuario no puede terminar en punto, guion bajo ni guion.',
        cumple: u.length > 0 && !signo(u[u.length - 1]) },
      { id: 'seguidos', texto: 'Sin dos signos seguidos', falta: 'El usuario no puede tener dos signos seguidos (por ejemplo «..» o «_-»).', cumple: !seguidos },
      { id: 'numeros', texto: 'No es solo números', falta: 'El usuario no puede ser solo números.', cumple: !soloNumeros },
      { id: 'reservado', texto: 'No es un nombre reservado', falta: 'Ese nombre de usuario está reservado. Elija otro.', cumple: !USUARIOS_RESERVADOS.includes(u) }
    ];
  }

  /** Por qué no sirve el usuario, nombrando cada regla que no cumple, o null si sirve. */
  function problemaUsuario(texto) {
    if (!normalizar(texto)) return 'Escriba un nombre de usuario.';
    const mensajes = reglasUsuario(texto).filter(r => !r.cumple).map(r => r.falta);
    return mensajes.length ? mensajes.join(' ') : null;
  }

  /**
   * Por qué no sirve el correo (ya normalizado), o null si sirve. Las mismas reglas y textos que
   * AutenticacionService.problemaCorreo en Java: una sola @ y hasta 254 caracteres; antes de la @, de 1 a 64
   * letras, números y . _ % + - sin punto al inicio, al final ni dos seguidos; después, partes de 1 a 63
   * letras, números o guion (sin guion al inicio ni al final) y una extensión de al menos 2 letras.
   */
  function problemaCorreo(correo) {
    if (!correo) return 'Escriba su correo.';
    if (correo.length > CORREO_MAXIMO) return `El correo no puede tener más de ${CORREO_MAXIMO} caracteres.`;
    const arroba = correo.indexOf('@');
    if (arroba < 0) return 'Al correo le falta la @, por ejemplo nombre@correo.com.';
    if (correo.indexOf('@', arroba + 1) >= 0) return 'El correo solo puede tener una @.';
    const local = correo.slice(0, arroba), dominio = correo.slice(arroba + 1);
    if (!local) return 'Falta el nombre antes de la @.';
    if (local.length > CORREO_LOCAL_MAXIMO) return `El nombre antes de la @ no puede tener más de ${CORREO_LOCAL_MAXIMO} caracteres.`;
    for (const c of local) {
      if (!letraONumero(c) && !'._%+-'.includes(c)) return 'Antes de la @ solo se permiten letras sin tilde, números y los signos . _ % + -';
    }
    if (local.startsWith('.') || local.endsWith('.')) return 'El nombre antes de la @ no puede empezar ni terminar en punto.';
    if (local.includes('..')) return 'El nombre antes de la @ no puede tener puntos seguidos.';
    if (!dominio) return 'Falta el dominio después de la @, por ejemplo correo.com.';
    if (dominio.startsWith('.')) return 'El dominio no puede empezar con punto.';
    if (dominio.endsWith('.')) return 'El dominio no puede terminar en punto.';
    if (dominio.includes('..')) return 'El dominio no puede tener puntos seguidos.';
    const etiquetas = dominio.split('.');
    if (etiquetas.length < 2) return 'Al dominio le falta la extensión, por ejemplo .com.';
    for (const etiqueta of etiquetas) {
      if (etiqueta.length > CORREO_ETIQUETA_MAXIMA) return `Cada parte del dominio puede tener como máximo ${CORREO_ETIQUETA_MAXIMA} caracteres.`;
      for (const c of etiqueta) {
        if (!letraONumero(c) && c !== '-') return 'El dominio solo puede tener letras sin tilde, números, guiones y puntos.';
      }
      if (etiqueta.startsWith('-') || etiqueta.endsWith('-')) return 'Las partes del dominio no pueden empezar ni terminar en guion.';
    }
    const extension = etiquetas[etiquetas.length - 1];
    if (extension.length < 2 || !/^[a-z]+$/.test(extension)) {
      return 'La extensión del dominio (después del último punto) debe tener solo letras y al menos 2, por ejemplo .com.';
    }
    return null;
  }

  /**
   * Valida lo que envía la pantalla «Registrarse» y devuelve los datos limpios. Lanza un Error con el mismo
   * mensaje que Java ante el primer dato que no sirve. La clave nunca se devuelve en un mensaje.
   */
  function validarRegistro(d) {
    const usuario = normalizar(d.usuario);
    const problemaDeUsuario = problemaUsuario(usuario);
    if (problemaDeUsuario) throw new Error(problemaDeUsuario);
    const nombre = String(d.nombre == null ? '' : d.nombre).trim().replace(/\s+/g, ' ');
    if (nombre.length < NOMBRE_MINIMO || nombre.length > NOMBRE_MAXIMO) {
      throw new Error(`Escriba su nombre (entre ${NOMBRE_MINIMO} y ${NOMBRE_MAXIMO} caracteres).`);
    }
    const correo = normalizar(d.correo);
    const problema = problemaCorreo(correo);
    if (problema) throw new Error(problema);
    const clave = typeof d.clave === 'string' ? d.clave : '';
    const problemaDeClave = problemaClave(clave);
    if (problemaDeClave) throw new Error(problemaDeClave);
    if (clave !== d.confirmacion) throw new Error('Las contraseñas no coinciden.');
    // Solo estos dos: un valor enviado por la pantalla nunca puede dar otro rol.
    if (!ROLES_REGISTRO.includes(d.tipo)) throw new Error('Elija el tipo de cuenta: Organizador o Aficionado.');
    return { usuario, nombre, correo, clave, rol: d.tipo };
  }

  /* ---------------------------------------------------------------- hash de claves (web sola) */

  const aBase64 = bytes => btoa(String.fromCharCode(...bytes)).replace(/=+$/, '');
  const deBase64 = texto => Uint8Array.from(atob(texto), c => c.charCodeAt(0));

  function exigirCripto() {
    if (typeof crypto === 'undefined' || !crypto.subtle) {
      throw new Error('Este navegador no permite guardar contraseñas de forma segura en esta dirección. '
        + 'Abra web/index.html directamente o use el programa Java.');
    }
  }

  async function derivar(clave, sal, iteraciones) {
    const llave = await crypto.subtle.importKey('raw', new TextEncoder().encode(clave), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: sal, iterations: iteraciones }, llave, 256);
    return new Uint8Array(bits);
  }

  /** Hash nuevo con su propia sal, en el mismo formato que Java: pbkdf2_sha256$iteraciones$sal$hash. */
  async function hash(clave) {
    exigirCripto();
    const sal = crypto.getRandomValues(new Uint8Array(16));
    return `pbkdf2_sha256$${ITERACIONES}$${aBase64(sal)}$${aBase64(await derivar(clave, sal, ITERACIONES))}`;
  }

  /** true si la clave corresponde al hash; compara todos los bytes (no se detiene en el primero distinto). */
  async function coincide(clave, guardado) {
    exigirCripto();
    const partes = String(guardado || '').split('$');
    if (partes.length !== 4 || partes[0] !== 'pbkdf2_sha256') return false;
    const veces = Number(partes[1]);
    let sal, esperado;
    try {
      sal = deBase64(partes[2]);
      esperado = deBase64(partes[3]);
    } catch (e) {
      return false;
    }
    if (!(veces >= 1) || !sal.length || !esperado.length) return false;
    const calculado = await derivar(typeof clave === 'string' ? clave : '', sal, veces);
    let diferencia = calculado.length ^ esperado.length;
    for (let i = 0; i < Math.min(calculado.length, esperado.length); i++) diferencia |= calculado[i] ^ esperado[i];
    return diferencia === 0;
  }

  return { normalizar, problemaUsuario, reglasUsuario, AYUDA_USUARIO, problemaCorreo, problemaClave, reglasClave, validarRegistro, hash, coincide, ROLES_REGISTRO };
})();
