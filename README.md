# HomeFit

App Android nativa de ejercicio en casa. Kotlin, Jetpack Compose, Material 3, MVVM, Room, DataStore y WorkManager. Offline primero, en español y preparada para i18n. `minSdk 26`.

> Estado: hito H0 (base del proyecto). Las pantallas son marcadores de posición; el catálogo, las rutinas y el modo entrenamiento llegan en los siguientes hitos.

## Hitos

| Hito | Contenido |
| --- | --- |
| H0 | Base: Gradle, Compose, Room (modelo de datos), DataStore, CI, firma por tag |
| H1 | Onboarding, catálogo de 80+ ejercicios, rutinas predefinidas |
| H2 | Constructor de rutinas y modo entrenamiento (voz, pitidos, segundo plano) |
| H3 | Progresión (doble progresión y semanas de descarga) |
| H4 | Planificación y recordatorios |
| H5 | Medidas y estadísticas |
| H6 | Ajustes y copia de seguridad |
| H7 | Releases y actualización desde la app |

Un PR por hito.

## Compilar

Requisitos: JDK 17 y Android SDK 35.

```bash
./gradlew testDebugUnitTest assembleDebug
```

El APK de debug queda en `app/build/outputs/apk/debug/`. Ojo: el APK de debug y el de release llevan firmas distintas, no se pueden instalar uno encima del otro.

## Releases y firma

Al crear un tag `vX.Y.Z`, GitHub Actions compila el APK y el AAB de release firmados y los publica en GitHub Releases con un changelog hecho con los commits. `versionName` sale del tag y `versionCode` es `X*1000000 + Y*1000 + Z`, así que crece con cada versión. Las versiones `0.x` se marcan como pre-release. Solo se admiten tags numéricos `vX.Y.Z`, sin sufijos ni ceros iniciales; Y y Z deben ser menores que 1000 y el código debe estar entre 1 y 2100000000. La validación falla antes de restaurar el keystore.

Para que las actualizaciones se instalen unas encima de otras, la firma tiene que ser siempre la misma. El keystore nunca se guarda en el repositorio. Se genera una vez y se guarda en GitHub (Settings, Secrets and variables, Actions) con estos nombres:

| Secret | Contenido |
| --- | --- |
| `HOMEFIT_KEYSTORE_B64` | El keystore en base64 |
| `HOMEFIT_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `HOMEFIT_KEY_ALIAS` | Alias de la clave |
| `HOMEFIT_KEY_PASSWORD` | Contraseña de la clave |

Para generar un keystore propio:

```bash
keytool -genkeypair -v -keystore homefit.jks -alias homefit -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 homefit.jks   # el resultado va al secret HOMEFIT_KEYSTORE_B64
```

Guarda una copia del keystore y de sus contraseñas fuera de GitHub. Si se pierde, no se podrán publicar actualizaciones instalables sobre la app ya instalada.

## Actualización desde la app

En Ajustes, «Buscar actualizaciones» consulta hasta 100 releases públicas de GitHub y ofrece abrir el APK en el navegador (o la página de la release si no tiene APK). Elige la versión numérica X.Y.Z más alta, incluidas las 0.x marcadas como prerelease; excluye borradores y etiquetas con sufijos alfa/beta/rc. La comprobación es manual: no sondea en segundo plano ni descarga por sí misma. Si no hay releases válidas o falla la red, muestra que no pudo comprobar. El APK se instala a mano y solo actualiza si lleva la misma firma.

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencias de terceros

Ver [LICENSES.md](LICENSES.md).
