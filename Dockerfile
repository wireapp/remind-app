FROM registry.access.redhat.com/ubi9/openjdk-21 AS build
LABEL description="Wire Remind App"
LABEL project="wire-apps:reminder"

# Run build as root to avoid permission issues with Gradle cache
USER root

WORKDIR /setup

# Copy the entire project context for building
COPY . .

RUN ./gradlew clean build --no-daemon

FROM eclipse-temurin:21-jre

# Workaround for CVE-2026-114938 (Java4Shell, WPB-30191): disable the AVX-512 Base64 intrinsic.
# Remove once the image runs Temurin >= 21.0.13.1.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"

WORKDIR /deployments

ENV LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 --from=build /setup/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 --from=build /setup/build/quarkus-app/quarkus-run.jar /deployments/quarkus-run.jar
COPY --chown=185 --from=build /setup/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 --from=build /setup/build/quarkus-app/quarkus/ /deployments/quarkus/

RUN mkdir -p /deployments/storage && chown -R 185:0 /deployments/storage

EXPOSE 8080
USER 185

ENTRYPOINT ["java", "-jar", "-Dquarkus.http.host=0.0.0.0", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", "/deployments/quarkus-run.jar"]
