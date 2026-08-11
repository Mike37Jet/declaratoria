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

/**
 * Gatito que camina por el borde inferior de la pantalla, de ida y vuelta.
 */
export function WalkingCat() {
  return (
    <div aria-hidden className="animate-cat-walk pointer-events-none fixed bottom-0 left-0 z-30">
      <div className="animate-cat-step">
        <PixelCat size={34} />
      </div>
    </div>
  )
}
