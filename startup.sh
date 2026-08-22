#!/bin/bash

# AI IAM Gateway - Full Stack Startup Script
# This script starts all services needed for the application
# Usage: bash startup.sh

set -e

PROJECT_DIR="/Users/vaibhav/Documents/GitHub/ai-iam-gateway"
BACKEND_DIR="$PROJECT_DIR/backend/ai-security-gateway"
FRONTEND_DIR="$PROJECT_DIR/frontend"
INFRA_DIR="$PROJECT_DIR/infra"

echo "🚀 Starting AI IAM Gateway Stack..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Start Docker services (PostgreSQL)
echo "📦 1. Starting Docker services..."
cd "$INFRA_DIR"
docker compose up -d
echo "   ✅ PostgreSQL (5432) running"

# 2. Ensure Ollama is running and model is available
echo ""
echo "🤖 2. Checking Ollama setup..."
if ! curl -s http://localhost:11434/api/models > /dev/null 2>&1; then
  echo "   ⚠️  Ollama server not responding. Make sure Ollama is running:"
  echo "   → Open Ollama app or run: ollama serve"
  exit 1
fi

# Check if model exists
if ! ollama list 2>/dev/null | grep -q "llama3.2:1b"; then
  echo "   📥 Pulling llama3.2:1b model (this may take a few minutes)..."
  ollama pull llama3.2:1b
fi
echo "   ✅ Ollama model llama3.2:1b ready"

# 3. Start backend
echo ""
echo "🔧 3. Starting Spring Boot backend..."
cd "$BACKEND_DIR"
chmod +x mvnw
rm -f /tmp/backend.log
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local > /tmp/backend.log 2>&1 &
BACKEND_PID=$!
echo "   📍 Backend PID: $BACKEND_PID"

# Wait for backend to start
sleep 8
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
  echo "   ✅ Backend running on http://localhost:8080"
else
  echo "   ❌ Backend failed to start. Check /tmp/backend.log"
  tail -20 /tmp/backend.log
  exit 1
fi

# 4. Install frontend dependencies (if needed)
echo ""
echo "📦 4. Setting up frontend..."
cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
  echo "   📥 Installing npm dependencies..."
  npm install
fi
echo "   ✅ Frontend dependencies ready"

# 5. Start frontend
echo ""
echo "🎨 5. Starting Next.js frontend..."
rm -f /tmp/frontend.log
nohup npm run dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "   📍 Frontend PID: $FRONTEND_PID"

sleep 3
if curl -s http://localhost:3000 > /dev/null 2>&1; then
  echo "   ✅ Frontend running on http://localhost:3000"
else
  echo "   ⚠️  Frontend may still be starting..."
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✨ All services started successfully!"
echo ""
echo "📍 Access points:"
echo "   • Web App:  http://localhost:3000"
echo "   • API:      http://localhost:8080"
echo "   • Ollama:   http://localhost:11434"
echo "   • Database: localhost:5432 (ai/ai)"
echo ""
echo "📋 Log files:"
echo "   • Backend:  tail -f /tmp/backend.log"
echo "   • Frontend: tail -f /tmp/frontend.log"
echo ""
echo "🛑 To stop services:"
echo "   • Backend:  kill $BACKEND_PID"
echo "   • Frontend: kill $FRONTEND_PID"
echo "   • Docker:   docker compose -f $INFRA_DIR/docker-compose.yml down"
echo ""
