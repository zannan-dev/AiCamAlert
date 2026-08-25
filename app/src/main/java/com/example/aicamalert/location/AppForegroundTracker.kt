package com.example.aicamalert.location

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the app has a visible activity in the foreground.
 *
 * Used to suppress duplicate background alerts and switch GPS profiles.
 */
class AppForegroundTracker {

    private val _isInForeground = MutableStateFlow(false)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    fun start(application: Application, onForegroundChanged: (Boolean) -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                _isInForeground.value = true
                onForegroundChanged(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                _isInForeground.value = false
                onForegroundChanged(false)
            }
        })
    }
}
