/**
 * ⚙️ Configuración de la página — edita aquí lo importante.
 */

/** Nombre de ella, aparece en la marquesina y la gran pregunta. */
export const HER_NAME = 'Rommi'

/** Tu nombre, aparece en los créditos. */
export const YOUR_NAME = 'Miguel'

/** Sus apodos: los momentos más íntimos de la función los usan a ellos. */
export const HER_NICKNAME = 'Mimina'
export const YOUR_NICKNAME = 'Roguelito'

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
 * 🔔 Canal secreto de ntfy.sh para avisarte cada vez que ella presione "Sí".
 *
 * Para recibir los avisos en tu celular:
 *   1. Instala la app gratuita "ntfy" (App Store / Play Store).
 *   2. Suscríbete al tema con el nombre exacto de abajo.
 * También puedes verlo en el navegador: https://ntfy.sh/<tema>
 *
 * Déjalo como '' para desactivar los avisos.
 */
export const NOTIFY_TOPIC = 'cine-rommina-si-6c6efe241bcb'

/**
 * 📸 Fotos del carrusel. Los archivos viven en public/photos/.
 * Reemplaza los .svg de ejemplo por tus fotos reales (jpg/png/webp),
 * actualiza aquí los nombres de archivo y personaliza cada pie de foto.
 *
 * `imax: true` marca la foto especial: su fotograma se expande a la
 * proporción 1.43:1 del IMAX, igual que en el cine cuando la película
 * pasa a formato IMAX y la pantalla crece. Marca solo una — funciona
 * mejor con una foto vertical o donde salgan los dos.
 */
export const PHOTOS: { file: string; caption: string; imax?: boolean }[] = [
  { file: 'foto1.svg', caption: 'Escena 1 · Donde todo empezó' },
  { file: 'foto2.svg', caption: 'Escena 2 · Esa sonrisa merece un Óscar' },
  { file: 'foto3.svg', caption: 'Escena 3 · Toma favorita del director', imax: true },
  { file: 'foto4.svg', caption: 'Escena 4 · Sin guion, pura improvisación' },
  { file: 'foto5.svg', caption: 'Escena 5 · La mejor coprotagonista' },
  { file: 'foto6.svg', caption: 'Escena 6 · Continuará…' },
]
