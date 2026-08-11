import { motion } from 'motion/react'
import { Ticket } from 'lucide-react'
import { CINEMA_NAME, HER_NICKNAME, YOUR_NICKNAME } from '../config'

type Props = { onStart: () => void }

/** Fila de foquitos de marquesina que parpadean alternados. */
function Bulbs({ count = 11 }: { count?: number }) {
  return (
    <div aria-hidden className="flex justify-center gap-2 sm:gap-3">
      {Array.from({ length: count }, (_, i) => (
        <span
          key={i}
          className="size-2 rounded-full sm:size-2.5"
          style={{
            background: 'var(--color-gold-light)',
            boxShadow: '0 0 8px 2px rgba(243, 213, 128, 0.7)',
            animation: `bulb-blink 1.4s ease-in-out ${i % 2 === 0 ? '0s' : '0.7s'} infinite`,
          }}
        />
      ))}
      <style>{`
        @keyframes bulb-blink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.25; }
        }
      `}</style>
    </div>
  )
}

export default function Marquee({ onStart }: Props) {
  return (
    <motion.div
      className="flex min-h-dvh flex-col items-center justify-center gap-10 px-4 py-12"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 0.96 }}
      transition={{ duration: 0.8 }}
    >
      {/* Marquesina */}
      <motion.div
        initial={{ y: -30, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.9, delay: 0.2 }}
        className="w-full max-w-xl rounded-2xl border-4 p-6 text-center sm:p-10"
        style={{
          borderColor: 'var(--color-gold)',
          background: 'linear-gradient(180deg, #16090b 0%, #0a0505 100%)',
          boxShadow:
            '0 0 40px rgba(212, 175, 55, 0.25), inset 0 0 30px rgba(0,0,0,0.8)',
        }}
      >
        <Bulbs />

        <p className="font-pixel mt-6 text-[9px] tracking-widest text-(--color-gold) sm:text-[11px]">
          {CINEMA_NAME}
        </p>
        <p className="mt-3 text-sm italic opacity-70">presenta</p>

        <h1
          className="font-marquee animate-flicker mt-4 text-4xl leading-tight text-(--color-gold-light) sm:text-5xl"
          style={{ textShadow: '0 0 25px rgba(243, 213, 128, 0.5)' }}
        >
          Una historia
          <br />
          de amor
        </h1>

        <p className="mt-4 text-xs tracking-[0.2em] uppercase opacity-70 sm:text-sm">
          Protagonizada por
          <br />
          <span className="text-(--color-gold)">
            {HER_NICKNAME} &amp; {YOUR_NICKNAME}
          </span>
        </p>

        {/* Sello de formato */}
        <div className="mt-5 flex justify-center">
          <span
            className="font-pixel rounded-sm border px-3 py-2 text-[7px] tracking-[0.25em] sm:text-[8px]"
            style={{
              borderColor: 'var(--color-gold)',
              color: 'var(--color-gold)',
              background: 'rgba(212, 175, 55, 0.08)',
            }}
          >
            PROYECTADA EN IMAX 70mm
          </span>
        </div>

        <p className="mt-4 text-xs tracking-[0.3em] uppercase opacity-60 sm:text-sm">
          Función privada · Una sola butaca
        </p>

        <div className="mt-6">
          <Bulbs />
        </div>
      </motion.div>

      {/* Boleto de entrada */}
      <motion.button
        onClick={onStart}
        initial={{ y: 30, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.9, delay: 0.7 }}
        whileHover={{ scale: 1.05, rotate: -1 }}
        whileTap={{ scale: 0.95 }}
        className="group relative px-2 py-1"
        aria-label="Iniciar función"
      >
        <span
          className="relative flex items-center gap-3 rounded-lg border-2 border-dashed px-8 py-4 text-sm font-bold tracking-widest uppercase sm:text-base"
          style={{
            borderColor: 'var(--color-cream)',
            background: 'linear-gradient(135deg, var(--color-velvet) 0%, var(--color-velvet-dark) 100%)',
            color: 'var(--color-cream)',
            boxShadow: '0 6px 24px rgba(139, 0, 0, 0.5)',
          }}
        >
          <Ticket aria-hidden size={20} strokeWidth={1.8} />
          Ir a la taquilla
          {/* Muescas laterales del boleto */}
          <span aria-hidden className="absolute top-1/2 -left-3 size-5 -translate-y-1/2 rounded-full bg-(--color-cinema)" />
          <span aria-hidden className="absolute top-1/2 -right-3 size-5 -translate-y-1/2 rounded-full bg-(--color-cinema)" />
        </span>
      </motion.button>

      <motion.p
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.5 }}
        transition={{ delay: 1.5, duration: 1 }}
        className="font-pixel text-[8px] text-(--color-cream)"
      >
        ▼ admite un gato ▼
      </motion.p>
    </motion.div>
  )
}
