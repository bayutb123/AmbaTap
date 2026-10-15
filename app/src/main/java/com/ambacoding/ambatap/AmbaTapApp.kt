package com.ambacoding.ambatap

import android.app.Application
import com.ambacoding.ambatap.service.notification.PlaybackNotifier
import com.ambacoding.ambatap.service.trigger.MacroScheduler
import com.ambacoding.ambatap.service.trigger.TriggerNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AmbaTapApp : Application() {

    @Inject lateinit var playbackNotifier: PlaybackNotifier
    @Inject lateinit var triggerNotifier: TriggerNotifier
    @Inject lateinit var scheduler: MacroScheduler

    override fun onCreate() {
        super.onCreate()
        playbackNotifier.createChannel()
        triggerNotifier.createChannel()
        scheduler.start()
    }
}
