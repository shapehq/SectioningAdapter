package com.novasa.sectioningadapterexample.injection

import com.novasa.sectioningadapterexample.data.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.reactivex.Observable
import kotlin.random.Random

@Module
abstract class ExampleModule {

    @Provides
    fun provideItemDataSource(): DataSource<Item> = object : DataSource<Item> {
        private val rng = Random(0)

        override fun data(): Observable<Data<Item>> = Observable.just(Data(ArrayList<Item>().also {
            for (i in 1..20) {
                it.add(Item(i, rng.nextInt(5) + 1))
            }
        }))
    }

    @Provides
    fun provideTypedItemDataSource(): DataSource<TypedItem> = object : DataSource<TypedItem> {
        private val rng = Random(0)

        override fun data(): Observable<Data<TypedItem>> = Observable.just(Data(ArrayList<TypedItem>().also {
            for (i in 1..10) {
                it.add(TypedItem(i, "Type 1", rng.nextInt(5) + 1))
            }
            for (i in 11..20) {
                it.add(TypedItem(i, "Type 2", rng.nextInt(5) + 1))
            }
        }))
    }
}