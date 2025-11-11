# 1. Используем образ с Maven и JDK 21
FROM maven:3.9.8-eclipse-temurin-21 AS build

# 2. Рабочая директория
WORKDIR /app

# 3. Копируем pom.xml и подготавливаем зависимости
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 4. Копируем исходники и собираем jar
COPY src ./src
RUN mvn clean package -DskipTests

# 5. Второй этап - минимальный runtime
FROM eclipse-temurin:21-jre

WORKDIR /test-bot
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
