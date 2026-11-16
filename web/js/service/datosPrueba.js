'use strict';
/*
 * Equipos de prueba con plantilla completa (20 jugadores), director técnico y
 * estadio generados a partir de una semilla: la misma semilla produce siempre
 * los mismos datos. Usa el mismo algoritmo que alphasport.service.DatosPrueba
 * (Java), así que la web y el escritorio generan exactamente los mismos equipos.
 * Todos los nombres y documentos son inventados.
 */

/** Posiciones específicas agrupadas por línea (las que se usan en todo el fútbol). */
const GRUPOS_POSICIONES = [
  { grupo: 'Portería', posiciones: ['Portero'] },
  { grupo: 'Defensa', posiciones: ['Defensa central', 'Líbero', 'Lateral derecho', 'Lateral izquierdo', 'Carrilero derecho', 'Carrilero izquierdo'] },
  { grupo: 'Mediocampo', posiciones: ['Mediocampista defensivo', 'Mediocampista central', 'Interior derecho', 'Interior izquierdo', 'Volante derecho', 'Volante izquierdo', 'Mediocampista ofensivo'] },
  { grupo: 'Ataque', posiciones: ['Mediapunta', 'Extremo derecho', 'Extremo izquierdo', 'Segundo delantero', 'Delantero centro'] }
];

const DatosPrueba = (() => {
  const MAXIMO_EQUIPOS = 32;

  const EQUIPOS = [
    'Halcones del Norte', 'Tigres de la Sabana', 'Lobos de Montaña', 'Pumas del Valle', 'Cóndores FC',
    'Toros de la Costa', 'Leones Dorados', 'Águilas del Sur', 'Jaguares FC', 'Dragones Rojos',
    'Titanes del Puerto', 'Corsarios FC', 'Centauros del Llano', 'Vikingos de Altamira', 'Búhos Nocturnos',
    'Delfines del Caribe', 'Zorros del Bosque', 'Osos Andinos', 'Tiburones del Golfo', 'Linces FC',
    'Gavilanes del Río', 'Bisontes United', 'Coyotes del Desierto', 'Venados de la Sierra', 'Caimanes del Magdalena',
    'Albatros FC', 'Guepardos del Este', 'Rinocerontes FC', 'Mantarrayas del Mar', 'Colibríes del Jardín',
    'Truenos del Oeste', 'Relámpagos FC'
  ];
  const ESTADIOS = [
    'Los Almendros', 'La Ceiba', 'El Bosque', 'Las Palmas', 'San Rafael', 'La Colina', 'El Refugio', 'Santa Lucía',
    'Los Pinos', 'La Floresta', 'El Mirador', 'Las Acacias', 'Villa Real', 'La Esperanza', 'El Prado', 'Monteverde'
  ];
  const NOMBRES = [
    'Santiago', 'Mateo', 'Sebastián', 'Nicolás', 'Samuel', 'Alejandro', 'Daniel', 'Juan Pablo', 'Andrés', 'Felipe',
    'Tomás', 'Emiliano', 'Martín', 'David', 'Gabriel', 'Julián', 'Diego', 'Camilo', 'Esteban', 'Lucas',
    'Miguel Ángel', 'Jerónimo', 'Simón', 'Maximiliano', 'Joaquín', 'Benjamín', 'Cristian', 'Kevin', 'Brayan', 'Jhon',
    'Óscar', 'Luis', 'Carlos', 'Jorge', 'Iván', 'Ricardo', 'Fernando', 'Manuel', 'Rafael', 'Hernán',
    'Wilmer', 'Yeison', 'Duván', 'Edwin', 'Fabián', 'Gustavo', 'Héctor', 'Jairo', 'Leonardo', 'Mauricio',
    'Néstor', 'Pablo', 'Rodrigo', 'Sergio', 'Víctor', 'Álvaro', 'Bruno', 'Elkin', 'Franco', 'Thiago'
  ];
  const APELLIDOS = [
    'García', 'Rodríguez', 'Martínez', 'López', 'González', 'Hernández', 'Pérez', 'Sánchez', 'Ramírez', 'Torres',
    'Flores', 'Rivera', 'Gómez', 'Díaz', 'Reyes', 'Morales', 'Cruz', 'Ortiz', 'Gutiérrez', 'Chávez',
    'Ramos', 'Vargas', 'Castillo', 'Jiménez', 'Moreno', 'Romero', 'Herrera', 'Medina', 'Aguilar', 'Castro',
    'Ruiz', 'Mendoza', 'Álvarez', 'Rojas', 'Vásquez', 'Cárdenas', 'Ospina', 'Restrepo', 'Cuadrado', 'Arango',
    'Zapata', 'Mosquera', 'Valencia', 'Palacios', 'Quintero', 'Salazar', 'Montoya', 'Londoño', 'Giraldo', 'Correa',
    'Murillo', 'Córdoba', 'Benítez', 'Navarro', 'Paredes', 'Sosa', 'Acosta', 'Méndez', 'Pineda', 'Cabrera'
  ];
  const PAISES = ['Colombia', 'Argentina', 'Uruguay', 'México', 'Chile', 'Perú', 'España', 'Brasil', 'Ecuador', 'Paraguay'];
  // once titular con los dorsales clásicos y nueve suplentes
  const TITULARES = ['Portero', 'Lateral derecho', 'Lateral izquierdo', 'Defensa central', 'Mediocampista defensivo', 'Defensa central',
    'Extremo derecho', 'Mediocampista central', 'Delantero centro', 'Mediocampista ofensivo', 'Extremo izquierdo'];
  const SUPLENTES = ['Portero', 'Defensa central', 'Carrilero derecho', 'Carrilero izquierdo', 'Interior izquierdo',
    'Volante derecho', 'Mediapunta', 'Segundo delantero', 'Delantero centro'];

  /** Generador pseudoaleatorio mulberry32 (idéntico al de la versión Java). */
  function azar(semilla) {
    let estado = semilla | 0;
    const siguiente = () => {
      estado = (estado + 0x6D2B79F5) | 0;
      let t = estado;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    const entero = limite => Math.floor(siguiente() * limite);
    return {
      entero,
      elegir: opciones => opciones[entero(opciones.length)],
      mezclar(lista) {
        for (let i = lista.length - 1; i > 0; i--) {
          const j = entero(i + 1);
          [lista[i], lista[j]] = [lista[j], lista[i]];
        }
      }
    };
  }

  const documento = a => '1' + (a.entero(900000000) + 100000000);

  function plantilla(a) {
    const dorsales = [];
    for (let d = 12; d <= 30; d++) dorsales.push(d);
    a.mezclar(dorsales);
    const nombres = new Set();
    const jugadores = [];
    for (let i = 0; i < TITULARES.length + SUPLENTES.length; i++) {
      let nombre;
      do { nombre = a.elegir(NOMBRES) + ' ' + a.elegir(APELLIDOS); } while (nombres.has(nombre));
      nombres.add(nombre);
      const titular = i < TITULARES.length;
      jugadores.push({
        id: 0, nombre, documento: documento(a),
        dorsal: titular ? i + 1 : dorsales[i - TITULARES.length],
        posicion: titular ? TITULARES[i] : SUPLENTES[i - TITULARES.length]
      });
    }
    return jugadores;
  }

  /**
   * Crea `cantidad` equipos nuevos (sin repetir nombres ya usados) y una
   * plantilla para cada uno de los `porCompletar` equipos existentes.
   */
  function generar(semilla, cantidad, nombresUsados, porCompletar) {
    const a = azar(semilla);
    const usados = new Set(nombresUsados.map(n => n.trim().toLowerCase()));
    const nombres = EQUIPOS.slice();
    a.mezclar(nombres);
    const equipos = [];
    for (const nombre of nombres) {
      if (equipos.length >= cantidad) break;
      if (usados.has(nombre.toLowerCase())) continue;
      const estadio = 'Estadio ' + a.elegir(ESTADIOS);
      const dt = { nombre: a.elegir(NOMBRES) + ' ' + a.elegir(APELLIDOS), documento: documento(a), nacionalidad: a.elegir(PAISES) };
      equipos.push({ nombre, estadio, dt, jugadores: plantilla(a) });
    }
    const plantillas = [];
    for (let i = 0; i < porCompletar; i++) plantillas.push(plantilla(a));
    return { equipos, plantillas };
  }

  return { MAXIMO_EQUIPOS, generar };
})();
