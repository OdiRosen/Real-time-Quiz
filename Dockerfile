# שלב 1: בנייה
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn -f backend/demo/demo/pom.xml clean package -DskipTests

# שלב 2: הרצה
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/backend/demo/demo/target/*.jar app.jar

EXPOSE 8080
# הוספת הגדרת הפורט ישירות ל-Runtime
ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]