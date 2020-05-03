package com.novasa.sectioningadapterexample.app

import com.novasa.sectioningadapterexample.injection.DaggerTestComponent
import com.novasa.sectioningadapterexample.injection.TestComponent
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication

class TestApplication : DaggerApplication() {

    lateinit var component: TestComponent

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        component =  DaggerTestComponent.builder()
            .application(this)
            .build()

        return component
    }
}
