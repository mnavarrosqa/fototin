# Fototin

App de Android para tener widgets lindos con tus fotos en la pantalla de inicio.

**Descargar:** [fototin.apk (última versión)](https://github.com/mnavarrosqa/fototin/releases/latest/download/fototin.apk).
Abrilo desde el teléfono para instalar o actualizar. Requiere Android 9 o superior.

## Qué hace

- **Elegí fotos de la galería o de Google Fotos** con el selector de fotos del sistema
  (no pide permisos de almacenamiento). Si en tu teléfono está activado Google Fotos como
  "app de contenido multimedia en la nube", sus fotos aparecen en el mismo selector.
- **"Compartir → Fototin"** desde Google Fotos o cualquier galería también agrega fotos.
- **7 estilos de widget:** Redondeado, Polaroid, Arco, Círculo, Pétalo, Marco y Borde a borde,
  con la fecha de la foto opcional. Cada widget elige su estilo al agregarlo (y se puede
  reconfigurar manteniéndolo presionado).
- **Rotación automática** cada 15 min, 1 h, 6 h o 1 día. En orden aleatorio no se repite
  ninguna foto hasta haberlas mostrado todas. Tocar el widget pasa a la siguiente.
- **Administrar fotos:** mantené presionada una foto para seleccionar varias y quitarlas.

## Cómo funciona

- Las fotos elegidas se copian (reducidas a 1600 px, más una miniatura de 360 px para la grilla)
  a la carpeta privada de la app; los datos de cada una (tamaño y fecha) van en `photos.json`.
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

## Compilar

Requiere JDK 17 y el SDK de Android (API 35).

```sh
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. GitHub Actions lo compila en
cada push y, en `main`, lo publica como release.

Los APK se firman con `app/fototin.keystore`, una clave incluida en el repo para que cada versión
se instale encima de la anterior. Sirve para uso personal; para publicar en Play Store hay que
firmar con una clave privada.
