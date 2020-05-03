package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.data.DataSource
import com.novasa.sectioningadapterexample.data.ExampleDataSource
import dagger.Binds
import dagger.Module

@Module
abstract class ExampleModule {

    @Binds
    abstract fun bindDataSource(instance: ExampleDataSource): DataSource
}