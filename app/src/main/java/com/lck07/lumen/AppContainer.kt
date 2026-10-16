package com.lck07.lumen

import android.content.Context
import com.lck07.lumen.core.xtream.XtreamClient
import com.lck07.lumen.data.AccountRepository
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.CredentialStore
import com.lck07.lumen.data.GuideRepository
import com.lck07.lumen.data.LibraryRepository
import com.lck07.lumen.data.NetworkMonitor
import com.lck07.lumen.data.ParentalController
import com.lck07.lumen.data.SessionController
import com.lck07.lumen.data.SettingsStore
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.player.PlaybackManager
import com.lck07.lumen.tv.WatchNextPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency injection: one instance of everything for the app's lifetime. */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", XtreamClient.USER_AGENT).build())
        }
        .build()

    val db: LumenDatabase by lazy { LumenDatabase.create(context) }
    val settings = SettingsStore(context)
    val credentials = CredentialStore(context)
    val network = NetworkMonitor(context)
    val account = AccountRepository(credentials, http, scope)
    val catalog by lazy { CatalogRepository(db) }
    val library by lazy { LibraryRepository(db, catalog).also { it.listener = watchNext } }
    val guide by lazy { GuideRepository(db, http) }
    val playback by lazy { PlaybackManager(context, http, settings) }
    val watchNext by lazy { WatchNextPublisher(context, db) }
    val parental = ParentalController(settings)
    val session by lazy { SessionController(context, scope, account, catalog, settings) }
}
