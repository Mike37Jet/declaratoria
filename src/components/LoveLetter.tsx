import { useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'motion/react'
import { CAT_COLORS, PixelCat } from './PixelCat'
import { HER_NAME, YOUR_NAME } from '../config'

type Props = { onDone: () => void }

/**
 * ✍️ LA CARTA — edita aquí el texto.
 * Cada bloque es una "escena" del guion: un encabezado estilo
 * cinematográfico y sus líneas. Escribe con tus propias palabras
 * y anécdotas reales; esto es solo el borrador del guionista.
 */
const SCENES: { heading: string; lines: string[] }[] = [
  {
    heading: `ESCENA 1 — INT. UN DÍA CUALQUIERA — DE PRONTO`,
    lines: [
      `${HER_NAME}:`,
      `Dicen que las mejores películas no se anuncian: simplemente empiezan. Así llegaste tú — sin tráiler, sin aviso — y de repente mi historia tenía protagonista.`,
    ],
  },
  {
    heading: `ESCENA 2 — INT. MI CABEZA — TODO EL TIEMPO`,
    lines: [
      `Me fijé en los detalles, porque sé que tú te fijas en los detalles: en cómo se te iluminan los ojos cuando hablas de una película que amas, en la ternura con la que miras a un gato como si fuera el único en el mundo, en esa forma tuya de hacer que lo ordinario parezca rodado en 35mm.`,
    ],
  },
  {
    heading: `ESCENA 3 — INT. MI CORAZÓN — DÍA Y NOCHE`,
    lines: [
      `He repetido esta escena en mi cabeza más veces que mi película favorita. La he editado, le he cambiado la música, he ensayado el diálogo... y siempre termina igual: contigo sonriendo.`,
      `Porque contigo no quiero un cortometraje. Quiero la saga completa, las escenas post-créditos, el detrás de cámaras y todas las secuelas.`,
    ],
  },
  {
    heading: `ESCENA FINAL — EXT. NUESTRO FUTURO — AMANECER`,
    lines: [
      `Así que aquí va, sin doble de riesgo y sin segunda toma:`,
    ],
  },
]

const SIGNATURE = `— ${YOUR_NAME}, tu director y tu fan número uno`

/** Velocidad de tipeo (ms por caracter). */
const TYPE_MS = 28

export default function LoveLetter({ onDone }: Props) {
  const [sceneIdx, setSceneIdx] = useState(0)
  const [chars, setChars] = useState(0)
  const doneRef = useRef<HTMLDivElement>(null)

  const sceneTexts = useMemo(() => SCENES.map((s) => s.lines.join('\n')), [])
  const finished = sceneIdx >= SCENES.length

  useEffect(() => {
    if (finished) return
    const total = sceneTexts[sceneIdx].length
    if (chars >= total) {
      const t = setTimeout(() => {
        setSceneIdx((i) => i + 1)
        setChars(0)
      }, 900)
      return () => clearTimeout(t)
    }
    const t = setTimeout(() => setChars((c) => c + 1), TYPE_MS)
    return () => clearTimeout(t)
  }, [chars, sceneIdx, finished, sceneTexts])

  useEffect(() => {
    if (finished) doneRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [finished])

  /** Un toque completa la escena actual (para no esperar el tipeo). */
  const fastForward = () => {
    if (finished) return
    const total = sceneTexts[sceneIdx].length
    if (chars < total) setChars(total)
    else {
      setSceneIdx((i) => i + 1)
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
          ★ GUION ORIGINAL · ROMANCE · +18 GATOS ★
        </p>

        {SCENES.slice(0, sceneIdx + 1).map((scene, i) => {
          if (i > sceneIdx) return null
          const isCurrent = i === sceneIdx && !finished
          const text = isCurrent ? sceneTexts[i].slice(0, chars) : sceneTexts[i]
          const lines = text.split('\n')
          return (
            <div key={scene.heading} className="mb-8">
              <h2 className="font-letter mb-3 text-xs font-bold tracking-[0.18em] uppercase opacity-60 sm:text-sm">
                {scene.heading}
              </h2>
              {lines.map((line, j) => (
                <p
                  key={j}
                  className="font-letter mb-3 text-base leading-relaxed sm:text-lg"
                >
                  {line}
                  {isCurrent && j === lines.length - 1 && (
                    <span className="animate-blink ml-0.5 inline-block w-2 border-b-2 border-(--color-velvet)">
                      &nbsp;
                    </span>
                  )}
                </p>
              ))}
            </div>
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
