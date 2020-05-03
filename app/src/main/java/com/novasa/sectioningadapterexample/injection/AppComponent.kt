package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.app.ExampleApplication
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
        ExampleModule::class
    ]
)
interface AppComponent : AndroidInjector<ExampleApplication> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: ExampleApplication): Builder

        fun build(): AppComponent
    }
}