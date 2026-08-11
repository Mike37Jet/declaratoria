import { useState } from 'react'
import { AnimatePresence } from 'motion/react'
import GrainOverlay from './components/GrainOverlay'
import FloatingHearts from './components/FloatingHearts'
import { WalkingCat } from './components/PixelCat'
import Marquee from './components/Marquee'
import Curtains from './components/Curtains'
import FilmCountdown from './components/FilmCountdown'
import LoveLetter from './components/LoveLetter'
import FilmStripCarousel from './components/FilmStripCarousel'
import Proposal from './components/Proposal'
import Celebration from './components/Celebration'
import { useYesDate } from './hooks/useYesDate'

/** Actos de la función, en orden. */
type Scene = 'marquee' | 'countdown' | 'letter' | 'carousel' | 'proposal' | 'celebration'

export default function App() {
  const { yesDate, saveYes } = useYesDate()
  // Si ya dijo que sí en una visita anterior, entra directo a la celebración.
  const [scene, setScene] = useState<Scene>(yesDate ? 'celebration' : 'marquee')
  const [justSaidYes, setJustSaidYes] = useState(false)

  const handleYes = () => {
    if (!yesDate) saveYes()
    setJustSaidYes(true)
    setScene('celebration')
  }

  return (
    <main className="relative min-h-dvh overflow-hidden bg-(--color-cinema)">
      <GrainOverlay />
      {scene !== 'countdown' && <FloatingHearts intensity={scene === 'celebration' ? 0.55 : 0.3} />}
      {(scene === 'letter' || scene === 'carousel' || scene === 'celebration') && <WalkingCat />}

      <AnimatePresence mode="wait">
        {scene === 'marquee' && (
          <Marquee key="marquee" onStart={() => setScene('countdown')} />
        )}

        {scene === 'countdown' && (
          <div key="countdown">
            <Curtains />
            <FilmCountdown onDone={() => setScene('letter')} />
          </div>
        )}

        {scene === 'letter' && (
          <LoveLetter key="letter" onDone={() => setScene('carousel')} />
        )}

        {scene === 'carousel' && (
          <FilmStripCarousel key="carousel" onDone={() => setScene('proposal')} />
        )}

        {scene === 'proposal' && <Proposal key="proposal" onYes={handleYes} />}

        {scene === 'celebration' && yesDate && (
          <Celebration
            key="celebration"
            yesDate={yesDate}
            justSaidYes={justSaidYes}
            onReplay={() => {
              setJustSaidYes(false)
              setScene('marquee')
            }}
          />
        )}
      </AnimatePresence>
    </main>
  )
}
