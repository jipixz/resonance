package com.jipix.resonance

import android.app.Application
import com.jipix.resonance.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ResonanceApp : Application() {

    /** Outlives any single screen, which is what the player connection needs. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var container: AppContainer
        private set

    /**
     * Process-scoped, not activity-scoped. Returning to a still-resident app
     * should not replay the launch animation; only a genuinely cold start does,
     * and a cold start is by definition a fresh process.
     */
    var splashPlayed: Boolean = false

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
    }
}
