# Actualizaciones desde la app

Al abrir la app tras el onboarding se consulta GitHub Releases una vez por instancia de pantalla principal, sin sondeo periódico. El estado sobrevive a giros. Ajustes permite comprobar de nuevo.

Descargar APK requiere tocar el botón. Solo acepta assets APK en el repositorio kryll/homefit con tag numérico. Las redirecciones HTTPS quedan limitadas a GitHub y sus hosts de assets. El archivo temporal vive en caché, con límite de100MiB y tiempo máximo de espera de red; una descarga fallida o cancelada se elimina.

Antes de ofrecer instalar se compara el paquete, un versionCode estrictamente superior y los certificados firmantes actuales con la app instalada. La comparación exige igualdad exacta: una rotación de clave no se admite automáticamente. La app vuelve a comprobar el archivo al pulsar Instalar. El FileProvider expone solo cache/updates, no bases de datos ni archivos privados.

Android pide permiso para instalar desde esta app si no está concedido. Después de permitirlo, vuelve a la app y pulsa Instalar otra vez. El instalador de Android solicita la confirmación final y verifica el APK. No hay instalación silenciosa. No desinstales para actualizar; exporta una copia antes de instalar.

Una instalación debug no acepta un APK firmado con la clave de release. Eso es una protección, no un motivo para saltar la comprobación de firma. Los archivos descargados pueden desaparecer por limpieza de caché; en ese caso se debe descargar otra vez.

## Verificación pendiente

Los tests cubren reglas de origen, redirecciones, paquete/versión/firma, rechazo de archivo ilegible y aislamiento del FileProvider. Las capturas prueban las pantallas simuladas, no una instalación real. Quedan por probar en dispositivo: descarga completa contra una release real, permiso de origen desconocido, cancelación del instalador, instalación encima conservando datos y apertura tras actualizar. La preparación de este flujo no publica una release ni ejecuta la firma.
