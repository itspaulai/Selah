package com.example

import android.app.Application
import com.example.data.BibleCorpus

class SelahApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BibleCorpus.ensureLoaded(this)
    }
}
