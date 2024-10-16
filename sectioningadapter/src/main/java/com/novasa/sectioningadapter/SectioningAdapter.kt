package com.novasa.sectioningadapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("MemberVisibilityCanBePrivate", "unused", "SameParameterValue")
abstract class SectioningAdapter<TItem : Any, TSectionKey : Any> :
    RecyclerView.Adapter<SectioningAdapter.BaseViewHolder>() {

    private val name: String
        get() = this.javaClass.name

    private val updateHandler = Handler(Looper.getMainLooper())

    // region Diff

    var mostRecentUpdateTime = -1L
    private var diffTimeRef = -1L
    private var updateInProgress = AtomicBoolean(false)
    private var pendingUpdate = AtomicBoolean(false)

    private val differItemCallback = object : DiffUtil.ItemCallback<Wrapper<TItem, TSectionKey>>() {

        override fun areItemsTheSame(oldItem: Wrapper<TItem, TSectionKey>, newItem: Wrapper<TItem, TSectionKey>): Boolean =

        // If the item has a new view type, it must always be rebound.
            // This is necessary for when an item moves to a new section that has a different view type, without actually changing.
            !forceRebindAllNext && oldItem.viewType == newItem.viewType && let(oldItem.item, newItem.item, oldItem.sectionKey, newItem.sectionKey) { o, n, ok, nk ->
                !forceRebindItemsNext && areItemsTheSame(o, n, ok, nk)

            } ?: let(oldItem.nonItem, newItem.nonItem) { o, n ->
                o.isEqualTo(n)

            } ?: false

        override fun areContentsTheSame(
            oldItem: Wrapper<TItem, TSectionKey>,
            newItem: Wrapper<TItem, TSectionKey>,
        ): Boolean =
            let(oldItem.item, newItem.item, oldItem.sectionKey, newItem.sectionKey) { o, n, ok, nk ->
                areContentsTheSame(o, n, ok, nk)

            } ?: let(oldItem.nonItem, newItem.nonItem) { _, n ->
                !n.hasPendingChange

            } ?: true


        override fun getChangePayload(
            oldItem: Wrapper<TItem, TSectionKey>,
            newItem: Wrapper<TItem, TSectionKey>,
        ): Any? {
            let(oldItem.item, newItem.item, oldItem.sectionKey, newItem.sectionKey) { o, n, ok, nk ->
                return getChangePayload(o, n, ok, nk)
            }
            let(oldItem.nonItem, newItem.nonItem) { _, n ->
                return n.consumePendingChange()
            }
            return null
        }
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

    private val differ = AsyncListDiffer(
        listUpdateCallback,
        AsyncDifferConfig.Builder(differItemCallback).build()
    ).apply {
        addListListener { _, _ ->
            onUpdateComplete()
        }
    }

    /**
     * Override to manually determine if items in the adapter are equal.
     *
     * This is always called during a list refresh, and it is only called if the items have been determined to belong to the same section,
     * Since if the item has moved to a different section, it must always be rebound.
     *
     * @param oldItem The current item
     * @param newItem The updated item
     * @param oldSectionKey The section key associated with the current item
     * @param newSectionKey The section key associated with the new item
     * @return true if the items are equal, even if their contents differ, e.g. if the items have the same id,
     * but have different values in other fields.
     *
     * If this returns true, [areContentsTheSame] will be called to determine if the contents have changed,
     * and the view representing the item should be updated.
     *
     * Default returns the equals implementation between the items.
     * @see [DiffUtil.ItemCallback.areItemsTheSame]
     */
    open fun areItemsTheSame(oldItem: TItem, newItem: TItem, oldSectionKey: TSectionKey, newSectionKey: TSectionKey) =
        oldItem == newItem

    /**
     * Override to determine if item contents have changed, and view updates should be administered.
     *
     * This is only called if [areItemsTheSame] returned true.
     * @param oldItem The current item
     * @param newItem The updated item
     * @param oldSectionKey The section key associated with the current item
     * @param newSectionKey The section key associated with the new item
     * @return true if the items' contents are equal, and the view should not be updated.
     *
     * If this returns false, [getChangePayload] is called to determine if a change payload should be sent to [BaseViewHolder.partialBind].
     *
     * Default returns true
     * @see [DiffUtil.ItemCallback.areContentsTheSame]
     */
    open fun areContentsTheSame(oldItem: TItem, newItem: TItem, oldSectionKey: TSectionKey, newSectionKey: TSectionKey): Boolean =
        true

    /**
     * If [areContentsTheSame] returns false, this will be called to determine if an update payload should be sent to [BaseViewHolder.partialBind].
     * @param oldItem The current item
     * @param newItem The updated item
     * @param oldSectionKey The section key associated with the current item
     * @param newSectionKey The section key associated with the new item
     * @return A payload object, or null if no payload should be sent.
     *
     * Default returns null
     */
    open fun getChangePayload(oldItem: TItem, newItem: TItem, oldSectionKey: TSectionKey, newSectionKey: TSectionKey): Any? = null

    // endregion


    // region Content

    private class Wrapper<TItem, TSectionKey>(
        val sectionKey: TSectionKey?,
        val viewType: Int,
        val item: TItem?,
        val nonItem: NonItem<TSectionKey>?,
    ) {

        constructor(sectionKey: TSectionKey?, viewType: Int, item: TItem) : this(
            sectionKey,
            viewType,
            item,
            null
        )

        constructor(nonItem: NonItem<TSectionKey>) : this(
            nonItem.sectionKey,
            nonItem.viewType,
            null,
            nonItem
        )

        override fun toString(): String =
            "Wrapper - section: $sectionKey, view type: $viewType, content: ${item ?: nonItem ?: "empty"}"
    }

    private class NonItem<TSectionKey>(
        val type: Int,
        var viewType: Int,
        val sectionKey: TSectionKey?,
        var id: Int,
    ) {

        companion object {
            const val TYPE_HEADER = 1
            const val TYPE_FOOTER = 2
            const val TYPE_NO_CONTENT = 3
        }

        var hasPendingChange: Boolean = false
            private set

        private var changePayload: Any? = null

        fun setHasPendingChange(payload: Any?) {
            hasPendingChange = true
            changePayload = payload
        }

        fun consumePendingChange(): Any? {
            hasPendingChange = false

            val payload = changePayload
            changePayload = null
            return payload
        }

        fun isEqualTo(other: NonItem<TSectionKey>): Boolean = other.type == this.type &&
                other.viewType == this.viewType &&
                other.sectionKey == this.sectionKey &&
                other.id == this.id

        override fun toString(): String =
            "NonItem<TSectionKey type: $type, viewType: $viewType, sectionKey: $sectionKey, sort: $id"
    }

    private val content = ArrayList<Wrapper<TItem, TSectionKey>>()

    private val currentContent: List<Wrapper<TItem, TSectionKey>>
        get() = differ.currentList

    private val globalHeaders = SparseArray<NonItem<TSectionKey>>()
    private val globalFooters = SparseArray<NonItem<TSectionKey>>()

    private val sections = ArrayList<Section>()
    private val sectionsMap = HashMap<TSectionKey, Section>()


    /**
     * The main update function of the adapter. When this is called, the items will be sorted into sections, according to the result of [getSectionKeyForItem].
     *
     * The adapter then uses an [AsyncListDiffer] to apply adapter updates. To receive a callback when the diff has finished, override [onContentChanged].
     *
     * Any items currently in the adapter, that are not in the new data set will be removed.
     * @param items The items that the adapter should display.
     * @see [AsyncListDiffer]
     */
    fun setItems(items: Collection<TItem>) {
        for (section in sections) {
            section.items.clear()
        }

        addItems(items)
    }

    /**
     * Add items to the current data set.
     *
     * Similar to [setItems], but does not remove current items first.
     */
    fun addItems(items: Collection<TItem>) {
        for (item in items) {
            getSectionKeyForItem(item)?.let { key ->
                val section = sectionsMap.getOrPut(key) {
                    createSection(key)
                }

                section.items.add(item)
            }
        }

        updateSections()
    }

    /**
     * Add a single item to the current data set.
     */
    fun addItem(item: TItem) {
        getSectionKeyForItem(item)?.let { key ->
            val section = sectionsMap.getOrPut(key) {
                createSection(key)
            }

            section.items.add(item)
        }

        updateSections()
    }

    /**
     * Refresh all content in the adapter.
     */
    fun refresh() {
        setItems(getAllItems())
    }

    fun updateItem(item: TItem) {
        removeSingleItem(item)
        addItem(item)
    }

    /**
     * Remove the items in the collection from the adapter.
     * @param items The items to remove.
     */
    fun removeItems(items: Collection<TItem>) {
        val set = HashSet(items)
        removeItemsIf { item ->
            set.contains(item)
        }
    }

    /**
     * Remove a single item from the adapter.
     * @param item The item to remove.
     */
    fun removeItem(item: TItem) {
        if (removeSingleItem(item)) {
            updateSections()
        }
    }

    /**
     * Remove all items in the adapter that matches the predicate.
     * @param predicate Any item for which this returns true will be removed.
     */
    fun removeItemsIf(predicate: (TItem) -> Boolean) {
        for (section in sections) {
            section.items.removeAll { item ->
                predicate(item)
            }
        }
        updateSections()
    }

    /**
     * Remove all items from the adapter.
     *
     * This will not remove static sections.
     */
    fun removeAllItems() {
        for (section in sections) {
            section.items.clear()
        }
        updateSections()
    }

    /**
     * Called when an asynchronous diff is about to be executed. This includes any add or remove in the adapter, including non item views.
     */
    open fun onContentWillChange() {}

    /**
     * Called when the asynchronous diff has finished, and the adapter notifications have been applied.
     */
    open fun onContentChanged() {}

    fun onNextChange(action: () -> Unit) {
        nextChangeCallbacks.add(action)
    }

    private val nextChangeCallbacks: MutableList<() -> Unit> = ArrayList()

    private fun getWrapper(adapterPosition: Int): Wrapper<TItem, TSectionKey>? =
        if (adapterPosition >= 0 && adapterPosition < currentContent.size) currentContent[adapterPosition] else null

    /**
     * Get the item at a given adapter position.
     * @param adapterPosition The absolute adapter position.
     * @return The item at the given adapter position, or null if there is no item at the position.
     */
    fun getItem(adapterPosition: Int): TItem? = getWrapper(adapterPosition)?.item

    /**
     * Iterate over all items currently in the adapter
     *
     * @param action Lambda function supplying the item, its associated section sectionKey, and its current adapter position.
     *
     * If the section is currently collapsed, the adapter position will be -1.
     */
    fun forEach(action: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Unit) {
        currentContent.forEachIndexed { index, wrapper ->
            let(wrapper.item, wrapper.sectionKey) { item, sectionKey ->
                action(item, sectionKey, index)
            }
        }
    }

    /**
     * Searches all items in the adapter according to a predicate.
     *
     * @param predicate Predicate supplying the item, its associated section sectionKey, and its current adapter position.
     *
     * @return The first item for which the predicate returns true, or null if the predicate never returns true.
     */
    fun getItemIf(predicate: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Boolean): TItem? {

        var result: TItem? = null

        forEach { item, sectionKey, adapterPosition ->
            if (predicate(item, sectionKey, adapterPosition)) {
                result = item
                return@forEach
            }
        }

        return result
    }

    /**
     * Searches all items in the adapter according to a predicate.
     *
     * @param predicate Predicate supplying the item, its associated section sectionKey, and its current adapter position.
     *
     * @return A list of all items for which the predicate returns true.
     */
    fun getItemsIf(predicate: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Boolean): List<TItem> {
        val result = ArrayList<TItem>()

        forEach { item, sectionKey, adapterPosition ->
            if (predicate(item, sectionKey, adapterPosition)) {
                result.add(item)
            }
        }

        return result
    }

    /**
     * @return All items currently being displayed in the adapter, in the order they are being displayed in.
     *
     * This includes items in collapsed sections.
     */
    fun getAllItems(): List<TItem> = currentContent.mapNotNull { wrapper -> wrapper.item }

    /**
     * @return The total number of items currently being displayed in the adapter.
     *
     * This includes items in collapsed sections.
     */
    fun getTotalItemCount(): Int = sections.sumOf { section ->
        section.itemCount
    }

    /**
     * @return true if the adapter contains no items, false otherwise.
     *
     * This will still return true if there are non-items in the adapter (headers, footers, etc.)
     */
    fun isEmpty(): Boolean = sections.all { section ->
        section.isEmpty
    }

    /**
     * Find the adapter position for the given item.
     *
     * @param item The item to search for.
     * @return The adapter position of the item if found, or -1 if the item is not in the adapter.
     */
    fun findAdapterPositionForItem(item: TItem): Int {

        currentContent.forEachIndexed { index, wrapper ->
            let(wrapper.item, wrapper.sectionKey) { sItem, sectionKey ->
                if (areItemsTheSame(item, sItem, sectionKey, sectionKey)) {
                    return@findAdapterPositionForItem index
                }
            }
        }

        return -1
    }


    // region Changes

    private var forceRebindAllNext = false
    private var forceRebindItemsNext = false

    /**
     * Forces all views to be rebound on the next update, even if they have no changes.
     */
    fun forceRebindAllNext() {
        forceRebindAllNext = true
    }

    /**
     * Forces all item views to be rebound on the next update, even if they have no changes.
     */
    fun forceRebindItemsNext() {
        forceRebindItemsNext = true
    }

    fun notifyItemChanged(item: TItem, payload: Any?) {
        val pos = findAdapterPositionForItem(item)
        if (pos >= 0) {
            notifyItemChanged(pos, payload)
        }
    }

    // endregion


    private fun removeSingleItem(item: TItem): Boolean {
        for (section in sections) {
            if (section.items.remove(item)) {
                return true
            }
        }
        return false
    }

    private fun updateSections() {
        // Purge empty non-static sections
        for (i in sections.size - 1 downTo 0) {
            val section = sections[i]
            if (section.itemCount == 0 && !section.static) {
                sections.removeAt(i)
                sectionsMap.remove(section.key)
            }
        }

        if (sortSections()) {
            sections.sortWith(sectionComparator)
        }

        var p0 = globalHeaderCount

        sections.forEachIndexed { i, section: Section ->
            with(section) {

                itemViewTypes.clear()
                items.forEach {
                    itemViewTypes[it] = getItemViewTypeForItemInSection(key, it)
                }

                headerCount = getHeaderCountForSection(key)
                require(headerCount >= 0) {
                    "Section header count must be >= 0"
                }

                footerCount = getFooterCountForSection(key)
                require(footerCount >= 0) {
                    "Section footer count must be >= 0"
                }

                showNoContent = section.static && showNoContentForStaticSection(section.key)

                if (sortItemsInSection(key)) {
                    items.sortWith(itemComparator(key))
                }

                index = i
                adapterPosition = p0

                p0 += size
            }
        }

        globalSectionsSize = p0 - globalHeaderCount

        refreshContent()
    }

    private fun refreshContent() {
        with(content) {

            clear()

            for (i in 0 until globalHeaders.size()) {
                val header = globalHeaders.valueAt(i)
                add(createWrapper(header))
            }

            globalNoContentVisible = false
            if (globalSectionsSize > 0) {
                for (section: Section in sections) {
                    with(section) {
                        for (footer: NonItem<TSectionKey> in headers) {
                            add(createWrapper(footer))
                        }

                        if (!collapsed) {
                            if (isEmpty && emptyContentCount > 0) {
                                for (item: NonItem<TSectionKey> in emptyContent) {
                                    add(createWrapper(item))
                                }

                            } else {
                                for (item: TItem in items) {
                                    add(createWrapper(section, item))
                                }
                            }
                        }

                        for (footer: NonItem<TSectionKey> in footers) {
                            add(createWrapper(footer))
                        }
                    }
                }

            } else if (showGlobalNoContent()) {
                add(createWrapper(getGlobalNoContent()))
                globalNoContentVisible = true
            }

            for (i in 0 until globalFooters.size()) {
                val footer = globalFooters.valueAt(i)
                add(createWrapper(footer))
            }
        }

        submitUpdate()
    }

    private val update = {

        if (updateInProgress.get()) {
            pendingUpdate.set(true)

        } else {
            updateInProgress.set(true)
            diffTimeRef = SystemClock.elapsedRealtime()

            onContentWillChange()

            differ.submitList(ArrayList(content))
        }
    }

    private fun submitUpdate() {
        updateHandler.removeCallbacksAndMessages(null)
        updateHandler.post(update)
    }

    private fun onUpdateComplete() {
        forceRebindAllNext = false
        forceRebindItemsNext = false

        updateInProgress.set(false)
        mostRecentUpdateTime = SystemClock.elapsedRealtime() - diffTimeRef

        onContentChanged()

        nextChangeCallbacks.apply {
            forEach { it() }
            clear()
        }

        if (pendingUpdate.get()) {
            pendingUpdate.set(false)
            submitUpdate()
        }
    }

    private fun removeContentRange(p0: Int, size: Int) {
        require(size >= 0 && p0 >= 0 && p0 + size <= content.size) {
            "Tried to remove content range outside bounds ($p0 + $size, size = ${content.size})"
        }

        for (i in 0 until size) {
            content.removeAt(p0)
        }
    }


    private fun createWrapper(section: Section, item: TItem): Wrapper<TItem, TSectionKey> =
        Wrapper(
            sectionKey = section.key,
            viewType = requireNotNull(section.itemViewTypes[item]) {
                "No item view type found for item: $item"
            },
            item = item
        )

    private fun createWrapper(nonItem: NonItem<TSectionKey>): Wrapper<TItem, TSectionKey> =
        Wrapper(nonItem)

    // endregion


    // region Sections

    /**
     * The number of sections currently in the adapter.
     *
     * This includes collapsed sections.
     */
    val sectionCount
        get() = sections.size

    /**
     * The current size of all the sections, including any section headers and footers.
     *
     * This excludes any global headers or footers.
     */
    private var globalSectionsSize = 0


    /**
     * Override to determine view types for the given section, as supplied in [onCreateViewHolder].
     *
     * Default is 0.
     * @param sectionKey The section sectionKey
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getItemViewTypeForSection(sectionKey: TSectionKey): Int = 0

    /**
     * Override to determine view types for an item in a given section, as supplied in [onCreateViewHolder].
     *
     * Default returns [getItemCountForSection] implementation.
     * @param sectionKey The section sectionKey
     * @param item The item in the section associated with [sectionKey]
     * @see [getItemViewTypeForSection]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getItemViewTypeForItemInSection(sectionKey: TSectionKey, item: TItem): Int = getItemViewTypeForSection(sectionKey)

    /**
     * Get the current index of the supplied section sectionKey, or -1 if the section is not in the adapter.
     */
    fun getSectionIndex(sectionKey: TSectionKey): Int = sectionsMap[sectionKey]?.index ?: -1

    /**
     * Removes all sections (and items) from the adapter.
     */
    fun removeAllSections() {

        if (globalSectionsSize > 0) {
            removeContentRange(globalHeaderCount, globalSectionsSize)

            globalSectionsSize = 0
            sections.clear()
            sectionsMap.clear()

            insertGlobalNoContentIfRequired()

            submitUpdate()
        }
    }

    /**
     * Removes the section associated with the given section sectionKey, and all items associated with it.
     *
     * This will remove the section even if it is static.
     *
     * To remove the items in a static section without removing the section, use [removeItemsInSection].
     */
    fun removeSection(key: TSectionKey) {
        sectionsMap[key]?.let { section ->
            removeSection(section)
        }
    }

    private fun createSection(key: TSectionKey): Section = Section(key).apply {
        collapsed = collapseNewSections
        sections.add(this)
    }

    private fun getSectionForKey(key: TSectionKey): Section? = sectionsMap[key]

    private fun findSectionForAdapterPosition(position: Int): Section? {
        if (position >= globalHeaderCount) {
            for (section in sections) {
                if (position < section.adapterPosition + section.size) {
                    return section
                }
            }
        }
        return null
    }

    private fun removeSection(section: Section) {

        val size = section.size
        removeContentRange(section.adapterPosition, size)

        sections.removeAt(section.index)
        sectionsMap.remove(section.key)

        offsetSectionPositions(-size, section.index)
        offsetSectionIndices(-1, section.index)

        globalSectionsSize -= size
        if (globalSectionsSize == 0) {
            insertGlobalNoContentIfRequired()
        }

        submitUpdate()
    }

    private fun offsetSectionPositions(offset: Int) {
        offsetSectionPositions(offset, 0)
    }

    private fun offsetSectionPositions(offset: Int, startSection: Int) {
        for (i in startSection until sections.size) {
            sections[i].adapterPosition += offset
        }
    }

    private fun offsetSectionIndices(offset: Int) {
        offsetSectionIndices(offset, 0)
    }

    private fun offsetSectionIndices(offset: Int, startSection: Int) {
        for (i in startSection until sections.size) {
            sections[i].index += offset
        }
    }

    private inner class Section(val key: TSectionKey) {

        val items = ArrayList<TItem>()
        val itemViewTypes = HashMap<TItem, Int>()
        lateinit var headers: ArrayList<NonItem<TSectionKey>>
        lateinit var footers: ArrayList<NonItem<TSectionKey>>
        lateinit var emptyContent: ArrayList<NonItem<TSectionKey>>

        var index = -1
        var adapterPosition = -1

        var static = false
        var collapsed = false

        val size
            get() = when {
                collapsed -> 0
                else -> itemCount + emptyContentSize
            } + headerCount + footerCount

        val itemCount
            get() = items.size

        val isEmpty
            get() = itemCount == 0

        val itemSize
            get() = if (collapsed) 0 else itemCount

        val emptyContentCount
            get() = if (isEmpty && ::emptyContent.isInitialized) emptyContent.size else 0

        val emptyContentSize
            get() = if (collapsed) 0 else emptyContentCount

        var headerCount: Int
            get() = if (::headers.isInitialized) headers.size else 0
            set(value) {
                headers = ArrayList(value)
                for (i in 0 until value) {
                    val viewType = getHeaderViewTypeForSection(key, i)
                    headers.add(NonItem(NonItem.TYPE_HEADER, viewType, key, i))
                }
            }

        var footerCount: Int
            get() = if (::footers.isInitialized) footers.size else 0
            set(value) {
                footers = ArrayList(value)
                for (i in 0 until value) {
                    val viewType = getFooterViewTypeForSection(key, i)
                    footers.add(NonItem(NonItem.TYPE_FOOTER, viewType, key, i))
                }
            }

        var showNoContent: Boolean = false
            set(value) {
                if (value != field) {
                    field = value
                    if (value) {
                        insertEmptyContent(
                            NonItem(
                                NonItem.TYPE_NO_CONTENT,
                                getNoContentViewTypeForStaticSection(key),
                                key,
                                0
                            ), 0
                        )
                    } else {
                        removeEmptyContent(0)
                    }
                }
            }

        val noContent: NonItem<TSectionKey>?
            get() = if (::emptyContent.isInitialized) emptyContent.firstOrNull() else null

        private fun insertEmptyContent(item: NonItem<TSectionKey>, index: Int) {
            if (!::emptyContent.isInitialized) {
                emptyContent = ArrayList()
            }
            emptyContent.add(index, item)
        }

        private fun removeEmptyContent(index: Int) {
            if (::emptyContent.isInitialized) {
                emptyContent.removeAt(index)
            }
        }

        override fun toString(): String =
            "Key: $key, Index: $index, position: $adapterPosition, itemCount: $itemCount, size: $size, collapsed: $collapsed"

        fun verifyItemPosition(itemPosition: Int) {
            if (itemPosition < 0 || itemPosition >= items.size) {
                throw IndexOutOfBoundsException("Illegal item position ($itemPosition, size: ${items.size}) for section with key $key. ${state()}")
            }
        }
    }

    // region Keys

    /**
     * The main sectioning function of the adapter.
     *
     * This is called for all items in the adapter, when they are supplied, to determine which section the item will belong to.
     * @param item Any and all items in the adapter.
     * @return A unique sectionKey that will represent a section in the adapter, or null if the item should not be included in the data set.
     */
    protected abstract fun getSectionKeyForItem(item: TItem): TSectionKey?

    /**
     * Find the sectionKey for the section associated with the given adapter position.
     * @param position The adapter position. Must be between [globalHeaderCount] and [globalHeaderCount] + [globalSectionsSize] - 1.
     */
    fun findSectionKeyForAdapterPosition(position: Int): TSectionKey? =
        findSectionForAdapterPosition(position)?.key

    /**
     * Get all section keys in the adapter, in the order they are currently being displayed.
     */
    fun getAllSectionKeys(): List<TSectionKey> = sections.map { section ->
        section.key
    }

    // endregion


    // region Headers / Footers

    /**
     * Override to display header views before the given section.
     *
     * If this is overridden, [getHeaderViewTypeForSection] must also be overridden.
     *
     * Default = 0
     * @param sectionKey The section sectionKey
     * @return The number of headers that the given section should display. Must be >= 0.
     * @see [getHeaderViewTypeForSection]
     */
    protected open fun getHeaderCountForSection(sectionKey: TSectionKey) = 0

    /**
     * If [getHeaderCountForSection] returns > 0, this must also be overridden, to determine the view types of the section headers.
     * @param sectionKey The section sectionKey
     * @param headerIndex The index of the header. This is relative to the section, so if all sections have only one section header, this will always be 0.
     * @see [getHeaderCountForSection]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getHeaderViewTypeForSection(sectionKey: TSectionKey, headerIndex: Int): Int =
        throw NotImplementedError("Must override getHeaderViewTypeForSection() if getHeaderCountForSectionKey() returns > 0")

    fun notifySectionHeaderChanged(sectionKey: TSectionKey, position: Int, payload: Any? = null) {
        getSectionForKey(sectionKey)?.let { section ->
            require(position in 0 until section.headerCount) {
                "Failed to update section header. Invalid position: $position. Current header count was ${section.headerCount}"
            }

            section.headers[position].setHasPendingChange(payload)

            submitUpdate()
        }
    }

    /**
     * Override to display footer views after the given section.
     *
     * If this is overridden, [getFooterViewTypeForSection] must also be overridden.
     *
     * Default = 0
     * @param sectionKey The section sectionKey
     * @return The number of footers that the given section should display. Must be >= 0.
     * @see [getFooterViewTypeForSection]
     */
    protected open fun getFooterCountForSection(sectionKey: TSectionKey) = 0

    /**
     * If [getFooterCountForSection] returns > 0, this must also be overridden, to determine the view types of the section footers.
     * @param sectionKey The section sectionKey
     * @param footerIndex The index of the footer. This is relative to the section, so if all sections have only one section footer, this will always be 0.
     * @see [getFooterCountForSection]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getFooterViewTypeForSection(sectionKey: TSectionKey, footerIndex: Int): Int =
        throw NotImplementedError("Must override getFooterViewTypeForSection() if getFooterCountForSectionKey() returns > 0")

    fun notifySectionFooterChanged(sectionKey: TSectionKey, position: Int, payload: Any? = null) {
        getSectionForKey(sectionKey)?.let { section ->
            require(position in 0 until section.footerCount) {
                "Failed to update section footer. Invalid position: $position. Current footer count was ${section.footerCount}"
            }

            section.footers[position].setHasPendingChange(payload)

            submitUpdate()
        }
    }

    // endregion


    // region No Content

    /**
     * Override and return true if a static section should show a view when it is empty.
     *
     * If this is overridden, [getNoContentViewTypeForStaticSection] must also be overridden.
     *
     * Default = false
     * @param sectionKey The section sectionKey
     * @see [getNoContentViewTypeForStaticSection]
     */
    protected open fun showNoContentForStaticSection(sectionKey: TSectionKey): Boolean = false

    /**
     * If [showNoContentForStaticSection] returns true, this must also be overridden to return the view type of the no content view.
     *
     * @param sectionKey The section sectionKey
     * @see [showNoContentForStaticSection]
     */
    protected open fun getNoContentViewTypeForStaticSection(sectionKey: TSectionKey): Int =
        throw NotImplementedError("Must override getNoContentViewTypeForStaticSection() if showNoContentForStaticSection() return true.")

    fun refreshStaticSectionNoContent(sectionKey: TSectionKey) {
        getSectionForKey(sectionKey)?.let { section ->
            if (section.static && section.noContent != null != showNoContentForStaticSection(
                    sectionKey
                ) || section.noContent?.viewType != getNoContentViewTypeForStaticSection(sectionKey)
            ) {
                updateSections()
            }
        }
    }

    fun notifyStaticSectionNoContentChanged(sectionKey: TSectionKey, payload: Any? = null) {
        getSectionForKey(sectionKey)?.let { section ->
            section.noContent?.let {
                it.setHasPendingChange(payload)
                submitUpdate()
            }
        }
    }

    // endregion


    // region Sorting

    private val sectionComparator: Comparator<Section> by lazy {
        Comparator { o1, o2 ->
            compareSectionKeys(o1.key, o2.key)
        }
    }

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


    // region Items

    /**
     * Get The current number of items in the section.
     * @param sectionKey The section sectionKey.
     * @return The current number of items in the section associated with the section sectionKey, or -1 if the section does not exist.
     */
    fun getItemCountForSection(sectionKey: TSectionKey): Int =
        sectionsMap[sectionKey]?.itemCount ?: -1

    /**
     * @param sectionKey The section sectionKey.
     * @return true if the section associated with the given section sectionKey contains no items, or if it does not exist. False otherwise.
     */
    fun isSectionEmpty(sectionKey: TSectionKey): Boolean = getItemCountForSection(sectionKey) <= 0

    /**
     * Sets the items for a single section.
     *
     * If the section does not exist, it will be created.
     *
     * If the section is currently not empty, any items not in the provided list will be removed.
     *
     * If there are items in the provided list that belong to a different section, they will be ignored.
     * @param sectionKey The section sectionKey.
     * @param items The items to set in the section.
     */
    fun setItemsInSection(sectionKey: TSectionKey, items: List<TItem>) {
        if (items.isEmpty()) {
            removeItemsInSection(sectionKey)

        } else {
            sectionsMap[sectionKey]?.items?.clear()
            addItemsInSection(sectionKey, items)
        }
    }

    /**
     * Add items in a single section.
     *
     * If there are items in the provided list that belong to a different section, they will be ignored.
     * @param sectionKey
     * @param items
     */
    fun addItemsInSection(sectionKey: TSectionKey, items: List<TItem>) {
        if (items.isNotEmpty()) {
            val section = sectionsMap.getOrPut(sectionKey) {
                createSection(sectionKey)
            }

            with(section.items) {
                clear()

                items.forEach { item ->
                    if (getSectionKeyForItem(item) == sectionKey) {
                        add(item)
                    }
                }
            }

            updateSections()
        }
    }

    /**
     * Similar to [removeSection], but does not remove the section if it is static.
     *
     * If the section is not static, this behaves identically to the former.
     */
    fun removeItemsInSection(key: TSectionKey) {
        sectionsMap[key]?.let { section ->
            removeItemsInSection(section)
        }
    }

    private fun removeItemsInSection(section: Section) {
        if (!section.static) {
            removeSection(section)

        } else if (!section.isEmpty) {
            section.items.clear()
            updateSections()
        }
    }

    /**
     * @return The items associated with the given section sectionKey, in the order they are presented in the adapter.
     *
     * If the section is not found, an empty list is returned.
     */
    fun getItemsInSection(sectionKey: TSectionKey): List<TItem> =
        getSectionForKey(sectionKey)?.items ?: emptyList()

    // endregion


    // region Static sections

    /**
     * Add a static section. Static sections remain even if they have no items.
     *
     * If the the section key is already associated with a section in the adapter, that section will become static.
     */
    fun addStaticSection(sectionKey: TSectionKey) {
        sectionsMap.getOrPut(sectionKey) {
            createSection(sectionKey)

        }.apply {
            static = true
        }

        updateSections()
    }

    /**
     * @see [addStaticSection]
     */
    fun addStaticSections(sectionKeys: Collection<TSectionKey>) {
        for (key in sectionKeys) {
            sectionsMap.getOrPut(key) {
                createSection(key)

            }.apply {
                static = true
            }
        }
        updateSections()
    }

    /**
     * Remove a static section.
     *
     * This only removes the static flag from the section. If the section contains items, it will remain in the adapter.
     * @see [removeSection]
     * @see [removeItemsInSection]
     */
    fun removeStaticSection(sectionKey: TSectionKey) {
        sectionsMap[sectionKey]?.static = false
        updateSections()
    }

    /**
     * @see [removeStaticSection]
     */
    fun removeStaticSections(sectionKeys: Collection<TSectionKey>) {
        for (key in sectionKeys) {
            sectionsMap[key]?.static = false
        }
        updateSections()
    }

    // endregion


    // region Expand / Collapse

    /**
     * Determines if newly added sections are collapsed when added.
     *
     * Default = false
     */
    var collapseNewSections = false

    /**
     * Collapse the section associated with the given section sectionKey.
     *
     * All item views in the section will be removed from the adapter. Section headers and footers will remain.
     *
     * Has no effect if the section is already collapsed.
     * @see [expandSection]
     */
    fun collapseSection(sectionKey: TSectionKey) = getSectionForKey(sectionKey)?.let {
        collapseSection(it)
    }

    /**
     * Expand the section associated with the given section sectionKey.
     *
     * Has no effect if the section is already expanded.
     *
     * This is the default state.
     * @see [collapseSection]
     */
    fun expandSection(sectionKey: TSectionKey) = getSectionForKey(sectionKey)?.let {
        expandSection(it)
    }

    /**
     * Expand a section if is collapsed, and vice versa.
     * @see [collapseSection]
     * @see [expandSection]
     */
    fun toggleExpandSection(sectionKey: TSectionKey) {
        getSectionForKey(sectionKey)?.let { section ->
            if (section.collapsed) {
                expandSection(section)
            } else {
                collapseSection(section)
            }
        }
    }

    /**
     * Collapse all sections in the adapter.
     * @see [collapseSection]
     * @see [expandSection]
     */
    fun collapseAllSections() {
        for (section in sections) {
            collapseSection(section)
        }
    }

    /**
     * Expand all sections in the adapter.
     * @see [collapseSection]
     * @see [expandSection]
     */
    fun expandAllSections() {
        for (section in sections) {
            expandSection(section)
        }
    }

    /**
     * Expands all sections if they are collapsed, and vice versa.
     *
     * If both expanded and collapsed sections exist, expandDefault is used to determine behaviour
     * @param expandDefault If true, all sections will expand if sections of both states exist, and vice versa. Default = true.
     * @see [collapseAllSections]
     * @see [expandAllSections]
     */
    fun toggleExpandAllSections(expandDefault: Boolean = true) {
        if (sections.isNotEmpty()) {
            var expand = sections.first().collapsed
            for (section in sections) {
                if (section.collapsed != expand) {
                    expand = expandDefault
                    break
                }
            }

            if (expand) {
                expandAllSections()

            } else {
                collapseAllSections()
            }
        }
    }

    private fun collapseSection(section: Section) {
        if (!section.collapsed) {

            val size = section.itemSize + section.emptyContentSize
            removeContentRange(section.adapterPosition + section.headerCount, size)
            offsetSectionPositions(-size, section.index + 1)
            globalSectionsSize -= size

            section.collapsed = true

            submitUpdate()
        }
    }

    private fun expandSection(section: Section) {
        if (section.collapsed) {
            section.collapsed = false

            val size: Int
            val p0 = section.adapterPosition + section.headerCount
            if (!section.isEmpty) {
                size = section.itemCount
                for (i in 0 until size) {
                    content.add(i + p0, createWrapper(section, section.items[i]))
                }

            } else {
                size = section.emptyContentCount
                for (i in 0 until size) {
                    content.add(i + p0, createWrapper(section.emptyContent[i]))
                }
            }

            offsetSectionPositions(size, section.index + 1)
            globalSectionsSize += size

            submitUpdate()
        }
    }

    // endregion
    // endregion


    // region Global Headers / Footers

    /** The current number of global headers. */
    val globalHeaderCount: Int
        get() = globalHeaders.size()

    /** The current number of global footers. */
    val globalFooterCount: Int
        get() = globalFooters.size()

    val globalNonItemContentSize: Int
        get() = if (globalNoContentVisible) 1 else 0


    /**
     * @param id The global header id.
     * @return true if the adapter currently contains a header with the specified id.
     */
    fun hasGlobalHeader(id: Int) = globalHeaders.get(id) != null

    /**
     * Insert a global header in the adapter.
     *
     * Global headers are displayed before the first section.
     *
     * @param id A unique id that doubles as sorting order.
     * If another header is added with the same id, the current one will be overridden.
     * @param viewType The view type of the global header, when supplied in [onCreateViewHolder]
     * @see [onCreateViewHolder]
     */
    fun insertGlobalHeader(id: Int, viewType: Int) {

        val header = NonItem<TSectionKey>(NonItem.TYPE_HEADER, viewType, null, id)
        val pSize = globalHeaders.size()
        var index = globalHeaders.indexOfKey(id)

        globalHeaders.put(id, header)

        if (index >= 0) {
            content.removeAt(index)

        } else {
            // SparseArray guarantees ordered keys! Thanks SparseArray!
            index = globalHeaders.indexOfKey(id)
        }

        content.add(index, createWrapper(header))

        offsetSectionPositions(globalHeaders.size() - pSize)

        submitUpdate()
    }

    /**
     * Remove any currently displayed global header.
     *
     * @param id The global header id.
     */
    fun removeGlobalHeader(id: Int) {
        val index = globalHeaders.indexOfKey(id)
        if (index >= 0) {
            globalHeaders.remove(id)
            content.removeAt(index)
            offsetSectionPositions(-1)
        }

        submitUpdate()
    }

    /**
     * Send a view update to a global header.
     *
     * @param id The global header id.
     * @param payload The payload to send to the view holder. It will be received in the [BaseViewHolder.partialBind] function.
     */
    fun notifyGlobalHeaderChanged(id: Int, payload: Any? = null) {
        globalHeaders.get(id)?.let {
            it.setHasPendingChange(payload)
            submitUpdate()
        }
    }

    /**
     * @param id The global footer id.
     * @return true if the adapter currently contains a footer with the specified id.
     */
    fun hasGlobalFooter(id: Int) = globalFooters.get(id) != null

    /**
     * Insert a global footer in the adapter.
     *
     * Global footers are displayed after the final section.
     *
     * @param id A unique id that doubles as sorting order.
     * If another footer is added with the same id, the current one will be overridden.
     * @param viewType The view type of the global footer, when supplied in [onCreateViewHolder]
     * @see [onCreateViewHolder]
     */
    fun insertGlobalFooter(id: Int, viewType: Int) {

        val footer = NonItem<TSectionKey>(NonItem.TYPE_HEADER, viewType, null, id)
        val offset = globalFooterStartPosition
        var index = globalFooters.indexOfKey(id)

        globalFooters.put(id, footer)

        if (index >= 0) {
            content.removeAt(offset + index)

        } else {
            index = globalFooters.indexOfKey(id)
        }

        content.add(index + offset, createWrapper(footer))

        submitUpdate()
    }

    /**
     * Remove any currently displayed global footer.
     *
     * @param id The global footer id.
     */
    fun removeGlobalFooter(id: Int) {

        val offset = globalFooterStartPosition
        val index = globalFooters.indexOfKey(id)

        if (index >= 0) {
            globalFooters.remove(id)
            content.removeAt(offset + index)
        }

        submitUpdate()
    }

    /**
     * Send a view update to a global footer.
     *
     * @param id The global footer id.
     * @param payload The payload to send to the view holder. It will be received in the [BaseViewHolder.partialBind] function.
     */
    fun notifyGlobalFooterChanged(id: Int, payload: Any? = null) {
        globalFooters.get(id)?.let {
            it.setHasPendingChange(payload)
            submitUpdate()
        }
    }

    private val globalFooterStartPosition: Int
        get() = globalHeaderCount + globalSectionsSize + globalNonItemContentSize

    // endregion


    // region Global no content

    /**
     * Override and return true if the adapter should display a view indicating that the adapter is empty.
     *
     * If this returns true. [getGlobalNoContentViewType] must also be overridden.
     * @return true if the adapter should display a no content view. Default = false.
     */
    protected open fun showGlobalNoContent(): Boolean = false

    /**
     * If [showGlobalNoContent] returns true, this must be overridden.
     * @return The view type of the no content view.
     * @see [onCreateViewHolder]
     */
    protected open fun getGlobalNoContentViewType(): Int {
        throw NotImplementedError("Must override getNoContentViewType() if showNoContent == true")
    }

    fun notifyGlobalNoContentChanged(payload: Any? = null) {
        globalNoContent?.let {
            it.setHasPendingChange(payload)

            submitUpdate()
        }
    }

    private var globalNoContentVisible = false

    private val globalNoContentPosition: Int
        get() = if (globalNoContentVisible) globalHeaderCount else -1

    private var globalNoContent: NonItem<TSectionKey>? = null
    private fun getGlobalNoContent(): NonItem<TSectionKey> =
        getGlobalNoContentViewType().let { viewType ->
            globalNoContent?.also {
                it.viewType = viewType
            } ?: NonItem<TSectionKey>(NonItem.TYPE_NO_CONTENT, viewType, null, 0).also {
                globalNoContent = it
            }
        }

    private fun insertGlobalNoContentIfRequired() {
        if (showGlobalNoContent()) {
            val globalNoContent = getGlobalNoContent()
            content.add(globalHeaderCount, Wrapper(globalNoContent))
            globalNoContentVisible = true
        }
    }

    // endregion


    // region Initialization

    init {
        refreshContent()
    }

    // endregion


    // region Adapter overrides

    final override fun getItemCount(): Int = currentContent.size

    final override fun getItemViewType(position: Int): Int = currentContent[position].viewType

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val context = parent.context
        val holder = onCreateViewHolder(context, parent, viewType)

        if (::viewHolderConfigs.isInitialized) {
            for (config in viewHolderConfigs) {
                config.onCreateViewHolder(this, holder, context, parent, viewType)
            }
        }

        holder.onCreate()

        return holder
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(position)

        if (::viewHolderConfigs.isInitialized) {
            for (config in viewHolderConfigs) {
                config.onBindViewHolder(this, holder, holder.itemView.context, position)
            }
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty()) {
            holder.partialBind(position, payloads)

            if (::viewHolderConfigs.isInitialized) {
                for (config in viewHolderConfigs) {
                    config.onBindViewHolder(
                        this,
                        holder,
                        holder.itemView.context,
                        position,
                        payloads
                    )
                }
            }

        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (::viewHolderConfigs.isInitialized) {
            for (config in viewHolderConfigs) {
                config.onAdapterAttachedToRecyclerView(this, recyclerView)
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (::viewHolderConfigs.isInitialized) {
            for (config in viewHolderConfigs) {
                config.onAdapterDetachedFromRecyclerView(this, recyclerView)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder) {
        holder.onAttachedToWindow()

        if (::viewHolderConfigs.isInitialized) {
            for (config in viewHolderConfigs) {
                config.onAttachViewHolder(this, holder, holder.itemView.context)
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: BaseViewHolder) {
        holder.onDetachedFromWindow()

        if (::viewHolderConfigs.isInitialized) {
            for (config in viewHolderConfigs) {
                config.onDetachViewHolder(this, holder, holder.itemView.context)
            }
        }
    }

    // endregion


    // region ViewHolders

    private lateinit var viewHolderConfigs: ArrayList<ViewHolderConfig>

    fun addViewHolderConfig(config: ViewHolderConfig) {
        if (!::viewHolderConfigs.isInitialized) {
            viewHolderConfigs = ArrayList()
        }

        viewHolderConfigs.add(config)
    }

    abstract fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): BaseViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun onCreate() {}

        open fun bind(adapterPosition: Int) {}

        open fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            bind(adapterPosition)
        }

        open fun onAttachedToWindow() {}
        open fun onDetachedFromWindow() {}
    }

    abstract inner class SectionViewHolder(itemView: View) : BaseViewHolder(itemView) {

        final override fun bind(adapterPosition: Int) {
            val wrapper = currentContent[adapterPosition]

            bind(adapterPosition, wrapper.sectionKey as TSectionKey)
        }

        abstract fun bind(adapterPosition: Int, sectionKey: TSectionKey)

        final override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            val wrapper: Wrapper<TItem, TSectionKey> = currentContent[adapterPosition]

            partialBind(adapterPosition, wrapper.sectionKey as TSectionKey, payloads)
        }

        open fun partialBind(
            adapterPosition: Int,
            sectionKey: TSectionKey,
            payloads: MutableList<Any>,
        ) {
            bind(adapterPosition, sectionKey)
        }

        protected fun getSectionKey(): TSectionKey? {
            return if (bindingAdapterPosition >= 0) currentContent[bindingAdapterPosition].sectionKey else null
        }
    }

    abstract inner class SectionItemViewHolder(itemView: View) : BaseViewHolder(itemView) {

        final override fun bind(adapterPosition: Int) {
            val wrapper = currentContent[adapterPosition]
            val item = wrapper.item
                ?: throw IllegalArgumentException("Item was null for wrapper ($wrapper) bound to SectionItemViewHolder at position: $adapterPosition")

            bind(adapterPosition, wrapper.sectionKey as TSectionKey, item)
        }

        abstract fun bind(adapterPosition: Int, sectionKey: TSectionKey, item: TItem)

        final override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            val wrapper = currentContent[adapterPosition]
            val item = wrapper.item
                ?: throw IllegalArgumentException("Item was null for wrapper ($wrapper) bound to SectionItemViewHolder at position: $adapterPosition")

            partialBind(adapterPosition, wrapper.sectionKey as TSectionKey, item, payloads)
        }

        open fun partialBind(
            adapterPosition: Int,
            sectionKey: TSectionKey,
            item: TItem,
            payloads: MutableList<Any>,
        ) {
            bind(adapterPosition, sectionKey, item)
        }

        protected fun getItem(): TItem? = getItem(bindingAdapterPosition)
    }

    // endregion


    // region Logging

    fun state(): String {
        val sb = StringBuilder()
            .appendLine("Sectioning adapter state ($name)")
            .appendLine("- content size: ${content.size}")
            .appendLine("- submitted size: ${currentContent.size}")
            .appendLine("- section count: ${sections.size}")

        for (section in sections) {
            sb.appendLine(" - section: ${section.key}")

            for (item in section.items) {
                sb.appendLine("  - item: $item")
            }
        }

        sb.appendLine("Content:")

        for (item in content) {
            sb.appendLine("- $item")
        }

        return sb.toString()
    }

    // endregion
}
