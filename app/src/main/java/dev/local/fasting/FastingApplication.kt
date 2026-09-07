package dev.local.fasting

import android.app.Application
import android.content.Context
import dev.local.fasting.data.FastingRepository
import dev.local.fasting.data.SettingsStore
import dev.local.fasting.data.db.FastingDatabase
import dev.local.fasting.tracking.FastTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. The app is single-user and fully local, so a handful of eagerly
 * created singletons beats pulling in a DI framework.
 */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: FastingDatabase = FastingDatabase.get(context)
    val repository = FastingRepository(database)
    val settings = SettingsStore(context)
    val tracker = FastTracker(context.applicationContext, repository, settings)
}

class FastingApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Container access for activities, receivers and widget callbacks. */
val Context.appContainer: AppContainer
    get() = (applicationContext as FastingApplication).container
