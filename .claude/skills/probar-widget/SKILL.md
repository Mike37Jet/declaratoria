---
name: probar-widget
description: Instala el APK en el emulador, pone el widget del contador, lo redimensiona (2×1, 3×1, 4×1, 4×2) y verifica toques y redibujos.
disable-model-invocation: true
---

# Probar el widget en el emulador

Antes de empezar, **avisa al usuario**: tocar el botón de enviar publica un toque real en
Firebase y puede llegarle a Mimina.

1. Compilar: `cd android && ./gradlew --offline assembleDebug -q`.
2. Arrancar el AVD `Pixel_10a` si no hay dispositivo (`adb devices`), y esperar `sys.boot_completed`.
3. Instalar: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. **Esto borra los widgets
   del launcher**: hay que volver a ponerlos.
4. Poner el widget: abrir la app → «Añadir widget» → «Contador» → «Add to home screen».
   Esperar ~3 s entre pasos; si no, los toques se pierden y se abre otra app (Calendar, Chrome).
   Mirar cada pantalla con una captura antes de tocar.
5. Redimensionar: mantener pulsado el widget ~1 s; aparecen controles en los bordes. Arrastrar el
   inferior para la altura y el derecho para el ancho; tocar fuera para soltar.
   Un 2×1 mide ~180 dp, 3×1 ~270 dp, 4×1 ~370 dp, 4×2 ~370×200 dp.
6. Qué debe verse: mini (2×1, 1×1) = «♥ 17 días · 04 h» + minutos:segundos; franja (3×1, 4×1) =
   cajitas DÍAS/HORAS/MIN·SEG + ♥; completo (4×2) = título, cajitas y pie con el último toque.
7. Comprobar que **no hay parpadeo** tras un toque:
   `adb logcat -c`, tocar, y `adb logcat -d | grep -c "App widget created"` debe dar `0`.
   Un doble toque rápido debe producir un solo `Worker result SUCCESS`.
8. Reportar con capturas y el log, y decir qué tamaños no se probaron.
