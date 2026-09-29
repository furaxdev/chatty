package com.furaxdev.chatty

import android.app.Application
import com.furaxdev.chatty.sms.Notifications

class ChattyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
