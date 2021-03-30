package com.novasa.sectioningadapterexample.data

import io.reactivex.Observable

interface DataSource<TItem> {
    fun data(): Observable<Data<TItem>>
}
