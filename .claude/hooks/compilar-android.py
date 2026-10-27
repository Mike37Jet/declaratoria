#!/usr/bin/env python3
"""Stop: si cambió algo en android/ desde la última comprobación, compila y corre los tests.

Si falla, devuelve el error (exit 2) para que Claude lo corrija antes de terminar.
"""
import hashlib
import json
import os
import subprocess
import sys

datos = json.load(sys.stdin)
if datos.get("stop_hook_active"):
    sys.exit(0)  # ya se reintentó una vez: no entrar en bucle

raiz = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
android = os.path.join(raiz, "android")

archivos = subprocess.run(
    ["git", "ls-files", "-m", "-o", "--exclude-standard", "android"],
    cwd=raiz, capture_output=True, text=True,
).stdout.split()
fuentes = sorted(f for f in archivos if f.endswith((".kt", ".xml", ".kts")) and os.path.exists(os.path.join(raiz, f)))
if not fuentes:
    sys.exit(0)

huella = hashlib.sha1()
for f in fuentes:
    huella.update(f.encode())
    with open(os.path.join(raiz, f), "rb") as fh:
        huella.update(fh.read())
marca = os.path.join("/tmp", "claude-android-" + hashlib.sha1(raiz.encode()).hexdigest()[:8])
if os.path.exists(marca) and open(marca).read() == huella.hexdigest():
    sys.exit(0)  # nada nuevo desde la última compilación buena

r = subprocess.run(
    ["./gradlew", "--offline", "assembleDebug", "testDebugUnitTest", "-q"],
    cwd=android, capture_output=True, text=True,
)
if r.returncode != 0:
    print("La app de Android no compila o fallan los tests:\n" + (r.stdout + r.stderr)[-3000:], file=sys.stderr)
    sys.exit(2)
open(marca, "w").write(huella.hexdigest())
