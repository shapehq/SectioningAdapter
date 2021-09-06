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
import com.novasa.sectioningadapterexample.databinding.*
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
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

    private val disposables = CompositeDisposable()

    @Inject
    lateinit var dataSource: DataSource<Item>

    private val sectioningAdapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        val binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val context = this

        with(binding.recyclerView) {
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
                sectioningAdapter.setItems(data.items)
            }

        binding.buttonShuffle.setOnClickListener {
            shuffle()
        }

        binding.buttonSet.setOnClickListener {
            setItems()
        }

        binding.buttonAdd.setOnClickListener {
            addItems()
        }

        binding.buttonRemove.setOnClickListener {
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
            Item(101, 3),
            Item(102, 3),
            Item(103, 3),
            Item(104, 3)
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

        override fun areItemsTheSame(oldItem: Item, newItem: Item, oldSectionKey: Int, newSectionKey: Int): Boolean {
            if (newItem.id == 102) {
                return false
            }
            return super.areItemsTheSame(oldItem, newItem, oldSectionKey, newSectionKey)
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item, oldSectionKey: Int, newSectionKey: Int): Boolean {
            return super.areContentsTheSame(oldItem, newItem, oldSectionKey, newSectionKey)
        }

        override fun onCreateViewHolder(
            context: Context,
            parent: ViewGroup,
            viewType: Int
        ): BaseViewHolder {
            val inflater = LayoutInflater.from(context)

            return when (viewType) {
                VIEW_TYPE_GLOBAL_HEADER_1 -> GlobalHeaderViewHolder(CellHeaderBinding.inflate(inflater))
                VIEW_TYPE_GLOBAL_HEADER_2 -> GlobalHeaderViewHolder(CellHeaderBinding.inflate(inflater))
                VIEW_TYPE_GLOBAL_FOOTER -> GlobalFooterViewHolder(CellHeader2Binding.inflate(inflater))
                VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(CellHeaderBinding.inflate(inflater))
                VIEW_TYPE_SECTION_FOOTER -> SectionFooterViewHolder(CellFooterBinding.inflate(inflater))
                VIEW_TYPE_SECTION_NO_CONTENT -> SectionNoContentViewHolder(CellNoContentBinding.inflate(inflater))
                VIEW_TYPE_ITEM -> ItemViewHolder(CellItemBinding.inflate(inflater))
                VIEW_TYPE_GLOBAL_NO_CONTENT -> BaseViewHolder(inflater.inflate(R.layout.cell_no_content, parent, false))
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

        inner class GlobalHeaderViewHolder(private val binding: CellHeaderBinding) : BaseViewHolder(binding.root) {
            init {


                itemView.setOnClickListener {
                    notifyGlobalHeaderChanged(0, "Hello")
                    notifyGlobalFooterChanged(0, "BORK")
                    toggleExpandAllSections()
                }
            }

            override fun bind(adapterPosition: Int) {
                binding.itemHeader.text = "Global Header"
            }

            override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
                if (payloads.isNotEmpty()) {
                    binding.itemHeader.text = payloads.first().toString()
                }
            }
        }

        inner class GlobalFooterViewHolder(private val binding: CellHeader2Binding) : BaseViewHolder(binding.root) {
            init {
                binding.itemHeader.text = "Global Footer"
                binding.root.setOnClickListener {
                    collapseAllSections()
                    notifyGlobalFooterChanged(0, "BORK")
                }
            }

            override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
                if (payloads.isNotEmpty()) {
                    binding.itemHeader.text = payloads.first().toString()
                }
            }
        }

        inner class SectionHeaderViewHolder(private val binding: CellHeaderBinding) : SectionViewHolder(binding.root) {

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
                binding.itemHeader.text = "Section $sectionKey"
            }

            override fun partialBind(
                adapterPosition: Int,
                sectionKey: Int,
                payloads: MutableList<Any>
            ) {
                if (payloads.isNotEmpty()) {
                    binding.itemHeader.text = payloads.first().toString()
                }
            }
        }

        inner class SectionFooterViewHolder(private val binding: CellFooterBinding) : SectionViewHolder(binding.root) {

            override fun bind(adapterPosition: Int, sectionKey: Int) {
                binding.itemHeader.text = "Total item count: ${getItemCountForSection(sectionKey)}"
            }
        }

        inner class SectionNoContentViewHolder(private val binding: CellNoContentBinding) : SectionViewHolder(binding.root) {

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
                binding.noContent.text = "Section $sectionKey is empty"
            }

            override fun partialBind(
                adapterPosition: Int,
                sectionKey: Int,
                payloads: MutableList<Any>
            ) {
                if (payloads.isNotEmpty()) {
                    binding.noContent.text = binding.noContent.text.toString() + payloads.first().toString()
                }
            }
        }

        inner class ItemViewHolder(private val binding: CellItemBinding) : SectionItemViewHolder(binding.root) {

            init {
                itemView.setOnClickListener {
                    getItem()?.let {
                        it.id++
                        notifyItemChanged(it, UPDATE_INCREMENT)
                    }
                }
            }

            override fun bind(adapterPosition: Int, sectionKey: Int, item: Item) {
                binding.itemTitle.text = "[${item.section}] Item ${item.id}"
            }

            override fun partialBind(
                adapterPosition: Int,
                sectionKey: Int,
                item: Item,
                payloads: MutableList<Any>
            ) {
                payloads.forEach {
                    when (it) {
                        UPDATE_INCREMENT -> {
                            binding.itemTitle.text = "Item ${item.id}"
                        }
                    }
                }
            }
        }
    }
}
