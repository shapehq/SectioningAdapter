package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup

interface ViewHolderConfig {
    fun onCreateViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, parent: ViewGroup, viewType: Int)
    fun onAttachViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context)
    fun onDetachViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context)
    fun onBindViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int)
    fun onBindViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int, payloads: MutableList<Any>)
}