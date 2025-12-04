FROM eclipse-temurin:23-jdk-alpine AS build
WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle

RUN ./gradlew dependencies --no-daemon || return 0

COPY src src

RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]