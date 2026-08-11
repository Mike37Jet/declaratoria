import { HER_NICKNAME, NOTIFY_TOPIC } from './config'

/** Fecha en hora local de su celular, lista para pegar en OFFICIAL_YES_DATE. */
function localIso(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/**
 * Avisa por ntfy.sh cada vez que se presiona "Sí".
 * Es "dispara y olvida": si no hay internet o falla, la página sigue
 * su fiesta como si nada — el aviso nunca bloquea el momento.
 */
export function notifyYes(isFirstYes: boolean, when: Date) {
  if (!NOTIFY_TOPIC) return

  const legible = when.toLocaleString('es', {
    dateStyle: 'full',
    timeStyle: 'medium',
  })

  const payload = isFirstYes
    ? {
        topic: NOTIFY_TOPIC,
        title: '🎬❤️ ¡¡DIJO QUE SÍ!! ❤️🎬',
        message: `¡${HER_NICKNAME} presionó "Sí" por PRIMERA vez!\n\n📅 ${legible}\n\nPara dejarlo tallado en piedra, en src/config.ts pon:\nOFFICIAL_YES_DATE = '${localIso(when)}'`,
        priority: 5,
        tags: ['tada', 'heart', 'cat'],
      }
    : {
        topic: NOTIFY_TOPIC,
        title: '↻ Repitió la función',
        message: `Volvió a presionar "Sí" (repetición, la fecha original no cambia).\n\n📅 ${legible}`,
        priority: 3,
        tags: ['heart', 'clapper'],
      }

  fetch('https://ntfy.sh', {
    method: 'POST',
    body: JSON.stringify(payload),
  }).catch(() => {
    // sin internet no hay aviso, pero el show continúa
  })
}
