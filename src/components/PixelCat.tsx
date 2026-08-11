/**
 * Gatito pixel art dibujado a mano (rejilla de 14×10 "píxeles").
 * `.` vacío · `X` cuerpo · `o` ojo · `n` nariz · `p` interior de oreja
 */
const CAT_MAP = [
  '.X...X........',
  '.Xp.pX........',
  '.XXXXX.......X',
  'XoXXXoX.....XX',
  'XXXnXXX.....X.',
  '.XXXXXXXXX.XX.',
  '.XXXXXXXXXXX..',
  '.XXXXXXXXXX...',
  '.XX.XX.XX.....',
  '.XX.XX.XX.....',
]

type PixelCatProps = {
  /** Alto en px del gato completo. */
  size?: number
  color?: string
  className?: string
  style?: React.CSSProperties
}

export function PixelCat({ size = 40, color = '#1a1114', className, style }: PixelCatProps) {
  const rows = CAT_MAP.length
  const cols = CAT_MAP[0].length
  const px = size / rows

  return (
    <svg
      width={cols * px}
      height={size}
      viewBox={`0 0 ${cols} ${rows}`}
      shapeRendering="crispEdges"
      className={className}
      style={style}
      aria-hidden
    >
      {CAT_MAP.flatMap((row, y) =>
        row.split('').map((cell, x) => {
          if (cell === '.') return null
          const fill =
            cell === 'o' ? 'var(--color-gold)' : cell === 'n' ? '#e8829a' : cell === 'p' ? '#c96a80' : color
          return <rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} fill={fill} />
        }),
      )}
    </svg>
  )
}

/** Colores de la pandilla de gatos. */
export const CAT_COLORS = {
  negro: '#1a1114',
  amarillo: '#e0a52e',
  blanca: '#ded5c4',
  rojo: '#8b0000',
}

/**
 * Desfile de gatitos que caminan por el borde inferior de la pantalla,
 * de ida y vuelta: el negro, el amarillo y la gatita blanca.
 */
export function WalkingCat() {
  const parade = [
    { color: CAT_COLORS.negro, size: 34, delay: '0s' },
    { color: CAT_COLORS.amarillo, size: 30, delay: '-1.6s' },
    { color: CAT_COLORS.blanca, size: 24, delay: '-2.9s' },
  ]
  return (
    <>
      {parade.map((cat) => (
        <div
          key={cat.color}
          aria-hidden
          className="animate-cat-walk pointer-events-none fixed bottom-0 left-0 z-30"
          style={{ animationDelay: cat.delay }}
        >
          <div className="animate-cat-step" style={{ animationDelay: cat.delay }}>
            <PixelCat size={cat.size} color={cat.color} />
          </div>
        </div>
      ))}
    </>
  )
}
