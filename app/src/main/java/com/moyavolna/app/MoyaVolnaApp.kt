package com.moyavolna.app

import android.app.Application
import com.moyavolna.app.util.CrashHandler

class MoyaVolnaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Перехватываем все необработанные исключения, чтобы при следующем
        // запуске показать диалог и предложить скопировать лог в буфер.
        CrashHandler.install(this)
    }
}
