/**
 * Servidor de avisos de ROMMICINES (Cloudflare Worker).
 *
 * La app ya no lleva la llave de Firebase: le pide a este servidor que publique un toque o un
 * aviso de la libreta. El servidor solo lo hace si quien lo pide es miembro de la pareja, y lo
 * comprueba leyendo `parejas/{pareja}` de Firestore con la propia sesión del celular: las reglas
 * de Firestore solo dejan leer ese documento a sus dos miembros.
 *
 * La llave (JSON de la cuenta de servicio) vive como secreto `LLAVE`: `npx wrangler secret put LLAVE`.
 *
 * POST /publicar
 *   Authorization: Bearer <token de Firebase Auth del celular>
 *   { "pareja": "<id>", "datos": { ... }, "ttl": "3600s" }
 * Publica `datos` en el tema `pareja-<id>`, que solo escuchan los dos celulares.
 */

const ALCANCE = 'https://www.googleapis.com/auth/firebase.messaging';
const TTL_MAXIMO = 86400; // un día, como los avisos de la libreta

export default {
  async fetch(peticion, env) {
    const url = new URL(peticion.url);
    if (url.pathname !== '/publicar') return respuesta(404, 'no existe');
    if (peticion.method !== 'POST') return respuesta(405, 'solo POST');

    const sesion = (peticion.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
    if (!sesion) return respuesta(401, 'falta la sesión');

    let cuerpo;
    try {
      cuerpo = await peticion.json();
    } catch {
      return respuesta(400, 'cuerpo inválido');
    }
    const error = revisar(cuerpo);
    if (error) return respuesta(400, error);

    const llave = JSON.parse(env.LLAVE);
    const proyecto = llave.project_id;

    // ¿Es de esta pareja? Firestore aplica sus reglas con la sesión del celular.
    const doc = await fetch(
      `https://firestore.googleapis.com/v1/projects/${proyecto}/databases/(default)/documents/parejas/${cuerpo.pareja}?mask.fieldPaths=miembros`,
      { headers: { Authorization: `Bearer ${sesion}` } },
    );
    if (doc.status === 401) return respuesta(401, 'sesión vencida');
    if (!doc.ok) return respuesta(403, 'no eres de esta libreta');

    const mensaje = {
      message: {
        topic: `pareja-${cuerpo.pareja}`,
        data: cuerpo.datos,
        android: { priority: 'high', ttl: cuerpo.ttl || '3600s' },
      },
    };
    const token = await tokenDeAcceso(llave);
    const fcm = await fetch(`https://fcm.googleapis.com/v1/projects/${proyecto}/messages:send`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(mensaje),
    });
    if (!fcm.ok) {
      console.log('FCM respondió', fcm.status, await fcm.text());
      return respuesta(502, `Firebase respondió ${fcm.status}`);
    }
    return respuesta(200, 'ok');
  },
};

/** Devuelve el problema del cuerpo en palabras, o null si está bien. */
export function revisar(cuerpo) {
  if (!cuerpo || typeof cuerpo !== 'object') return 'cuerpo inválido';
  if (typeof cuerpo.pareja !== 'string' || !/^[A-Za-z0-9]{10,40}$/.test(cuerpo.pareja)) return 'pareja inválida';
  const datos = cuerpo.datos;
  if (!datos || typeof datos !== 'object' || Array.isArray(datos)) return 'faltan los datos';
  const claves = Object.keys(datos);
  if (claves.length === 0 || claves.length > 20) return 'datos inválidos';
  if (!claves.every((k) => typeof datos[k] === 'string')) return 'los datos deben ser texto';
  if (JSON.stringify(datos).length > 3000) return 'mensaje muy largo';
  if (cuerpo.ttl !== undefined) {
    const m = /^(\d{1,6})s$/.exec(cuerpo.ttl);
    if (!m || Number(m[1]) > TTL_MAXIMO) return 'ttl inválido';
  }
  return null;
}

function respuesta(codigo, texto) {
  return new Response(JSON.stringify({ resultado: texto }), {
    status: codigo,
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
  });
}

// Token OAuth de Google (dura una hora). Se guarda mientras el Worker siga vivo.
let token = null;
let tokenVence = 0;

async function tokenDeAcceso(llave) {
  if (token && Date.now() < tokenVence) return token;
  const ahora = Math.floor(Date.now() / 1000);
  const sinFirma =
    b64(JSON.stringify({ alg: 'RS256', typ: 'JWT' })) +
    '.' +
    b64(JSON.stringify({ iss: llave.client_email, scope: ALCANCE, aud: llave.token_uri, iat: ahora, exp: ahora + 3600 }));

  const pem = llave.private_key.replace(/-----[^-]+-----/g, '').replace(/\s/g, '');
  const privada = await crypto.subtle.importKey(
    'pkcs8',
    Uint8Array.from(atob(pem), (c) => c.charCodeAt(0)),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const firma = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', privada, new TextEncoder().encode(sinFirma));

  const r = await fetch(llave.token_uri, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: sinFirma + '.' + b64(firma),
    }),
  });
  if (!r.ok) throw new Error(`Google no dio token (${r.status}): ${await r.text()}`);
  const json = await r.json();
  tokenVence = Date.now() + (json.expires_in - 60) * 1000;
  token = json.access_token;
  return token;
}

function b64(valor) {
  const bytes = typeof valor === 'string' ? new TextEncoder().encode(valor) : new Uint8Array(valor);
  let texto = '';
  for (const b of bytes) texto += String.fromCharCode(b);
  return btoa(texto).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
