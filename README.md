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
