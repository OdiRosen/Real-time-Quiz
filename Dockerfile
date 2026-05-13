# שלב 1: בנייה
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

COPY . .

RUN mvn -f backend/demo/demo/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

COPY --from=build /app/backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
