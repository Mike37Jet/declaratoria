# Para Rommina (declaratoria)

Dos cosas en un repo: la página web de la declaración y una app de Android (ROMMICINES)
con widgets. Todo el texto visible va **en español**.

## Web (raíz)

```bash
npm run dev      # servidor local
npm run build    # tsc -b && vite build
npm run lint     # oxlint
```

React 19 + TypeScript + Vite + Tailwind 4. Se publica sola en GitHub Pages
(`.github/workflows/deploy.yml`). Nombres, URL y fecha del "sí" viven en `src/config.ts`.

## Android (`android/`)

```bash
cd android
./gradlew --offline assembleDebug testDebugUnitTest -q   # compilar y probar
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Emulador: `Pixel_10a`.

Widget del contador ([ContadorWidget.kt](android/app/src/main/java/com/roguelito/rommicines/ContadorWidget.kt)),
con tres diseños según el tamaño: mini (2×1, 1×1), franja (3×1, 4×1) y completo (4×2).
Los umbrales están en `COMPLETO` / `FRANJA` / `MINI` (en dp; un 2×1 mide ~180 dp).

El contador tiene 20 diseños ([Tema.kt](android/app/src/main/java/com/roguelito/rommicines/Tema.kt)),
cada widget el suyo, y se eligen en `ElegirTemaActivity` (ajustes del widget o «Añadir widget» en la app).
Cada diseño tiene sus tres layouts `widget_<tema>.xml`, `_franja` y `_mini` con los mismos ids
(un widget no puede cambiar de fuente en tiempo de ejecución). El ♥ va como `♥&#xFE0E;` para que
no salga como emoji y tome el color del diseño.
Los 10 de estilo Apple / One UI (Vidrio, Anillo, Puntos, Papel, Tinta, Transparente, Barra Now, Pastel,
Bloques, Coral) se generan con `python3 android/herramientas/generar_temas_nuevos.py android/app/src/main/res`:
se cambian en el script, no a mano. Márgenes en escala áurea (5 · 8 · 13 · 21 dp): sin cajas: completo 21, franja y mini 13; con cajas (Bloques, Cine, Cielo, Neón, Material, Azul): completo 13 afuera y 8 entre cajas, franja y mini 8 y 5 (`PADDING` en el script; los diez originales, a mano). Letra en escala áurea de dos bases: 8 · 10 · 13 · 16 · 21 · 26 · 34 · 42 · 55 sp (`ESCALA_LETRA`; el ♥ del latido crece por pasos de esa escala, ver `Tema.kt`). Vidrio líquido lleva todo en φ (botones de 34 dp, «Toque» φ veces «Abrir página», botón de la franja 34×55). Anillo y Puntos se pintan como imagen (`Graficos.kt`), Barra Now usa un
ProgressBar; los tres leen `Juntos.mes()` (mes juntos por fechas del calendario, «faltan N días para X meses»).
Las esquinas de los marcos usan `@dimen/radio_widget` (en Android 12+ el radio del launcher, para que no recorte el borde).

La **libreta compartida** vive en `android/.../libreta/` (`LibretaActivity`, datos en `Libreta.kt`):
Firestore + acceso anónimo + código de 6 letras para unir el segundo celular. Las reglas están en
`android/firestore.rules` y se pegan a mano en la consola de Firebase (Firestore → Reglas).
Diseño de papelería (letra Caveat, `colors_libreta.xml`), nada del estilo cine.

El **álbum de recortes** (`Album.kt`, `AlbumActivity.kt`, `RecuerdoView.kt`): al tachar un plan se pide
foto + una línea y queda en `parejas/{p}/recuerdos/{idDelPlan}`. La foto va **dentro del documento**
como Blob JPEG ≤ 200 KB (`Album.LIMITE`), porque Cloud Storage exige el plan Blaze. El Nº del boleto
es el orden en que se tachó (`Album.numeroDeBoleto`).

Etapa 3 de la libreta: una **propuesta** (clip en el renglón nuevo, `Estuche.kt`) lleva clip y «firma:» hasta
que el otro la firma; mientras, no se puede tachar. Cada uno elige **lapicero** (bolígrafo, plumón, pluma; se
guarda en las prefs «libreta»). Las **manchas de café** salen al llevar 3, 10 y 20 planes hechos (`Libreta.manchas`)
y se limpian frotando de lado a lado (`PaginaView`); las limpias quedan en `parejas/{p}/hoja/manchas`.
Las preguntas dentro de la libreta van en una notita de papel (`nota()`), no en `AlertDialog`.
En la libreta **no se usan emojis**: los íconos son de trazo, de Phosphor Icons (light, MIT) pasados a
`res/drawable/ic_libreta_*.xml` (licencia en `res/raw/phosphor_licencia.txt`). Los toques sí llevan emoji (`TipoToque`).
Pulsación larga: en un plan sin tachar, corregirlo o borrarlo (`corregir()`); en uno tachado, quitar el tachón.
Los **avisos** de la libreta (`AvisosLibreta.kt`) viajan por el mismo tema de la pareja con `"clase":"libreta"`
y el id de la pareja (sin campo `nombre`, para que un APK viejo no los tome por toque); cada celular descarta
los de otra pareja y los suyos. Para probar en el emulador sin mandar nada: modo avión + prefs falsas.

La **pantalla principal** es una taquilla de cine ([Taquilla.kt](android/app/src/main/java/com/roguelito/rommicines/Taquilla.kt):
`Marquesina`, `Boleto`): la frase del toque se elige en un campo que abre una lista (`TipoToque.elegibles`), los recuerdos
en una tira de película horizontal y la «cartelera» lee la misma copia local que el widget de la libreta.
El último toque recibido se guarda aparte (`Toques.ultimoRecibido`). El nombre se cambia desde Ayuda.

El **widget de la libreta** (`libreta/LibretaWidget.kt`) es una lista que se desliza con todos los planes pendientes
(firmados y por firmar; los tachados no salen), servida por `LibretaWidgetServicio`. Cada renglón es una imagen
(Caveat; un widget no admite fuentes propias) con su acción a la derecha: casilla (tachar), pluma (firmar la
propuesta del otro) o clip (propuesta tuya, espera firma). Los toques pasan por `AccionLibretaActivity`: firmar
es una notita encima de la pantalla de inicio; tachar, abrir un plan y el «+» abren la libreta con un extra
`accion`, y ella abre la tapa, baja al plan y tacha (sello + foto), firma o enfoca el renglón nuevo.
Papel con grano, cantos de hojas y agujeros perforados (se ve el fondo); bajo 100 dp de alto no lleva cabecera
(`widget_libreta_corta`). Medidas áureas: cabecera 55, renglón 34, letra 21/26. Lee una copia local
(prefs «libreta_widget», con `mio` por plan) que deja `LibretaActivity.pintar` y parchan los avisos
(`alAviso`) y la firma desde el widget (`firmadaAqui`); no lee Firestore. **No usar `setEmptyView`** con la
imagen de «nada pendiente»: se colaba en los renglones y cada plan salía con la acción del siguiente.

«Nuestros recuerdos» (pantalla principal y tablero de corcho) se comparte cuando la libreta está
vinculada ([FotosCompartidas.kt](android/app/src/main/java/com/roguelito/rommicines/FotosCompartidas.kt)):
la lista liviana en `parejas/{p}/fotos` y cada foto aparte en `fotosDatos` (se baja una sola vez).
Cada celular sigue leyendo su copia de `filesDir/fotos`; quitar marca `quitada` en vez de borrar.
Las fotos de la página (`0000000000000-…`) no se suben: vienen en la app. Solo sincroniza con la app abierta.

El **tablero de corcho** (`tablero/`: `Tablero.kt` datos, `DibujoTablero.kt` dibujo, `TableroView` arrastre,
`TableroActivity`; se abre con «Tablero ▸» en la pantalla principal o tocando el widget) clava fotos de «Nuestros
recuerdos» (con pie opcional) y notitas propias (amarilla, rosa, celeste) con chinchetas. Corcho claro de grano fino y
marco blanco. Cada pieza guarda su centro como **fracción** del tablero, giro y `orden` (la última tocada va encima);
se comparte en `parejas/{p}/tablero/{id}` (quitar marca `quitada`) y la copia local está en las prefs «tablero».
Mientras nadie acomoda nada se arma solo con las 7 fotos más recientes (`automatico`); el primer cambio guarda ese
acomodo (`Tablero.fijar`). El **widget de fotos es el tablero**: la clase sigue siendo `CarruselWidget` (y
`carrusel_info.xml`) para no perder los widgets puestos, pero dibuja una sola imagen con `DibujoTablero.widget`
(fuera del hilo principal) y muestra las piezas de más arriba que caben (`cabenEnWidget`: 2 en 2×2, 3 en 3×2, 4 en
4×2, hasta 7), estiradas para llenar su forma (`estirar`). `CarruselWidget.refrescar` lo vuelve a dibujar.
En los layouts de widget no se puede usar `<View>` suelto (la vista previa sale «Couldn't add widget»): `FrameLayout`.

## Cosas que no se ven en el código

- **La fecha del "sí" está duplicada**: `OFFICIAL_YES_DATE` en `src/config.ts` y `DESDE` en
  `android/.../Juntos.kt`. Si cambia una, cambia la otra. Lo mismo con la URL de la página.
- **Los toques son reales.** La app no lleva la llave de Firebase: toques y avisos se piden al
  **servidor de avisos** (`servidor/`, Cloudflare Worker `rommicines-avisos.rommicines.workers.dev`),
  que comprueba con la sesión del celular que es miembro de la pareja y publica en el tema
  `pareja-<id>` (`Toques.tema`). El tema viejo `toques` ya no se usa. Antes de probar enviar, avisa al usuario;
  en el emulador se prueba con una libreta propia (nadie más escucha su tema).
- **Publicar el servidor:** `cd servidor && npx wrangler deploy`. La llave vive como secreto `LLAVE`
  en Cloudflare (`npx wrangler secret put LLAVE < archivo`, lo corre el usuario). Nunca leerla.
- **Al clonar falta** `android/app/google-services.json` (está en `.gitignore`); sin él no compila.
  Cómo conseguirlo: README, «Archivos que no están en el repo».
- **Llaves de Firebase**: ninguna va en el repo ni en el APK. Nunca leerlas, imprimirlas ni subirlas (un hook lo bloquea).
- **No reactivar `RescheduleReceiver` de WorkManager.** Está quitado en el manifiesto a
  propósito: activarlo/desactivarlo en cada envío hacía parpadear los widgets.
- **Reinstalar el APK borra los widgets del launcher** del emulador; hay que volver a ponerlos.
- Los tamaños de widget se redimensionan arrastrando los controles tras mantener pulsado.
