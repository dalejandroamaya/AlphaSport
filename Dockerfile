# AlphaSport web publicado en internet (Render). La base es la de Neon: se pasa en la variable secreta
# DATABASE_URL, nunca en el repositorio. Render da PORT y RENDER_EXTERNAL_HOSTNAME.

FROM eclipse-temurin:21-jdk AS compilar
WORKDIR /app
COPY src src
COPY lib/postgresql-42.7.14.jar lib/
# La web no usa JavaFX: se compila todo menos el escritorio.
RUN find src -name "*.java" -not -path "*/escritorio/*" > fuentes.txt \
 && javac -encoding UTF-8 -nowarn -cp lib/postgresql-42.7.14.jar -d clases @fuentes.txt

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilar /app/clases clases
COPY lib/postgresql-42.7.14.jar lib/
COPY web web
CMD ["java", "-XX:MaxRAMPercentage=70", "-cp", "clases:lib/postgresql-42.7.14.jar", "alphasport.ui.AlphaSportApp"]
