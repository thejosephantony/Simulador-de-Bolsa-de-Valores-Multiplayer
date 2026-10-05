FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY services/pom.xml services/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/auth-service/src services/auth-service/src

RUN mvn -B -ntp -f services/auth-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/auth-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
