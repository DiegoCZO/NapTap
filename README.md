# Doble toque para dormir

App Android mínima (Kotlin, sin librerías) que apaga la pantalla con un doble toque
en la **pantalla de inicio** y/o en la **pantalla de bloqueo**, usando un servicio de
accesibilidad. Android 9 o superior.

## Compilar el APK

### Opción A: GitHub Actions (sin instalar nada)
1. Creá un repo (puede ser privado) y subí esta carpeta tal cual (incluida `.github/`).
2. En la pestaña **Actions** se lanza "Compilar APK" (~3-4 min).
3. Al terminar, abrí la ejecución → **Artifacts** → `DobleToque-apk` (zip con `app-release.apk`).

### Opción B: Android Studio
1. *File → Open* → elegí la carpeta `DobleToqueDormir`.
2. Esperá el Gradle sync y después *Build → Build App Bundle(s) / APK(s) → Build APK(s)*,
   o directamente ▶ Run con el teléfono conectado por USB (depuración USB activada).

Por línea de comandos (con el SDK instalado): `./gradlew assembleRelease`
→ `app/build/outputs/apk/release/app-release.apk`.

## Instalar y activar
1. Instalá el APK (permitir "instalar apps desconocidas").
2. Abrí **Doble toque** → *Activar en Accesibilidad* → activá el servicio.
3. **Android 13+**: si el interruptor aparece gris / "ajuste restringido":
   Ajustes → Apps → Doble toque → ⋮ → **Permitir ajustes restringidos**, y volvé al paso 2.
4. Desactivá la app de doble toque que usabas antes, para que no se pisen.
5. Recomendable: en Ajustes → Batería, ponerla en "Sin restricciones" para que Android
   (sobre todo Samsung/Xiaomi) no mate el servicio.

## Cómo funciona
- Una ventana de accesibilidad de 1×1 px con `FLAG_WATCH_OUTSIDE_TOUCH` recibe un
  `ACTION_OUTSIDE` por cada toque en pantalla sin bloquearlo (el launcher sigue
  funcionando normal). Android no da coordenadas, solo el momento del toque.
- Dos toques en menos de N ms (configurable, 300 por defecto) →
  - si el teléfono está bloqueado y no se ve el teclado del PIN/contraseña → apaga;
  - si está desbloqueado y la ventana activa es tu launcher por defecto → apaga.
- Si el primer toque abre un ícono, una notificación, etc. (evento de *click*), se cancela.
- Apaga con `GLOBAL_ACTION_LOCK_SCREEN`: igual que el botón de encendido, así que la
  huella y el desbloqueo facial siguen funcionando.

## Limitaciones conocidas
- Como no hay coordenadas, un doble toque sobre un widget del escritorio que no emita
  *click* también apaga la pantalla.
- En la lockscreen, la detección del PIN se basa en IDs de vista típicos (AOSP, Pixel,
  Samsung, Xiaomi…). Si en tu teléfono escribir el PIN rápido apaga la pantalla, hay que
  agregar el ID correcto en `BOUNCER_ID_HINTS` (`DoubleTapService.kt`).
- El APK está firmado con la clave *debug*: perfecto para uso personal, no para Play Store.
