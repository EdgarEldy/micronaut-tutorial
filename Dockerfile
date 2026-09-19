# Multi-stage image for the packaged JVM application.
# Stage 1 compiles and packages with Maven, stage 2 keeps only a JRE and the runnable jar.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copying the pom alone first lets Docker cache the dependency download until the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped here: they rely on Test Resources, which needs a Docker daemon a build stage
# does not have. They run in CI with mvn verify instead.
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user so a compromised process has no rights on the container.
RUN useradd --system --no-create-home micronaut
USER micronaut

# The shaded jar is self-contained. The prod environment turns on application-prod.yml, which reads
# the database and the JWT key locations from environment variables (no defaults).
COPY --from=build --chown=micronaut /build/target/micronaut-tutorial-*.jar ./app.jar
ENV MICRONAUT_ENVIRONMENTS=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
