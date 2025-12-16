#!/bin/bash

# Скрипт для развертывания MCP Weather Server

set -e

echo "🚀 Deploying MCP Weather Server..."

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo "❌ Error: .env file not found!"
    echo "Please create .env file from .env.example:"
    echo "  cp .env.example .env"
    echo "  nano .env  # Add your API key"
    exit 1
fi

# Проверка наличия API ключа
if ! grep -q "OPENWEATHER_API_KEY=.\+" .env; then
    echo "❌ Error: OPENWEATHER_API_KEY is not set in .env file!"
    echo "Please add your OpenWeatherMap API key to .env file"
    exit 1
fi

echo "✅ Configuration validated"

# Остановка старых контейнеров
echo "🛑 Stopping old containers..."
docker-compose down

# Сборка нового образа
echo "🔨 Building new image..."
docker-compose build

# Запуск контейнеров
echo "▶️  Starting containers..."
docker-compose up -d

# Ожидание запуска
echo "⏳ Waiting for server to start..."
sleep 5

# Проверка здоровья
echo "🏥 Checking server health..."
if curl -s http://localhost:8080/health | grep -q "healthy"; then
    echo "✅ Server is healthy!"
    echo ""
    echo "📊 Server Status:"
    docker-compose ps
    echo ""
    echo "🎉 Deployment successful!"
    echo ""
    echo "Server is running at: http://localhost:8080"
    echo "Health check: http://localhost:8080/health"
    echo "MCP endpoint: http://localhost:8080/mcp"
    echo ""
    echo "View logs: docker-compose logs -f"
else
    echo "❌ Server health check failed!"
    echo "Check logs: docker-compose logs"
    exit 1
fi