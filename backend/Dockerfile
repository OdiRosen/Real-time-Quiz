# שלב 1: בנייה באמצעות Maven
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
# מעתיקים את הקבצים שנמצאים בתוך תיקיית backend (כי אנחנו כבר שם)
COPY . .
# מריצים בנייה ומדלגים על טסטים כדי לחסוך זמן ובעיות ב-Build
RUN mvn clean package -DskipTests

# שלב 2: הרצה
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# מעתיקים את ה-JAR שנוצר בשלב הקודם
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]