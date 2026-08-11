/**
 * Genera el código QR y un boleto de cine imprimible con el QR integrado.
 *
 *   node scripts/generate-qr.mjs
 *
 * Salida (en la carpeta qr/):
 *   qr/qr-rommina.png  — el QR solo, alta resolución
 *   qr/ticket.html     — boleto de cine listo para imprimir (ábrelo y Cmd+P)
 *
 * La URL se lee de SITE_URL en src/config.ts — cámbiala ahí cuando publiques.
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import QRCode from 'qrcode'

const config = await readFile(new URL('../src/config.ts', import.meta.url), 'utf8')
const url = config.match(/SITE_URL\s*=\s*'([^']+)'/)?.[1]
if (!url) {
  console.error('No encontré SITE_URL en src/config.ts')
  process.exit(1)
}

await mkdir(new URL('../qr', import.meta.url), { recursive: true })

const qrOptions = {
  errorCorrectionLevel: 'H',
  margin: 2,
  color: { dark: '#4a0404', light: '#f5ead6' },
}

await QRCode.toFile(
  new URL('../qr/qr-rommina.png', import.meta.url).pathname,
  url,
  { ...qrOptions, width: 1200 },
)

const qrDataUrl = await QRCode.toDataURL(url, { ...qrOptions, width: 600 })

const ticket = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Boleto — Cine Rommina</title>
<style>
  @page { margin: 0; }
  body {
    margin: 0; min-height: 100vh; display: grid; place-items: center;
    background: #0a0505; font-family: Georgia, serif;
  }
  .ticket {
    position: relative; width: 720px; display: flex; overflow: hidden;
    background: linear-gradient(135deg, #f7efdd, #e7d8b8);
    border-radius: 18px; box-shadow: 0 20px 60px rgba(0,0,0,.6);
  }
  .main { flex: 1; padding: 40px 36px; border-right: 3px dashed #8b0000; }
  .stub {
    width: 230px; padding: 28px 20px; display: flex; flex-direction: column;
    align-items: center; justify-content: center; gap: 12px; text-align: center;
  }
  .notch {
    position: absolute; width: 34px; height: 34px; border-radius: 50%;
    background: #0a0505; left: calc(100% - 230px - 20px);
  }
  .notch.top { top: -17px; } .notch.bottom { bottom: -17px; }
  .kicker { font-size: 11px; letter-spacing: 5px; color: #8b0000; }
  h1 { margin: 10px 0 4px; font-size: 34px; color: #4a0404; }
  .sub { font-style: italic; color: #6b4c2a; margin: 0 0 22px; }
  .row { display: flex; gap: 26px; margin-top: 16px; }
  .field .k { font-size: 10px; letter-spacing: 3px; color: #8b0000; }
  .field .v { font-size: 15px; color: #2b1d14; margin-top: 3px; }
  img { width: 170px; height: 170px; border: 4px solid #4a0404; border-radius: 10px; }
  .scan { font-size: 11px; letter-spacing: 2px; color: #8b0000; }
  .hearts { font-size: 18px; }
</style>
</head>
<body>
  <div class="ticket">
    <div class="main">
      <div class="kicker">★ CINE ROMMINA · FUNCIÓN PRIVADA ★</div>
      <h1>Una historia de amor</h1>
      <p class="sub">Se admite exactamente una espectadora (y sus gatos)</p>
      <div class="row">
        <div class="field"><div class="k">FILA</div><div class="v">Mi corazón</div></div>
        <div class="field"><div class="k">BUTACA</div><div class="v">Única</div></div>
        <div class="field"><div class="k">FECHA</div><div class="v">Hoy y siempre</div></div>
        <div class="field"><div class="k">PRECIO</div><div class="v">Una sonrisa</div></div>
      </div>
    </div>
    <div class="stub">
      <img src="${qrDataUrl}" alt="QR de la función">
      <div class="scan">ESCANÉAME 🎬</div>
      <div class="hearts">❤️ 🐱 ❤️</div>
    </div>
    <div class="notch top"></div>
    <div class="notch bottom"></div>
  </div>
</body>
</html>`

await writeFile(new URL('../qr/ticket.html', import.meta.url), ticket)

console.log('✅ QR generado para:', url)
console.log('   qr/qr-rommina.png  (QR en alta resolución)')
console.log('   qr/ticket.html     (boleto imprimible — ábrelo y presiona Cmd+P)')
