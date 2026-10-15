FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/portfolio-service/pom.xml backend/portfolio-service/pom.xml
COPY backend/portfolio-service/src backend/portfolio-service/src

RUN mvn -B -ntp -f backend/portfolio-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/portfolio-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
