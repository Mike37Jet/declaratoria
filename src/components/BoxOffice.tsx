import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { Ticket } from 'lucide-react'
import { CINEMA_NAME, HER_NAME, HER_NICKNAME, YOUR_NICKNAME } from '../config'

type Props = { onDone: () => void }

/** Butaca libre: la única que no está vendida. */
const FREE_SEAT = { row: 2, col: 3 }
const ROWS = 5
const COLS = 8
const ROW_LETTERS = ['A', 'B', 'C', 'D', 'E']

/** Etiqueta de la butaca reservada, p. ej. "C4". */
const SEAT_LABEL = `${ROW_LETTERS[FREE_SEAT.row]}${FREE_SEAT.col + 1}`

/** Distancia (px) que hay que arrastrar el talón para que el boleto se rompa. */
const TEAR_DISTANCE = 70

/**
 * Mapa de butacas: todas vendidas menos una, que late esperando a que la elija.
 */
function SeatMap({ selected, onSelect }: { selected: boolean; onSelect: () => void }) {
  return (
    <div className="flex flex-col items-center gap-4">
      {/* La pantalla del cine, arriba de todo */}
      <div className="w-full max-w-xs">
        <div
          className="h-2 rounded-[50%]"
          style={{
            background: 'linear-gradient(90deg, transparent, var(--color-gold-light), transparent)',
            boxShadow: '0 6px 24px rgba(243, 213, 128, 0.45)',
          }}
        />
        <p className="font-pixel mt-2 text-center text-[7px] tracking-[0.3em] opacity-50">
          PANTALLA
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
        {Array.from({ length: ROWS }, (_, row) => (
          <div key={row} className="flex items-center gap-1.5">
            <span className="font-pixel w-3 text-[6px] opacity-40">{ROW_LETTERS[row]}</span>
            {Array.from({ length: COLS }, (_, col) => {
              const isFree = row === FREE_SEAT.row && col === FREE_SEAT.col
              if (!isFree) {
                return (
                  <span
                    key={col}
                    aria-hidden
                    className="size-4 rounded-t-md sm:size-5"
                    style={{ background: 'rgba(245, 234, 214, 0.10)' }}
                  />
                )
              }
              return (
                <motion.button
                  key={col}
                  onClick={onSelect}
                  aria-label={`Elegir la butaca ${SEAT_LABEL}`}
                  aria-pressed={selected}
                  className="size-4 rounded-t-md sm:size-5"
                  style={{
                    background: selected ? 'var(--color-gold-light)' : 'var(--color-velvet)',
                    border: '1px solid var(--color-gold)',
                  }}
                  animate={
                    selected
                      ? { scale: 1.15, boxShadow: '0 0 16px 3px rgba(243, 213, 128, 0.8)' }
                      : { scale: [1, 1.18, 1], boxShadow: '0 0 10px 2px rgba(212, 175, 55, 0.55)' }
                  }
                  transition={selected ? { duration: 0.25 } : { duration: 1.3, repeat: Infinity }}
                />
              )
            })}
          </div>
        ))}
      </div>

      <p className="font-pixel text-[7px] leading-relaxed opacity-50">
        {selected ? `BUTACA ${SEAT_LABEL} · TUYA` : 'TODO VENDIDO · QUEDA UNA'}
      </p>
    </div>
  )
}

/**
 * Acto de la taquilla: ella elige la única butaca libre, "compra" su boleto
 * y lo rompe por la línea perforada para entrar a la sala.
 */
export default function BoxOffice({ onDone }: Props) {
  const [selected, setSelected] = useState(false)
  const [bought, setBought] = useState(false)
  const [torn, setTorn] = useState(false)

  /** Rompe el boleto y, tras la animación, abre el telón. */
  const tear = () => {
    if (torn) return
    setTorn(true)
    setTimeout(onDone, 1600)
  }

  return (
    <motion.div
      className="flex min-h-dvh flex-col items-center justify-center gap-8 px-4 py-12"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 0.96 }}
      transition={{ duration: 0.6 }}
    >
      <div className="text-center">
        <p className="font-pixel text-[9px] tracking-[0.25em] text-(--color-gold) sm:text-[11px]">
          TAQUILLA
        </p>
        <p className="mt-3 text-sm italic opacity-70 sm:text-base">
          {bought ? 'Rompe tu boleto para entrar' : 'Elige tu butaca, ' + HER_NAME}
        </p>
      </div>

      <AnimatePresence mode="wait">
        {!bought ? (
          <motion.div
            key="venta"
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.4 }}
            className="flex flex-col items-center gap-7"
          >
            <SeatMap selected={selected} onSelect={() => setSelected(true)} />

            {/* Precio */}
            <div className="text-center">
              <p className="font-pixel text-[7px] tracking-[0.25em] opacity-50">PRECIO</p>
              <p className="font-letter mt-1 text-xl text-(--color-gold-light) sm:text-2xl">
                Un beso
              </p>
            </div>

            <motion.button
              onClick={() => setBought(true)}
              disabled={!selected}
              whileHover={selected ? { scale: 1.05 } : undefined}
              whileTap={selected ? { scale: 0.95 } : undefined}
              className="font-pixel rounded-md border-2 px-6 py-4 text-[8px] tracking-[0.2em] transition-opacity disabled:cursor-not-allowed disabled:opacity-30 sm:text-[9px]"
              style={{
                borderColor: 'var(--color-gold)',
                color: 'var(--color-gold-light)',
                background: 'rgba(212, 175, 55, 0.08)',
              }}
            >
              COMPRAR MI BOLETO
            </motion.button>
          </motion.div>
        ) : (
          <motion.div
            key="boleto"
            initial={{ y: -140, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 90, damping: 14 }}
            className="flex flex-col items-center gap-6"
          >
            {/* El boleto: cuerpo fijo + talón que se arrastra hasta romperse */}
            <div className="flex items-stretch">
              <motion.div
                animate={torn ? { x: -22, rotate: -4 } : {}}
                transition={{ type: 'spring', stiffness: 200, damping: 14 }}
                className="relative rounded-l-lg border-2 border-r-0 px-6 py-5 sm:px-8"
                style={{
                  borderColor: 'var(--color-cream)',
                  background:
                    'linear-gradient(135deg, var(--color-velvet) 0%, var(--color-velvet-dark) 100%)',
                  color: 'var(--color-cream)',
                }}
              >
                <p className="font-pixel text-[7px] tracking-[0.25em] opacity-70">
                  {CINEMA_NAME}
                </p>
                <p className="font-marquee mt-2 text-xl leading-tight sm:text-2xl">
                  Una historia
                  <br />
                  de amor
                </p>
                <p className="font-pixel mt-3 text-[6px] tracking-[0.2em] opacity-70">
                  {HER_NICKNAME.toUpperCase()} &amp; {YOUR_NICKNAME.toUpperCase()}
                </p>
                <p className="font-pixel mt-1 text-[6px] tracking-[0.2em] opacity-70">
                  IMAX 70mm · FUNCIÓN PRIVADA
                </p>
                <span
                  aria-hidden
                  className="absolute top-1/2 -left-2.5 size-4 -translate-y-1/2 rounded-full bg-(--color-cinema)"
                />
              </motion.div>

              {/* Talón arrastrable */}
              <motion.div
                drag={torn ? false : 'x'}
                dragConstraints={{ left: 0, right: TEAR_DISTANCE + 30 }}
                dragElastic={0.15}
                // Si lo suelta sin llegar al umbral, el talón vuelve a su sitio.
                dragSnapToOrigin
                onDragEnd={(_, info) => {
                  if (info.offset.x > TEAR_DISTANCE) tear()
                }}
                onClick={tear}
                animate={torn ? { x: 120, rotate: 12, opacity: 0 } : {}}
                transition={{ duration: 0.5 }}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') tear()
                }}
                aria-label="Romper el boleto y entrar a la función"
                className="relative flex cursor-grab flex-col items-center justify-center rounded-r-lg border-2 border-l-0 border-dashed px-4 py-5 active:cursor-grabbing"
                style={{
                  borderColor: 'var(--color-cream)',
                  background:
                    'linear-gradient(135deg, var(--color-velvet-dark) 0%, var(--color-velvet) 100%)',
                  color: 'var(--color-cream)',
                }}
              >
                <Ticket aria-hidden size={20} strokeWidth={1.8} />
                <p className="font-pixel mt-2 text-[9px] tracking-widest">{SEAT_LABEL}</p>
                <span
                  aria-hidden
                  className="absolute top-1/2 -right-2.5 size-4 -translate-y-1/2 rounded-full bg-(--color-cinema)"
                />
              </motion.div>
            </div>

            {/* Sello que aparece al romperlo */}
            <div className="flex h-10 items-center">
              <AnimatePresence>
                {torn ? (
                  <motion.p
                    key="sello"
                    initial={{ scale: 2.4, opacity: 0, rotate: -14 }}
                    animate={{ scale: 1, opacity: 1, rotate: -8 }}
                    transition={{ type: 'spring', stiffness: 260, damping: 12 }}
                    className="font-pixel rounded border-2 px-4 py-2 text-[9px] tracking-[0.25em]"
                    style={{ borderColor: 'var(--color-gold)', color: 'var(--color-gold-light)' }}
                  >
                    ADMITIDA
                  </motion.p>
                ) : (
                  <motion.p
                    key="pista"
                    animate={{ x: [0, 6, 0] }}
                    transition={{ duration: 1.4, repeat: Infinity }}
                    className="font-pixel text-[7px] opacity-50"
                  >
                    arrastra el talón → (o tócalo)
                  </motion.p>
                )}
              </AnimatePresence>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}
