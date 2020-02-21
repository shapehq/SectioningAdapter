package com.novasa.sectioningadapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

interface ViewHolderConfig {
    fun onAdapterAttachedToRecyclerView(adapter: RecyclerView.Adapter<*>, recyclerView: RecyclerView)
    fun onAdapterDetachedFromRecyclerView(adapter: RecyclerView.Adapter<*>, recyclerView: RecyclerView)

    fun onCreateViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context, parent: ViewGroup, viewType: Int)
    fun onAttachViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context)
    fun onDetachViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context)
    fun onBindViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context, position: Int)
    fun onBindViewHolder(adapter: RecyclerView.Adapter<*>, holder: RecyclerView.ViewHolder, context: Context, position: Int, payloads: MutableList<Any>)
}