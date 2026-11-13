# Layered Spring Boot image built from the jar ("mvnw package" first); build context: 03-containers-ci/catalog.
#   docker build -t hephaestus/catalog:dockerfile 03-containers-ci/catalog
ARG JRE_IMAGE=eclipse-temurin:25-jre

FROM ${JRE_IMAGE} AS extract
WORKDIR /work
COPY target/catalog.jar catalog.jar
# Split the jar into layers.idx layers; an empty layer gets an empty directory so every COPY below works
RUN java -Djarmode=tools -jar catalog.jar extract --layers --launcher --destination extracted \
    && mkdir -p extracted/dependencies extracted/spring-boot-loader extracted/snapshot-dependencies extracted/application

FROM ${JRE_IMAGE}
ARG REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/cucumber405d/hephaestus" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.title="containers-ci-catalog"
WORKDIR /app
# Least to most frequently changed: a code change rebuilds only the last layer
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./
USER 1000:1000
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Exec form: java is PID 1 and receives SIGTERM directly
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
