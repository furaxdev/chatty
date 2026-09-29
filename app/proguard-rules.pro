# Chatty — règles R8 (les workers WorkManager sont instanciés par réflexion)
-keep class com.chatty.fr.sms.ScheduledSendWorker { *; }
-keep class com.chatty.fr.sms.ReminderWorker { *; }
