package com.lck07.lumen.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The parental lock for this session. Locked categories stay hidden until the PIN is entered;
 * the unlock lasts until the app goes to the background.
 */
class ParentalController(private val settings: SettingsStore) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** Returns true when the PIN is right; the locked categories then show. */
    suspend fun unlock(pin: String): Boolean = settings.checkPin(pin).also { if (it) _unlocked.value = true }

    fun lock() { _unlocked.value = false }

    suspend fun isLocked(type: String, categoryId: String?): Boolean {
        if (_unlocked.value || categoryId == null) return false
        val s = settings.current()
        return s.parentalLockOn && SettingsStore.categoryKey(type, categoryId) in s.lockedCategories
    }
}
