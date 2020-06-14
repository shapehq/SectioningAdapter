package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.data.DataSource
import dagger.Module
import dagger.Provides
import io.mockk.mockk

@Module
class MockModule {

    @Provides
    fun provideDataSource(): DataSource = mockk()
}