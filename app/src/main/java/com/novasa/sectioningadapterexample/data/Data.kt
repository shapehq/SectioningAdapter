package com.novasa.sectioningadapterexample.data

import java.util.*

data class Data<TItem>(val items: List<TItem>, val static: List<Int> = Collections.emptyList())
