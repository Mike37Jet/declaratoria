/**
 * Genera una plantilla imprimible para dibujar el QR a mano.
 *
 *   node scripts/generate-qr-plantilla.mjs
 *
 * Salida:
 *   qr/plantilla-a-mano.html — ábrela y dale a imprimir (Cmd+P, tamaño real)
 *
 * Las casillas que hay que rellenar salen en gris muy claro: se imprime y se
 * repasan con rotulador. La URL se lee de SITE_URL en src/config.ts.
 *
 * Se usa corrección M a propósito: da la misma rejilla que L (29x29 para una
 * URL como la nuestra) pero tolera el doble de errores, que es justo lo que
 * hace falta cuando lo pinta una mano y no una impresora.
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import QRCode from 'qrcode'

/** Milímetros por casilla. 5 mm da un QR de ~15 cm, cómodo de pintar y de leer. */
const CELL_MM = 5
/** Margen blanco obligatorio alrededor del código, en casillas. */
const QUIET = 4
const EC = 'M'

const config = await readFile(new URL('../src/config.ts', import.meta.url), 'utf8')
const url = config.match(/SITE_URL\s*=\s*'([^']+)'/)?.[1]
if (!url) {
  console.error('No encontré SITE_URL en src/config.ts')
  process.exit(1)
}

const qr = QRCode.create(url, { errorCorrectionLevel: EC })
const size = qr.modules.size
const dark = (row, col) => qr.modules.get(row, col) === 1

/** El QR de referencia, para comparar mientras se pinta y para comprobarlo. */
const referencia = await QRCode.toDataURL(url, {
  errorCorrectionLevel: EC,
  margin: QUIET,
  width: 400,
})

const total = size + QUIET * 2
const filas = []
for (let r = 0; r < size; r++) {
  const celdas = []
  for (let c = 0; c < size; c++) {
    const clases = ['c']
    if (dark(r, c)) clases.push('n')
    // Líneas de guía cada 5 casillas, para no perder la cuenta al contar.
    if (c % 5 === 4 && c !== size - 1) clases.push('gx')
    if (r % 5 === 4 && r !== size - 1) clases.push('gy')
    celdas.push(`<i class="${clases.join(' ')}" data-r="${r}" data-c="${c}"></i>`)
  }
  filas.push(celdas.join(''))
}

const html = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Plantilla para dibujar el QR a mano</title>
<style>
  @page { size: A4; margin: 10mm; }
  body { margin: 0; font-family: Georgia, serif; color: #222; }
  h1 { font-size: 15pt; margin: 0 0 2mm; }
  .info { font-size: 9pt; color: #555; margin: 0 0 4mm; line-height: 1.5; }
  .info code { background: #f0f0f0; padding: 0 2px; }
  .hoja { display: flex; gap: 8mm; align-items: flex-start; }
  .rejilla {
    display: grid;
    grid-template-columns: repeat(${size}, ${CELL_MM}mm);
    grid-auto-rows: ${CELL_MM}mm;
    border: 0.3mm solid #999;
    /* El margen blanco es parte del código: sin él muchos lectores fallan. */
    padding: ${QUIET * CELL_MM}mm;
    outline: 0.6mm dashed #c00;
    outline-offset: 0;
    width: max-content;
  }
  .c { border-right: 0.15mm solid #ddd; border-bottom: 0.15mm solid #ddd; }
  .c.gx { border-right-color: #999; }
  .c.gy { border-bottom-color: #999; }
  .c.n { background: #d6d6d6; }
  .lado { width: 45mm; flex-shrink: 0; font-size: 8.5pt; line-height: 1.6; }
  .lado img { width: 100%; border: 0.3mm solid #ccc; }
  .lado h2 { font-size: 10pt; margin: 4mm 0 1mm; }
  .lado ol { padding-left: 4mm; margin: 0; }
  .lado li { margin-bottom: 2mm; }
  @media print { .noprint { display: none; } }
</style>
</head>
<body>
  <h1>Plantilla para dibujar el QR a mano</h1>
  <p class="info">
    <b>${url}</b><br>
    Rejilla ${size}×${size} · ${size * size} casillas · corrección ${EC} (tolera un ${EC === 'M' ? '15' : '?'} % de fallos) ·
    casilla de ${CELL_MM} mm → ${((size * CELL_MM) / 10).toFixed(1)} cm de lado
  </p>
  <p class="info noprint">
    Imprime a <b>tamaño real</b> (sin «ajustar a la página») y repasa con rotulador
    las casillas grises.
  </p>

  <div class="hoja">
    <div class="rejilla">${filas.join('')}</div>
    <div class="lado">
      <img src="${referencia}" alt="QR de referencia">
      <h2>Cómo hacerlo</h2>
      <ol>
        <li>Rellena <b>del todo</b> las casillas grises. No las contornees.</li>
        <li>Respeta el borde rojo punteado: ese margen blanco de ${QUIET} casillas
            es parte del código.</li>
        <li>Los tres cuadrados de las esquinas son los que primero busca el
            lector. Si algo te sale torcido, que no sea eso.</li>
        <li>Negro sobre blanco. Nada de colores claros ni de invertirlo.</li>
        <li>Las líneas grises marcadas van cada 5 casillas, para contar sin
            perderte.</li>
      </ol>
      <h2>Antes de darlo</h2>
      <ol>
        <li>Escanéalo tú con el celular.</li>
        <li>Si no lee, repasa los bordes de las casillas y que no haya huecos
            blancos dentro de las negras.</li>
      </ol>
    </div>
  </div>
</body>
</html>`

await mkdir(new URL('../qr', import.meta.url), { recursive: true })
await writeFile(new URL('../qr/plantilla-a-mano.html', import.meta.url), html, 'utf8')

console.log(`Plantilla lista: qr/plantilla-a-mano.html`)
console.log(`  ${size}x${size} = ${size * size} casillas, ${(size * CELL_MM) / 10} cm de lado`)
console.log(`  negras: ${filas.join('').split('class="c n').length - 1}`)
console.log(`  total con margen: ${(total * CELL_MM) / 10} cm`)
