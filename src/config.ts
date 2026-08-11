/**
 * ⚙️ Configuración de la página — edita aquí lo importante.
 */

/** Nombre de ella, aparece en la marquesina y la carta. */
export const HER_NAME = 'Rommina'

/** Tu nombre, aparece en la firma y los créditos. */
export const YOUR_NAME = 'Miguel'

/**
 * URL pública final (GitHub Pages). El script scripts/generate-qr.mjs
 * la usa para generar el código QR y el boleto imprimible.
 * Cámbiala cuando publiques el repositorio.
 */
export const SITE_URL = 'https://mike37jet.github.io/declaratoria/'

/**
 * 📅 Fecha oficial del "sí".
 *
 * Déjala en null antes del gran día: cuando ella presione "Sí",
 * la fecha se guarda en su dispositivo (localStorage) y el contador
 * arranca desde ese instante.
 *
 * Después de que diga que sí, pon aquí la fecha real para que el
 * contador muestre lo mismo en cualquier dispositivo, por ejemplo:
 *   export const OFFICIAL_YES_DATE: string | null = '2026-08-15T21:30:00'
 */
export const OFFICIAL_YES_DATE: string | null = null

/** Clave de localStorage donde se guarda el momento del "sí". */
export const YES_STORAGE_KEY = 'rommina-dijo-que-si'

/**
 * 📸 Fotos del carrusel. Los archivos viven en public/photos/.
 * Reemplaza los .svg de ejemplo por tus fotos reales (jpg/png/webp),
 * actualiza aquí los nombres de archivo y personaliza cada pie de foto.
 */
export const PHOTOS: { file: string; caption: string }[] = [
  { file: 'foto1.svg', caption: 'Escena 1 · Donde todo empezó' },
  { file: 'foto2.svg', caption: 'Escena 2 · Esa sonrisa merece un Óscar' },
  { file: 'foto3.svg', caption: 'Escena 3 · Toma favorita del director' },
  { file: 'foto4.svg', caption: 'Escena 4 · Sin guion, pura improvisación' },
  { file: 'foto5.svg', caption: 'Escena 5 · La mejor coprotagonista' },
  { file: 'foto6.svg', caption: 'Escena 6 · Continuará…' },
]
