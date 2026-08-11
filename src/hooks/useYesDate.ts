import { useCallback, useState } from 'react'
import { OFFICIAL_YES_DATE, YES_STORAGE_KEY } from '../config'

/**
 * Fuente de verdad del momento del "sí":
 * 1. OFFICIAL_YES_DATE de config.ts (si Miguel ya la fijó) gana siempre.
 * 2. Si no, lo que quedó guardado en localStorage cuando ella presionó "Sí".
 */
function readYesDate(): Date | null {
  if (OFFICIAL_YES_DATE) return new Date(OFFICIAL_YES_DATE)
  try {
    const stored = localStorage.getItem(YES_STORAGE_KEY)
    return stored ? new Date(stored) : null
  } catch {
    return null
  }
}

export function useYesDate() {
  const [yesDate, setYesDate] = useState<Date | null>(readYesDate)

  const saveYes = useCallback(() => {
    const now = new Date()
    try {
      localStorage.setItem(YES_STORAGE_KEY, now.toISOString())
    } catch {
      // modo incógnito: el contador vive solo en memoria
    }
    setYesDate(OFFICIAL_YES_DATE ? new Date(OFFICIAL_YES_DATE) : now)
  }, [])

  return { yesDate, saveYes }
}
