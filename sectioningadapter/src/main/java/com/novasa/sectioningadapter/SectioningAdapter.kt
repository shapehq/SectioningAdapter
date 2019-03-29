package com.novasa.sectioningadapter

import android.os.Handler
import android.view.View
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class SectioningAdapter<TItem : Any, TSectionKey : Any> :
    RecyclerView.Adapter<SectioningAdapter.ViewHolder>() {

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
        AsyncListDiffer<Wrapper<TItem>>(this, differItemCallback)
    }

    open fun areItemsTheSame(item1: TItem, item2: TItem) = item1 == item2

    open fun areContentsTheSame(item1: TItem, item2: TItem): Boolean = true

    // endregion


    // region Init

    private val content = ArrayList<Wrapper<TItem>>()
    private val globalHeaders = ArrayList<HeaderFooter>()
    private val globalFooters = ArrayList<HeaderFooter>()

    private val sections = ArrayList<Section>()
    private val sectionsMap = HashMap<TSectionKey, Section>()

    init {
        Handler().post {
            if (sections.isEmpty() && showNoContent() && !noContentVisible) {
                content.add(globalHeaderCount, Wrapper(NoContent))
                noContentVisible = true
                submitUpdate()
            }
        }
    }

    // endregion


    // region Content

    /**
     * The main update function of the adapter. When this is called, the items will be sorted into sections, according to the result of [getSectionKeyForItem].
     * @param items
     */
    fun setItems(items: List<TItem>) {

        sections.forEach { section ->
            section.items.clear()
        }

        addItems(items)
    }

    fun addItems(items: List<TItem>) {
        items.forEach { item ->
            val key = getSectionKeyForItem(item)
            val section = sectionsMap.getOrPut(key) {
                Section(key).apply {
                    headerCount = getHeaderCountForSectionKey(key)
                    require(headerCount >= 0) {
                        "Section header count must be >= 0"
                    }

                    footerCount = getFooterCountForSectionKey(key)
                    require(footerCount >= 0) {
                        "Section footer count must be >= 0"
                    }

                    sections.add(this)
                }
            }

            section.items.add(item)
        }

        // Purge empty sections
        for (i in sections.size - 1 downTo 0) {
            val section = sections[i]
            if (section.itemCount == 0) {
                sections.removeAt(i)
                sectionsMap.remove(section.key)
            }
        }

        if (sortSections()) {
            sections.sortWith(sectionComparator)
        }

        var p0 = globalHeaderCount

        sections.forEachIndexed { index, section ->
            if (sortItemsInSection(section.key)) {
                section.items.sortWith(itemComparator(section.key))
            }

            section.index = index
            section.adapterPosition = p0

            p0 += section.size
        }

        sectionsSize = p0 - globalHeaderCount

        refreshContent()
    }

    /**
     * Get the item at a given adapter position.
     * @param adapterPosition The absolute adapter position.
     * @return The item at the given adapter position, or null if there is no item at the position.
     */
    fun getItem(adapterPosition: Int): TItem? {
        val section = findSectionForAdapterPosition(adapterPosition)
        return section.items[adapterPosition - section.adapterPosition - section.headerCount]
    }

    /**
     * Iterate over all items currently displayed in the adapter
     *
     * @param action Lambda function supplying the item, its associated section key, and its current adapter position.
     *
     * If the section is currently collapsed, the adapter position will be -1.
     */
    fun forEach(action: (item: TItem, sectionKey: TSectionKey, adapterPosition: Int) -> Unit) {
        sections.forEach { section ->
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
        sections.forEach { section ->
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

        sections.forEach { section ->
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
        sections.forEach { section ->
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

    private fun refreshContent() {
        with(content) {
            clear()

            globalHeaders.forEach {
                add(Wrapper(it))
            }

            if (sectionsSize > 0) {
                sections.forEach { section ->
                    section.headers.forEach {
                        add(Wrapper(it))
                    }

                    if (!section.collapsed) {
                        section.items.forEach {
                            add(Wrapper(it))
                        }
                    }

                    section.footers.forEach {
                        add(Wrapper(it))
                    }
                }

                noContentVisible = false

            } else if (showNoContent()) {
                add(Wrapper(NoContent))
                noContentVisible = true
            }

            globalFooters.forEach {
                add(Wrapper(it))
            }
        }

        submitUpdate()
    }

    private fun submitUpdate() {
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
    var sectionsSize = 0
        private set

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
     * @param key The section key
     * @param adapterPosition The current adapter position of the view that is being determined.
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getItemViewTypeForSection(key: TSectionKey, adapterPosition: Int): Int = super.getItemViewType(adapterPosition)

    /**
     * Override to display header views before the given section.
     *
     * If this is overridden, [getHeaderViewTypeForSection] must also be overridden.
     * @param key The section key
     * @return The number of headers that the given section should display. Must be >= 0.
     * @see [getHeaderViewTypeForSection]
     */
    protected open fun getHeaderCountForSectionKey(key: TSectionKey) = 0

    /**
     * Override to display footer views after the given section.
     *
     * If this is overridden, [getFooterViewTypeForSection] must also be overridden.
     * @param key The section key
     * @return The number of footers that the given section should display. Must be >= 0.
     * @see [getFooterViewTypeForSection]
     */
    protected open fun getFooterCountForSectionKey(key: TSectionKey) = 0

    /**
     * If [getHeaderCountForSectionKey] returns > 0, this must also be overridden, to determine the view types of the section headers.
     * @param key The section key
     * @param headerIndex The index of the header. This is relative to the section, so if all sections have only one section header, this will always be 0.
     * @see [getHeaderCountForSectionKey]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getHeaderViewTypeForSection(key: TSectionKey, headerIndex: Int): Int =
        throw NotImplementedError("Must override getHeaderViewTypeForSection() if getHeaderCountForSectionKey() returns > 0")

    /**
     * If [getFooterCountForSectionKey] returns > 0, this must also be overridden, to determine the view types of the section footers.
     * @param key The section key
     * @param footerIndex The index of the footer. This is relative to the section, so if all sections have only one section footer, this will always be 0.
     * @see [getFooterCountForSectionKey]
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [onCreateViewHolder]
     */
    protected open fun getFooterViewTypeForSection(key: TSectionKey, footerIndex: Int): Int =
        throw NotImplementedError("Must override getFooterViewTypeForSection() if getFooterCountForSectionKey() returns > 0")

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
     * Removes all sections (and items) from the adapter.
     */
    fun removeAllSections() {

        if (sectionsSize > 0) {
            removeContentRange(globalHeaderCount, sectionsSize)

            sectionsSize = 0
            sections.clear()
            sectionsMap.clear()

            if (showNoContent()) {
                content.add(globalHeaderCount, Wrapper(NoContent))
                noContentVisible = true
            }

            submitUpdate()
        }
    }

    /**
     * Removes the section associated with the given section key, and all items associated with it.
     */
    fun removeSection(key: TSectionKey) {
        sectionsMap[key]?.let { section ->
            removeSection(section)
        }
    }

    /**
     * Returns the items associated with the given section key, in the order they are presented in the adapter.
     */
    fun getItemsInSection(sectionKey: TSectionKey): List<TItem> = getSectionForKey(sectionKey).items

    /**
     * Find the key for the section associated with the given adapter position.
     * @param position The adapter position. Must be between [globalHeaderCount] and [globalHeaderCount] + [sectionsSize] - 1.
     */
    fun findSectionKeyForAdapterPosition(position: Int): TSectionKey = findSectionForAdapterPosition(position).key

    /**
     * Get all section keys in the adapter, in the order they are currently being displayed.
     */
    fun getAllSectionKeys(): List<TSectionKey> = sections.map { section ->
        section.key
    }

    /**
     * Collapse the section associated with the given section key.
     *
     * All item views in the section will be removed from the adapter. Section headers and footers will remain.
     *
     * Has no effect if the section is already collapsed.
     * @see [expandSection]
     */
    fun collapseSection(sectionKey: TSectionKey): Boolean = collapseSection(getSectionForKey(sectionKey))

    /**
     * Expand the section associated with the given section key.
     *
     * Has no effect if the section is already expanded.
     *
     * This is the default state.
     * @see [collapseSection]
     */
    fun expandSection(sectionKey: TSectionKey): Boolean = expandSection(getSectionForKey(sectionKey))

    /**
     * Expand a section if is collapsed, and vice versa.
     * @see [collapseSection]
     * @see [expandSection]
     */
    fun toggleExpandSection(sectionKey: TSectionKey): Boolean {
        val section = getSectionForKey(sectionKey)
        return if (section.collapsed) {
            expandSection(section)
        } else {
            !collapseSection(section)
        }
    }

    /**
     * Collapse all sections in the adapter.
     * @see [collapseSection]
     * @see [expandSection]
     */
    fun collapseAllSections() {
        sections.forEach { section ->
            collapseSection(section)
        }
    }

    /**
     * Expand all sections in the adapter.
     * @see [collapseSection]
     * @see [expandSection]
     */
    fun expandAllSections() {
        sections.forEach { section ->
            expandSection(section)
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

    private fun getSectionForKey(key: TSectionKey): Section {
        return sectionsMap.getOrElse(key) {
            throw IllegalArgumentException("No section found for section key: $key")
        }
    }

    private fun findSectionForAdapterPosition(position: Int): Section {
        require(position in globalHeaderCount until globalHeaderCount + sectionsSize) {
            "Can't find section key. Invalid adapter position: $position"
        }
        sections.forEach { section ->
            if (position < section.adapterPosition + section.size) {
                return section
            }
        }

        throw IllegalStateException()
    }

    private fun removeSection(section: Section) {

        removeContentRange(section.adapterPosition, section.size)

        sections.removeAt(section.index)
        sectionsMap.remove(section.key)

        offsetSectionPositions(-section.size, section.index)
        offsetSectionIndices(-1, section.index)

        sectionsSize -= section.size
        if (sectionsSize == 0 && showNoContent()) {
            content.add(globalHeaderCount, Wrapper(NoContent))
            noContentVisible = true
        }

        submitUpdate()
    }

    private fun collapseSection(section: Section): Boolean {
        if (!section.collapsed) {
            section.collapsed = true

            removeContentRange(section.adapterPosition + section.headerCount, section.itemCount)
            offsetSectionPositions(-section.itemCount, section.index + 1)
            sectionsSize -= section.itemCount

            submitUpdate()
            return true
        }

        return false
    }

    private fun expandSection(section: Section): Boolean {
        if (section.collapsed) {
            section.collapsed = false

            val p0 = section.adapterPosition + section.headerCount
            for (i in 0 until section.itemCount) {
                content.add(i + p0, Wrapper(section.items[i]))
            }
            offsetSectionPositions(section.itemCount, section.index + 1)
            sectionsSize += section.itemCount

            submitUpdate()
            return true
        }

        return false
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

        var index = -1
        var adapterPosition = -1

        var collapsed = false

        val itemCount
            get() = items.size

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

        val size
            get() = (if (collapsed) 0 else itemCount) + headerCount + footerCount
    }

    // endregion


    // region Headers / Footers

    /** The current number of global headers. */
    val globalHeaderCount: Int
        get() = globalHeaders.size

    /** The current number of global footers. */
    val globalFooterCount: Int
        get() = globalFooters.size

    val nonItemContentSize: Int
        get() = if (noContentVisible) 1 else 0

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
        content.add(globalHeaderCount + sectionsSize + nonItemContentSize + position, Wrapper(footer))

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
        content.removeAt(globalHeaderCount + sectionsSize + nonItemContentSize + position)

        submitUpdate()
    }

    private class HeaderFooter(val viewType: Int, val key: Any, var position: Int) : NonItem {

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
     * If this returns true. [getNoContentViewType] must also be overridden.
     * @return true if the adapter should display a no content view. Default = false.
     */
    protected open fun showNoContent(): Boolean = false

    /**
     * If [showNoContent] returns true, this must be overridden.
     * @return The view type of the no content view.
     * @see [onCreateViewHolder]
     */
    protected open fun getNoContentViewType(): Int {
        throw NotImplementedError("Must override getNoContentViewType() if showNoContent == true")
    }

    private var noContentVisible = false

    private val noContentPosition: Int
        get() = if (noContentVisible) globalHeaderCount else -1


    private object NoContent : NonItem {
        override fun isEqualTo(other: NonItem): Boolean = other == this // always only 1
        override fun toString(): String = "No content"
    }

    // endregion


    // region Adapter overrides

    final override fun getItemCount(): Int = content.size

    final override fun getItemViewType(position: Int): Int {
        return when (position) {
            in 0 until globalHeaderCount -> globalHeaders[position].viewType
            noContentPosition -> getNoContentViewType()
            in globalHeaderCount + sectionsSize + nonItemContentSize until itemCount -> globalFooters[position - globalHeaderCount - sectionsSize - nonItemContentSize].viewType
            else -> {
                val section = findSectionForAdapterPosition(position)
                val sectionPosition = position - section.adapterPosition

                return when {
                    sectionPosition < section.headerCount -> section.headers[sectionPosition].viewType
                    sectionPosition < section.headerCount + section.itemCount -> getItemViewTypeForSection(section.key, position)
                    sectionPosition < section.size -> section.footers[sectionPosition - section.headerCount - section.itemCount].viewType
                    else -> throw IllegalArgumentException() // Can't happen
                }
            }
        }
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

    abstract inner class SectionItemViewHolder(itemView: View) : ViewHolder(itemView) {

        final override fun bind(adapterPosition: Int) {
            val section = findSectionForAdapterPosition(adapterPosition)
            bind(adapterPosition, adapterPosition - section.adapterPosition, section.key)
        }

        abstract fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey)

        final override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            val section = findSectionForAdapterPosition(adapterPosition)
            partialBind(adapterPosition, adapterPosition - section.adapterPosition, section.key, payloads)
        }

        open fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey, payloads: MutableList<Any>) {
            bind(adapterPosition, sectionPosition, sectionKey)
        }

        protected fun getSectionKey(): TSectionKey? {
            return if (adapterPosition >= 0) findSectionKeyForAdapterPosition(adapterPosition) else null
        }
    }

    abstract inner class ItemViewHolder(itemView: View) : SectionItemViewHolder(itemView) {

        final override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey) {
            val section = getSectionForKey(sectionKey)
            val sectionItemPosition = sectionPosition - section.headerCount
            val item = section.items[sectionItemPosition]
            bind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item)
        }

        abstract fun bind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: TSectionKey, item: TItem)

        final override fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey, payloads: MutableList<Any>) {
            val section = getSectionForKey(sectionKey)
            val sectionItemPosition = sectionPosition - section.headerCount
            val item = section.items[sectionItemPosition]
            partialBind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item, payloads)
        }

        open fun partialBind(adapterPosition: Int, sectionPosition: Int, sectionItemPosition: Int, sectionKey: TSectionKey, item: TItem, payloads: MutableList<Any>) {
            bind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item)
        }

        protected fun getItem(): TItem? = if (adapterPosition >= 0) getItem(adapterPosition) else null
    }

    // endregion
}
