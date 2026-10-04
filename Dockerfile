# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────────
# Backend AURA (Spring Boot 3, Java 17) en contenedor.
#
# Dos etapas: compila con Maven (con caché de ~/.m2 entre builds, así un
# cambio de código no vuelve a bajar dependencias) y corre solo el JRE con el
# jar. La JVM queda acotada a la memoria del contenedor (ver docker-compose.yml).
# ─────────────────────────────────────────────────────────────────────────────

# ── 1. Compilar ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencias primero: esta capa solo se rehace si cambia el pom.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline

# Código: cada cambio en src/ rehace solo desde aquí.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -DskipTests package \
 && cp target/*.jar /app/app.jar

# ── 2. Correr ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=America/Bogota

# Memoria y CPU contenidas:
#  · MaxRAMPercentage: el heap es un % del límite del contenedor (no de la máquina).
#  · SerialGC: el recolector más liviano; para un solo desarrollador sobra.
#  · TieredStopAtLevel=1: arranca más rápido y gasta menos CPU compilando (desarrollo).
#  · ExitOnOutOfMemoryError: si se queda sin memoria, se cae y se reinicia en vez de quedar colgado.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Bogota"

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 9001

# El .env apunta la base a localhost (la máquina); dentro del contenedor
# localhost es el propio contenedor, así que se cambia por host.docker.internal.
# El .env no se toca: sirve igual para correr fuera de Docker.
ENTRYPOINT ["sh", "-c", "export DB_URL=$(echo \"$DB_URL\" | sed -e 's#//localhost#//host.docker.internal#' -e 's#//127.0.0.1#//host.docker.internal#'); exec java -jar /app/app.jar"]
