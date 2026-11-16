# Para Rommina (declaratoria)

Dos cosas en un repo: la página web de la declaración y una app de Android (ROMMICINES)
con widgets. Todo el texto visible va **en español**.

## Web (raíz)

```bash
npm run dev      # servidor local
npm run build    # tsc -b && vite build
npm run lint     # oxlint
```

React 19 + TypeScript + Vite + Tailwind 4. Se publica sola en GitHub Pages
(`.github/workflows/deploy.yml`). Nombres, URL y fecha del "sí" viven en `src/config.ts`.

## Android (`android/`)

```bash
cd android
./gradlew --offline assembleDebug testDebugUnitTest -q   # compilar y probar
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Emulador: `Pixel_10a`.

Widget del contador ([ContadorWidget.kt](android/app/src/main/java/com/roguelito/rommicines/ContadorWidget.kt)),
con tres diseños según el tamaño: mini (2×1, 1×1), franja (3×1, 4×1) y completo (4×2).
Los umbrales están en `COMPLETO` / `FRANJA` / `MINI` (en dp; un 2×1 mide ~180 dp).

El contador tiene 10 diseños ([Tema.kt](android/app/src/main/java/com/roguelito/rommicines/Tema.kt)),
cada widget el suyo, y se eligen en `ElegirTemaActivity` (ajustes del widget o «Añadir widget» en la app).
Cada diseño tiene sus tres layouts `widget_<tema>.xml`, `_franja` y `_mini` con los mismos ids
(un widget no puede cambiar de fuente en tiempo de ejecución). El ♥ va como `♥&#xFE0E;` para que
no salga como emoji y tome el color del diseño.

La **libreta compartida** vive en `android/.../libreta/` (`LibretaActivity`, datos en `Libreta.kt`):
Firestore + acceso anónimo + código de 6 letras para unir el segundo celular. Las reglas están en
`android/firestore.rules` y se pegan a mano en la consola de Firebase (Firestore → Reglas).
Diseño de papelería (letra Caveat, `colors_libreta.xml`), nada del estilo cine.

## Cosas que no se ven en el código

- **La fecha del "sí" está duplicada**: `OFFICIAL_YES_DATE` en `src/config.ts` y `DESDE` en
  `android/.../Juntos.kt`. Si cambia una, cambia la otra. Lo mismo con la URL de la página.
- **Los toques son reales.** Enviar un toque desde el emulador publica en el tema `toques` de
  Firebase y le puede llegar a Mimina. Antes de probar el botón de enviar, avisa al usuario.
- **Al clonar faltan dos archivos de Firebase** (están en `.gitignore`): `android/app/google-services.json`
  y la llave de abajo. Sin el primero no compila. Cómo conseguirlos: README, «Archivos que no están en el repo».
- **Llave de Firebase**: `android/app/secreto/firebase-llave.json` viaja dentro del APK y está
  en `.gitignore`. Nunca leerla, imprimirla ni subirla (un hook lo bloquea).
- **No reactivar `RescheduleReceiver` de WorkManager.** Está quitado en el manifiesto a
  propósito: activarlo/desactivarlo en cada envío hacía parpadear los widgets.
- **Reinstalar el APK borra los widgets del launcher** del emulador; hay que volver a ponerlos.
- Los tamaños de widget se redimensionan arrastrando los controles tras mantener pulsado.
