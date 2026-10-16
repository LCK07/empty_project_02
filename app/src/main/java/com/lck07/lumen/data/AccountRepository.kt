package com.lck07.lumen.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.lck07.lumen.core.model.AccountInfo
import com.lck07.lumen.core.model.XtreamAccount
import com.lck07.lumen.core.xtream.XtreamClient
import com.lck07.lumen.core.xtream.XtreamException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient

sealed interface AccountState {
    data object Loading : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val account: XtreamAccount, val client: XtreamClient) : AccountState
}

/** The signed-in Xtream account and the client that talks to it. */
class AccountRepository(
    private val store: CredentialStore,
    private val http: OkHttpClient,
    scope: CoroutineScope,
) {
    val state: StateFlow<AccountState> = store.account
        .distinctUntilChanged()
        .map { a -> if (a == null) AccountState.SignedOut else AccountState.SignedIn(a, XtreamClient(a, http)) }
        .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)

    val info = store.info

    val client: XtreamClient? get() = (state.value as? AccountState.SignedIn)?.client

    fun clientFor(account: XtreamAccount) = XtreamClient(account, http)

    /** Logs in and, when it works, stores the credentials encrypted. Throws [XtreamException]. */
    suspend fun signIn(account: XtreamAccount): AccountInfo {
        val info = XtreamClient(account, http).login()
        store.save(account, info)
        return info
    }

    /** Checks the account again (status, expiry). Network errors are ignored; the cache stays. */
    suspend fun refreshStatus(): AccountInfo? {
        val c = client ?: return null
        return try {
            c.login().also { store.saveInfo(it) }
        } catch (e: XtreamException.Inactive) {
            store.saveStatus(e.status); null
        } catch (e: XtreamException.AuthFailed) {
            store.saveStatus("Disabled"); null
        } catch (_: XtreamException) {
            null
        }
    }

    suspend fun signOut() = store.clear()
}

/** Whether the device has a working network, for the offline banner. */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = true }
                override fun onLost(network: Network) { _online.value = currentlyOnline() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        }
    }

    private fun currentlyOnline(): Boolean = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)
}
