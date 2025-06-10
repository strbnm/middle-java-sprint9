FROM eclipse-temurin:21-jammy
LABEL authors="strbnm"
ARG CI_VERSION=""
WORKDIR /opt/app
COPY build/libs/*${CI_VERSION}.jar app.jar
RUN addgroup --system app && adduser --system --group app
RUN chmod +x app.jar && chown -R app:app /opt/app
EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]