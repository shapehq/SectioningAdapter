package com.novasa.sectioningadapterexample.data

import io.reactivex.Observable
import javax.inject.Inject
import kotlin.random.Random

class ExampleDataSource @Inject constructor() : DataSource {

    companion object {
        private const val ITEM_COUNT = 20
        private const val SECTION_COUNT = 5
    }

    private val rng = Random(0)

    override fun data(): Observable<Data> = Observable.just(Data(ArrayList<Item>().also {
        for (i in 1..ITEM_COUNT) {
            it.add(Item(i, rng.nextInt(SECTION_COUNT) + 1))
        }
    }))
}