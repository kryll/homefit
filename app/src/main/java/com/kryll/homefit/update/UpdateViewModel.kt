package com.kryll.homefit.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kryll.homefit.data.UpdateChecker
import com.kryll.homefit.domain.ReleaseInfo
import com.kryll.homefit.domain.UpdateCheck
import com.kryll.homefit.domain.UpdateState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado compartido entre aviso al abrir y Ajustes; sobrevive a giros, nunca instala sin un toque. */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updates = state.asStateFlow()
    private val transfer = MutableStateFlow(DownloadUi())
    val download = transfer.asStateFlow()
    private var checkedAtLaunch = false
    private val notice = MutableStateFlow<ReleaseInfo?>(null)
    val launchNotice = notice.asStateFlow()

    fun dismissNotice() { notice.value = null }
    fun checkOnLaunch() { if (!checkedAtLaunch) { checkedAtLaunch = true; check(true) } }
    @Suppress("DEPRECATION")
    fun check(automatic: Boolean = false) {
        if (state.value == UpdateState.Checking || transfer.value.busy) return
        state.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val info = UpdateChecker.latest()
                val app = getApplication<Application>()
                val current = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
                state.value = if (info != null && UpdateCheck.isNewer(info.version, current)) {
                    if (automatic) notice.value = info
                    UpdateState.Available(info)
                } else UpdateState.UpToDate
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { state.value = UpdateState.Failed }
        }
    }

    fun fetch() {
        val info = (state.value as? UpdateState.Available)?.info ?: return
        if (transfer.value.busy || info.apkUrl == null) return
        transfer.value = DownloadUi(busy = true)
        viewModelScope.launch {
            try {
                val file = ApkDownloader.download(getApplication(), info) { percent ->
                    transfer.value = DownloadUi(busy = true, percent = percent)
                }
                transfer.value = DownloadUi(file = file)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { transfer.value = DownloadUi(failed = true) }
        }
    }
    fun installFailed() { transfer.value = DownloadUi(failed = true) }
}

data class DownloadUi(val busy: Boolean = false, val percent: Int? = null, val file: File? = null, val failed: Boolean = false)
