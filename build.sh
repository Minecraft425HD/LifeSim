#!/bin/bash
set -e

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  THRONGLETS V7  –  MAXIMUM LIFE                 ║"
echo "║  SNN + FEP (Friston) + NanoTransformer          ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
rm -rf out && mkdir -p out
echo "🔨 Kompiliere alle Klassen..."
javac --release 21 -d out src/thronglets/*.java
echo "✅ Kompilierung erfolgreich!"
echo ""
echo "🚀 Starte Simulation..."
java -cp out thronglets.MainV7
