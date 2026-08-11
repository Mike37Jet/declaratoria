import { useRef, useState } from 'react'
import { Printer, Download } from 'lucide-react'
import { HER_NAME, HER_NICKNAME, YOUR_NICKNAME } from '../config'

type Props = { yesDate: Date }

/** La butaca que eligió en la taquilla. Debe coincidir con BoxOffice. */
const SEAT_LABEL = 'C4'

/**
 * Arte del boleto: recorte vertical de la foto IMAX, en proporción de póster.
 *
 * Se prueban las dos extensiones porque al cambiar la foto es fácil dejarla
 * como .png; si solo se buscara el .jpg, el boleto saldría en negro sin avisar.
 */
const POSTER_SOURCES = ['jpg', 'png'].map(
  (ext) => `${import.meta.env.BASE_URL}photos/poster-boleto.${ext}`,
)

/**
 * Ancho fijo del boleto en pantalla. Las muescas del troquelado se calculan
 * a partir de él, así que no puede ser fluido sin descuadrarlas.
 */
const TICKET_W = 300
/** Surcos por borde: 3 al medio y uno a cada lado, como en el boleto real. */
const SCALLOPS = 5

/**
 * Geometría del troquelado, medida sobre el boleto de La Odisea.
 *
 * Tres cosas lo definen, y las tres son fáciles de equivocar:
 *  - Los surcos de los extremos caen JUSTO EN LA ESQUINA, así que se ven
 *    partidos por la mitad. Por eso se cuentan 3 enteros al medio y medio
 *    surco a cada lado: los centros van en 0, ¼, ½, ¾ y el ancho completo.
 *  - Cada surco es un semicírculo hondo, con el centro sobre el borde.
 *  - Entre surco y surco el papel queda PLANO, no en punta.
 *
 *   depth = cuánto muerde hacia dentro (igual al radio, al ser semicírculo)
 *   r     = radio del surco. Súbelo para surcos más anchos y hondos.
 *   xs    = centro horizontal de cada surco
 */
function scallopGeometry(width: number) {
  const spacing = width / (SCALLOPS - 1)
  const r = spacing * 0.35
  const xs = Array.from({ length: SCALLOPS }, (_, i) => i * spacing)
  return { depth: r, r, xs }
}

/** Sans gruesa de sistema: el título va en caja alta muy espaciada. */
const SANS =
  'ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'

/** "11 de agosto de 2026" */
function formatDate(d: Date) {
  return d.toLocaleDateString('es-ES', { day: 'numeric', month: 'long', year: 'numeric' })
}

/** "21:30" */
function formatTime(d: Date) {
  return d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Tira de muescas semicirculares que imita el troquelado del boleto.
 * Se pinta del color del fondo, encima del borde, para simular el recorte.
 *
 * El arco claro que sigue a cada muesca es el filo del papel: sin él, en el
 * borde inferior (que ya es casi negro) el troquelado no se vería.
 */
function Scallops({ edge }: { edge: 'top' | 'bottom' }) {
  const { depth, r, xs } = scallopGeometry(TICKET_W)
  // El centro va sobre el borde: solo se ve la mitad del círculo.
  const cy = edge === 'top' ? 0 : depth
  return (
    <svg
      aria-hidden
      width={TICKET_W}
      height={depth}
      viewBox={`0 0 ${TICKET_W} ${depth}`}
      className={`pointer-events-none absolute inset-x-0 z-20 ${edge === 'top' ? 'top-0' : 'bottom-0'}`}
    >
      {xs.map((x) => (
        <circle key={x} cx={x} cy={cy} r={r} fill="var(--color-cinema)" />
      ))}
      {/* Filo del papel: sin él, el corte no se ve sobre las zonas oscuras. */}
      {xs.map((x) => (
        <circle
          key={`edge-${x}`}
          cx={x}
          cy={cy}
          r={r + 0.5}
          fill="none"
          stroke="rgba(255,255,255,0.34)"
          strokeWidth={1}
        />
      ))}
    </svg>
  )
}

/**
 * Recorta las muescas del troquelado en el canvas y les deja el filo claro,
 * para que el borde se lea también sobre las zonas oscuras del boleto.
 */
function punchScallops(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const { r, xs } = scallopGeometry(w)
  const centers: [number, number][] = xs.flatMap(
    (x): [number, number][] => [
      [x, 0],
      [x, h],
    ],
  )

  ctx.globalCompositeOperation = 'destination-out'
  ctx.fillStyle = '#000'
  for (const [x, y] of centers) {
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.globalCompositeOperation = 'source-over'
  ctx.strokeStyle = 'rgba(255,255,255,0.34)'
  ctx.lineWidth = 2
  for (const [x, y] of centers) {
    ctx.beginPath()
    ctx.arc(x, y, r + 1, 0, Math.PI * 2)
    ctx.stroke()
  }
}

/**
 * Bancos de niebla en la franja donde la foto se funde con el texto, para que
 * el título no se apoye sobre un degradado plano.
 *
 * Cada uno es un óvalo de borde muy suave. Las posiciones van en tanto por uno
 * del boleto, así que sirven igual para la pantalla y para el canvas.
 */
const FOG = [
  { x: 0.28, y: 0.667, rx: 0.36, ry: 0.067, a: 0.2 },
  { x: 0.69, y: 0.689, rx: 0.33, ry: 0.059, a: 0.17 },
  { x: 0.5, y: 0.644, rx: 0.42, ry: 0.052, a: 0.14 },
  { x: 0.17, y: 0.711, rx: 0.24, ry: 0.044, a: 0.16 },
  { x: 0.84, y: 0.652, rx: 0.22, ry: 0.041, a: 0.13 },
]

/** La niebla en CSS, para el boleto de pantalla. */
const fogCss = FOG.map(
  (f) =>
    `radial-gradient(ellipse ${(f.rx * 200).toFixed(1)}% ${(f.ry * 200).toFixed(1)}% at ${(f.x * 100).toFixed(1)}% ${(f.y * 100).toFixed(1)}%, rgba(226,222,230,${f.a}) 0%, rgba(226,222,230,0) 70%)`,
).join(', ')

/** La misma niebla en el canvas, para la imagen descargable. */
function drawFog(ctx: CanvasRenderingContext2D, w: number, h: number) {
  for (const f of FOG) {
    const rx = f.rx * w
    const ry = f.ry * h
    ctx.save()
    // Se dibuja un círculo y se aplasta, que es como se hace un óvalo difuso.
    ctx.translate(f.x * w, f.y * h)
    ctx.scale(1, ry / rx)
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, rx)
    g.addColorStop(0, `rgba(226,222,230,${f.a})`)
    g.addColorStop(0.7, 'rgba(226,222,230,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.arc(0, 0, rx, 0, Math.PI * 2)
    ctx.fill()
    ctx.restore()
  }
}

/**
 * Grano de película. Se genera una baldosa de ruido una sola vez y se repite,
 * así el boleto de pantalla y la imagen descargable llevan el mismo grano.
 *
 * Son motas blancas y negras sobre transparente, a partes iguales, y se pintan
 * en mezcla normal. Con `overlay` el grano desaparecía en las zonas oscuras
 * —que aquí son casi todo el boleto— porque ese modo modula según el fondo.
 */
const GRAIN_TILE = 128
/** Sube esto si quieres más grano; a partir de 0.15 ya ensucia la foto. */
const GRAIN_ALPHA = 0.06

let grainCanvas: HTMLCanvasElement | null = null
function grainTile() {
  if (grainCanvas) return grainCanvas
  const c = document.createElement('canvas')
  c.width = GRAIN_TILE
  c.height = GRAIN_TILE
  const g = c.getContext('2d')
  if (g) {
    const noise = g.createImageData(GRAIN_TILE, GRAIN_TILE)
    for (let i = 0; i < noise.data.length; i += 4) {
      const n = Math.random() - 0.5
      const v = n > 0 ? 255 : 0
      noise.data[i] = v
      noise.data[i + 1] = v
      noise.data[i + 2] = v
      // Igual de probable aclarar que oscurecer: el brillo medio no cambia.
      noise.data[i + 3] = Math.abs(n) * 2 * 255
    }
    g.putImageData(noise, 0, 0)
  }
  grainCanvas = c
  return c
}

let grainUrl: string | null = null
function grainDataUrl() {
  if (!grainUrl) grainUrl = grainTile().toDataURL()
  return grainUrl
}

/** El mismo grano en el canvas, a la escala que le toca según el ancho. */
function drawGrain(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const pattern = ctx.createPattern(grainTile(), 'repeat')
  if (!pattern) return
  // El canvas es más grande que el boleto de pantalla: se escala la baldosa
  // para que el grano se vea del mismo tamaño en los dos.
  const scale = w / TICKET_W
  pattern.setTransform(new DOMMatrix([scale, 0, 0, scale, 0, 0]))
  ctx.save()
  // Sin esto el navegador interpola la baldosa al ampliarla y se lleva por
  // delante el grano: las motas se promedian y no queda casi nada.
  ctx.imageSmoothingEnabled = false
  ctx.globalAlpha = GRAIN_ALPHA
  ctx.fillStyle = pattern
  ctx.fillRect(0, 0, w, h)
  ctx.restore()
}

/** Dibuja una imagen recortada tipo `object-fit: cover`. */
function drawCover(ctx: CanvasRenderingContext2D, img: HTMLImageElement, w: number, h: number) {
  const scale = Math.max(w / img.width, h / img.height)
  const dw = img.width * scale
  const dh = img.height * scale
  ctx.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh)
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}

/** Carga la primera de las fuentes que exista. */
async function loadPoster() {
  for (const src of POSTER_SOURCES) {
    const img = await loadImage(src).catch(() => null)
    if (img) return img
  }
  console.warn('No encontré el arte del boleto en', POSTER_SOURCES)
  return null
}

/** Texto centrado con espaciado entre letras, que el canvas no trae de fábrica. */
function tracked(ctx: CanvasRenderingContext2D, text: string, cx: number, y: number, sp: number) {
  const chars = [...text]
  const width = chars.reduce((sum, c) => sum + ctx.measureText(c).width + sp, -sp)
  let x = cx - width / 2
  for (const c of chars) {
    ctx.fillText(c, x, y)
    x += ctx.measureText(c).width + sp
  }
}

/**
 * Dibuja el boleto conmemorativo en un canvas para descargarlo como imagen.
 * Se dibuja a mano (sin librerías) para que funcione sin conexión.
 */
async function renderTicketImage(yesDate: Date): Promise<Blob | null> {
  // Sin esto el canvas dibujaría con la tipografía de reserva.
  await document.fonts.ready
  const photo = await loadPoster()

  const W = 900
  const H = 1350
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  const cx = W / 2

  // La foto llena el boleto de borde a borde: ella es el póster.
  ctx.fillStyle = '#0d0709'
  ctx.fillRect(0, 0, W, H)
  if (photo) drawCover(ctx, photo, W, H)

  // Velos oscuros arriba y abajo para que el texto se lea encima
  const top = ctx.createLinearGradient(0, 0, 0, 330)
  top.addColorStop(0, 'rgba(6, 3, 4, 0.92)')
  top.addColorStop(1, 'rgba(6, 3, 4, 0)')
  ctx.fillStyle = top
  ctx.fillRect(0, 0, W, 330)

  const bottom = ctx.createLinearGradient(0, 620, 0, H)
  bottom.addColorStop(0, 'rgba(6, 3, 4, 0)')
  bottom.addColorStop(0.42, 'rgba(6, 3, 4, 0.86)')
  bottom.addColorStop(1, 'rgba(6, 3, 4, 0.99)')
  ctx.fillStyle = bottom
  ctx.fillRect(0, 620, W, H - 620)

  // La niebla y el grano van sobre el velo y bajo el texto.
  drawFog(ctx, W, H)
  drawGrain(ctx, W, H)

  ctx.textAlign = 'start'
  ctx.textBaseline = 'alphabetic'

  // Reparto
  ctx.fillStyle = '#ffffff'
  ctx.font = `600 20px ${SANS}`
  tracked(ctx, `${HER_NICKNAME.toUpperCase()}     ${YOUR_NICKNAME.toUpperCase()}`, cx, 132, 6)

  // Formato, arriba del todo
  ctx.fillStyle = 'rgba(255,255,255,0.9)'
  ctx.font = `600 19px ${SANS}`
  tracked(ctx, 'CADA ESCENA EN', cx, 205, 9)

  // El wordmark va con degradado metálico a rojo, como en el boleto real.
  const imaxSize = 92
  const imax = ctx.createLinearGradient(0, 285 - imaxSize * 0.78, 0, 285 + imaxSize * 0.06)
  imax.addColorStop(0, '#ffffff')
  imax.addColorStop(0.42, '#eceff4')
  imax.addColorStop(0.58, '#e0525c')
  imax.addColorStop(1, '#a8121c')
  ctx.fillStyle = imax
  ctx.font = `900 ${imaxSize}px ${SANS}`
  ctx.shadowColor = 'rgba(0,0,0,0.7)'
  ctx.shadowBlur = 24
  tracked(ctx, 'IMAX', cx, 285, 8)
  ctx.shadowBlur = 0

  // Bloque de título. Nada baja de ~1260: por debajo muerde el troquelado.
  ctx.fillStyle = 'rgba(255,255,255,0.88)'
  ctx.font = `600 17px ${SANS}`
  tracked(ctx, `UNA PELÍCULA DE ${YOUR_NICKNAME.toUpperCase()}`, cx, 915, 6)

  ctx.fillStyle = '#eef3f8'
  ctx.font = `900 62px ${SANS}`
  ctx.shadowColor = 'rgba(0,0,0,0.55)'
  ctx.shadowBlur = 18
  tracked(ctx, 'UNA HISTORIA', cx, 990, 8)
  tracked(ctx, 'DE AMOR', cx, 1056, 8)
  ctx.shadowBlur = 0

  // Subtitulo, como el nombre de una entrega de la saga
  ctx.fillStyle = '#f3d580'
  ctx.font = `600 16px ${SANS}`
  tracked(ctx, 'LA DECLARATORIA', cx, 1090, 10)

  ctx.fillStyle = '#e23b45'
  ctx.font = `800 28px ${SANS}`
  tracked(ctx, `SÓLO PARA ${HER_NICKNAME.toUpperCase()}`, cx, 1130, 7)

  // Separador y pie con la sala, el momento del sí y la butaca
  ctx.strokeStyle = 'rgba(255,255,255,0.25)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(cx - 150, 1172)
  ctx.lineTo(cx + 150, 1172)
  ctx.stroke()

  ctx.fillStyle = '#ffffff'
  ctx.font = `800 24px ${SANS}`
  tracked(ctx, `CINE ${HER_NAME.toUpperCase()}`, cx, 1208, 8)

  ctx.fillStyle = 'rgba(255,255,255,0.8)'
  ctx.font = `600 15px ${SANS}`
  tracked(
    ctx,
    `${formatDate(yesDate).toUpperCase()}  ·  ${formatTime(yesDate)}  ·  BUTACA ${SEAT_LABEL}`,
    cx,
    1238,
    2,
  )

  punchScallops(ctx, W, H)

  return new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
}

/**
 * Boleto conmemorativo con el troquelado de los que reparten en los estrenos:
 * la foto llena el boleto y todo el texto va encima. Se imprime o se guarda.
 */
export default function KeepsakeTicket({ yesDate }: Props) {
  const [saving, setSaving] = useState(false)
  // Si el arte no está en .jpg, se pasa a la siguiente extensión.
  const [posterIndex, setPosterIndex] = useState(0)
  const linkRef = useRef<HTMLAnchorElement>(null)

  /**
   * El nombre que propone "Guardar como PDF" sale del título del documento,
   * así que lo cambiamos mientras dura la impresión y lo dejamos como estaba.
   */
  const print = () => {
    const previous = document.title
    document.title = `Boleto conmemorativo - ${HER_NICKNAME} y ${YOUR_NICKNAME}`
    const restore = () => {
      document.title = previous
      window.removeEventListener('afterprint', restore)
    }
    window.addEventListener('afterprint', restore)
    window.print()
    // Safari en iOS no siempre dispara afterprint.
    setTimeout(restore, 4000)
  }

  const save = async () => {
    setSaving(true)
    try {
      const blob = await renderTicketImage(yesDate)
      if (!blob) return
      const url = URL.createObjectURL(blob)
      const a = linkRef.current
      if (a) {
        a.href = url
        a.download = `boleto-conmemorativo-${HER_NAME.toLowerCase()}.png`
        a.click()
      }
      // Se libera después de que el navegador arranque la descarga.
      setTimeout(() => URL.revokeObjectURL(url), 10_000)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex w-full flex-col items-center gap-5">
      <p className="font-pixel text-center text-[8px] leading-relaxed tracking-[0.25em] text-(--color-gold) sm:text-[9px]">
        ★ LLÉVATE TU BOLETO CONMEMORATIVO ★
      </p>

      {/* El boleto. data-print-ticket es lo único que sale al imprimir. */}
      <div
        data-print-ticket
        className="relative aspect-2/3 max-w-full overflow-hidden"
        style={{ width: TICKET_W, background: '#0d0709', fontFamily: SANS }}
      >
        <Scallops edge="top" />

        {/* La foto llena el boleto */}
        <img
          src={POSTER_SOURCES[posterIndex]}
          alt={`${HER_NICKNAME} y ${YOUR_NICKNAME}`}
          className="absolute inset-0 size-full object-cover"
          draggable={false}
          onError={() => setPosterIndex((i) => Math.min(i + 1, POSTER_SOURCES.length - 1))}
        />

        {/* Velo superior */}
        <div
          aria-hidden
          className="absolute inset-x-0 top-0 h-1/4"
          style={{
            background: 'linear-gradient(180deg, rgba(6,3,4,0.92) 0%, rgba(6,3,4,0) 100%)',
          }}
        />
        {/* Velo inferior */}
        <div
          aria-hidden
          className="absolute inset-x-0 bottom-0 h-1/2"
          style={{
            background:
              'linear-gradient(180deg, rgba(6,3,4,0) 0%, rgba(6,3,4,0.86) 42%, rgba(6,3,4,0.99) 100%)',
          }}
        />

        {/* Reparto y formato, arriba */}
        <div className="absolute inset-x-0 top-0 z-10 px-3 pt-9 text-center">
          <p className="text-[7px] font-semibold tracking-[0.3em] text-white">
            {HER_NICKNAME.toUpperCase()}
            <span className="mx-3" />
            {YOUR_NICKNAME.toUpperCase()}
          </p>
          <p className="mt-5 text-[6.5px] font-semibold tracking-[0.45em] text-white/90">
            CADA ESCENA EN
          </p>
          <p
            className="mt-1.5 text-[31px] leading-none font-black tracking-[0.09em]"
            style={{
              // Degradado metálico a rojo, como el wordmark del boleto real.
              backgroundImage:
                'linear-gradient(180deg, #ffffff 0%, #eceff4 42%, #e0525c 58%, #a8121c 100%)',
              WebkitBackgroundClip: 'text',
              backgroundClip: 'text',
              color: 'transparent',
              filter: 'drop-shadow(0 2px 10px rgba(0,0,0,0.75))',
            }}
          >
            IMAX
          </p>
        </div>

        {/* Niebla en la transición: va sobre los velos y bajo el texto */}
        <div aria-hidden className="absolute inset-0" style={{ backgroundImage: fogCss }} />

        {/* Grano de película, también bajo el texto para que no lo ensucie */}
        <div
          aria-hidden
          className="absolute inset-0"
          style={{
            backgroundImage: `url(${grainDataUrl()})`,
            backgroundRepeat: 'repeat',
            opacity: GRAIN_ALPHA,
          }}
        />

        {/* Título y pie, abajo */}
        <div className="absolute inset-x-0 bottom-0 z-10 px-3 pb-8 text-center">
          <p className="text-[6px] font-semibold tracking-[0.32em] text-white/90">
            UNA PELÍCULA DE {YOUR_NICKNAME.toUpperCase()}
          </p>
          <p
            className="mt-2 text-[21px] leading-[1.12] font-black tracking-[0.13em] text-[#eef3f8]"
            style={{ textShadow: '0 2px 12px rgba(0,0,0,0.6)' }}
          >
            UNA HISTORIA
            <br />
            DE AMOR
          </p>
          {/* Subtítulo, como el nombre de una entrega de la saga */}
          <p className="mt-1.5 text-[6.5px] font-semibold tracking-[0.4em] text-(--color-gold-light)">
            LA DECLARATORIA
          </p>
          {/* La línea roja no repite IMAX: eso ya lo dicen el wordmark de
              arriba y el pie de abajo. */}
          <p className="mt-1.5 text-[9px] font-extrabold tracking-[0.24em] text-[#e23b45]">
            SÓLO PARA {HER_NICKNAME.toUpperCase()}
          </p>
          <div className="mx-auto my-3 h-px w-1/2 bg-white/25" />

          <p className="text-[8px] font-extrabold tracking-[0.3em] text-white">
            CINE {HER_NAME.toUpperCase()}
          </p>
          <p className="mt-1.5 text-[5.5px] font-semibold tracking-[0.12em] text-white/80">
            {formatDate(yesDate).toUpperCase()}
            <span className="mx-1.5">·</span>
            {formatTime(yesDate)}
            <span className="mx-1.5">·</span>
            BUTACA {SEAT_LABEL}
          </p>
        </div>

        <Scallops edge="bottom" />
      </div>

      <div className="flex flex-wrap justify-center gap-3">
        <button
          onClick={print}
          className="font-pixel flex items-center gap-2.5 rounded-md border px-5 py-3 text-[8px] tracking-wider opacity-80 transition hover:opacity-100"
          style={{ borderColor: 'var(--color-gold)', color: 'var(--color-gold-light)' }}
        >
          <Printer aria-hidden size={14} strokeWidth={2.2} />
          IMPRIMIR MI BOLETO
        </button>
        <button
          onClick={save}
          disabled={saving}
          className="font-pixel flex items-center gap-2.5 rounded-md border px-5 py-3 text-[8px] tracking-wider opacity-80 transition hover:opacity-100 disabled:opacity-40"
          style={{ borderColor: 'var(--color-cream)', color: 'var(--color-cream)' }}
        >
          <Download aria-hidden size={14} strokeWidth={2.2} />
          {saving ? 'GUARDANDO…' : 'GUARDAR COMO IMAGEN'}
        </button>
        {/* Ancla oculta que dispara la descarga. */}
        <a ref={linkRef} className="hidden" aria-hidden />
      </div>

      {/* Al imprimir sale solo el boleto, centrado y a todo color. */}
      <style>{`
        @media print {
          @page { margin: 16mm; }
          body * { visibility: hidden; }
          [data-print-ticket], [data-print-ticket] * { visibility: visible; }
          [data-print-ticket] {
            position: absolute;
            top: 0;
            left: 50%;
            transform: translateX(-50%);
            print-color-adjust: exact;
            -webkit-print-color-adjust: exact;
          }
        }
      `}</style>
    </div>
  )
}
