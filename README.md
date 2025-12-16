# Weather MCP Server

MCP (Model Context Protocol) сервер для получения данных о погоде через OpenWeatherMap API.

## Описание

Этот сервер реализует протокол MCP и предоставляет инструменты для получения:
- Текущей погоды для указанного города
- 5-дневного прогноза погоды

## Возможности

- Реализация MCP протокола (JSON-RPC 2.0)
- Интеграция с OpenWeatherMap API
- Поддержка различных единиц измерения (metric, imperial, standard)
- Контейнеризация с Docker
- Health check endpoints
- Автоматическое логирование

## Технологии

- Kotlin
- Ktor (Web Framework)
- kotlinx.serialization (JSON сериализация)
- Docker & Docker Compose
- OpenWeatherMap API

## Предварительные требования

1. JDK 17 или выше (для локальной разработки)
2. Docker и Docker Compose (для контейнерного развертывания)
3. API ключ от OpenWeatherMap (получить на https://openweathermap.org/api)

## Установка и запуск

### Локальный запуск

1. Клонируйте репозиторий
2. Создайте файл `.env` на основе `.env.example`:
   ```bash
   cp .env.example .env
   ```

3. Добавьте ваш API ключ в `.env`:
   ```
   OPENWEATHER_API_KEY=ваш_api_ключ
   ```

4. Запустите сервер:
   ```bash
   export OPENWEATHER_API_KEY=ваш_api_ключ
   ./gradlew run
   ```

### Запуск через Docker

1. Создайте файл `.env`:
   ```bash
   cp .env.example .env
   ```

2. Добавьте ваш API ключ в `.env`

3. Соберите и запустите контейнер:
   ```bash
   docker-compose up -d
   ```

4. Проверьте статус:
   ```bash
   docker-compose ps
   ```

5. Просмотр логов:
   ```bash
   docker-compose logs -f
   ```

### Развертывание на VPS

1. Подключитесь к VPS:
   ```bash
   ssh user@your-vps-ip
   ```

2. Установите Docker и Docker Compose (если не установлены)

3. Скопируйте проект на VPS:
   ```bash
   git clone your-repo-url
   cd mcp-power-server
   ```

4. Настройте переменные окружения:
   ```bash
   cp .env.example .env
   nano .env  # добавьте ваш API ключ
   ```

5. Запустите сервер:
   ```bash
   docker-compose up -d
   ```

6. Настройте автозапуск (опционально):
   ```bash
   # Docker Compose автоматически перезапустит контейнер (restart: unless-stopped)
   ```

## API Endpoints

### Health Check
```bash
GET /health
```

### MCP Endpoint
```bash
POST /mcp
Content-Type: application/json
```

## MCP Tools

### 1. get_current_weather

Получить текущую погоду для города.

**Параметры:**
- `city` (обязательный): Название города (например, "London", "Moscow")
- `units` (опциональный): Единицы измерения - "metric" (по умолчанию), "imperial", "standard"

**Пример запроса:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "get_current_weather",
    "arguments": {
      "city": "London",
      "units": "metric"
    }
  }
}
```

### 2. get_weather_forecast

Получить 5-дневный прогноз погоды.

**Параметры:**
- `city` (обязательный): Название города
- `units` (опциональный): Единицы измерения

**Пример запроса:**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "get_weather_forecast",
    "arguments": {
      "city": "Moscow",
      "units": "metric"
    }
  }
}
```

## MCP Методы

- `initialize` - Инициализация MCP соединения
- `tools/list` - Получить список доступных инструментов
- `tools/call` - Вызвать инструмент

## Тестирование

```bash
# Проверка здоровья сервера
curl http://localhost:8080/health

# Тест MCP initialize
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize"
  }'

# Тест получения погоды
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "get_current_weather",
      "arguments": {
        "city": "London",
        "units": "metric"
      }
    }
  }'
```

## Разработка

### Сборка проекта
```bash
./gradlew build
```

### Запуск тестов
```bash
./gradlew test
```

### Сборка Fat JAR
```bash
./gradlew buildFatJar
```

### Сборка Docker образа
```bash
docker build -t mcp-weather-server:latest .
```

## Структура проекта

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/bazik/
│   │       ├── mcp/
│   │       │   ├── models/      # MCP модели данных
│   │       │   └── McpService.kt # MCP бизнес-логика
│   │       ├── weather/
│   │       │   ├── models/      # OpenWeatherMap модели
│   │       │   └── WeatherService.kt # Работа с API
│   │       ├── Application.kt   # Точка входа
│   │       ├── Routing.kt       # Маршруты
│   │       └── Serialization.kt # Настройка сериализации
│   └── resources/
│       └── application.conf     # Конфигурация
```

## Troubleshooting

### Ошибка "API key is not configured"
Убедитесь, что переменная окружения `OPENWEATHER_API_KEY` установлена.

### Контейнер не запускается
Проверьте логи:
```bash
docker-compose logs
```

### Порт 8080 занят
Измените порт в `docker-compose.yml` или `.env`:
```yaml
ports:
  - "8081:8080"  # Используем 8081 вместо 8080
```

## Лицензия

MIT

