# שלב 1: בנייה
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# העתקת כל הפרויקט
COPY . .

# הרצת Maven על הנתיב המדויק שלך
RUN mvn -f backend/demo/demo/pom.xml clean package -DskipTests

# שלב 2: הרצה - שימוש ב-Temurin במקום OpenJDK שנכשל
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# העתקה מהנתיב שבו ה-JAR נוצר
COPY --from=build /app/backend/demo/demo/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]