#!/bin/bash

# AI IAM Gateway - Cleanup & Shutdown Script
# This script gracefully stops all running services and optionally cleans up resources
# Usage: bash cleanup.sh [--full]
# Options:
#   --full    Remove Docker volumes and node_modules (complete cleanup)

set -e

PROJECT_DIR="/Users/vaibhav/Documents/GitHub/ai-iam-gateway"
INFRA_DIR="$PROJECT_DIR/infra"
BACKEND_DIR="$PROJECT_DIR/backend/ai-security-gateway"
FRONTEND_DIR="$PROJECT_DIR/frontend"
FULL_CLEANUP=false

# Check for --full flag
if [ "$1" == "--full" ]; then
  FULL_CLEANUP=true
fi

echo "🛑 Shutting down AI IAM Gateway Stack..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Stop backend
echo ""
echo "1️⃣  Stopping backend..."
if pkill -f "mvnw spring-boot:run" 2>/dev/null; then
  echo "   ✅ Backend stopped"
else
  echo "   ℹ️  Backend was not running"
fi

# 2. Stop frontend
echo ""
echo "2️⃣  Stopping frontend..."
if pkill -f "npm run dev" 2>/dev/null; then
  echo "   ✅ Frontend stopped"
else
  echo "   ℹ️  Frontend was not running"
fi

# 3. Stop Docker services
echo ""
echo "3️⃣  Stopping Docker services..."
if docker compose -f "$INFRA_DIR/docker-compose.yml" down 2>/dev/null; then
  echo "   ✅ Docker services stopped"
else
  echo "   ⚠️  Docker services may not have been running"
fi

# 4. Kill any lingering Java processes (optional)
echo ""
echo "4️⃣  Cleaning up lingering processes..."
pkill -9 -f "mvnw" 2>/dev/null || true
pkill -9 -f "java" 2>/dev/null || true
echo "   ✅ Process cleanup complete"

# 5. Full cleanup if requested
if [ "$FULL_CLEANUP" = true ]; then
  echo ""
  echo "🧹 FULL CLEANUP MODE"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Remove Docker volumes
  echo ""
  echo "5️⃣  Removing Docker volumes..."
  docker volume prune -f 2>/dev/null || true
  echo "   ✅ Volumes removed"

  # Remove node_modules and build artifacts
  echo ""
  echo "6️⃣  Removing build artifacts..."
  cd "$FRONTEND_DIR"
  rm -rf node_modules .next
  echo "   ✅ Frontend artifacts removed"

  cd "$BACKEND_DIR"
  rm -rf target
  echo "   ✅ Backend artifacts removed"

  # Clear log files
  echo ""
  echo "7️⃣  Clearing log files..."
  rm -f /tmp/backend.log /tmp/frontend.log
  echo "   ✅ Log files cleared"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✨ Shutdown complete!"
echo ""

if [ "$FULL_CLEANUP" = true ]; then
  echo "📊 Full cleanup details:"
  echo "   • Backend Java processes: killed"
  echo "   • Frontend Node processes: killed"
  echo "   • Docker containers: stopped & removed"
  echo "   • Docker volumes: removed"
  echo "   • node_modules: removed"
  echo "   • Target (build): removed"
  echo "   • Log files: cleared"
  echo ""
  echo "💾 To restart fresh, run: bash startup.sh"
else
  echo "📊 Graceful shutdown details:"
  echo "   • Backend process: stopped"
  echo "   • Frontend process: stopped"
  echo "   • Docker containers: stopped (volumes preserved)"
  echo "   • Build artifacts: preserved"
  echo ""
  echo "💾 To restart, run: bash startup.sh"
  echo "🧹 For full cleanup (remove everything), run: bash cleanup.sh --full"
fi

echo ""
