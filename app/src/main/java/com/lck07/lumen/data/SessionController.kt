package com.lck07.lumen.data

import android.content.Context
import android.content.res.Resources
import com.lck07.lumen.R
import com.lck07.lumen.core.xtream.XtreamClient
import com.lck07.lumen.core.xtream.XtreamException
import com.lck07.lumen.work.GuideWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUi {
    data object Idle : ImportUi
    data class Running(val progress: ImportProgress) : ImportUi
    data class Failed(val error: Throwable) : ImportUi
}

/**
 * Signing in and keeping the cached library fresh: the first import (with visible progress),
 * a quiet refresh when the cache is a day old, and the background guide import.
 */
class SessionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val account: AccountRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsStore,
) {
    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val import: StateFlow<ImportUi> = _import.asStateFlow()
    private var job: Job? = null

    /** The first import after signing in; the UI shows its progress. */
    fun startImport(client: XtreamClient? = account.client) {
        if (job?.isActive == true) return
        client ?: return
        job = scope.launch {
            _import.value = ImportUi.Running(ImportProgress(ImportProgress.Step.Categories))
            try {
                catalog.import(client) { p -> _import.value = ImportUi.Running(p) }
                settings.setCatalogImportedAt(System.currentTimeMillis())
                _import.value = ImportUi.Idle
                GuideWorker.refreshNow(context)
                GuideWorker.scheduleDaily(context, settings.current().guideRefreshHour)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _import.value = ImportUi.Failed(e)
            }
        }
    }

    /** At launch: check the account and refresh a day-old catalogue without blocking anything. */
    fun refreshInBackground() {
        scope.launch {
            account.refreshStatus()
            val s = settings.current()
            val client = account.client ?: return@launch
            if (s.catalogImportedAt > 0 && System.currentTimeMillis() - s.catalogImportedAt > 24 * 3600_000L && job?.isActive != true) {
                job = launch {
                    runCatching { catalog.import(client) }.onSuccess { settings.setCatalogImportedAt(System.currentTimeMillis()) }
                }
            }
            GuideWorker.scheduleDaily(context, s.guideRefreshHour)
        }
    }

    /** Reloads the library now (Settings); progress shows like the first import. */
    fun reimport() {
        scope.launch { settings.setCatalogImportedAt(0); startImport() }
    }

    suspend fun signOut() {
        job?.cancel()
        GuideWorker.cancelAll(context)
        account.signOut()
        settings.resetForSignOut()
        _import.value = ImportUi.Idle
    }
}

/** An error explained in a way that tells the owner how to fix it. */
fun Resources.explain(e: Throwable): String = when (e) {
    is XtreamException.AuthFailed -> getString(R.string.error_auth)
    is XtreamException.Inactive -> getString(R.string.error_inactive, e.status.lowercase())
    is XtreamException.Network -> getString(R.string.error_network)
    is XtreamException.Http -> getString(R.string.error_http, e.code)
    is XtreamException.BadResponse -> getString(R.string.error_bad_response)
    is IllegalArgumentException -> getString(R.string.error_bad_address)
    is java.io.IOException -> getString(R.string.error_network)
    else -> getString(R.string.error_generic)
}
