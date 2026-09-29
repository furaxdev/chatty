package com.chatty.fr

import android.app.Application
import com.chatty.fr.sms.Notifications

class ChattyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
