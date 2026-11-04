package com.kryll.homefit.data

/**
 * Importa una copia con compensación: Room es atómico por sí solo, pero ajustes (DataStore) y recordatorios no
 * entran en esa transacción. Antes de tocar nada se toma una instantánea de lo actual; si cualquier paso falla
 * (Room, ajustes o reprogramación), se restaura la instantánea y se propaga el error.
 */
class BackupApplier(
    private val repo: BackupRepository,
    private val currentSettings: suspend () -> AppSettings,
    private val writeSettings: suspend (AppSettings) -> Unit,
    /** Se llama tras aplicar datos y ajustes (p. ej. reprogramar el recordatorio diario). */
    private val afterApply: suspend () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun apply(b: Backup) {
        repo.validate(b)
        val before = currentSettings()
        val snapshot = repo.export(before, now())
        try {
            repo.restore(b)
            writeSettings(b.settings.copy(onboardingDone = before.onboardingDone || b.profile != null))
            afterApply()
        } catch (e: Throwable) {
            rollback(snapshot, before)
            throw e
        }
    }

    private suspend fun rollback(snapshot: Backup, before: AppSettings) {
        runCatching { repo.restore(snapshot.copy(profile = snapshot.profile)) }
        runCatching { writeSettings(before) }
        runCatching { afterApply() }
    }
}
