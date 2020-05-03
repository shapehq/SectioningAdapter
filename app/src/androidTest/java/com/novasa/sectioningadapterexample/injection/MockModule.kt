package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.data.DataSource
import dagger.Module
import dagger.Provides
import org.mockito.Mockito

@Module
class MockModule {

    @Provides
    fun provideDataSource(): DataSource = Mockito.mock(DataSource::class.java)
}