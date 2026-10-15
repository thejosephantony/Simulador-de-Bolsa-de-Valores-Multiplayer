FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/auth-service/pom.xml backend/auth-service/pom.xml
COPY backend/auth-service/src backend/auth-service/src

RUN mvn -B -ntp -f backend/auth-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/auth-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
