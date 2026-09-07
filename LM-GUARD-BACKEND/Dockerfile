# Multi-stage build: compile with Maven + JDK, run on a slim JRE.
# Render (and most PaaS) have no native Java runtime, so this is how the
# backend deploys there - point a Render "Web Service" (env: docker) at this file.

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Cache dependencies separately from source so `mvn` doesn't re-download on every code change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/lm-guard-backend-*.jar app.jar

# Render sets $PORT; server.port in application.yml already reads it via SERVER_PORT,
# so map the two (Render's convention is $PORT, ours is SERVER_PORT).
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
