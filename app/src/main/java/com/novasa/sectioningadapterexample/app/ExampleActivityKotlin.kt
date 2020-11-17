package com.novasa.sectioningadapterexample.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.novasa.sectioningadapter.SectioningAdapter
import com.novasa.sectioningadapterexample.R
import com.novasa.sectioningadapterexample.data.DataSource
import com.novasa.sectioningadapterexample.data.Item
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cell_header.view.*
import kotlinx.android.synthetic.main.cell_item.view.*
import kotlinx.android.synthetic.main.cell_no_content.view.*
import java.lang.RuntimeException
import java.util.ArrayList
import javax.inject.Inject
import kotlin.random.Random

@SuppressLint("SetTextI18n")
class ExampleActivityKotlin : AppCompatActivity() {

    companion object {
        private const val TAG = "SectioningAdapter"


        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_SECTION_HEADER = 2
        private const val VIEW_TYPE_SECTION_FOOTER = 3
        private const val VIEW_TYPE_SECTION_NO_CONTENT = 4
        private const val VIEW_TYPE_GLOBAL_HEADER_1 = 5
        private const val VIEW_TYPE_GLOBAL_HEADER_2 = 6
        private const val VIEW_TYPE_GLOBAL_FOOTER = 7
        private const val VIEW_TYPE_GLOBAL_NO_CONTENT = 8

        private const val UPDATE_INCREMENT = "update_increment"
    }

    private val items = ArrayList<Item>()

    private val disposables = CompositeDisposable()

    @Inject
    lateinit var dataSource: DataSource

    private val sectioningAdapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        setContentView(R.layout.activity_main)

        val context = this

        with(recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = sectioningAdapter
        }

        with(sectioningAdapter) {
            insertGlobalHeader(1, VIEW_TYPE_GLOBAL_HEADER_2)
            insertGlobalHeader(0, VIEW_TYPE_GLOBAL_HEADER_1)
            insertGlobalFooter(0, VIEW_TYPE_GLOBAL_FOOTER)
            insertGlobalFooter(1, VIEW_TYPE_GLOBAL_FOOTER)

            addStaticSections(listOf(1, 2, 3, 4, 5))

            insertGlobalHeader(0, VIEW_TYPE_GLOBAL_HEADER_1)
            insertGlobalFooter(0, VIEW_TYPE_GLOBAL_FOOTER)
            insertGlobalFooter(0, VIEW_TYPE_GLOBAL_FOOTER)
        }

        disposables += dataSource.data()
            .subscribe { data ->
                for (s in data.static) {
                    sectioningAdapter.state()
                }
            }

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

        val items = ArrayList(sectioningAdapter.getAllItems())
        items.forEach {
            it.section = Random.Default.nextInt(5) + 1
        }

        sectioningAdapter.forceRebindItemsNext()
        sectioningAdapter.setItems(items)
    }

    private fun setItems() {
        val items = arrayListOf(
            Item(101, 2),
            Item(102, 2),
            Item(103, 2),
            Item(104, 1)
        )

        sectioningAdapter.setItemsInSection(3, items)
    }

    private fun addItems() {
//        sectioningAdapter.addItems(items2)
        sectioningAdapter.getAllItems().first().section++
        sectioningAdapter.refresh()
    }

    private fun removeItems() {
        sectioningAdapter.removeAllItems()
    }

    class Adapter : SectioningAdapter<Item, Int>() {

        init {
            collapseNewSections = false
        }

        override fun onContentChanged() {
            Log.d(TAG, "Update time: $mostRecentUpdateTime ms")
        }

        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return super.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return super.areContentsTheSame(oldItem, newItem)
        }

        override fun onCreateViewHolder(
            context: Context,
            parent: ViewGroup,
            viewType: Int
        ): BaseViewHolder {
            val inflater = LayoutInflater.from(context)

            return when (viewType) {
                VIEW_TYPE_GLOBAL_HEADER_1 -> GlobalHeaderViewHolder(
                    inflater.inflate(
                        R.layout.cell_header,
                        parent,
                        false
                    )
                )
                VIEW_TYPE_GLOBAL_HEADER_2 -> GlobalHeaderViewHolder(
                    inflater.inflate(
                        R.layout.cell_header_2,
                        parent,
                        false
                    )
                )
                VIEW_TYPE_GLOBAL_FOOTER -> GlobalFooterViewHolder(
                    inflater.inflate(
                        R.layout.cell_footer,
                        parent,
                        false
                    )
                )
                VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(
                    inflater.inflate(
                        R.layout.cell_header,
                        parent,
                        false
                    )
                )
                VIEW_TYPE_SECTION_FOOTER -> SectionFooterViewHolder(
                    inflater.inflate(
                        R.layout.cell_footer,
                        parent,
                        false
                    )
                )
                VIEW_TYPE_SECTION_NO_CONTENT -> SectionNoContentViewHolder(
                    inflater.inflate(
                        R.layout.cell_no_content, parent, false
                    )
                )
                VIEW_TYPE_ITEM -> ItemViewHolder(
                    inflater.inflate(
                        R.layout.cell_item,
                        parent,
                        false
                    )
                )
                VIEW_TYPE_GLOBAL_NO_CONTENT -> BaseViewHolder(
                    inflater.inflate(
                        R.layout.cell_no_content,
                        parent,
                        false
                    )
                )
                else -> throw IllegalArgumentException()
            }
        }

        override fun showGlobalNoContent(): Boolean = true

        override fun getGlobalNoContentViewType(): Int = VIEW_TYPE_GLOBAL_NO_CONTENT

        override fun getSectionKeyForItem(item: Item): Int? = item.section

        override fun getHeaderCountForSection(sectionKey: Int): Int = 1

        override fun getFooterCountForSection(sectionKey: Int): Int = 0

        override fun getItemViewTypeForSection(sectionKey: Int): Int = VIEW_TYPE_ITEM

        override fun getHeaderViewTypeForSection(sectionKey: Int, headerIndex: Int): Int =
            VIEW_TYPE_SECTION_HEADER

        override fun getFooterViewTypeForSection(sectionKey: Int, footerIndex: Int): Int =
            VIEW_TYPE_SECTION_FOOTER

        override fun showNoContentForStaticSection(sectionKey: Int): Boolean = true

        override fun getNoContentViewTypeForStaticSection(sectionKey: Int): Int =
            VIEW_TYPE_SECTION_NO_CONTENT

        override fun sortSections(): Boolean = true

        override fun sortItemsInSection(sectionKey: Int): Boolean = true

        override fun compareSectionKeys(key1: Int, key2: Int): Int = key1.compareTo(key2)

        override fun compareItems(sectionKey: Int, item1: Item, item2: Item): Int =
            item1.id.compareTo(item2.id)

        inner class GlobalHeaderViewHolder(view: View) : BaseViewHolder(view) {
            init {
                itemView.setOnClickListener {
                    notifyGlobalHeaderChanged(0, "Hello")
                    notifyGlobalFooterChanged(0, "BORK")
                    toggleExpandAllSections()
                }
            }

            override fun bind(adapterPosition: Int) {
                itemView.itemHeader.text = "Global Header"
            }

            override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
                if (payloads.isNotEmpty()) {
                    itemView.itemHeader.text = payloads.first().toString()
                }
            }
        }

        inner class GlobalFooterViewHolder(view: View) : BaseViewHolder(view) {
            init {
                with(itemView) {
                    itemHeader.text = "Global Footer"
                    setOnClickListener {
                        collapseAllSections()
                        notifyGlobalFooterChanged(0, "BORK")
                    }
                }
            }

            override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
                if (payloads.isNotEmpty()) {
                    itemView.itemHeader.text = payloads.first().toString()
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
//                            notifySectionHeaderChanged(it, 0)
                        }
                    }
                }
            }

            override fun bind(adapterPosition: Int, sectionKey: Int) {
                with(itemView) {
                    itemHeader.text = "Section $sectionKey"
                }
            }

            override fun partialBind(
                adapterPosition: Int,
                sectionKey: Int,
                payloads: MutableList<Any>
            ) {
                if (payloads.isNotEmpty()) {
                    itemView.itemHeader.text = payloads.first().toString()
                }
            }
        }

        inner class SectionFooterViewHolder(view: View) : SectionViewHolder(view) {

            override fun bind(adapterPosition: Int, sectionKey: Int) {
                with(itemView) {
                    itemHeader.text = "Total item count: ${getItemCountForSection(sectionKey)}"
                }
            }
        }

        inner class SectionNoContentViewHolder(view: View) : SectionViewHolder(view) {

            init {
                with(itemView) {
                    setOnClickListener {
                        getSectionKey()?.let {
                            notifyStaticSectionNoContentChanged(it, " blugr")
                        }
                    }
                }
            }

            override fun bind(adapterPosition: Int, sectionKey: Int) {
                with(itemView) {
                    noContent.text = "Section $sectionKey is empty"
                }
            }

            override fun partialBind(
                adapterPosition: Int,
                sectionKey: Int,
                payloads: MutableList<Any>
            ) {
                with(itemView) {
                    if (payloads.isNotEmpty()) {
                        noContent.text = noContent.text.toString() + payloads.first().toString()
                    }
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

            override fun bind(adapterPosition: Int, sectionKey: Int, item: Item) {
                with(itemView) {
                    itemTitle.text = "[${item.section}] Item ${item.id}"
                }
            }

            override fun partialBind(
                adapterPosition: Int,
                sectionKey: Int,
                item: Item,
                payloads: MutableList<Any>
            ) {
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
