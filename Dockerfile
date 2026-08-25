FROM gradle:9.4.0-jdk21 AS builder

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle
COPY config config
COPY app-bootstrap app-bootstrap
COPY common common
COPY database database
COPY module-identity module-identity
COPY module-flight module-flight
COPY module-booking module-booking
COPY module-payment module-payment
COPY module-checkin module-checkin
COPY module-notification module-notification
COPY module-scheduler module-scheduler
COPY module-ai module-ai
COPY module-web module-web

RUN chmod +x gradlew && ./gradlew --no-daemon :app-bootstrap:bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app-bootstrap/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
