# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

COPY pom.xml .
# Credencial de leitura do GitHub Packages (srportto-commons-java) so existe durante este RUN --
# settings.xml e escrito e apagado na mesma camada, nunca persiste na imagem final. Requer o
# secret "github_token" declarado no build do compose (ver apps/docker-compose.yml).
RUN --mount=type=secret,id=github_token \
    mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>x-access-token</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/github_token)" > /root/.m2/settings.xml && \
    mvn -B dependency:go-offline && \
    rm -f /root/.m2/settings.xml

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/contratocommand-*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
