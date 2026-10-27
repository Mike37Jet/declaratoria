#!/usr/bin/env python3
"""PreToolUse: impide leer o editar la llave privada de Firebase y google-services.json."""
import json
import sys

PROTEGIDOS = ("secreto/", "firebase-llave", "adminsdk", "google-services.json")

datos = json.load(sys.stdin)
entrada = datos.get("tool_input", {})
objetivo = entrada.get("file_path") or entrada.get("command") or ""

if any(p in objetivo for p in PROTEGIDOS):
    print(
        "Bloqueado: la llave de Firebase (android/app/secreto/) no se lee ni se edita desde Claude. "
        "Si de verdad hace falta, pídelo en el chat.",
        file=sys.stderr,
    )
    sys.exit(2)
