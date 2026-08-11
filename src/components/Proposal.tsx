import { useState } from 'react'
import { motion } from 'motion/react'
import { PixelCat } from './PixelCat'
import { HER_NAME } from '../config'

type Props = { onYes: () => void }

/** Frases que dice el botón "No" mientras huye. */
const NO_EXCUSES = [
  'No',
  '¿Segura? 🥺',
  'Piénsalo bien…',
  'El gato dice que sí 🐱',
  'Última oportunidad…',
  '¡Me voy de la sala!',
]

export default function Proposal({ onYes }: Props) {
  const [attempts, setAttempts] = useState(0)
  const [noPos, setNoPos] = useState({ x: 0, y: 0 })

  const noGone = attempts >= NO_EXCUSES.length

  /** Cada intento de tocar "No" lo manda a otro lado y lo encoge. */
  const dodge = () => {
    setAttempts((a) => a + 1)
    const range = 120
    setNoPos({
      x: (Math.random() - 0.5) * 2 * range,
      y: (Math.random() - 0.5) * 2 * range,
    })
  }

  const noScale = Math.max(0.35, 1 - attempts * 0.12)

  return (
    <motion.div
      className="flex min-h-dvh flex-col items-center justify-center gap-12 px-6 text-center"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <motion.p
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.7 }}
        transition={{ delay: 0.3 }}
        className="font-pixel text-[9px] tracking-widest text-(--color-gold)"
      >
        ★ ESCENA FINAL · SIN SEGUNDA TOMA ★
      </motion.p>

      <motion.h2
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.9, delay: 0.5 }}
        className="font-marquee max-w-3xl text-4xl leading-snug text-(--color-cream) sm:text-6xl"
        style={{ textShadow: '0 0 40px rgba(212, 175, 55, 0.35)' }}
      >
        {HER_NAME},
        <br />
        <span className="text-(--color-gold-light)">¿quieres ser mi novia?</span>
      </motion.h2>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 1.4 }}
        className="relative flex flex-wrap items-center justify-center gap-8"
      >
        {/* SÍ — crece un poquito con cada intento fallido del "No" */}
        <motion.button
          onClick={onYes}
          whileHover={{ scale: 1.08 }}
          whileTap={{ scale: 0.94 }}
          animate={{ scale: 1 + attempts * 0.06 }}
          className="rounded-xl px-12 py-5 text-2xl font-bold tracking-wide"
          style={{
            background: 'linear-gradient(135deg, var(--color-gold) 0%, #b8860b 100%)',
            color: 'var(--color-cinema)',
            boxShadow: '0 0 35px rgba(212, 175, 55, 0.55)',
            animation: 'yes-pulse 1.6s ease-in-out infinite',
          }}
        >
          ¡Sí! ❤️
        </motion.button>

        {!noGone ? (
          <motion.button
            onPointerEnter={dodge}
            onPointerDown={(e) => {
              e.preventDefault()
              dodge()
            }}
            animate={{ x: noPos.x, y: noPos.y, scale: noScale }}
            transition={{ type: 'spring', stiffness: 300, damping: 18 }}
            className="rounded-xl border px-8 py-4 text-base opacity-80"
            style={{ borderColor: 'rgba(245, 234, 214, 0.4)', color: 'var(--color-cream)' }}
          >
            {NO_EXCUSES[Math.min(attempts, NO_EXCUSES.length - 1)]}
          </motion.button>
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="flex items-center gap-2 text-sm italic opacity-60"
          >
            <PixelCat size={26} />
            el “No” salió corriendo de la sala
          </motion.div>
        )}
      </motion.div>

      <style>{`
        @keyframes yes-pulse {
          0%, 100% { box-shadow: 0 0 35px rgba(212, 175, 55, 0.55); }
          50% { box-shadow: 0 0 55px rgba(212, 175, 55, 0.9); }
        }
      `}</style>
    </motion.div>
  )
}
