FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn -f backend/demo/demo/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# כאן חשוב שהנתיב יהיה זהה לזה שבפקודת ה-RUN למעלה
COPY --from=build /app/backend/demo/demo/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:10000}", "-jar", "app.jar"]