# AlphaSport · Formatos de campeonato y reglas

Investigación de los formatos que usa el programa, cómo se aplican en ligas reales y **cómo los implementamos** (incluyendo las simplificaciones).

---

## 1. Liga (todos contra todos)

**Regla.** Todos los equipos se enfrentan entre sí. Victoria 3 puntos, empate 1, derrota 0 (los puntos se pueden configurar). Gana quien acumule más puntos.

- **Una vuelta:** cada equipo juega una vez contra cada rival → `n·(n−1)/2` partidos en `n−1` jornadas (si `n` es impar, `n` jornadas y en cada una descansa un equipo).
- **Ida y vuelta (doble round-robin):** cada pareja juega dos veces, una de local y otra de visitante. Es el formato de LaLiga, Premier League y Serie A (20 equipos, 38 jornadas).

**Implementación.** `GeneradorFixture.todosContraTodos` usa el **método del círculo** (el mismo del prototipo de P2): un equipo queda fijo y los demás rotan una posición por jornada. Se alterna local/visitante para equilibrar las localías; la segunda vuelta repite las jornadas invirtiendo la localía.

## 2. Copa (eliminación directa)

**Regla.** El que pierde queda eliminado. Se arma una llave: dieciseisavos (32), octavos (16), cuartos (8), semifinales (4) y final (2).

- **Partido único:** si hay empate se define con prórroga y/o penales (Copa Mundial desde octavos, FA Cup).
- **Ida y vuelta:** avanza quien tenga mejor **marcador global**; si el global empata, penales al final de la vuelta (fases finales de Copa Libertadores y Champions). La regla del **gol de visitante** fue eliminada por la UEFA desde la temporada 2021/22 y por la CONMEBOL desde 2022, por eso **no** la usamos.

**Implementación.**
- **Siembra:** los equipos se ordenan por sorteo (o por orden de registro si se desactiva el sorteo; el primero registrado es el cabeza de serie 1). La llave se arma con el orden estándar `1-8, 4-5, 2-7, 3-6` para que el 1 y el 2 solo puedan cruzarse en la final (`GeneradorFixture.ordenSembrado`).
- **Equipos que no son potencia de 2:** se completa la llave con posiciones *libres*; los mejores sembrados pasan directo a la segunda ronda (*bye*). Ej.: 6 equipos → los sembrados 1 y 2 pasan directo, 3-6 y 4-5 juegan.
- En ida y vuelta, el mejor sembrado juega la **vuelta de local**.
- Opción de **final a partido único** aunque el resto sea a ida y vuelta.
- **Prórroga:** el programa registra el marcador final (con prórroga incluida) y, si sigue el empate, exige los **penales**.

## 3. Tipo Mundial (grupos y eliminatorias)

**Regla.** Los equipos se dividen en grupos (normalmente de 4) que juegan todos contra todos (una o dos vueltas). Los mejores de cada grupo (ej. 1.º y 2.º) pasan a una llave de eliminación directa. Es el modelo de la Copa del Mundo, la Copa América y la Eurocopa.

- **Mundial 2026:** 48 equipos en 12 grupos de 4; clasifican los 2 primeros de cada grupo **más los 8 mejores terceros** → dieciseisavos con 32 equipos.

**Implementación.**
- **Sorteo por bombos:** tras ordenar los equipos (sorteo u orden de registro), el bombo 1 tiene los primeros, el 2 los siguientes, etc.; cada grupo recibe un equipo de cada bombo (`repartirPorBombos`). Si se desactiva el sorteo, registrar primero a los equipos más fuertes los deja como cabezas de grupo.
- **Clasificados:** primero todos los 1.º (ordenados entre sí por puntos y desempates), luego los 2.º, etc. Si no completan una llave de 4/8/16/32, se completa con los mejores de la siguiente posición (*mejores terceros*). Para comparar equipos de grupos distintos se omite el enfrentamiento directo (nunca jugaron entre sí).
- En la primera ronda de la llave se intercambian rivales para que **no se crucen dos equipos del mismo grupo**.

## 4. Apertura y Clausura (con liguilla)

**Regla.** Sistema dominante en América (Liga BetPlay en Colombia, Liga MX, MLS). La temporada se divide en dos torneos independientes (Apertura y Clausura), cada uno con su campeón.

- **Fase regular:** todos contra todos a una vuelta.
- **Postemporada:** los mejores de la tabla (ej. 8) clasifican a:
  - **Liguilla** de eliminación directa: 1.º vs 8.º, 2.º vs 7.º, etc. En la Liga MX las series son a ida y vuelta y, si el global empata, avanza el **mejor ubicado** en la tabla (*ventaja deportiva*), excepto en la final, que se define en prórroga y penales.
  - **Cuadrangulares semifinales** (Liga BetPlay): los 8 clasificados se dividen en 2 grupos de 4 que juegan todos contra todos a ida y vuelta; los ganadores de cada grupo juegan la **final** (ida y vuelta).

**Implementación.**
- Liguilla: llave sembrada por la tabla (1-8, 4-5, 2-7, 3-6). Opción de **ventaja deportiva** (no se aplica en la final).
- Cuadrangulares: reparto en **serpentina** según la tabla (grupo A = 1.º, 4.º, 5.º, 8.º; grupo B = 2.º, 3.º, 6.º, 7.º) y final entre los ganadores de cada grupo. *Simplificación:* en la Liga BetPlay los grupos se sortean con el 1.º y el 2.º como cabezas de grupo.
- **Siguiente torneo corto:** el menú *Iniciar siguiente torneo corto (Clausura)* guarda el historial del torneo terminado y crea uno nuevo con los mismos equipos, plantillas y reglas.
- *No implementado:* el *play-in* de la Liga MX (puestos 7 a 10) ni la tabla de reclasificación anual.

## 5. Tipo Champions (sistema suizo)

**Regla.** Desde la temporada 2024/25 la Champions League, la Europa League y la Conference League usan una **fase de liga**: 36 clubes en **una sola tabla**, pero cada uno juega solo **8 partidos contra 8 rivales distintos** (4 de local y 4 de visitante). Al final:

- Puestos **1 a 8**: pasan directo a octavos de final.
- Puestos **9 a 24**: juegan un **playoff (repechaje)** a ida y vuelta (9-16 contra 17-24). Los ganadores completan los octavos.
- Puestos 25 a 36: eliminados.

**Implementación.**
- *Simplificación importante:* la UEFA sortea los 8 rivales de cada equipo **antes** de empezar, con 4 bombos por coeficiente (2 rivales de cada bombo). Nosotros usamos el **sistema suizo clásico** (el de los torneos de ajedrez): la **ronda 1** enfrenta la mitad de arriba del sorteo contra la otra mitad, y desde la **ronda 2** se emparejan equipos con puntaje parecido que **no se hayan enfrentado** (búsqueda con retroceso / *backtracking* en `emparejarSinRepetir`). Se mantiene la idea central: una tabla, pocos partidos por equipo, sin repetir rival.
- Se alterna la localía (juega de local quien tenga menos partidos de local).
- Si el número de equipos es impar, en cada ronda **descansa** el peor ubicado que todavía no haya descansado (sin sumar puntos).
- Configurable: número de rondas, clasificados directos y cantidad en repechaje (por defecto 8 y 16, como la UEFA). El repechaje empareja el mejor contra el peor de la zona (9 vs 24, 10 vs 23…) y luego la llave final siembra a los directos contra los ganadores del repechaje.

## 6. Doble oportunidad (doble eliminación)

**Regla.** No se usa en el fútbol profesional FIFA, pero es muy útil en fútbol amateur, torneos relámpago y fútbol base: nadie queda fuera por un solo mal partido.

- Perder una vez manda al equipo a la **llave de perdedores**.
- Perder dos veces lo elimina.
- **Gran final:** campeón de ganadores vs campeón de perdedores. Con **reinicio** (*bracket reset*): si gana el de perdedores, ambos tienen una derrota y se juega un partido más.

**Implementación.** El estado se calcula contando las derrotas de cada equipo: 0 derrotas = llave de ganadores, 1 = llave de perdedores, 2 = eliminado. En cada ronda se emparejan los de la llave de ganadores siguiendo el orden de la llave, y en la de perdedores los que cayeron antes contra los que acaban de caer. Todos los partidos son eliminatorios (empate → penales). Un torneo de `n` equipos tiene `2n−2` partidos (`2n−1` si hay reinicio), lo que verifica la prueba automática.

---

## Criterios de desempate

Cuando dos o más equipos empatan en puntos, cada liga aplica criterios distintos y en distinto orden. Por ejemplo, LaLiga aplica primero el **enfrentamiento directo**, mientras que la Premier League aplica primero la **diferencia de goles**; la FIFA, en los grupos del Mundial, también da prioridad al enfrentamiento directo. Por eso el programa deja **escoger cuáles criterios usar y en qué orden**.

| Criterio | Cómo se calcula |
|---|---|
| Diferencia de goles | Goles a favor − goles en contra |
| Goles a favor | Total de goles anotados |
| Enfrentamiento directo | Mini tabla **solo con los partidos entre los equipos empatados**: puntos, luego diferencia y luego goles en esos partidos. Funciona con 3 o más empatados. |
| Partidos ganados | Número de victorias |
| Juego limpio (Fair Play) | Suma las tarjetas de los jugadores del equipo: amarilla = 1 punto, roja = 3; gana el que tenga **menos**. *Simplificación:* la FIFA distingue roja directa (4) y doble amarilla (3). |
| Sorteo | Número aleatorio fijo por equipo dentro del campeonato (no cambia cada vez que se refresca la tabla). |

**Algoritmo (`TablaPosiciones.desempatar`).** Se ordena por puntos; cada bloque de equipos empatados se ordena con el primer criterio; los que siguen empatados forman un bloque nuevo que se desempata con el segundo criterio, y así sucesivamente. Esto es importante para el enfrentamiento directo: si empatan A, B y C y el criterio separa a A, la mini tabla entre B y C se vuelve a calcular **solo con sus partidos**. Si se acaban los criterios se usa el sorteo para que el orden nunca quede al azar entre refrescos.

## Tarjetas y suspensiones

**Regla.** El árbitro amonesta a un **jugador** (amarilla) o lo expulsa (roja). Dos amarillas en el mismo partido equivalen a una roja. Un expulsado queda **suspendido** un número de fechas que fija el comité disciplinario de cada torneo (normalmente 1 por doble amarilla y más por una roja directa, según la falta). En muchas ligas también suspende la **acumulación** de amarillas (por ejemplo 5 en el torneo).

**Implementación (`service.Sanciones`).**
- Cada `Tarjeta` guarda equipo, jugador (por un id que no cambia aunque se edite el dorsal), tipo y, si es roja, las **fechas de suspensión** que se escriben al registrarla.
- La suspensión cubre los **siguientes N partidos del equipo** del jugador en el orden del fixture (incluye las rondas que se generen después). Una fecha se cuenta como **cumplida** cuando ese partido está jugado; un partido aplazado no la cumple hasta que se juegue.
- Al abrir un partido se muestran los suspendidos; si se le pone tarjeta a uno, el programa pide confirmación (el jugador no debería haber jugado).
- Una segunda amarilla del mismo jugador en el mismo partido se puede convertir en roja (1 fecha).
- *No implementado:* la suspensión automática por acumulación de amarillas; se puede registrar manualmente con una roja del número de fechas correspondiente.

## Otras reglas que maneja el programa

- **Aplazado / suspendido:** el partido no cuenta para la tabla y no se puede avanzar de fase hasta reprogramarlo y jugarlo (o darlo por W.O.).
- **W.O. (walkover):** 3-0 a favor del equipo que se presentó.
- **Retiro de un equipo** con el campeonato en curso: sus partidos pendientes de la fase actual se dan por W.O. al rival y no se tiene en cuenta en nuevos emparejamientos.
- **Corrección de resultados:** como la tabla se calcula desde los partidos, cualquier corrección se refleja de inmediato. Si cambia quién clasificó a una fase ya generada, el programa lo detecta y ofrece regenerar esas fases.
