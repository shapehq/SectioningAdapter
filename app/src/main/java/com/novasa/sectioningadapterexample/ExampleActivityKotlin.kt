package com.novasa.sectioningadapterexample

import android.annotation.SuppressLint
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

@SuppressLint("SetTextI18n")
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

    private val rng = Random(0)

    private val items = ArrayList<Item>().also {
        for (i in 1..ITEM_COUNT) {
            it.add(Item(i, rng.nextInt(SECTION_COUNT) + 1))
        }
    }

    private val items2 = ArrayList<Item>().also {
        for (i in ITEM_COUNT + 1..ITEM_COUNT + 5) {
            it.add(Item(i, rng.nextInt(SECTION_COUNT) + 1))
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

        buttonSet.setOnClickListener {
            setItems()
        }

        buttonAdd.setOnClickListener {
            addItems()
        }

        buttonRemove.setOnClickListener {
            removeItems()
        }
    }

    private fun shuffle() {
        items.forEach {
            it.section = rng.nextInt(SECTION_COUNT) + 1
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

    private fun setItems() {
        val items = arrayListOf(
            Item(101, 2),
            Item(102, 2),
            Item(103, 2),
            Item(104, 1)
        )

        sectioningAdapter.setItemsInSection(2, items)
    }

    private fun addItems() {
        sectioningAdapter.addItems(items2)
    }

    private fun removeItems() {
        sectioningAdapter.removeItems(items.subList(0, ITEM_COUNT / 2))
    }

    data class Item(var id: Int, var section: Int)

    class Adapter : SectioningAdapter<Item, Int>() {

        init {
            collapseNewSections = false
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val inflater = LayoutInflater.from(parent.context)

            return when (viewType) {
                VIEW_TYPE_GLOBAL_HEADER -> GlobalHeaderViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_GLOBAL_FOOTER -> GlobalFooterViewHolder(inflater.inflate(R.layout.cell_footer, parent, false))
                VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(inflater.inflate(R.layout.cell_header, parent, false))
                VIEW_TYPE_SECTION_FOOTER -> SectionFooterViewHolder(inflater.inflate(R.layout.cell_footer, parent, false))
                VIEW_TYPE_SECTION_NO_CONTENT -> SectionNoContentViewHolder(inflater.inflate(R.layout.cell_no_content, parent, false))
                VIEW_TYPE_ITEM -> ItemViewHolder(inflater.inflate(R.layout.cell_item, parent, false))
                VIEW_TYPE_GLOBAL_NO_CONTENT -> BaseViewHolder(inflater.inflate(R.layout.cell_no_content, parent, false))
                else -> throw IllegalArgumentException()
            }
        }

        override fun showGlobalNoContent(): Boolean = true

        override fun getGlobalNoContentViewType(): Int = VIEW_TYPE_GLOBAL_NO_CONTENT

        override fun getSectionKeyForItem(item: Item): Int = item.section

        override fun getHeaderCountForSectionKey(sectionKey: Int): Int = 1

        override fun getFooterCountForSectionKey(sectionKey: Int): Int = 1

        override fun getItemViewTypeForSection(sectionKey: Int, adapterPosition: Int): Int = VIEW_TYPE_ITEM

        override fun getHeaderViewTypeForSection(sectionKey: Int, headerIndex: Int): Int = VIEW_TYPE_SECTION_HEADER

        override fun getFooterViewTypeForSection(sectionKey: Int, footerIndex: Int): Int = VIEW_TYPE_SECTION_FOOTER

        override fun showNoContentForStaticSection(sectionKey: Int): Boolean = true

        override fun getNoContentViewTypeForStaticSection(sectionKey: Int): Int = VIEW_TYPE_SECTION_NO_CONTENT

        override fun sortSections(): Boolean = true

        override fun sortItemsInSection(sectionKey: Int): Boolean = true

        override fun compareSectionKeys(key1: Int, key2: Int): Int = key1.compareTo(key2)

        override fun compareItems(sectionKey: Int, item1: Item, item2: Item): Int = item1.id.compareTo(item2.id)

        inner class GlobalHeaderViewHolder(view: View) : BaseViewHolder(view) {
            init {
                with(itemView) {
                    itemHeader.text = "Global Header"
                    setOnClickListener {
                        toggleExpandAllSections()
                    }
                }
            }
        }

        inner class GlobalFooterViewHolder(view: View) : BaseViewHolder(view) {
            init {
                with(itemView) {
                    itemHeader.text = "Global Footer"
                    setOnClickListener {
                        collapseAllSections()
                    }
                }
            }
        }

        inner class SectionHeaderViewHolder(view: View) : SectionViewHolder(view) {

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

        inner class SectionFooterViewHolder(view: View) : SectionViewHolder(view) {

            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: Int) {
                with(itemView) {
                    itemHeader.text = "Total item count: ${getItemCountForSection(sectionKey)}"
                }
            }
        }

        inner class SectionNoContentViewHolder(view: View) : SectionViewHolder(view) {

            override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: Int) {
                with(itemView) {
                    noContent.text = "Section $sectionKey is empty"
                }
            }
        }

        inner class ItemViewHolder(view: View) : SectionItemViewHolder(view) {

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
