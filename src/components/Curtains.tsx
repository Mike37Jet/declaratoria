import { motion } from 'motion/react'

/**
 * Telones de terciopelo rojo que se abren al montar.
 * Se renderiza como overlay encima de la escena siguiente.
 */
export default function Curtains() {
  const velvet =
    'repeating-linear-gradient(90deg, #6d0000 0 26px, #8b0000 26px 44px, #560000 44px 70px)'

  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-50 flex">
      <motion.div
        className="h-full w-1/2"
        style={{ background: velvet, boxShadow: 'inset -30px 0 50px rgba(0,0,0,0.6)' }}
        initial={{ x: 0 }}
        animate={{ x: '-100%' }}
        transition={{ duration: 2.2, ease: [0.7, 0, 0.3, 1], delay: 0.4 }}
      />
      <motion.div
        className="h-full w-1/2"
        style={{ background: velvet, boxShadow: 'inset 30px 0 50px rgba(0,0,0,0.6)' }}
        initial={{ x: 0 }}
        animate={{ x: '100%' }}
        transition={{ duration: 2.2, ease: [0.7, 0, 0.3, 1], delay: 0.4 }}
      />
      {/* Cenefa superior */}
      <motion.div
        className="absolute inset-x-0 top-0 h-16"
        style={{
          background: velvet,
          borderBottom: '4px solid var(--color-gold)',
          borderRadius: '0 0 40% 40% / 0 0 100% 100%',
        }}
        initial={{ y: 0 }}
        animate={{ y: '-100%' }}
        transition={{ duration: 1.4, ease: 'easeIn', delay: 2 }}
      />
    </div>
  )
}
