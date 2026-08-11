import { useEffect, useRef, useState } from 'react'
import { motion } from 'motion/react'
import { ChevronLeft, ChevronRight, Clapperboard } from 'lucide-react'
import { PHOTOS } from '../config'

type Props = { onDone: () => void }

/** Perforaciones laterales de la tira de película. */
function Sprockets() {
  return (
    <div
      aria-hidden
      className="h-5 w-full"
      style={{
        background:
          'repeating-linear-gradient(90deg, transparent 0 10px, var(--color-cream) 10px 22px, transparent 22px 32px)',
        opacity: 0.85,
        maskImage: 'linear-gradient(90deg, black, black)',
      }}
    />
  )
}

/**
 * Carrusel de fotos con forma de tira de película 35mm.
 * Desliza con el dedo (scroll-snap) o con las flechas.
 */
export default function FilmStripCarousel({ onDone }: Props) {
  const trackRef = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)

  // Garantiza que la tira siempre empiece en el fotograma 1.
  useEffect(() => {
    trackRef.current?.scrollTo({ left: 0 })
  }, [])

  const scrollTo = (i: number, smooth = true) => {
    const track = trackRef.current
    if (!track) return
    const clamped = Math.max(0, Math.min(PHOTOS.length - 1, i))
    const slide = track.children[clamped] as HTMLElement | undefined
    if (!slide) return
    const left = slide.offsetLeft - (track.clientWidth - slide.clientWidth) / 2
    track.scrollTo({ left, behavior: smooth ? 'smooth' : 'instant' })
  }

  const onScroll = () => {
    const track = trackRef.current
    if (!track) return
    const center = track.scrollLeft + track.clientWidth / 2
    let best = 0
    let bestDist = Infinity
    Array.from(track.children).forEach((el, i) => {
      const c = (el as HTMLElement).offsetLeft + (el as HTMLElement).clientWidth / 2
      const d = Math.abs(c - center)
      if (d < bestDist) {
        bestDist = d
        best = i
      }
    })
    setIndex(best)
  }

  return (
    <motion.div
      className="flex min-h-dvh flex-col items-center justify-center gap-8 py-14"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <div className="flex items-center gap-3 px-4 text-center">
        <Clapperboard className="text-(--color-gold)" size={28} aria-hidden />
        <h2 className="font-marquee text-2xl text-(--color-gold-light) sm:text-3xl">
          Detrás de cámaras
        </h2>
      </div>

      {/* Tira de película */}
      <div className="w-full" style={{ background: '#151210' }}>
        <Sprockets />
        <div
          ref={trackRef}
          onScroll={onScroll}
          className="flex snap-x snap-mandatory gap-4 overflow-x-auto px-[12vw] py-4"
          style={{ scrollbarWidth: 'none' }}
        >
          {PHOTOS.map((photo, i) => (
            <figure
              key={photo.file}
              className="w-[76vw] max-w-sm shrink-0 snap-center"
            >
              <div
                className="aspect-4/3 overflow-hidden border-4"
                style={{ borderColor: '#151210', background: '#000' }}
              >
                <img
                  src={`${import.meta.env.BASE_URL}photos/${photo.file}`}
                  alt={photo.caption}
                  loading="lazy"
                  className="size-full object-cover transition-transform duration-500 hover:scale-105"
                  draggable={false}
                />
              </div>
              <figcaption className="font-pixel mt-3 text-center text-[8px] leading-relaxed text-(--color-cream) opacity-80 sm:text-[9px]">
                {photo.caption}
              </figcaption>
              {/* Número de fotograma */}
              <p aria-hidden className="mt-1 text-center text-[10px] tracking-widest text-(--color-gold) opacity-50">
                {String(i + 1).padStart(2, '0')}A
              </p>
            </figure>
          ))}
        </div>
        <Sprockets />
      </div>

      {/* Controles */}
      <div className="flex items-center gap-5">
        <button
          onClick={() => scrollTo(index - 1)}
          aria-label="Foto anterior"
          className="rounded-full border border-(--color-gold) p-2 text-(--color-gold) transition hover:bg-(--color-gold) hover:text-(--color-cinema)"
        >
          <ChevronLeft size={18} />
        </button>
        <div className="flex gap-2">
          {PHOTOS.map((_, i) => (
            <span
              key={i}
              className="size-1.5 rounded-full transition-all"
              style={{
                background: i === index ? 'var(--color-gold)' : 'rgba(245, 234, 214, 0.3)',
                transform: i === index ? 'scale(1.4)' : undefined,
              }}
            />
          ))}
        </div>
        <button
          onClick={() => scrollTo(index + 1)}
          aria-label="Foto siguiente"
          className="rounded-full border border-(--color-gold) p-2 text-(--color-gold) transition hover:bg-(--color-gold) hover:text-(--color-cinema)"
        >
          <ChevronRight size={18} />
        </button>
      </div>

      <motion.button
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
        onClick={onDone}
        className="font-pixel rounded-md border-2 px-6 py-3 text-[10px] tracking-wider"
        style={{
          borderColor: 'var(--color-gold)',
          color: 'var(--color-gold)',
          background: 'rgba(212, 175, 55, 0.08)',
        }}
      >
        IR A LA ESCENA FINAL ▸
      </motion.button>
    </motion.div>
  )
}
