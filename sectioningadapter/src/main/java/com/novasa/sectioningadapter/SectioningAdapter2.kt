package com.novasa.sectioningadapter

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.*
import java.lang.IllegalStateException

abstract class SectioningAdapter2<TItem : Any, TSectionKey : Any> : RecyclerView.Adapter<SectioningAdapter2.BaseViewHolder<TItem, TSectionKey>>() {

    interface Entry<TItem : Any, TSectionKey : Any> {
        val viewType: Int
    }

    class ItemWrapper<TItem : Any, TSectionKey : Any>(
        val item: TItem,
        val section: Section<TItem, TSectionKey>

    ) : Entry<TItem, TSectionKey> {
        override val viewType: Int = section.viewType
    }

    class NonItem<TItem : Any, TSectionKey : Any>(
        val type: Int,
        val id: Int,
        val section: Section<TItem, TSectionKey>?,
        override val viewType: Int

    ) : Entry<TItem, TSectionKey> {

        companion object {
            const val TYPE_HEADER = 1
            const val TYPE_FOOTER = 2
            const val TYPE_NO_CONTENT = 3
        }

        internal fun isEqualTo(other: NonItem<TItem, TSectionKey>): Boolean = other.type == this.type
                && other.id == this.id
                && other.viewType == this.viewType
                && other.section?.key == this.section?.key
    }

    class Section<TItem : Any, TSectionKey : Any>(
        val key: TSectionKey,
        val parent: TSectionKey?,
        override val viewType: Int

    ) : Entry<TItem, TSectionKey> {
        val entries = ArrayList<Entry<TItem, TSectionKey>>()

        var depth = -1
            internal set
    }

    private val listUpdateCallback = object : ListUpdateCallback {

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val differItemCallback = object : DiffUtil.ItemCallback<Entry<TItem, TSectionKey>>() {

        override fun areItemsTheSame(oldItem: Entry<TItem, TSectionKey>, newItem: Entry<TItem, TSectionKey>): Boolean {
            return let(oldItem as? ItemWrapper<TItem, TSectionKey>, newItem as? ItemWrapper<TItem, TSectionKey>) { o, n ->
                areItemsTheSame(o.item, n.item)

            } ?: let(oldItem as? NonItem<TItem, TSectionKey>, newItem as? NonItem<TItem, TSectionKey>) { o, n ->
                o.isEqualTo(n)

            } ?: false
        }

        override fun areContentsTheSame(oldItem: Entry<TItem, TSectionKey>, newItem: Entry<TItem, TSectionKey>): Boolean {
            return let(oldItem as? ItemWrapper<TItem, TSectionKey>, newItem as? ItemWrapper<TItem, TSectionKey>) { o, n ->
                areContentsTheSame(o.item, n.item)

            } ?: let(oldItem as? NonItem<TItem, TSectionKey>, newItem as? NonItem<TItem, TSectionKey>) { o, n ->
                true

            } ?: true
        }

        override fun getChangePayload(oldItem: Entry<TItem, TSectionKey>, newItem: Entry<TItem, TSectionKey>): Any? {
            return super.getChangePayload(oldItem, newItem)
        }
    }

    open fun areItemsTheSame(oldItem: TItem, newItem: TItem) = oldItem == newItem

    open fun areContentsTheSame(oldItem: TItem, newItem: TItem) = oldItem == newItem


    private val updateHandler = Handler()

    private val differ = AsyncListDiffer(listUpdateCallback, AsyncDifferConfig.Builder(differItemCallback).build())

    private val currentContent: List<Entry<TItem, TSectionKey>>
        get() = differ.currentList

    private val globalHeaders = SparseArray<NonItem<TItem, TSectionKey>>()
    private val globalFooters = SparseArray<NonItem<TItem, TSectionKey>>()

    private val sections = ArrayList<Section<TItem, TSectionKey>>()
    private val sectionsMap = HashMap<TSectionKey, Section<TItem, TSectionKey>>()

    private fun buildContent(): List<Entry<TItem, TSectionKey>> = ArrayList<Entry<TItem, TSectionKey>>().apply {

        for (i in 0 until globalHeaders.size()) {
            add(globalHeaders.valueAt(i))
        }

        fun addSectionEntries(section: Section<TItem, TSectionKey>, depth: Int) {
            section.depth = depth
            for (entry in section.entries) {
                when (entry) {
                    is Section -> addSectionEntries(entry, depth + 1)
                    else -> add(entry)
                }
            }
        }

        for (section in sections) {
            addSectionEntries(section, 0)
        }

        for (i in 0 until globalFooters.size()) {
            add(globalFooters.valueAt(i))
        }
    }

    private val update = {
        differ.submitList(buildContent())
    }

    fun update() {
        updateHandler.removeCallbacksAndMessages(null)
        updateHandler.post(update)
    }

    fun setItems(items: List<TItem>) {
        for (section in sections) {
            section.entries.clear()
        }

        addItems(items)
    }

    fun addItems(items: List<TItem>) {

        fun getOrCreateSection(key: TSectionKey): Section<TItem, TSectionKey> {
            val parentKey = getParentSectionKeyForSectionKey(key)
            return sectionsMap.getOrPut(key) {
                Section<TItem, TSectionKey>(key, parentKey, getItemViewTypeForSectionKey(key)).also { section ->
                    parentKey?.let { getOrCreateSection(it) }?.entries?.add(section)
                        ?: sections.add(section)
                }
            }
        }

        for (item in items) {
            getSectionKeyForItem(item)?.let { key ->
                val section = getOrCreateSection(key)
                section.entries.add(ItemWrapper(item, section))
            }
        }

        update()
    }

    fun getItemAtPosition(adapterPosition: Int): TItem? = (currentContent[adapterPosition] as? ItemWrapper)?.item

    abstract fun getSectionKeyForItem(item: TItem): TSectionKey?

    open fun getParentSectionKeyForSectionKey(key: TSectionKey): TSectionKey? = null

    open fun getItemViewTypeForSectionKey(key: TSectionKey): Int = 0


    // region Sorting



    private val itemComparators: HashMap<TSectionKey, Comparator<TItem>> by lazy {
        HashMap()
    }

    private fun itemComparator(key: TSectionKey): Comparator<TItem> =
        itemComparators.getOrPut(key) {
            Comparator { o1, o2 ->
                compareItems(key, o1, o2)
            }
        }

    /**
     * Override and return true if the adapter sections should be sorted. If this returns true, [compareSectionKeys] must also be overridden.
     *
     * If this returns false, the sections are displayed in the order that their items are supplied.
     * @return true if the sections should be sorted. Default = false
     * @see [compareSectionKeys]
     */
    protected open fun sortSections(): Boolean = false

    /**
     * If [sortSections] returns true, this must also be overridden, to determine the order of the sections.
     *
     * Uses a [Comparator] for sorting.
     * @see [Comparator]
     */
    protected open fun compareSectionKeys(key1: TSectionKey, key2: TSectionKey): Int =
        throw NotImplementedError("Must override compareSectionKeys() if sortSections() returns true")

    /**
     * Override and return true if the items in the given section should be sorted.
     *
     * If this returns true, [compareItems] must also be overridden.
     * @param sectionKey The section sectionKey.
     */
    protected open fun sortItemsInSection(sectionKey: TSectionKey): Boolean = false

    /**
     * If [sortItemsInSection] returns true for any section, this must also be overridden to determine the order of the items.
     *
     * Uses a [Comparator] for sorting.
     * @param sectionKey The section sectionKey that the items are associated with.
     * @see [Comparator]
     */
    protected open fun compareItems(sectionKey: TSectionKey, item1: TItem, item2: TItem): Int =
        throw NotImplementedError("Must override compareItems() if sortItemsInSection() returns true")

    // endregion


    // region Adapter overrides

    final override fun getItemCount(): Int = currentContent.size

    final override fun getItemViewType(position: Int): Int = currentContent[position].viewType

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<TItem, TSectionKey> = onCreateViewHolder(parent.context, parent, viewType)

    abstract fun onCreateViewHolder(context: Context, parent: ViewGroup, viewType: Int): BaseViewHolder<TItem, TSectionKey>

    override fun onBindViewHolder(holder: BaseViewHolder<TItem, TSectionKey>, position: Int) {
        val entry = currentContent[position]
        holder.bind(position, entry)
    }

    // endregion


    open class BaseViewHolder<TItem : Any, TSectionKey : Any>(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun bind(adapterPosition: Int, entry: Entry<TItem, TSectionKey>) {

        }
    }

    open class SectionViewHolder<TItem : Any, TSectionKey : Any>(itemView: View) : BaseViewHolder<TItem, TSectionKey>(itemView) {

        override fun bind(adapterPosition: Int, entry: Entry<TItem, TSectionKey>) {
            (entry as? NonItem)?.section ?: (entry as? ItemWrapper)?.section?.let {
                bind(adapterPosition, it)
            } ?: throw IllegalStateException("Section view holder was used for entry with no section associated")
        }

        open fun bind(adapterPosition: Int, section: Section<TItem, TSectionKey>) {

        }
    }

    open class ItemViewHolder<TItem : Any, TSectionKey : Any>(itemView: View) : BaseViewHolder<TItem, TSectionKey>(itemView) {

        final override fun bind(adapterPosition: Int, entry: Entry<TItem, TSectionKey>) {
            (entry as? ItemWrapper)?.let {
                bind(adapterPosition, it.section, it.item)
            } ?: throw IllegalStateException("Item view holder was used for entry with no item associated")
        }

        open fun bind(adapterPosition: Int, section: Section<TItem, TSectionKey>, item: TItem) {

        }
    }
}