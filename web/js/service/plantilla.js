'use strict';

/* Reglas de la plantilla, iguales a ReglasPlantilla en Java: la pantalla avisa mientras se escribe y la web sola
   las aplica al guardar. Con el programa Java, el servidor vuelve a revisarlas. */
const Plantilla = {
  /** Jugadores que necesita cada equipo para iniciar el campeonato (su XI titular). */
  MINIMO_JUGADORES: 11,
  /** Solo los dígitos: los espacios y puntos que se escriban para separar no cuentan. */
  soloDigitos: doc => String(doc || '').replace(/[\s.]/g, ''),
  /** Problema del documento, o null si sirve: vacío o solo números de 5 a 12 dígitos. */
  problemaDocumento(doc) {
    const d = Plantilla.soloDigitos(doc);
    if (!d || /^\d{5,12}$/.test(d)) return null;
    return /\D/.test(d) ? 'debe tener solo números, sin letras ni signos' : 'debe tener de 5 a 12 dígitos';
  }
};
