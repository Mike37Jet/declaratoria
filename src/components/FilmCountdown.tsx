import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'

type Props = { onDone: () => void }

/**
 * Cuenta regresiva clásica de cinta de cine: 5…4…3…2…1 con
 * círculo giratorio y cruz de encuadre.
 */
export default function FilmCountdown({ onDone }: Props) {
  const [n, setN] = useState(5)

  useEffect(() => {
    if (n === 0) {
      const t = setTimeout(onDone, 400)
      return () => clearTimeout(t)
    }
    const t = setTimeout(() => setN((v) => v - 1), 1000)
    return () => clearTimeout(t)
  }, [n, onDone])

  return (
    <motion.div
      className="flex min-h-dvh items-center justify-center"
      style={{ background: '#151210' }}
      exit={{ opacity: 0 }}
    >
      <div className="relative flex size-72 items-center justify-center sm:size-96">
        {/* Sello de formato */}
        <p
          aria-hidden
          className="font-pixel absolute -bottom-10 left-1/2 -translate-x-1/2 text-[7px] tracking-[0.3em] whitespace-nowrap text-(--color-cream) opacity-40 sm:text-[8px]"
        >
          IMAX 70mm · 1.43:1
        </p>

        {/* Cruz de encuadre */}
        <div aria-hidden className="absolute inset-0">
          <div className="absolute top-1/2 right-0 left-0 h-px bg-(--color-cream) opacity-30" />
          <div className="absolute top-0 bottom-0 left-1/2 w-px bg-(--color-cream) opacity-30" />
        </div>

        {/* Círculos */}
        <svg viewBox="0 0 100 100" className="absolute inset-0 size-full opacity-60">
          <circle cx="50" cy="50" r="46" fill="none" stroke="var(--color-cream)" strokeWidth="0.6" />
          <circle cx="50" cy="50" r="38" fill="none" stroke="var(--color-cream)" strokeWidth="0.6" />
        </svg>

        {/* Barrido giratorio */}
        <motion.div
          aria-hidden
          key={`sweep-${n}`}
          className="absolute inset-0"
          initial={{ rotate: 0 }}
          animate={{ rotate: 360 }}
          transition={{ duration: 1, ease: 'linear' }}
          style={{
            background:
              'conic-gradient(rgba(245, 234, 214, 0.18) 0deg 20deg, transparent 20deg)',
            borderRadius: '50%',
          }}
        />

        {/* Número */}
        <AnimatePresence mode="popLayout">
          {n > 0 && (
            <motion.span
              key={n}
              initial={{ scale: 1.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.7, opacity: 0 }}
              transition={{ duration: 0.25 }}
              className="font-marquee relative text-8xl text-(--color-cream) sm:text-9xl"
              style={{ textShadow: '0 0 30px rgba(245, 234, 214, 0.4)' }}
            >
              {n}
            </motion.span>
          )}
          {n === 0 && (
            <motion.span
              key="heart"
              initial={{ scale: 0.4, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="relative text-7xl"
            >
              ❤️
            </motion.span>
          )}
        </AnimatePresence>
      </div>
    </motion.div>
  )
}
