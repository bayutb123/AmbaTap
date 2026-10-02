package com.ambacoding.ambatap

import android.app.Application
import com.ambacoding.ambatap.service.notification.PlaybackNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AmbaTapApp : Application() {

    @Inject lateinit var playbackNotifier: PlaybackNotifier

    override fun onCreate() {
        super.onCreate()
        playbackNotifier.createChannel()
    }
}
