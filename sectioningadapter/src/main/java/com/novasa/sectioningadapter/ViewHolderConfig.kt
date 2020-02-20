package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

interface ViewHolderConfig {
    fun onAdapterAttachedToRecyclerView(adapter: SectioningAdapter<*, *>, recyclerView: RecyclerView)
    fun onAdapterDetachedFromRecyclerView(adapter: SectioningAdapter<*, *>, recyclerView: RecyclerView)

    fun onCreateViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context, parent: ViewGroup, viewType: Int)
    fun onAttachViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context)
    fun onDetachViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context)
    fun onBindViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int)
    fun onBindViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int, payloads: MutableList<Any>)
}