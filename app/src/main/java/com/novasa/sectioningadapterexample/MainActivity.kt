package com.novasa.sectioningadapterexample

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.novasa.sectioningadapter.SectioningAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cell_header.view.*
import kotlinx.android.synthetic.main.cell_item.view.*
import java.util.ArrayList
import kotlin.Comparator
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SectioningAdapter"

        private const val COUNT = 10

        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_HEADER = 2
        private const val VIEW_TYPE_GLOBAL_HEADER = 3
        private const val VIEW_TYPE_GLOBAL_FOOTER = 4
        private const val VIEW_TYPE_NO_CONTENT = 5
    }

    private val items = ArrayList<Item>().also {
        for (i in 1..COUNT) {
            it.add(Item(i, i % 3 + 1))
        }
    }

    private val items2 = ArrayList<Item>().also {
        for (i in COUNT..COUNT+5) {
            it.add(Item(i, 4))
        }
    }

    private val sectioningAdapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val context = this

        with(recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = sectioningAdapter
        }

        sectioningAdapter.setItems(items)

        sectioningAdapter.insertGlobalHeader(0, VIEW_TYPE_GLOBAL_HEADER)
        sectioningAdapter.insertGlobalFooter(0, VIEW_TYPE_GLOBAL_FOOTER)

        buttonShuffle.setOnClickListener {
            shuffle()
        }

        buttonAdd.setOnClickListener {
            sectioningAdapter.setItems(items + items2)
        }

        buttonRemove.setOnClickListener {
            sectioningAdapter.setItems(items)
        }
    }

    private fun shuffle() {
        items.forEach {
            it.section = Random.nextInt(1, 4)
        }

        sectioningAdapter.setItems(items)


        val sorted: List<Item> = items.sortedWith(Comparator { o1, o2 ->
            when {
                o1.section < o2.section -> -1
                o1.section > o2.section -> 1
                o1.id < o2.id -> -1
                o1.id > o2.id -> 1
                else -> 0
            }
        })

        val sb = StringBuilder()
        var sec = -1
        sorted.forEach { item ->
            if (sec < item.section) {
                sec = item.section
                sb.append("\nSECTION $sec")
            }
            sb.append("\n- Item ${item.id}")
        }

        Log.d(TAG, sb.toString())
    }

    data class Item(val id: Int, var section: Int)

    class Adapter : SectioningAdapter<Item, Int>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)

            return when (viewType) {
                VIEW_TYPE_GLOBAL_HEADER -> GlobalHeaderViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_GLOBAL_FOOTER -> GlobalFooterViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_ITEM -> ItemViewHolder(inflater.inflate(R.layout.cell_item, parent, false))
                VIEW_TYPE_NO_CONTENT -> ViewHolder(inflater.inflate(R.layout.cell_no_content, parent, false))
                else -> throw IllegalArgumentException()
            }
        }

        override fun showNoContent(): Boolean = true

        override fun getNoContentViewType(): Int = VIEW_TYPE_NO_CONTENT

        override fun getSectionKeyForItem(item: Item): Int = item.section

        override fun getHeaderCountForSectionKey(key: Int): Int = 1

        override fun getItemViewTypeForSection(key: Int, adapterPosition: Int): Int = VIEW_TYPE_ITEM

        override fun getHeaderViewTypeForSection(key: Int, headerIndex: Int): Int = VIEW_TYPE_HEADER

        override fun sortSections(): Boolean = true

        override fun sortItemsInSection(key: Int): Boolean = true

        override fun compareSectionKeys(key1: Int, key2: Int): Int = key1.compareTo(key2)

        override fun compareItems(item1: Item, item2: Item): Int = item1.id.compareTo(item2.id)

        inner class GlobalHeaderViewHolder(view: View) : SectioningAdapter.ViewHolder(view) {
            init {
                with(itemView) {
                    itemHeader.text = "Global Header"
                    setOnClickListener {
                        expandAllSections()
                    }
                }
            }
        }
        inner class GlobalFooterViewHolder(view: View) : SectioningAdapter.ViewHolder(view) {
            init {
                with(itemView) {
                    itemHeader.text = "Global Footer"
                    setOnClickListener {
                        collapseAllSections()
                    }
                }
            }
        }

        inner class HeaderViewHolder(view: View) : SectioningAdapter<Item, Int>.SectionItemViewHolder(view) {

            init {
                with(itemView) {
                    setOnClickListener {
                        requestFocus()
                        getSectionKey()?.let {
                            toggleExpandSection(it)
                        }
                    }
                }
            }

            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: Int) {
                with(itemView) {
                    itemHeader.text = "Section $sectionKey"
                    Log.d(TAG, "bind pos: $adapterPosition, $sectionPosition, key: $sectionKey")
                }
            }
        }

        inner class ItemViewHolder(view: View) : SectioningAdapter<Item, Int>.ItemViewHolder(view) {
            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: Int, item: Item) {
                with(itemView) {
                    itemTitle.text = "Item ${item.id}"
                }
            }
        }
    }
}
