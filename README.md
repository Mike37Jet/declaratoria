# 🎬 Para Rommina — Una función de cine privada

Página de declaración con temática cinematográfica: marquesina, telón, cuenta
regresiva de cinta, carta escrita a mano, carrusel tipo tira de película,
la gran pregunta y un contador en vivo desde el "sí". Con gatitos pixel art. 🐱❤️

## Desarrollo local

```bash
npm install
npm run dev
```

## Personalizar (todo en español y en un solo lugar cada cosa)

| Qué | Dónde |
|---|---|
| Nombres, apodos, URL final y fecha oficial del "sí" | [src/config.ts](src/config.ts) |
| El texto de la carta (array `PARAGRAPHS`) | [src/components/LoveLetter.tsx](src/components/LoveLetter.tsx) |
| Fotos del carrusel y sus pies de foto | Archivos en `public/photos/` + array `PHOTOS` en [src/config.ts](src/config.ts) |
| Créditos finales | [src/components/Credits.tsx](src/components/Credits.tsx) |

**Fotos:** reemplaza `public/photos/foto1.svg` … `foto6.svg` por tus fotos
reales (jpg/png/webp) y actualiza los nombres en `PHOTOS` dentro de
`src/config.ts`. Puedes poner más o menos de 6.

**La foto IMAX:** la que tenga `imax: true` en `PHOTOS` se proyecta en la
proporción 1.43:1 del IMAX — el fotograma crece de alto y se enmarca en
dorado, igual que en el cine cuando la película pasa a formato IMAX.
Marca solo una y elige la que mejor luzca en vertical.

**Contador:** la primera vez que ella presione "Sí", la fecha queda guardada en
su navegador y el contador arranca. Después del gran día, fija
`OFFICIAL_YES_DATE` en `src/config.ts` con la fecha real para que el contador
sea idéntico en todos los dispositivos.

## Publicar en GitHub Pages (gratis, para siempre)

1. Crea un repositorio en GitHub (por ejemplo `declaratoria`).
2. Sube el código:

   ```bash
   git init && git add -A && git commit -m "Función privada para Rommina 🎬"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/declaratoria.git
   git push -u origin main
   ```

3. En GitHub: **Settings → Pages → Source: GitHub Actions**. El workflow
   [.github/workflows/deploy.yml](.github/workflows/deploy.yml) publica solo en
   cada push.
4. Tu URL será `https://TU_USUARIO.github.io/declaratoria/`.

## Avisos cada vez que presione "Sí" 🔔

Cada "Sí" envía una notificación a un canal secreto de [ntfy.sh](https://ntfy.sh)
(configurado en `NOTIFY_TOPIC` dentro de `src/config.ts`):

1. Instala la app gratuita **ntfy** (App Store / Play Store).
2. Suscríbete al tema con el nombre exacto que está en `NOTIFY_TOPIC`.
3. El **primer "Sí"** llega con prioridad máxima, la fecha exacta y la línea
   lista para pegar en `OFFICIAL_YES_DATE`. Las repeticiones llegan marcadas
   como "↻ Repitió la función" (no cambian la fecha original).

También puedes verlo en el navegador: `https://ntfy.sh/<NOTIFY_TOPIC>`
(ntfy solo guarda ~12 horas de historial en la web; la app sí conserva lo
que recibe, así que mantén la suscripción activa en tu celular).

## Generar el código QR y el boleto imprimible

1. Pon la URL real en `SITE_URL` dentro de `src/config.ts`.
2. Ejecuta:

   ```bash
   npm run qr
   ```

3. Obtienes `qr/qr-rommina.png` (QR en alta resolución) y `qr/ticket.html`:
   un boleto de cine listo para imprimir (ábrelo en el navegador y `Cmd+P`).
   Imprímelo, recórtalo y entrégaselo como invitación a la función. 🎟️

## Widgets de Android 📱

En [android/](android/) vive una app pequeña (ROMMICINES) con dos widgets:

- **Carrusel de fotos:** las fotos pasan solas cada 6 segundos en una cinta de
  película. Tocar una foto abre la app para agregar o quitar fotos; el botón
  "▶ VER LA FUNCIÓN" abre la página.
- **Tiempo juntos:** días, horas, minutos y segundos desde el "sí". En el widget
  pequeño, tocar el contador envía un toque y el corazón abre la página. En el
  grande, el botón de corazón envía el toque y el contador abre la página.

**Toques con significado:** abre la app y elige «Estoy pensando en ti», «Te
extraño» o «Quiero un abrazo». El botón de enviar y los widgets usan ese mensaje.
La notificación recibida permite responder con «Un beso de vuelta» sin abrir la
app; muestra si se envió o permite volver a intentarlo si falló. El beso responde
al celular que mandó el mensaje y no cambia el mensaje elegido para el widget.
Instala el APK actualizado en ambos celulares para ver las frases y responder.
Los toques enviados por una versión anterior siguen llegando como corazones.

**Inicio:** el envío tiene una sola acción principal, estado visible y reintento.
Toca una foto para verla o quitarla del carrusel; puedes deshacer la última
eliminación. «Ayuda» contiene los pasos por tarea. La revisión de las diez
heurísticas de Nielsen está en [android/docs/usabilidad-inicio.md](android/docs/usabilidad-inicio.md).

La primera vez el carrusel arranca con las fotos de `public/photos/`. La fecha
del "sí" y la URL están en `android/app/src/main/java/com/roguelito/rommicines/Juntos.kt`
(si cambian en `src/config.ts`, cámbialas allí también).

### Archivos que no están en el repo

Al clonar, la app **no compila** hasta que pongas estos dos archivos de Firebase
(están en `.gitignore` a propósito):

| Archivo | Dónde va | Cómo conseguirlo |
|---|---|---|
| `google-services.json` | `android/app/` | Consola de Firebase → Configuración del proyecto → Tus apps → app Android `com.roguelito.rommicines` → «Descargar google-services.json» |
| `firebase-llave.json` | `android/app/secreto/` | Consola de Firebase → Configuración del proyecto → Cuentas de servicio → «Generar nueva clave privada»; renombra el archivo descargado a `firebase-llave.json` |

Sin el primero falla el build (plugin de Google Services); sin el segundo la app compila
pero no puede enviar toques. La llave privada viaja dentro del APK, así que no compartas
el APK con nadie más.

Para instalarla: abre la carpeta `android/` en Android Studio y dale ▶ con el
celular conectado, o compila el APK y pásalo por WhatsApp o Drive:

```bash
cd android && ./gradlew assembleDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.
