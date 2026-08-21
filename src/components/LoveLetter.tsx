import { useEffect, useRef, useState } from 'react'
import { motion } from 'motion/react'
import { CAT_COLORS, PixelCat } from './PixelCat'
import { HER_NAME, YOUR_NAME, YOUR_NICKNAME } from '../config'

type Props = { onDone: () => void }

/**
 * ✍️ LA CARTA — edita aquí el texto.
 * Es una carta corrida: cada elemento del array es un párrafo y se
 * tipea completo antes de pasar al siguiente. Un toque adelanta.
 */
const PARAGRAPHS: string[] = [
  `Hola ${HER_NAME}, la verdad no soy mucho de escribir cartas. A pesar de que suelo expresar mucho y decir cosas muy espontáneas, soy muy malo con las palabras, y aún más cuando son para expresar sentimientos. Así que lo poco que te puedo decir en esta carta es en realidad solo una pequeña parte de lo que siento por ti.`,

  `Para empezar, aún recuerdo la primera vez que te vi en persona: yo llegando a mi entrevista y tú sentada en el escritorio, en una reunión con Dome. Quién iba a pensar que aquella chica terminaría cambiando mi vida en tantos aspectos, y que ahora me costaría imaginar cómo sería mi vida sin ella.`,

  `En esos primeros meses no interactuamos mucho, pero la vida dio un giro que poco a poco nos permitió irnos conociendo. Y fui dándome cuenta de que, además de ser una chica muy linda, tenías una personalidad que me atraía aún más. Tu carisma, tu sonrisa, tu actitud conmigo, fueron capturando mi corazón de a poco. Y sin darme cuenta, ya me estaba enamorando perdidamente de ti.`,

  `Hasta que en un momento decidí ser más directo con mis intenciones, a pesar del temor de que posiblemente no funcionara. Pero tenía la corazonada de que contigo sería diferente. Y me alegra haberme arriesgado, porque me permitió conocer a una chica maravillosa.`,

  `Aunque siento que todavía me falta conocerte más, hay cosas que ya tengo clarísimas: eres serena al tomar decisiones, tierna cuando te sonrojas por algo, divertida cuando te sientes en confianza. Eres empática, cálida y dulce.`,

  `Y quizás no eres de decir mucho lo que sientes, pero lo demuestras: cada abrazo que me das lo haces con tanto gusto que me encanta, cada beso, cada gesto de amor hacia mí. Todo eso hace que me enamore más de ti.`,

  `Estoy dispuesto a seguir descubriéndote y a continuar esta historia a tu lado. Y parece que las palabras se acaban aquí, pero simplemente es el comienzo de algo más.`,

  `Así que me despido por ahora.`,
]

const SIGNATURE = `Con cariño, tu más grande admirador — ${YOUR_NICKNAME} (${YOUR_NAME})`

/** Velocidad de tipeo (ms por caracter). */
const TYPE_MS = 28

export default function LoveLetter({ onDone }: Props) {
  const [paraIdx, setParaIdx] = useState(0)
  const [chars, setChars] = useState(0)
  const doneRef = useRef<HTMLDivElement>(null)

  const finished = paraIdx >= PARAGRAPHS.length

  useEffect(() => {
    if (finished) return
    const total = PARAGRAPHS[paraIdx].length
    if (chars >= total) {
      const t = setTimeout(() => {
        setParaIdx((i) => i + 1)
        setChars(0)
      }, 900)
      return () => clearTimeout(t)
    }
    const t = setTimeout(() => setChars((c) => c + 1), TYPE_MS)
    return () => clearTimeout(t)
  }, [chars, paraIdx, finished])

  useEffect(() => {
    if (finished) doneRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [finished])

  /** Un toque completa el párrafo actual (para no esperar el tipeo). */
  const fastForward = () => {
    if (finished) return
    const total = PARAGRAPHS[paraIdx].length
    if (chars < total) setChars(total)
    else {
      setParaIdx((i) => i + 1)
      setChars(0)
    }
  }

  return (
    <motion.div
      className="relative min-h-dvh px-4 py-14 sm:py-20"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={fastForward}
    >
      <div
        className="relative mx-auto max-w-2xl rounded-sm px-6 py-10 shadow-2xl sm:px-12 sm:py-14"
        style={{
          background: 'linear-gradient(175deg, #f7efdd 0%, #efe3c8 60%, #e7d8b8 100%)',
          color: '#2b1d14',
          boxShadow: '0 20px 60px rgba(0,0,0,0.6), inset 0 0 60px rgba(139, 90, 43, 0.12)',
        }}
      >
        {/* Gatito amarillo espiando desde la esquina del papel */}
        <PixelCat
          size={30}
          color={CAT_COLORS.amarillo}
          className="absolute -top-[26px] right-8"
          style={{ filter: 'drop-shadow(0 2px 2px rgba(0,0,0,0.4))' }}
        />

        <p className="font-pixel mb-8 text-center text-[8px] tracking-widest text-(--color-velvet) sm:text-[10px]">
          ★ CARTA ORIGINAL · ROMANCE ★
        </p>

        {PARAGRAPHS.slice(0, paraIdx + 1).map((full, i) => {
          const isCurrent = i === paraIdx && !finished
          const text = isCurrent ? full.slice(0, chars) : full
          return (
            <p
              key={i}
              className="font-letter mb-5 text-base leading-relaxed sm:text-lg"
            >
              {text}
              {isCurrent && (
                <span className="animate-blink ml-0.5 inline-block w-2 border-b-2 border-(--color-velvet)">
                  &nbsp;
                </span>
              )}
            </p>
          )
        })}

        {finished && (
          <motion.div
            ref={doneRef}
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mt-10 text-center"
          >
            <p className="font-letter text-lg italic opacity-80 sm:text-xl">{SIGNATURE}</p>
            <div className="mt-3 flex justify-center gap-2 text-(--color-velvet)">
              ❤ <PixelCat size={20} /> ❤
            </div>
          </motion.div>
        )}
      </div>

      <div className="mt-10 flex flex-col items-center gap-4">
        {!finished && (
          <p className="font-pixel text-[8px] opacity-50">toca para adelantar ▸▸</p>
        )}
        {finished && (
          <motion.button
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.6 }}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={(e) => {
              e.stopPropagation()
              onDone()
            }}
            className="font-pixel rounded-md border-2 px-6 py-3 text-[10px] tracking-wider"
            style={{
              borderColor: 'var(--color-gold)',
              color: 'var(--color-gold)',
              background: 'rgba(212, 175, 55, 0.08)',
            }}
          >
            VER DETRÁS DE CÁMARAS ▸
          </motion.button>
        )}
      </div>
    </motion.div>
  )
}
