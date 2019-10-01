package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup

interface ViewHolderConfig {
    fun onCreateViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, parent: ViewGroup, viewType: Int)
}