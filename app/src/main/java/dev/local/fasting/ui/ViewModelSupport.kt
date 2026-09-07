package dev.local.fasting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.local.fasting.AppContainer
import dev.local.fasting.FastingApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Builds a [ViewModelProvider.Factory] that hands the ViewModel the app's dependency container. */
inline fun <reified VM : ViewModel> containerViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = this[APPLICATION_KEY] as FastingApplication
        create(application.container)
    }
}

/**
 * Emits the current instant on an interval. Collected through the ViewModel's scope, so it stops
 * with the screen rather than ticking in the background — the notification's chronometer is what
 * keeps time when the app is not visible.
 */
fun tickerFlow(intervalSeconds: Int = 1): Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        kotlinx.coroutines.delay(intervalSeconds.seconds)
    }
}
