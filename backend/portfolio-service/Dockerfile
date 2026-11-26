FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/ledger-core/pom.xml backend/ledger-core/pom.xml
COPY backend/ledger-core/src backend/ledger-core/src
COPY backend/portfolio-service/pom.xml backend/portfolio-service/pom.xml
COPY backend/portfolio-service/src backend/portfolio-service/src

# Instala o POM pai e o ledger-core no repositório local antes de empacotar o serviço.
RUN mvn -B -ntp -f backend/pom.xml -N install \
 && mvn -B -ntp -f backend/ledger-core/pom.xml install -DskipTests \
 && mvn -B -ntp -f backend/portfolio-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/portfolio-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
