# Multi-stage build для минимизации размера образа
FROM gradle:8-jdk17 AS build

WORKDIR /app

# Копируем gradle wrapper и зависимости
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Загружаем зависимости
RUN ./gradlew dependencies --no-daemon

# Копируем исходный код
COPY src src

# Собираем приложение
RUN ./gradlew buildFatJar --no-daemon

# Финальный образ
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Создаем пользователя для запуска приложения
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Копируем jar файл из build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Меняем владельца файлов
RUN chown -R appuser:appgroup /app

# Переключаемся на непривилегированного пользователя
USER appuser

# Открываем порт
EXPOSE 8080

# Устанавливаем healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]