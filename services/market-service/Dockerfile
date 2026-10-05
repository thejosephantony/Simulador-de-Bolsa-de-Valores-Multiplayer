FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY services/pom.xml services/pom.xml
COPY services/market-service/pom.xml services/market-service/pom.xml
COPY services/market-service/src services/market-service/src

RUN mvn -B -ntp -f services/market-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/market-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
