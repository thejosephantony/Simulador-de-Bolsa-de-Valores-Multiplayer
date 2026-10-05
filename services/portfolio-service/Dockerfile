FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY services/pom.xml services/pom.xml
COPY services/portfolio-service/pom.xml services/portfolio-service/pom.xml
COPY services/portfolio-service/src services/portfolio-service/src

RUN mvn -B -ntp -f services/portfolio-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/portfolio-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
