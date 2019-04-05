package com.novasa.sectioningadapter

import android.view.View
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class SectioningAdapter<TItem : Any, TSectionKey : Any> : RecyclerView.Adapter<SectioningAdapter.ViewHolder>() {

    object GlobalSectionKey

    // Diff

    private class Wrapper<TItem : Any> {

        val item: TItem?
        val nonItem: NonItem?

        constructor(item: TItem) {
            this.item = item
            this.nonItem = null
        }

        constructor(nonItem: NonItem) {
            this.item = null
            this.nonItem = nonItem
        }

        override fun toString(): String = "${item ?: nonItem}"
    }

    private interface NonItem {
        val viewType: Int
        fun isEqualTo(other: NonItem): Boolean
    }

    private val differItemCallback = object : DiffUtil.ItemCallback<Wrapper<TItem>>() {

        override fun areItemsTheSame(oldItem: Wrapper<TItem>, newItem: Wrapper<TItem>): Boolean {
            return com.novasa.core.let(oldItem.item, newItem.item) { o, n ->
                areItemsTheSame(o, n)

            } ?: com.novasa.core.let(oldItem.nonItem, newItem.nonItem) { o, n ->
                o.isEqualTo(n)

            } ?: false
        }

        override fun areContentsTheSame(oldItem: Wrapper<TItem>, newItem: Wrapper<TItem>): Boolean {
            return com.novasa.core.let(oldItem.item, newItem.item) { o, n ->
                areContentsTheSame(o, n)

            } ?: true
        }
    }

    private val differ: AsyncListDiffer<Wrapper<TItem>> by lazy {
        AsyncListDiffer<Wrapper<TItem>>(this, differItemCallback).apply {
            addListListener { _, _ ->
                onContentChanged()
            }
        }
    }

    open fun areItemsTheSame(item1: TItem, item2: TItem) = item1 == item2

    open fun areContentsTheSame(item1: TItem, item2: TItem): Boolean = true

    // endregion


    // region Content

    private val content = ArrayList<Wrapper<TItem>>()
    private val globalHeaders = ArrayList<HeaderFooter>()
    private val globalFooters = ArrayList<HeaderFooter>()

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
    fun setItems(items: List<TItem>) {
        for (section in sections) {
            section.items.clear()
        }

        addItems(items)
    }

    /**
     * Add items to the current data set.
     *
     * Similar to [setItems], but does not remove current items first.
     * @param items
     */
    fun addItems(items: List<TItem>) {
        for (item in items) {
            val key = getSectionKeyForItem(item)
            val section = sectionsMap.getOrPut(key) {
                createSection(key)
            }

            section.items.add(item)
        }

        updateSections()
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

    /**
     * Get the item at a given adapter position.
     * @param adapterPosition The absolute adapter position.
     * @return The item at the given adapter position, or null if there is no item at the position.
     */
    fun getItem(adapterPosition: Int): TItem? = findSectionForAdapterPosition(adapterPosition)?.let { section ->
        return section.items.getOrNull(adapterPosition - section.adapterPosition - section.headerCount)
    }

    /**
     * Iterate over all items currently in the adapter
     *
     * @param action Lambda function supplying the item, its associated section key, and its current adapter position.
     *
     * If the section is currently collapsed, the adapter position will be -1.
     */
    fun forEach(action: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Unit) {
        for (section in sections) {
            section.items.forEachIndexed { index, item ->
                val adapterPosition = if (section.collapsed) -1 else section.adapterPosition + section.headerCount + index
                action(item, section.key, adapterPosition)
            }
        }
    }

    /**
     * Searches all items in the adapter according to a predicate.
     *
     * @param predicate Predicate supplying the item, its associated section key, and its current adapter position.
     *
     * If the section is currently collapsed, the adapter position will be -1.
     * @return The first item for which the predicate returns true, or null if the predicate never returns true.
     */
    fun getItemIf(predicate: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Boolean): TItem? {
        for (section in sections) {
            section.items.forEachIndexed { index, item ->
                val adapterPosition = if (section.collapsed) -1 else section.adapterPosition + section.headerCount + index
                if (predicate(item, section.key, adapterPosition)) {
                    return item
                }
            }
        }
        return null
    }

    /**
     * Searches all items in the adapter according to a predicate.
     *
     * @param predicate Predicate supplying the item, its associated section key, and its current adapter position.
     *
     * If the section is currently collapsed, the adapter position will be -1.
     * @return A list of all items for which the predicate returns true.
     */
    fun getItemsIf(predicate: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Boolean): List<TItem> {
        val result = ArrayList<TItem>()

        for (section in sections) {
            section.items.forEachIndexed { index, item ->
                val adapterPosition = if (section.collapsed) -1 else section.adapterPosition + section.headerCount + index
                if (predicate(item, section.key, adapterPosition)) {
                    result.add(item)
                }
            }
        }

        return result
    }

    /**
     * Get all items currently being displayed in the adapter, in the order they are being displayed in.
     *
     * This includes items in collapsed sections.
     */
    fun getAllItems(): List<TItem> = sections.flatMap { section ->
        section.items
    }

    /**
     * Find the adapter position for the given item.
     *
     * @param item The item to search for.
     * @return The adapter position of the item if found, or -1 if the item is not in the adapter.
     */
    fun findAdapterPositionForItem(item: TItem): Int {
        for (section in sections) {
            section.items.forEachIndexed { index, sItem ->
                if (areItemsTheSame(item, sItem)) {
                    return section.adapterPosition + section.headerCount + index
                }
            }
        }

        return -1
    }

    fun notifyItemChanged(item: TItem, payload: Any?) {
        val pos = findAdapterPositionForItem(item)
        if (pos >= 0) {
            notifyItemChanged(pos, payload)
        }
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
                headerCount = getHeaderCountForSectionKey(key)
                require(headerCount >= 0) {
                    "Section header count must be >= 0"
                }

                footerCount = getFooterCountForSectionKey(key)
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

            for (header in globalHeaders) {
                add(Wrapper(header))
            }

            globalNoContentVisible = false
            if (globalSectionsSize > 0) {
                for (section in sections) {
                    with(section) {
                        for (footer in headers) {
                            add(Wrapper(footer))
                        }

                        if (!collapsed) {
                            if (isEmpty && emptyContentCount > 0) {
                                for (item in emptyContent) {
                                    add(Wrapper(item))
                                }

                            } else {
                                for (item in items) {
                                    add(Wrapper(item))
                                }
                            }
                        }

                        for (footer in footers) {
                            add(Wrapper(footer))
                        }
                    }
                }

            } else if (showGlobalNoContent()) {
                add(Wrapper(getGlobalNoContent()))
                globalNoContentVisible = true
            }

            for (footer in globalFooters) {
                add(Wrapper(footer))
            }
        }

        submitUpdate()
    }

    private fun submitUpdate() {
        onContentWillChange()
        differ.submitList(ArrayList<Wrapper<TItem>>(content))
    }

    private fun removeContentRange(p0: Int, size: Int) {
        require(size >= 0 && p0 >= 0 && p0 + size <= content.size) {
            "Tried to remove content range outside bounds ($p0 + $size, size = ${content.size})"
        }

        for (i in 0 until size) {
            content.removeAt(p0)

        }
    }

    // endregion


    // region Sections

    /**
     * The number of sections currently in the adapter.
     *
     * This includes collapsed sections.
     */
    val sectionCount = sections.size

    /**
     * The current size of all the sections, including any section headers and footers.
     *
     * This excludes any global headers or footers.
     */
    private var globalSectionsSize = 0

    /**
     * The main sectioning function of the adapter.
     *
     * This is called for all items in the adapter, when they are supplied, to determine which section the item will belong to.
     * @param item Any and all items in the adapter.
     * @return A unique key that will represent a section in the adapter.
     */
    protected abstract fun getSectionKeyForItem(item: TItem): TSectionKey

    /**
     * Override to determine view types for the given section, as supplied in [onCreateViewHolder].
     *
     * Default is 0.
     * @param sectionKey The section sectionKey
     * @param adapterPosition The current adapter position of the view that is being determined.
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getItemViewTypeForSection(sectionKey: TSectionKey, adapterPosition: Int): Int = super.getItemViewType(adapterPosition)

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
    protected open fun getHeaderCountForSectionKey(sectionKey: TSectionKey) = 0

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
    protected open fun getFooterCountForSectionKey(sectionKey: TSectionKey) = 0

    /**
     * If [getHeaderCountForSectionKey] returns > 0, this must also be overridden, to determine the view types of the section headers.
     * @param sectionKey The section sectionKey
     * @param headerIndex The index of the header. This is relative to the section, so if all sections have only one section header, this will always be 0.
     * @see [getHeaderCountForSectionKey]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getHeaderViewTypeForSection(sectionKey: TSectionKey, headerIndex: Int): Int =
        throw NotImplementedError("Must override getHeaderViewTypeForSection() if getHeaderCountForSectionKey() returns > 0")

    /**
     * If [getFooterCountForSectionKey] returns > 0, this must also be overridden, to determine the view types of the section footers.
     * @param sectionKey The section sectionKey
     * @param footerIndex The index of the footer. This is relative to the section, so if all sections have only one section footer, this will always be 0.
     * @see [getFooterCountForSectionKey]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getFooterViewTypeForSection(sectionKey: TSectionKey, footerIndex: Int): Int =
        throw NotImplementedError("Must override getFooterViewTypeForSection() if getFooterCountForSectionKey() returns > 0")

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
     * @param sectionKey The section key.
     */
    protected open fun sortItemsInSection(sectionKey: TSectionKey): Boolean = false

    /**
     * If [sortItemsInSection] returns true for any section, this must also be overridden to determine the order of the items.
     *
     * Uses a [Comparator] for sorting.
     * @param sectionKey The section key that the items are associated with.
     * @see [Comparator]
     */
    protected open fun compareItems(sectionKey: TSectionKey, item1: TItem, item2: TItem): Int =
        throw NotImplementedError("Must override compareItems() if sortItemsInSection() returns true")

    /**
     * Get The current number of items in the section.
     * @param sectionKey The section key.
     * @return The current number of items in the section associated with the section key, or -1 if the section does not exist.
     */
    fun getItemCountForSection(sectionKey: TSectionKey): Int = sectionsMap[sectionKey]?.itemCount ?: -1

    /**
     * @param sectionKey The section key.
     * @return true if the section associated with the given section key contains no items, or if it does not exist. False otherwise.
     */
    fun isSectionEmpty(sectionKey: TSectionKey): Boolean = getItemCountForSection(sectionKey) <= 0

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


    /**
     * Removes all sections (and items) from the adapter.
     */
    fun removeAllSections() {

        if (globalSectionsSize > 0) {
            removeContentRange(globalHeaderCount, globalSectionsSize)

            globalSectionsSize = 0
            sections.clear()
            sectionsMap.clear()

            if (showGlobalNoContent()) {
                content.add(globalHeaderCount, Wrapper(getGlobalNoContent()))
                globalNoContentVisible = true
            }

            submitUpdate()
        }
    }

    /**
     * Removes the section associated with the given section key, and all items associated with it.
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

    /**
     * Similar to [removeSection], but does not remove the section if it is static.
     *
     * If the section is static, this behaves identically to the former.
     */
    fun removeItemsInSection(key: TSectionKey) {
        sectionsMap[key]?.let { section ->
            removeItemsInSection(section)
        }
    }

    /**
     * @return The items associated with the given section key, in the order they are presented in the adapter.
     *
     * If the section is not found, an empty list is returned.
     */
    fun getItemsInSection(sectionKey: TSectionKey): List<TItem> = getSectionForKey(sectionKey)?.items ?: emptyList()

    /**
     * Find the key for the section associated with the given adapter position.
     * @param position The adapter position. Must be between [globalHeaderCount] and [globalHeaderCount] + [globalSectionsSize] - 1.
     */
    fun findSectionKeyForAdapterPosition(position: Int): TSectionKey? = findSectionForAdapterPosition(position)?.key

    /**
     * Get all section keys in the adapter, in the order they are currently being displayed.
     */
    fun getAllSectionKeys(): List<TSectionKey> = sections.map { section ->
        section.key
    }

    /**
     * Get the current index of the supplied section key, or -1 if the section is not in the adapter.
     */
    fun getSectionIndex(sectionKey: TSectionKey): Int = sectionsMap[sectionKey]?.index ?: -1

    /** Determines if newly added sections are collapsed when added.
     *
     * Default = false
     */
    var collapseNewSections = false

    /**
     * Collapse the section associated with the given section key.
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
     * Expand the section associated with the given section key.
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
        if (!sections.isEmpty()) {
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

    private val sectionComparator: Comparator<Section> by lazy {
        Comparator<Section> { o1, o2 ->
            compareSectionKeys(o1.key, o2.key)
        }
    }

    private val itemComparators: HashMap<TSectionKey, Comparator<TItem>> by lazy {
        HashMap<TSectionKey, Comparator<TItem>>()
    }

    private fun itemComparator(key: TSectionKey): Comparator<TItem> = itemComparators.getOrPut(key) {
        Comparator { o1, o2 ->
            compareItems(key, o1, o2)
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
        if (globalSectionsSize == 0 && showGlobalNoContent()) {
            content.add(globalHeaderCount, Wrapper(getGlobalNoContent()))
            globalNoContentVisible = true
        }

        submitUpdate()
    }

    private fun removeItemsInSection(section: Section) {
        if (!section.static) {
            removeSection(section)

        } else {
            if (!section.isEmpty) {
                val size = section.itemSize
                removeContentRange(section.adapterPosition + section.headerCount, size)
                offsetSectionPositions(-size, section.index)
                globalSectionsSize -= size

                section.items.clear()

                submitUpdate()
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
                    content.add(i + p0, Wrapper(section.items[i]))
                }

            } else {
                size = section.emptyContentCount
                for (i in 0 until size) {
                    content.add(i + p0, Wrapper(section.emptyContent[i]))
                }
            }

            offsetSectionPositions(size, section.index + 1)
            globalSectionsSize += size

            submitUpdate()
        }
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
        lateinit var headers: ArrayList<HeaderFooter>
        lateinit var footers: ArrayList<HeaderFooter>
        lateinit var emptyContent: ArrayList<NonItem>

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
            get() = headers.size
            set(value) {
                headers = ArrayList(value)
                for (i in 0 until value) {
                    val viewType = getHeaderViewTypeForSection(key, i)
                    headers.add(HeaderFooter(viewType, key, i))
                }
            }

        var footerCount: Int
            get() = footers.size
            set(value) {
                footers = ArrayList(value)
                for (i in 0 until value) {
                    val viewType = getFooterViewTypeForSection(key, i)
                    footers.add(HeaderFooter(viewType, key, i))
                }
            }

        var showNoContent: Boolean = false
            set(value) {
                if (value != field) {
                    field = value
                    if (value) {
                        insertEmptyContent(NoContent(getNoContentViewTypeForStaticSection(key)), 0)
                    } else {
                        removeEmptyContent(0)
                    }
                }
            }

        private fun insertEmptyContent(item: NonItem, index: Int) {
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

        override fun toString(): String = "Index: $index, position: $adapterPosition, itemCount: $itemCount, size: $size, collapsed: $collapsed"
    }

    // endregion


    // region Headers / Footers

    /** The current number of global headers. */
    val globalHeaderCount: Int
        get() = globalHeaders.size

    /** The current number of global footers. */
    val globalFooterCount: Int
        get() = globalFooters.size

    val globalNonItemContentSize: Int
        get() = if (globalNoContentVisible) 1 else 0

    /**
     * Insert a global header in the adapter.
     *
     * Global headers are displayed before the first section.
     *
     * @param position The global header position. This is relative to any current global headers. Must be in the range 0 - [globalHeaderCount] (inclusive)]
     * @param viewType The view type of the global header, when supplied in [onCreateViewHolder]
     * @see [onCreateViewHolder]
     */
    fun insertGlobalHeader(position: Int, viewType: Int) {
        require(position in 0..globalHeaderCount) {
            "Failed to insert global header. Invalid position: $position. Current global header count was $globalHeaderCount"
        }

        val header = HeaderFooter(viewType, GlobalSectionKey, position)

        globalHeaders.add(position, header)
        content.add(position, Wrapper(header))

        offsetSectionPositions(1)

        submitUpdate()
    }

    /**
     * Remove any currently displayed global header.
     *
     * @param position The global header position. Must be in the range 0 - [globalHeaderCount] (exclusive)
     */
    fun removeGlobalHeader(position: Int) {
        require(position in 0 until globalHeaderCount) {
            "Failed to remove global header. Invalid position: $position. Current global header count was $globalHeaderCount"
        }

        globalHeaders.removeAt(position)
        content.removeAt(position)

        offsetSectionPositions(-1)

        submitUpdate()
    }

    /**
     * Insert a global footer in the adapter.
     *
     * Global footers are displayed after the final section.
     *
     * @param position The global footer position. This is relative to any current global footers. Must be in the range 0 - [globalFooterCount] (inclusive)]
     * @param viewType The view type of the global footer, when supplied in [onCreateViewHolder]
     * @see [onCreateViewHolder]
     */
    fun insertGlobalFooter(position: Int, viewType: Int) {
        require(position in 0..globalFooterCount) {
            "Failed to insert global footer. Invalid position: $position. Current global footer count was $globalFooterCount"
        }

        val footer = HeaderFooter(viewType, GlobalSectionKey, position)

        globalFooters.add(position, footer)
        content.add(globalHeaderCount + globalSectionsSize + globalNonItemContentSize + position, Wrapper(footer))

        submitUpdate()
    }

    /**
     * Remove any currently displayed global footer.
     *
     * @param position The global footer. Must be in the range 0 - [globalFooterCount] (exclusive)
     */
    fun removeGlobalFooter(position: Int) {
        require(position in 0 until globalFooterCount) {
            "Failed to remove global footer. Invalid position: $position. Current global footer count was $globalFooterCount"
        }

        globalFooters.removeAt(position)
        content.removeAt(globalHeaderCount + globalSectionsSize + globalNonItemContentSize + position)

        submitUpdate()
    }

    private class HeaderFooter(override val viewType: Int, val key: Any, var position: Int) : NonItem {

        override fun isEqualTo(other: NonItem) = other is HeaderFooter &&
                other.key == this.key &&
                other.position == this.position

        override fun toString(): String = "HeaderFooter key=$key, pos=$position"
    }

    // endregion


    // region No content

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

    private var globalNoContentVisible = false

    private val globalNoContentPosition: Int
        get() = if (globalNoContentVisible) globalHeaderCount else -1

    private var noContent: NoContent? = null
    private fun getGlobalNoContent(): NoContent = getGlobalNoContentViewType().let { viewType ->
        noContent?.also {
            it.viewType = viewType
        } ?: NoContent(viewType).also {
            noContent = it
        }
    }

    private class NoContent(override var viewType: Int) : NonItem {
        override fun isEqualTo(other: NonItem): Boolean = other == this // always only 1
        override fun toString(): String = "No content"
    }

    // endregion


    // region Adapter overrides

    final override fun getItemCount(): Int = content.size

    final override fun getItemViewType(position: Int): Int = when (position) {

        // Global headers
        in 0 until globalHeaderCount -> globalHeaders[position].viewType

        // Global no content
        globalNoContentPosition -> getGlobalNoContentViewType()

        // Global footer
        in globalHeaderCount + globalSectionsSize + globalNonItemContentSize until itemCount -> globalFooters[position - globalHeaderCount - globalSectionsSize - globalNonItemContentSize].viewType

        // Section
        else -> findSectionForAdapterPosition(position)?.run {

            // Local section position
            val sectionPosition = position - adapterPosition

            when (sectionPosition) {

                // Section headers
                in 0 until headerCount -> headers[sectionPosition].viewType

                // Section item
                in headerCount until headerCount + itemSize -> getItemViewTypeForSection(key, position)

                // Section empty content (only > 0 if itemCount == 0)
                in headerCount until headerCount + emptyContentSize -> emptyContent[sectionPosition - headerCount].viewType

                // Section footer
                in headerCount + itemSize + emptyContentSize until size -> footers[sectionPosition - headerCount - itemSize - emptyContentSize].viewType

                // Should never happen
                else -> null
            }
        } ?: throw IllegalStateException("Tried to get view type for invalid adapter position")
    }

    override fun onBindViewHolder(holder: SectioningAdapter.ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onBindViewHolder(holder: SectioningAdapter.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (!payloads.isEmpty()) {
            holder.partialBind(position, payloads)

        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // endregion


    // region ViewHolders

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun bind(adapterPosition: Int) {}

        open fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            bind(adapterPosition)
        }
    }

    abstract inner class SectionViewHolder(itemView: View) : ViewHolder(itemView) {

        final override fun bind(adapterPosition: Int) {
            findSectionForAdapterPosition(adapterPosition)?.let { section ->
                bind(adapterPosition, adapterPosition - section.adapterPosition, section.key)
            }
        }

        abstract fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey)

        final override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            findSectionForAdapterPosition(adapterPosition)?.let { section ->
                partialBind(adapterPosition, adapterPosition - section.adapterPosition, section.key, payloads)
            }
        }

        open fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey, payloads: MutableList<Any>) {
            bind(adapterPosition, sectionPosition, sectionKey)
        }

        protected fun getSectionKey(): TSectionKey? {
            return if (adapterPosition >= 0) findSectionKeyForAdapterPosition(adapterPosition) else null
        }
    }

    abstract inner class ItemViewHolder(itemView: View) : SectionViewHolder(itemView) {

        final override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey) {
            getSectionForKey(sectionKey)?.let { section ->
                val sectionItemPosition = sectionPosition - section.headerCount
                val item = section.items[sectionItemPosition]
                bind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item)
            }
        }

        abstract fun bind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: TSectionKey, item: TItem)

        final override fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey, payloads: MutableList<Any>) {
            getSectionForKey(sectionKey)?.let { section ->
                val sectionItemPosition = sectionPosition - section.headerCount
                val item = section.items[sectionItemPosition]
                partialBind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item, payloads)
            }
        }

        open fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: TSectionKey, item: TItem, payloads: MutableList<Any>) {
            bind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item)
        }

        protected fun getItem(): TItem? = if (adapterPosition >= 0) getItem(adapterPosition) else null
    }

    // endregion
}
