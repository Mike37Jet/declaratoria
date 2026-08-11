import { useEffect, useState } from 'react'

type Props = { since: Date }

function split(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000))
  return {
    días: Math.floor(s / 86400),
    horas: Math.floor((s % 86400) / 3600),
    min: Math.floor((s % 3600) / 60),
    seg: s % 60,
  }
}

/**
 * Contador en vivo desde el momento del "sí".
 */
export default function TimeCounter({ since }: Props) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const parts = split(now - since.getTime())

  return (
    <div className="text-center">
      <p className="font-pixel mb-5 text-[9px] tracking-widest text-(--color-gold) sm:text-[10px]">
        ♥ TIEMPO DESDE QUE DIJISTE QUE SÍ ♥
      </p>
      <div className="flex justify-center gap-2 sm:gap-4">
        {Object.entries(parts).map(([label, value]) => (
          <div
            key={label}
            className="min-w-16 rounded-lg border-2 px-2 py-3 sm:min-w-20 sm:py-4"
            style={{
              borderColor: 'var(--color-gold)',
              background: 'rgba(212, 175, 55, 0.07)',
              boxShadow: 'inset 0 0 20px rgba(0,0,0,0.5)',
            }}
          >
            <div className="font-pixel text-lg text-(--color-gold-light) tabular-nums sm:text-2xl">
              {String(value).padStart(2, '0')}
            </div>
            <div className="font-pixel mt-2 text-[7px] uppercase opacity-60 sm:text-[8px]">
              {label}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-4 text-xs italic opacity-50">
        …y contando, fotograma a fotograma
      </p>
    </div>
  )
}
