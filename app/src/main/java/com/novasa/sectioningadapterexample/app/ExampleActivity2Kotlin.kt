package com.novasa.sectioningadapterexample.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novasa.sectioningadapter.SectioningAdapter2
import com.novasa.sectioningadapterexample.R
import com.novasa.sectioningadapterexample.data.DataSource
import com.novasa.sectioningadapterexample.data.Item
import com.novasa.sectioningadapterexample.data.TypedItem
import com.novasa.sectioningadapterexample.databinding.ActivityMainBinding
import com.novasa.sectioningadapterexample.databinding.CellItemBinding
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

@SuppressLint("SetTextI18n")
class ExampleActivity2Kotlin : AppCompatActivity() {

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
    lateinit var dataSource: DataSource<TypedItem>

    private val sectioningAdapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        val binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val context = this

        binding.apply {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = sectioningAdapter
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

        disposables += dataSource.data()
            .subscribe { data ->
                sectioningAdapter.setItems(data.items)
            }
    }

    private fun shuffle() {

    }

    private fun setItems() {

    }

    private fun addItems() {
//        sectioningAdapter.addItems(items2)
//        sectioningAdapter.getAllItems().first().section++
//        sectioningAdapter.refresh()
    }

    private fun removeItems() {
//        sectioningAdapter.removeAllItems()
    }

    data class Key(val type: String, val section: Int)

    class Adapter : SectioningAdapter2<TypedItem, Key>() {

        override fun getSectionKeyForItem(item: TypedItem): Key? {
            return Key(item.type, item.section)
        }

        override fun getParentSectionKeyForSectionKey(key: Key): Key? = if (key.section > 0) {
            Key(key.type, -1)
        } else null

        override fun getItemViewTypeForSectionKey(key: Key): Int {
            return VIEW_TYPE_ITEM
        }

        override fun onCreateViewHolder(context: Context, parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(context)

            return when (viewType) {
                VIEW_TYPE_ITEM -> ItemViewHolder(CellItemBinding.inflate(inflater))
                else -> throw IllegalArgumentException()
            }
        }

        class ItemViewHolder(private val binding: CellItemBinding) : RecyclerView.ViewHolder(binding.root) {

//            override fun bind(adapterPosition: Int, section: Section<TypedItem, Key>, item: TypedItem) {
//                super.bind(adapterPosition, section, item)
//
//                itemView.apply {
//                    itemTitle.text = "[${item.section}] Item ${item.id}"
//                }
//            }
        }
    }
}
