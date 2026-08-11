/**
 * Los tres personajes pixel art de la función:
 * el gato amarillo, el pingüino y la foca gris. 🐱🐧🦭
 * Cada sprite es una rejilla de caracteres: `.` = vacío,
 * y cada letra toma su color de la paleta del sprite.
 */

export const CAT_COLORS = {
  amarillo: '#e0a52e',
}

type Palette = Record<string, string>

type SpriteProps = {
  /** Alto en px del sprite completo. */
  size?: number
  className?: string
  style?: React.CSSProperties
}

function PixelSprite({
  map,
  palette,
  size = 40,
  className,
  style,
}: SpriteProps & { map: string[]; palette: Palette }) {
  const rows = map.length
  const cols = map[0].length
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
      {map.flatMap((row, y) =>
        row.split('').map((cell, x) => {
          const fill = palette[cell]
          if (!fill) return null
          return <rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} fill={fill} />
        }),
      )}
    </svg>
  )
}

/* ── Gato ─────────────────────────────────────────────── */

const CAT_MAP = [
  '.X...X........',
  '.Xp.pX........',
  '.XXXXX.......X',
  'XoXXXoX.....XX',
  'XXXnXXX.....X.',
  '.XXXXXXXXX.XX.',
  '.XXXXXXXXXXXX.',
  '.XXXXXXXXXX...',
  '.XX.XX.XX.....',
  '.XX.XX.XX.....',
]

export function PixelCat({
  size = 40,
  color = CAT_COLORS.amarillo,
  ...rest
}: SpriteProps & { color?: string }) {
  return (
    <PixelSprite
      map={CAT_MAP}
      palette={{ X: color, o: 'var(--color-gold)', n: '#e8829a', p: '#c96a80' }}
      size={size}
      {...rest}
    />
  )
}

/* ── Pingüino ─────────────────────────────────────────── */

const PENGUIN_MAP = [
  '..KKKK..',
  '.KKKKKK.',
  '.KoKKoK.',
  '.KWbbWK.',
  'KKWWWWKK',
  'KKWWWWKK',
  'KKWWWWKK',
  'KKWWWWKK',
  '.KWWWWK.',
  '.KKKKKK.',
  '..f..f..',
]

export function PixelPenguin({ size = 40, ...rest }: SpriteProps) {
  return (
    <PixelSprite
      map={PENGUIN_MAP}
      palette={{ K: '#1c1c26', W: '#f2ede3', o: '#f2ede3', b: '#e8912d', f: '#e8912d' }}
      size={size}
      {...rest}
    />
  )
}

/* ── Foca ─────────────────────────────────────────────── */

const SEAL_MAP = [
  '...GGGG.......',
  '..GGGGGG......',
  '..GoGGGG......',
  '.nGGGGGGGG.GG.',
  '..GGGGGGGGGGG.',
  '..GGGGGGGGGG..',
  '...GGGGGGG....',
  '..GG...GG.....',
]

export function PixelSeal({ size = 40, ...rest }: SpriteProps) {
  return (
    <PixelSprite
      map={SEAL_MAP}
      palette={{ G: '#9aa0a8', o: '#22242a', n: '#3a3d44' }}
      size={size}
      {...rest}
    />
  )
}

/* ── El paseo por el borde inferior ───────────────────── */

/**
 * El gato amarillo, el pingüino y la foca paseando juntos
 * por el borde inferior de la pantalla, de ida y vuelta.
 */
export function WalkingCat() {
  const stroll = [
    { key: 'gato', el: <PixelCat size={32} />, delay: '0s' },
    { key: 'pinguino', el: <PixelPenguin size={30} />, delay: '-1.4s' },
    { key: 'foca', el: <PixelSeal size={22} />, delay: '-2.6s' },
  ]
  return (
    <>
      {stroll.map((pet) => (
        <div
          key={pet.key}
          aria-hidden
          className="animate-cat-walk pointer-events-none fixed bottom-0 left-0 z-30"
          style={{ animationDelay: pet.delay }}
        >
          <div className="animate-cat-step" style={{ animationDelay: pet.delay }}>
            {pet.el}
          </div>
        </div>
      ))}
    </>
  )
}
