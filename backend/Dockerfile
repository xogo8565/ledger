FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY src ./src

ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx768m -Dorg.gradle.daemon=false"
RUN gradle clean bootJar --no-daemon --max-workers=1

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-kor tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

ENV APP_DATA_DIR=/data
ENV APP_UPLOAD_DIR=/data/uploads
ENV APP_LOG_DIR=/data/logs

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]