# Fototin

App de Android para tener widgets lindos con tus fotos en la pantalla de inicio.

**Descargar:** [fototin.apk (última versión)](https://github.com/mnavarrosqa/fototin/releases/latest/download/fototin.apk).
Abrilo desde el teléfono para instalar o actualizar. Requiere Android 9 o superior.

## Qué hace

La app tiene tres pestañas: **Fotos** (tu colección, álbumes y recuerdos), **Widgets** (los que
tenés puestos y para agregar nuevos) y **Ajustes** (rotación, copia de seguridad y ayuda).


- **Elegí fotos de la galería o de Google Fotos** con el selector de fotos del sistema
  (no pide permisos de almacenamiento). Si en tu teléfono está activado Google Fotos como
  "app de contenido multimedia en la nube", sus fotos aparecen en el mismo selector.
- **"Compartir → Fototin"** desde Google Fotos o cualquier galería también agrega fotos.
- **Widget de foto con 10 estilos:** Redondeado (con las esquinas del launcher), Polaroid, Washi,
  Arco, Círculo, Corazón, Pétalo, Estampilla, Marco y Borde a borde, con la fecha opcional.
  Al tocarlo pasa a la siguiente foto, la abre en grande o abre la app, según se elija.
- **Widget Collage** con 2, 3 o 4 fotos que se acomodan según la forma del widget.
- **Encuadre inteligente:** al importar se detectan las caras para que el recorte no las corte.
- Cada widget se configura al agregarlo y se puede reconfigurar manteniéndolo presionado.
- **Rotación automática** cada 15 min, 1 h, 6 h o 1 día. En orden aleatorio no se repite
  ninguna foto hasta haberlas mostrado todas. Tocar el widget pasa a la siguiente.
- **Álbumes:** agrupá fotos (Vacaciones, Familia…) y elegí qué álbum muestra cada widget.
- **Recuerdos:** un widget puede mostrar fotos de un día como hoy en otros años, con la
  leyenda "Hace N años". Si ese día no hay, usa la misma semana o el mismo mes.
- **Detalle de cada foto:** tocá una foto para escribirle una nota (aparece en el widget),
  marcarla como favorita, elegir qué parte se ve al recortarla y sumarla a álbumes.
- **Favoritas:** un widget puede mostrar solo tus fotos favoritas.
- **Tus widgets en la app:** cada widget puesto aparece con su foto actual, para cambiarla o
  editarlo sin ir a la pantalla de inicio.
- **Orden:** por fecha en que se agregaron o por fecha de la foto, separadas por año.
- **Sin repetidas:** al importar se reconocen las fotos que ya estaban.
- **Copia de seguridad:** guardá todas las fotos, notas y álbumes en un .zip y restauralas en
  otro teléfono o después de reinstalar (se suman a lo que ya hay, sin repetir).
- **Preparada para foldables:** desplegada usa una barra lateral y el detalle de cada foto se
  divide en dos paneles justo en la bisagra; en modo mesa, el visor muestra la foto arriba y los
  controles abajo. El collage pasa a fila o columna en widgets muy anchos o angostos.
- **Administrar fotos:** mantené presionada una foto para seleccionar varias y sumarlas a un
  álbum, sacarlas de él o borrarlas de Fototin (las originales no se tocan).

## Cómo funciona

- Las fotos elegidas se copian (reducidas a 1600 px, más una miniatura de 360 px para la grilla)
  a la carpeta privada de la app; los datos de cada una (tamaño y fecha) van en `photos.json` y
  los álbumes en `albums.json`. `PhotoPool` decide qué fotos le tocan a cada widget.
  Así el widget no depende de permisos temporales ni de conexión.
- El widget está hecho con [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance);
  cada estilo se dibuja en un bitmap del tamaño exacto del widget (`PhotoStyler`).
- La rotación usa WorkManager (`RotationScheduler`).

### Sobre Google Fotos

Desde marzo de 2025 Google ya no permite que apps de terceros lean toda la biblioteca de
Google Fotos con la Library API. Por eso Fototin usa el selector del sistema y "Compartir",
que funcionan sin claves ni cuentas. Una opción a futuro es la
[Google Photos Picker API](https://developers.google.com/photos/picker), que requiere un
proyecto en Google Cloud y OAuth.

## Tests

```sh
./gradlew testReleaseUnitTest
```

Cubren la lógica sin Android: rotación sin repetir, selección del collage, recuerdos y
leyendas. GitHub Actions los corre antes de cada build.

## Compilar

Requiere JDK 17 y el SDK de Android (API 35).

```sh
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. GitHub Actions lo compila en
cada push y, en `main`, lo publica como release.

Los APK oficiales se firman con una clave privada que no está en el repo: GitHub Actions la toma
de los secrets `FOTOTIN_KEYSTORE_BASE64` (el archivo .p12 en base64) y
`FOTOTIN_KEYSTORE_PASSWORD`. Sin esos secrets, el build usa la clave de debug y en `main` no se
publica nada.