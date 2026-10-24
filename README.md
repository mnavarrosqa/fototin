# Fototin

App de Android para tener widgets lindos con tus fotos en la pantalla de inicio.

## Qué hace

- **Elegí fotos de la galería o de Google Fotos** con el selector de fotos del sistema
  (no pide permisos de almacenamiento). Si en tu teléfono está activado Google Fotos como
  "app de contenido multimedia en la nube", sus fotos aparecen en el mismo selector.
- **"Compartir → Fototin"** desde Google Fotos o cualquier galería también agrega fotos.
- **Widgets redimensionables** con cuatro estilos: Redondeado, Polaroid, Círculo y Borde a borde.
  Cada widget elige su estilo al agregarlo (y se puede reconfigurar manteniéndolo presionado).
- **Rotación automática** cada 15 min, 1 h, 6 h o 1 día, en orden o aleatoria.
  Tocar el widget pasa a la siguiente foto.

## Cómo funciona

- Las fotos elegidas se copian (reducidas a 1600 px) a la carpeta privada de la app, así el
  widget no depende de permisos temporales ni de conexión.
- El widget está hecho con [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance);
  el estilo se dibuja en un bitmap del tamaño exacto del widget (`PhotoStyler`).
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
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions también lo
compila en cada push y lo deja como artefacto descargable (`fototin-debug-apk`).

Requiere Android 9 o superior.
