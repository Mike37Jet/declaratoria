import { useMemo } from 'react'

type Props = {
  count?: number
  /** Opacidad máxima de cada corazón (0–1). */
  intensity?: number
}

/**
 * Corazones que flotan suavemente desde abajo hacia arriba, de fondo.
 */
export default function FloatingHearts({ count = 12, intensity = 0.35 }: Props) {
  const hearts = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        size: 10 + Math.random() * 18,
        duration: 7 + Math.random() * 9,
        delay: -Math.random() * 16,
        drift: (Math.random() - 0.5) * 120,
        opacity: (0.3 + Math.random() * 0.7) * intensity,
        gold: Math.random() > 0.6,
      })),
    [count, intensity],
  )

  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-10 overflow-hidden">
      {hearts.map((h) => (
        <span
          key={h.id}
          className="animate-float-up absolute select-none"
          style={
            {
              left: `${h.left}%`,
              fontSize: h.size,
              animationDuration: `${h.duration}s`,
              animationDelay: `${h.delay}s`,
              color: h.gold ? 'var(--color-gold)' : 'var(--color-velvet)',
              '--drift': `${h.drift}px`,
              '--heart-opacity': h.opacity,
            } as React.CSSProperties
          }
        >
          ❤
        </span>
      ))}
    </div>
  )
}
