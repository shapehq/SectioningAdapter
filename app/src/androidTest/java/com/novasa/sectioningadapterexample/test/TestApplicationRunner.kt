package com.novasa.sectioningadapterexample.test

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.novasa.sectioningadapterexample.app.TestApplication

class TestApplicationRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return Instrumentation.newApplication(TestApplication::class.java, context)
    }
}