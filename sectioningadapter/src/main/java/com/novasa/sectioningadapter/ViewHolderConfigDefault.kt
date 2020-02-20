package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup

open abstract class ViewHolderConfigDefault : ViewHolderConfig {
    override fun onCreateViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, parent: ViewGroup, viewType: Int) {}
    override fun onAttachViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context) {}
    override fun onDetachViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context) {}
    override fun onBindViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int) {}
    override fun onBindViewHolder(holder: SectioningAdapter.BaseViewHolder, context: Context, position: Int, payloads: MutableList<Any>) {}
}