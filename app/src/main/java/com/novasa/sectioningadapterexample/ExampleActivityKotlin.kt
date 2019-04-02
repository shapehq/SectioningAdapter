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
import kotlinx.android.synthetic.main.cell_no_content.view.*
import java.util.ArrayList
import kotlin.Comparator
import kotlin.random.Random

class ExampleActivityKotlin : AppCompatActivity() {

    companion object {
        private const val TAG = "SectioningAdapter"

        private const val ITEM_COUNT = 10
        private const val SECTION_COUNT = 5

        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_SECTION_HEADER = 2
        private const val VIEW_TYPE_SECTION_FOOTER = 3
        private const val VIEW_TYPE_SECTION_NO_CONTENT = 4
        private const val VIEW_TYPE_GLOBAL_HEADER = 5
        private const val VIEW_TYPE_GLOBAL_FOOTER = 6
        private const val VIEW_TYPE_GLOBAL_NO_CONTENT = 7

        private const val UPDATE_INCREMENT = "update_increment"
    }

    private val items = ArrayList<Item>().also {
        for (i in 1..ITEM_COUNT) {
            it.add(Item(i, Random.nextInt(SECTION_COUNT) + 1))
        }
    }

    private val items2 = ArrayList<Item>().also {
        for (i in ITEM_COUNT + 1..ITEM_COUNT + 5) {
            it.add(Item(i, Random.nextInt(SECTION_COUNT) + 1))
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

        sectioningAdapter.insertGlobalHeader(0, VIEW_TYPE_GLOBAL_HEADER)
        sectioningAdapter.insertGlobalFooter(0, VIEW_TYPE_GLOBAL_FOOTER)

        sectioningAdapter.addStaticSections(listOf(1, 2, 3, 4, 5))
        sectioningAdapter.setItems(items)

        buttonShuffle.setOnClickListener {
            shuffle()
        }

        buttonAdd.setOnClickListener {
            addItems()
        }

        buttonRemove.setOnClickListener {
            sectioningAdapter.removeItems(items.subList(0, ITEM_COUNT / 2))
        }
    }

    private fun addItems() {
        sectioningAdapter.addItems(items2)
    }

    private fun shuffle() {
        items.forEach {
            it.section = Random.nextInt(SECTION_COUNT) + 1
        }

        sectioningAdapter.setItems(items)

        val allItems = sectioningAdapter.getAllItems()

        assert(allItems.size == items.size)

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
        sorted.forEachIndexed { index, item ->
            if (sec < item.section) {
                sec = item.section
                sb.append("\nSECTION $sec")
            }
            sb.append("\n- Item $item, from adapter: ${allItems[index]}")

            assert(item.id == allItems[index].id)
        }
        
        Log.d(TAG, sb.toString())
    }

    data class Item(var id: Int, var section: Int)

    class Adapter : SectioningAdapter<Item, Int>() {

        init {
            collapseNewSections = false
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)

            return when (viewType) {
                VIEW_TYPE_GLOBAL_HEADER -> GlobalHeaderViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_GLOBAL_FOOTER -> GlobalFooterViewHolder(inflater.inflate(R.layout.cell_footer, parent, false))
                VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_SECTION_FOOTER -> SectionFooterViewHolder(inflater.inflate(R.layout.cell_footer, parent, false))
                VIEW_TYPE_SECTION_NO_CONTENT -> SectionNoContentViewHolder(inflater.inflate(R.layout.cell_no_content, parent, false))
                VIEW_TYPE_ITEM -> ItemViewHolder(inflater.inflate(R.layout.cell_item, parent, false))
                VIEW_TYPE_GLOBAL_NO_CONTENT -> ViewHolder(inflater.inflate(R.layout.cell_no_content, parent, false))
                else -> throw IllegalArgumentException()
            }
        }

        override fun onContentChanged() {
            super.onContentChanged()
        }

        override fun showGlobalNoContent(): Boolean = true

        override fun getGlobalNoContentViewType(): Int = VIEW_TYPE_GLOBAL_NO_CONTENT

        override fun getSectionKeyForItem(item: Item): Int = item.section

        override fun getHeaderCountForSectionKey(sectionKey: Int): Int = 1

        override fun getItemViewTypeForSection(sectionKey: Int, adapterPosition: Int): Int = VIEW_TYPE_ITEM

        override fun getHeaderViewTypeForSection(sectionKey: Int, headerIndex: Int): Int = VIEW_TYPE_SECTION_HEADER

        override fun showNoContentForStaticSection(sectionKey: Int): Boolean = true

        override fun getNoContentViewTypeForStaticSection(sectionKey: Int): Int = VIEW_TYPE_SECTION_NO_CONTENT

        override fun sortSections(): Boolean = true

        override fun sortItemsInSection(sectionKey: Int): Boolean = true

        override fun compareSectionKeys(key1: Int, key2: Int): Int = key1.compareTo(key2)

        override fun compareItems(sectionKey: Int, item1: Item, item2: Item): Int = item1.id.compareTo(item2.id)

        inner class GlobalHeaderViewHolder(view: View) : SectioningAdapter.ViewHolder(view) {
            init {
                with(itemView) {
                    itemHeader.text = "Global Header"
                    setOnClickListener {
                        toggleExpandAllSections()
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

        inner class SectionHeaderViewHolder(view: View) : SectioningAdapter<Item, Int>.SectionViewHolder(view) {

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
                }
            }
        }

        inner class SectionFooterViewHolder(view: View) : SectioningAdapter<Item, Int>.SectionViewHolder(view) {

            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: Int) {
                with(itemView) {
                    itemHeader.text = "Total item count: ${getItemCountForSection(sectionKey)}"
                }
            }
        }

        inner class SectionNoContentViewHolder(view: View) : SectioningAdapter<Item, Int>.SectionViewHolder(view) {

            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: Int) {
                with (itemView) {
                    noContent.text = "Section $sectionKey is empty"
                }
            }
        }

        inner class ItemViewHolder(view: View) : SectioningAdapter<Item, Int>.ItemViewHolder(view) {

            init {
                itemView.setOnClickListener {
                    getItem()?.let {
                        it.id++
                        notifyItemChanged(it, UPDATE_INCREMENT)
                    }
                }
            }

            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: Int, item: Item) {
                with(itemView) {
                    itemTitle.text = "Item ${item.id}"
                }
            }

            override fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: Int, item: Item, payloads: MutableList<Any>) {
                with(itemView) {
                    payloads.forEach {
                        when (it) {
                            UPDATE_INCREMENT -> {
                                itemTitle.text = "Item ${item.id}"
                            }
                        }
                    }
                }
            }
        }
    }
}
