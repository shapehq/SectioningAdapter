package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class ViewHolderConfigDefault : ViewHolderConfig {
    override fun onAdapterAttachedToRecyclerView(adapter: RecyclerView.Adapter<*>, recyclerView: RecyclerView) {}
    override fun onAdapterDetachedFromRecyclerView(adapter: RecyclerView.Adapter<*>, recyclerView: RecyclerView) {}
    override fun onCreateViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context, parent: ViewGroup, viewType: Int) {}
    override fun onAttachViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context) {}
    override fun onDetachViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context) {}
    override fun onBindViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context, position: Int) {}
    override fun onBindViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context, position: Int, payloads: MutableList<Any>) {}
}