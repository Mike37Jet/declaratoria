# Pantalla principal: revisión de usabilidad

Revisión del 26 de octubre de 2026. Se aplicaron las diez heurísticas de
[Jakob Nielsen, Nielsen Norman Group](https://www.nngroup.com/articles/ten-usability-heuristics/)
al inicio de ROMMICINES. Son criterios de revisión, no una certificación ni un
sustituto de observar a Miguel y Rommina usando sus propios celulares.

El criterio visual sigue la explicación de NN/g sobre
[diseño estético y minimalista](https://www.nngroup.com/articles/aesthetic-minimalist-design/):
dar prioridad a las tareas y presentar las instrucciones cuando se necesitan.

| # | Heurística | Aplicación concreta en esta pantalla |
|---|---|---|
| 1 | Estado visible | El botón pasa a «Enviando…»; después aparece una confirmación con destinatario y hora, o un error persistente. El estado se recupera al reabrir la app. La importación de fotos muestra «Guardando…». |
| 2 | Lenguaje cotidiano | «Enviar toque», «Añadir fotos», «Nuestro tiempo juntos» y frases escritas en primera persona. La interfaz no expone Firebase ni detalles de programación. |
| 3 | Control y libertad | Los selectores permiten cancelar; la foto se puede cerrar sin cambiar nada. Quitar una foto requiere confirmar y ofrece «Deshacer». |
| 4 | Consistencia | Botones Android con áreas mínimas de 48 dp, estilos compartidos, una misma palabra para cada acción y el selector de fotos del sistema. |
| 5 | Prevención | Mensaje y destinatario visibles antes de enviar; selección e identidad bloqueadas durante el envío. La tarea única evita duplicar un envío que sigue en curso. La eliminación afecta solo a la copia del carrusel. |
| 6 | Reconocimiento | Identidad y frase elegida a la vista. Tocar una miniatura abre una vista con la acción «Quitar del carrusel»; no hace falta conocer la pulsación larga. |
| 7 | Eficiencia | La frase elegida queda guardada para la app y el widget. Se mantienen los accesos rápidos del widget y el beso desde la notificación. La pulsación larga sigue disponible como atajo para las fotos. |
| 8 | Minimalismo | Solo «Enviar toque» tiene el énfasis principal. «Añadir fotos» es secundario. Las instrucciones salen del pie de pantalla, la galería muestra seis recuerdos inicialmente y los widgets se eligen en un único acceso. |
| 9 | Recuperación | Los fallos de envío muestran una explicación breve y «Reintentar toque». El error se conserva al reabrir. Las fotos pueden restaurarse y los avisos desactivados tienen un acceso para activarlos. |
| 10 | Ayuda contextual | «Ayuda» abre cuatro tareas: enviar/responder, widgets, fotos e identidad. Los pasos aparecen solo al elegir una tarea. Si el launcher no admite fijar widgets, se muestra la alternativa manual. |

## Mensajes y notificaciones

Una notificación contiene el nombre y una frase corta. El botón «Un beso de
vuelta» es la acción; no se repiten instrucciones en cada mensaje. Una vez
enviado el beso se muestra una confirmación sin el botón anterior. Si falla,
se conserva una acción para reintentar.

El contador pequeño conserva su diseño: tocarlo envía la frase elegida y el
corazón abre la página. Los mensajes recibidos no sustituyen sus días y horas.

## Alcance y límites

- «Enviado» significa que Firebase aceptó la solicitud. No se presenta como
  confirmación de entrega ni de lectura en el otro celular.
- «Deshacer» recupera la última foto quitada; las copias apartadas permanecen
  en el almacenamiento privado de la app. Los originales de la galería no se
  modifican.
- La validación en emulador se complementa con el uso en ambos celulares:
  notificaciones, vibración, restricciones de batería y launcher pueden variar.

## Validación

La compilación, las seis pruebas de mensajes y Android Lint se ejecutan con:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Las comprobaciones de interfaz cubren cancelación de selectores, ayuda,
selección de widgets, quitar/restaurar una foto, fallo de red, reintento y
legibilidad con texto ampliado. Los resultados finales se anotan tras la
revisión en el emulador.
