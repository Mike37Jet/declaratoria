import { useEffect } from 'react'
import { motion } from 'motion/react'
import confetti from 'canvas-confetti'
import TimeCounter from './TimeCounter'
import Credits from './Credits'
import { PixelCat, PixelPenguin, PixelSeal } from './PixelCat'
import { HER_NAME } from '../config'

type Props = {
  yesDate: Date
  /** true cuando ella acaba de presionar "Sí" (dispara el confeti). */
  justSaidYes: boolean
  onReplay: () => void
}

/** Lanza ráfagas de confeti con forma de corazón. */
function fireHearts() {
  const heart = confetti.shapeFromPath({
    path: 'M12 21s-8.5-5.7-10.5-10C.2 8 1.6 4.6 4.9 4.1 7 3.8 8.9 4.9 12 8c3.1-3.1 5-4.2 7.1-3.9 3.3.5 4.7 3.9 3.4 6.9-2 4.3-10.5 10-10.5 10z',
  })
  const colors = ['#d4af37', '#8b0000', '#f3d580', '#e8829a']
  const defaults = { shapes: [heart], colors, scalar: 1.6, ticks: 180 }

  confetti({ ...defaults, particleCount: 60, spread: 100, origin: { y: 0.6 } })
  setTimeout(
    () => confetti({ ...defaults, particleCount: 40, angle: 60, spread: 70, origin: { x: 0 } }),
    350,
  )
  setTimeout(
    () => confetti({ ...defaults, particleCount: 40, angle: 120, spread: 70, origin: { x: 1 } }),
    600,
  )
  setTimeout(
    () => confetti({ ...defaults, particleCount: 80, spread: 140, origin: { y: 0.4 } }),
    1100,
  )
}

/**
 * Pantalla final: celebración, contador en vivo y créditos.
 * También es la pantalla de inicio en visitas posteriores al "sí".
 */
export default function Celebration({ yesDate, justSaidYes, onReplay }: Props) {
  useEffect(() => {
    if (!justSaidYes) return
    fireHearts()
    const encore = setTimeout(fireHearts, 2500)
    return () => clearTimeout(encore)
  }, [justSaidYes])

  return (
    <motion.div
      className="flex min-h-dvh flex-col items-center justify-center gap-12 px-4 py-16"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <motion.div
        initial={{ scale: 0.7, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 120, damping: 12, delay: 0.2 }}
        className="text-center"
      >
        <p className="font-pixel mb-4 text-[9px] tracking-widest text-(--color-gold)">
          ★ ESTRENO MUNDIAL ★
        </p>
        <h2
          className="font-marquee text-4xl text-(--color-gold-light) sm:text-6xl"
          style={{ textShadow: '0 0 40px rgba(243, 213, 128, 0.5)' }}
        >
          ¡Dijo que sí!
        </h2>
        <p className="mt-4 text-base italic opacity-75 sm:text-lg">
          {HER_NAME} y yo — ahora oficialmente en cartelera
        </p>
      </motion.div>

      {/* El trío celebrando: gato, pingüino y foca */}
      <div aria-hidden className="flex items-end gap-6">
        {[
          { key: 'gato', el: <PixelCat size={42} /> },
          { key: 'pinguino', el: <PixelPenguin size={38} /> },
          { key: 'foca', el: <PixelSeal size={28} /> },
        ].map((pet, i) => (
          <motion.div
            key={pet.key}
            animate={{ y: [0, -12, 0] }}
            transition={{ duration: 0.7, repeat: Infinity, delay: i * 0.18 }}
          >
            {pet.el}
          </motion.div>
        ))}
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.8 }}
      >
        <TimeCounter since={yesDate} />
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.4 }}
        className="w-full"
      >
        <Credits />
      </motion.div>

      <button
        onClick={onReplay}
        className="font-pixel rounded-md border px-5 py-3 text-[8px] tracking-wider opacity-60 transition hover:opacity-100"
        style={{ borderColor: 'var(--color-cream)', color: 'var(--color-cream)' }}
      >
        ↻ VOLVER A VER LA FUNCIÓN
      </button>
    </motion.div>
  )
}
