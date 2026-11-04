# HomeFit

App Android nativa de ejercicio en casa. Kotlin, Jetpack Compose, Material 3, Room, DataStore y WorkManager. Offline primero, en español y preparada para i18n. `minSdk 26`.

La lógica de negocio vive en clases Kotlin puras (motor de entrenamiento, progresión, estadísticas) probadas con tests unitarios; la UI sigue MVVM: composables que observan ViewModels de Android (`ui/vm/AppViewModels.kt`), que a su vez leen los repositorios Room.

## Qué incluye

- Onboarding con perfil (objetivo, nivel, material, días por semana) y edición posterior en Ajustes, con IMC calculado desde la última medida.
- Catálogo de 90 ejercicios con filtros por músculo, material y dificultad; instrucciones y media con licencia libre.
- 12 rutinas predefinidas por objetivo, nivel (principiante, intermedio y avanzado) y tipo (fuerza, superior, inferior, core, cardio, HIIT, movilidad, calentamiento y estiramiento), más un constructor de rutinas propias.
- Modo entrenamiento: pantalla encendida, temporizadores, pitidos y frases por voz (TTS con selector de voz), servicio en primer plano, pausa, saltar, repetir, ajustar descanso, RPE por serie y reanudación de sesiones interrumpidas.
- Progresión de doble progresión con semanas de descarga.
- Planificación semanal y mensual con recordatorios; sesiones que se marcan completadas, saltadas o movidas. El aviso puede ser exacto (permiso «Alarmas y recordatorios» de Android, puntual incluso con ahorro de batería) o usar el mecanismo normal (WorkManager, que el sistema puede retrasar). Con el modo exacto activo se programa además un respaldo de WorkManager con instante objetivo 15 minutos después: si Android borra la alarma (por ejemplo, al revocar el permiso con la app cerrada), el respaldo intenta avisar ese día y reactivar el mecanismo normal. Ese instante objetivo no es una garantía de entrega: WorkManager es inexacto por diseño y el ahorro de batería o las cuotas del sistema pueden aplazarlo más. Con el móvil apagado o la app detenida a la fuerza no hay aviso hasta el próximo arranque o apertura.
- Medidas corporales con IMC, importación y exportación CSV.
- Estadísticas: racha, volumen por grupo muscular, gráficas de evolución y récords personales.
- Copia de seguridad JSON/CSV y restauración desde Ajustes.
- Actualización manual desde GitHub Releases (ver más abajo).

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

La app consulta GitHub Releases al abrirse tras el onboarding (una vez por instancia de pantalla principal, sin sondeo periódico) y también desde Ajustes con «Buscar actualizaciones». Elige la versión numérica X.Y.Z más alta, incluidas las 0.x marcadas como prerelease; excluye borradores y etiquetas con sufijos alfa/beta/rc. Si no hay releases válidas o falla la red, muestra que no pudo comprobar.

Al tocar Descargar, la app baja el APK de la release (solo assets APK de kryll/homefit, redirecciones limitadas a GitHub, caché con límite de 100 MiB) y, antes de ofrecer Instalar, verifica paquete, versionCode estrictamente superior y certificado firmante idéntico al de la app instalada; una rotación de clave no se admite. Android pedirá el permiso de instalar orígenes desconocidos la primera vez y el instalador del sistema hace la verificación y confirmación final: no hay instalación silenciosa. Detalles en [docs/actualizaciones.md](docs/actualizaciones.md).

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencias de terceros

Ver [LICENSES.md](LICENSES.md).
