package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.app.ExampleActivity2Kotlin
import com.novasa.sectioningadapterexample.app.ExampleActivityKotlin
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivityInjectorModule {

    @ContributesAndroidInjector
    abstract fun contributeExampleActivityKotlinInjector(): ExampleActivityKotlin

    @ContributesAndroidInjector
    abstract fun contributeExampleActivity2KotlinInjector(): ExampleActivity2Kotlin
}