package com.novasa.sectioningadapterexample.data

import io.reactivex.Observable

interface DataSource {
    fun data(): Observable<List<Item>>
}