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

El contador tiene 10 diseños ([Tema.kt](android/app/src/main/java/com/roguelito/rommicines/Tema.kt)),
cada widget el suyo, y se eligen en `ElegirTemaActivity` (ajustes del widget o «Añadir widget» en la app).
Cada diseño tiene sus tres layouts `widget_<tema>.xml`, `_franja` y `_mini` con los mismos ids
(un widget no puede cambiar de fuente en tiempo de ejecución). El ♥ va como `♥&#xFE0E;` para que
no salga como emoji y tome el color del diseño.

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
Los **avisos** de la libreta (`AvisosLibreta.kt`) viajan por el mismo tema `toques` con `"clase":"libreta"`
y el id de la pareja (sin campo `nombre`, para que un APK viejo no los tome por toque); cada celular descarta
los de otra pareja y los suyos. Para probar en el emulador sin mandar nada: modo avión + prefs falsas.

La **pantalla principal** es una taquilla de cine ([Taquilla.kt](android/app/src/main/java/com/roguelito/rommicines/Taquilla.kt):
`Marquesina`, `Boleto`, `Renglones`): las frases del toque van como pastillas (`TipoToque.elegibles`), los recuerdos
en una tira de película horizontal y la «cartelera» lee la misma copia local que el widget de la libreta.
El último toque recibido se guarda aparte (`Toques.ultimoRecibido`). El nombre se cambia desde Ayuda.

El **widget de la libreta** (`libreta/LibretaWidget.kt`) se dibuja entero como imagen (papel, renglones,
Caveat) porque un widget no admite fuentes propias. Lee una copia local (prefs «libreta_widget») que deja
`LibretaActivity.pintar` y parchan los avisos del otro celular (`LibretaWidget.alAviso`); no lee Firestore.
Mini: el próximo plan (en dos renglones si no cabe); franja: el próximo y «n/100»; completo: título y hasta 3 planes.

«Nuestros recuerdos» (pantalla principal y widget carrusel) se comparte cuando la libreta está
vinculada ([FotosCompartidas.kt](android/app/src/main/java/com/roguelito/rommicines/FotosCompartidas.kt)):
la lista liviana en `parejas/{p}/fotos` y cada foto aparte en `fotosDatos` (se baja una sola vez).
Cada celular sigue leyendo su copia de `filesDir/fotos`; quitar marca `quitada` en vez de borrar.
Las fotos de la página (`0000000000000-…`) no se suben: vienen en la app. Solo sincroniza con la app abierta.

## Cosas que no se ven en el código

- **La fecha del "sí" está duplicada**: `OFFICIAL_YES_DATE` en `src/config.ts` y `DESDE` en
  `android/.../Juntos.kt`. Si cambia una, cambia la otra. Lo mismo con la URL de la página.
- **Los toques son reales.** Enviar un toque desde el emulador publica en el tema `toques` de
  Firebase y le puede llegar a Mimina. Antes de probar el botón de enviar, avisa al usuario.
- **Al clonar faltan dos archivos de Firebase** (están en `.gitignore`): `android/app/google-services.json`
  y la llave de abajo. Sin el primero no compila. Cómo conseguirlos: README, «Archivos que no están en el repo».
- **Llave de Firebase**: `android/app/secreto/firebase-llave.json` viaja dentro del APK y está
  en `.gitignore`. Nunca leerla, imprimirla ni subirla (un hook lo bloquea).
- **No reactivar `RescheduleReceiver` de WorkManager.** Está quitado en el manifiesto a
  propósito: activarlo/desactivarlo en cada envío hacía parpadear los widgets.
- **Reinstalar el APK borra los widgets del launcher** del emulador; hay que volver a ponerlos.
- Los tamaños de widget se redimensionan arrastrando los controles tras mantener pulsado.
