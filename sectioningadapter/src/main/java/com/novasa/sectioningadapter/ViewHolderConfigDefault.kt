package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class ViewHolderConfigDefault : ViewHolderConfig {
    override fun onAdapterAttachedToRecyclerView(adapter: SectioningAdapter<*, *>, recyclerView: RecyclerView) {}
    override fun onAdapterDetachedFromRecyclerView(adapter: SectioningAdapter<*, *>, recyclerView: RecyclerView) {}
    override fun onCreateViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context, parent: ViewGroup, viewType: Int) {}
    override fun onAttachViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context) {}
    override fun onDetachViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context) {}
    override fun onBindViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int) {}
    override fun onBindViewHolder(adapter: SectioningAdapter<*, *>, holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int, payloads: MutableList<Any>) {}
}