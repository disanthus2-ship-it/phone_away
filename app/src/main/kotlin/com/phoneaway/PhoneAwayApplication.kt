package com.phoneaway

import android.app.Application
import com.phoneaway.data.SessionRepository
import com.phoneaway.session.SessionController

/**
 * Manual dependency container. The graph is two objects deep, which is well
 * under the point where a DI framework would earn its build-time cost.
 */
class PhoneAwayApplication : Application() {

    val sessionController: SessionController by lazy { SessionController() }

    val sessionRepository: SessionRepository by lazy { SessionRepository(this) }
}
