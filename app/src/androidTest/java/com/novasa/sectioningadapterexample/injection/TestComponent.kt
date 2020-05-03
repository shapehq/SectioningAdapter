package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.app.TestApplication
import com.novasa.sectioningadapterexample.test.UITests
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        ActivityInjectorModule::class,
        MockModule::class
    ]
)
interface TestComponent : AndroidInjector<TestApplication> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: TestApplication): Builder

        fun build(): TestComponent
    }

    fun inject(uiTests: UITests)
}