'use strict';

/*
 * Cuentas de usuario: las reglas del registro (gemelo de AutenticacionService en Java) y, para la web sola,
 * el hash de las claves con PBKDF2-SHA256 de WebCrypto. La pantalla valida con las mismas reglas antes de
 * enviar; el servicio (Java o servicioLocal.js) vuelve a validar siempre.
 * En la web sola no hay servidor: las cuentas se guardan en este navegador (Almacen.cuentas) y no sirven en
 * otro navegador ni en el programa Java.
 */
const Cuentas = (() => {
  const USUARIO_MINIMO = 3, USUARIO_MAXIMO = 30, NOMBRE_MINIMO = 2, NOMBRE_MAXIMO = 80;
  const CORREO_MAXIMO = 254, CORREO_LOCAL_MAXIMO = 64, CORREO_ETIQUETA_MAXIMA = 63;
  const CLAVE_MINIMA = 8, CLAVE_MAXIMA = 128;
  /** Letras sin tilde, números, punto, guion y guion bajo; empieza con letra o número (igual que Java). */
  const USUARIO = /^[a-z0-9][a-z0-9._-]{2,29}$/;
  const ROLES_REGISTRO = ['ORGANIZADOR', 'AFICIONADO'];
  /** Menos que en Java (600 000) para que el navegador no se congele; el número va dentro del hash. */
  const ITERACIONES = 100000;

  /** Usuario o correo como se guardan: sin espacios alrededor y en minúsculas. */
  const normalizar = texto => String(texto == null ? '' : texto).trim().toLowerCase();

  const letraONumero = c => (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');

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
    if (!usuario) throw new Error('Escriba un nombre de usuario.');
    if (usuario.length < USUARIO_MINIMO || usuario.length > USUARIO_MAXIMO) {
      throw new Error(`El nombre de usuario debe tener entre ${USUARIO_MINIMO} y ${USUARIO_MAXIMO} caracteres.`);
    }
    if (!USUARIO.test(usuario)) {
      throw new Error('El nombre de usuario solo puede tener letras sin tilde, números, punto, guion y guion bajo, '
        + 'y debe empezar con una letra o un número.');
    }
    const nombre = String(d.nombre == null ? '' : d.nombre).trim().replace(/\s+/g, ' ');
    if (nombre.length < NOMBRE_MINIMO || nombre.length > NOMBRE_MAXIMO) {
      throw new Error(`Escriba su nombre (entre ${NOMBRE_MINIMO} y ${NOMBRE_MAXIMO} caracteres).`);
    }
    const correo = normalizar(d.correo);
    const problema = problemaCorreo(correo);
    if (problema) throw new Error(problema);
    const clave = typeof d.clave === 'string' ? d.clave : '';
    if (clave.length < CLAVE_MINIMA || clave.length > CLAVE_MAXIMA) {
      throw new Error(`La contraseña debe tener entre ${CLAVE_MINIMA} y ${CLAVE_MAXIMA} caracteres.`);
    }
    if (!/\p{L}/u.test(clave) || !/\p{Nd}/u.test(clave)) throw new Error('La contraseña debe tener letras y números.');
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

  return { normalizar, problemaCorreo, validarRegistro, hash, coincide, ROLES_REGISTRO };
})();
