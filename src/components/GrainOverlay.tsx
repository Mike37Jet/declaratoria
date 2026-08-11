/**
 * Grano de película + viñeta cinematográfica sobre toda la pantalla.
 */
export default function GrainOverlay() {
  return (
    <>
      {/* Grano animado */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0 z-40 opacity-[0.07] mix-blend-screen"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='120' height='120' filter='url(%23n)'/%3E%3C/svg%3E")`,
          animation: 'grain-shift 0.6s steps(3) infinite',
        }}
      />
      {/* Viñeta */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0 z-40"
        style={{
          background:
            'radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,0.55) 100%)',
        }}
      />
      <style>{`
        @keyframes grain-shift {
          0% { transform: translate(0, 0); }
          33% { transform: translate(-8px, 4px); }
          66% { transform: translate(6px, -6px); }
          100% { transform: translate(0, 0); }
        }
      `}</style>
    </>
  )
}
