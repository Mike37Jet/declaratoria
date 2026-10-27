---
name: convenciones-proyecto
description: Reglas y trampas de este repo (idioma, fecha duplicada, toques reales, llave de Firebase, WorkManager). Léela antes de tocar la app de Android, los widgets o src/config.ts.
user-invocable: false
---

- Todo texto visible, nombres de variables y comentarios nuevos van **en español**, como el resto del código.
- La fecha del «sí» y la URL de la página están **duplicadas**: `src/config.ts` y
  `android/app/src/main/java/com/roguelito/rommicines/Juntos.kt`. Cambiar las dos.
- Los toques que se envían desde el emulador son **reales** (Firebase, tema `toques`). Avisar antes de probar.
- `android/app/secreto/` y `google-services.json` no se leen ni se editan; un hook lo bloquea.
- No volver a habilitar `RescheduleReceiver` de WorkManager (está removido en el manifiesto: activarlo
  hacía que el launcher recreara y parpadeara los widgets en cada envío).
- Tras enviar o recibir un toque solo se redibuja el widget completo: `actualizarTodos(soloConPie = true)`.
  Los diseños mini y franja no se vuelven a inflar.
- Umbrales de tamaño del widget en dp: `MINI` 100×40, `FRANJA` 230×56, `COMPLETO` 250×100. Un 2×1 mide ~180 dp.
