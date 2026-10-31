# Álbum Widget (Android)

Widget que muestra imágenes de las carpetas de tu galería y las cambia cada cierto tiempo.

## Cómo se usa
1. Instala el APK, mantén presionada la pantalla de inicio > Widgets > «Álbum Widget».
2. Al colocarlo se abre la configuración: agrega carpetas, elige el tiempo por imagen y guarda.
3. Toca el widget para pasar a la siguiente imagen. Mantenlo presionado > «Reconfigurar» para cambiar ajustes.

## Cómo compilar el APK
**Android Studio:** File > Open esta carpeta, espera el sync y Build > Build APK(s).
El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

**Línea de comandos** (JDK 17 + Android SDK + Gradle 8.9): `gradle assembleDebug`.

**GitHub Actions:** sube la carpeta a un repo de GitHub; el workflow `.github/workflows/build.yml`
compila y deja el APK descargable en la pestaña Actions > Artifacts.
