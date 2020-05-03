package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.app.ExampleActivityKotlin
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivityInjectorModule {

    @ContributesAndroidInjector
    abstract fun contributeExampleActivityKotlinInjector(): ExampleActivityKotlin
}