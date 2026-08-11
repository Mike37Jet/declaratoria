import { HER_NAME, HER_NICKNAME, YOUR_NAME, YOUR_NICKNAME } from '../config'

const CREDITS: { role: string; name: string }[] = [
  { role: 'Protagonista', name: `${HER_NAME} «${HER_NICKNAME}»` },
  { role: 'Coprotagonista (y muy afortunado)', name: `${YOUR_NAME} «${YOUR_NICKNAME}»` },
  { role: 'Dirección', name: 'El destino' },
  { role: 'Guion', name: 'Un corazón muy nervioso' },
  { role: 'Fotografía', name: 'Tus ojos' },
  { role: 'Banda sonora', name: 'Tu voz diciendo mi nombre' },
  { role: 'Efectos especiales', name: 'Tu sonrisa' },
  { role: 'Vestuario', name: 'Mi camiseta turquesa con la que dormiste aquel día' },
  { role: 'Género', name: 'Romance / Comedia / Aventura' },
  { role: 'Formato', name: 'IMAX 70mm — no cabía en otra pantalla' },
  { role: 'Clasificación', name: 'Apta para enamorados' },
  { role: 'Duración', name: 'Toda la vida' },
]

/**
 * Créditos rodantes estilo final de película.
 */
export default function Credits() {
  return (
    <div className="relative mx-auto h-72 max-w-md overflow-hidden sm:h-80">
      {/* Degradados arriba/abajo para el efecto de fundido */}
      <div className="pointer-events-none absolute inset-x-0 top-0 z-10 h-16 bg-linear-to-b from-(--color-cinema) to-transparent" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-16 bg-linear-to-t from-(--color-cinema) to-transparent" />

      <div className="animate-credits-roll text-center">
        <p className="font-marquee mb-8 text-xl text-(--color-gold-light)">Créditos</p>
        {CREDITS.map((c) => (
          <div key={c.role} className="mb-6">
            <p className="font-pixel text-[7px] tracking-widest uppercase opacity-50 sm:text-[8px]">
              {c.role}
            </p>
            <p className="font-letter mt-1 text-lg text-(--color-cream) sm:text-xl">{c.name}</p>
          </div>
        ))}
        <p className="font-marquee mt-10 mb-2 text-3xl text-(--color-gold)">FIN</p>
        <p className="text-sm italic opacity-70">…es solo el comienzo ❤</p>
      </div>

      <style>{`
        @keyframes credits-roll {
          0% { transform: translateY(100%); }
          100% { transform: translateY(-100%); }
        }
        .animate-credits-roll {
          animation: credits-roll 38s linear infinite;
        }
      `}</style>
    </div>
  )
}
