# שלב 1: בנייה
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app

# מעתיקים את כל הפרויקט פנימה
COPY . .

# הרצת Maven על הנתיב המדויק שרואים בתמונה
RUN mvn -f backend/demo/demo/pom.xml clean package -DskipTests

# שלב 2: הרצה
FROM openjdk:17-jdk-slim
WORKDIR /app

# העתקה מהנתיב הפנימי שבו ה-JAR נוצר
COPY --from=build /app/backend/demo/demo/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]