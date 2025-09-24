FROM registry.access.redhat.com/ubi9/openjdk-21 AS build
LABEL description="Wire Remind App"
LABEL project="wire-apps:reminder"

WORKDIR /setup

# Copy the entire project context for building
COPY . .

RUN ./gradlew clean build --no-daemon

FROM registry.access.redhat.com/ubi9/openjdk-21

ENV LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 --from=build /setup/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 --from=build /setup/build/quarkus-app/quarkus-run.jar /deployments/quarkus-run.jar
COPY --chown=185 --from=build /setup/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 --from=build /setup/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
