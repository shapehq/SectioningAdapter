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

    open fun areItemsTheSame(oldItem: TItem, newItem: TItem) = oldItem == newItem

    open fun areContentsTheSame(oldItem: TItem, newItem: TItem): Boolean = true

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

    var sectionsSize = 0
        private set

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
                    footerCount = getFooterCountForSectionKey(key)
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
            sections.sortWith(sectionsComparator)
        }

        var p0 = globalHeaderCount

        sections.forEachIndexed { index, section ->
            if (sortItemsInSection(section.key)) {
                section.items.sortWith(itemsComparator)
            }

            section.index = index
            section.adapterPosition = p0

            p0 += section.size
        }

        sectionsSize = p0 - globalHeaderCount

        refreshContent()
    }

    fun getItem(adapterPosition: Int): TItem? {
        val section = findSectionForAdapterPosition(adapterPosition)
        return section.items[adapterPosition - section.adapterPosition - section.headerCount]
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

            } else {
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
        if (p0 + size > content.size) {
            throw IllegalArgumentException("Tried to remove content range outside bounds ($p0 + $size, size = ${content.size})")
        }

        for (i in 0 until size) {
            content.removeAt(p0)
        }
    }

    // endregion


    // region Headers / Footers

    val globalHeaderCount: Int
        get() = globalHeaders.size

    val globalFooterCount: Int
        get() = globalFooters.size

    val nonItemContentSize: Int
        get() = if (noContentVisible) 1 else 0

    fun insertGlobalHeader(position: Int, viewType: Int) {
        if (position < 0 || position > globalHeaderCount) {
            throw IllegalArgumentException("Failed to insert global header. Invalid position: $position. Current global header count was $globalHeaderCount")
        }

        val header = HeaderFooter(viewType, GlobalSectionKey, position)

        globalHeaders.add(position, header)
        content.add(position, Wrapper(header))

        offsetSectionPositions(1)

        submitUpdate()
    }

    fun removeGlobalHeader(position: Int) {
        if (position < 0 || position >= globalHeaderCount) {
            throw IllegalArgumentException("Failed to remove global header. Invalid position: $position. Current global header count was $globalHeaderCount")
        }

        globalHeaders.removeAt(position)
        content.removeAt(position)

        offsetSectionPositions(-1)

        submitUpdate()
    }

    fun insertGlobalFooter(position: Int, viewType: Int) {
        if (position < 0 || position > globalFooterCount) {
            throw IllegalArgumentException("Failed to insert global footer. Invalid position: $position. Current global footer count was $globalFooterCount")
        }

        val footer = HeaderFooter(viewType, GlobalSectionKey, position)

        globalFooters.add(position, footer)
        content.add(globalHeaderCount + sectionsSize + nonItemContentSize + position, Wrapper(footer))

        submitUpdate()
    }

    fun removeGlobalFooter(position: Int) {
        if (position < 0 || position >= globalFooterCount) {
            throw IllegalArgumentException("Failed to remove global footer. Invalid position: $position. Current global footer count was $globalFooterCount")
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

    protected open fun showNoContent(): Boolean = false

    private var noContentVisible = false

    private val noContentPosition: Int
        get() = if (noContentVisible) globalHeaderCount else -1


    private object NoContent : NonItem {
        override fun isEqualTo(other: NonItem): Boolean = other == this // always only 1
        override fun toString(): String = "No content"
    }

    protected open fun getNoContentViewType(): Int {
        throw NotImplementedError("Must override getNoContentViewType() if showNoContent == true")
    }

    // endregion


    // region Overrides

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


    // region Sections

    val sectionCount = sections.size

    private val sectionsComparator: Comparator<Section> by lazy {
        Comparator<Section> { o1, o2 ->
            compareSectionKeys(o1.key, o2.key)
        }
    }

    private val itemsComparator: Comparator<TItem> by lazy {
        Comparator<TItem> { o1, o2 ->
            compareItems(o1, o2)
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

    /** Must return a unique key that will represent a section in the adapter. */
    protected abstract fun getSectionKeyForItem(item: TItem): TSectionKey

    protected open fun getHeaderCountForSectionKey(key: TSectionKey) = 0

    protected open fun getFooterCountForSectionKey(key: TSectionKey) = 0

    protected open fun getItemViewTypeForSection(key: TSectionKey, adapterPosition: Int): Int = 0

    protected open fun getHeaderViewTypeForSection(key: TSectionKey, headerIndex: Int): Int =
        throw NotImplementedError("Must override getHeaderViewTypeForSection() if getHeaderCountForSectionKey() returns > 0")

    protected open fun getFooterViewTypeForSection(key: TSectionKey, footerIndex: Int): Int =
        throw NotImplementedError("Must override getFooterViewTypeForSection() if getFooterCountForSectionKey() returns > 0")

    protected open fun sortSections(): Boolean = false

    protected open fun compareSectionKeys(key1: TSectionKey, key2: TSectionKey): Int =
        throw NotImplementedError("Must override compareSectionKeys() if sortSections() returns true")

    protected open fun sortItemsInSection(key: TSectionKey): Boolean = false

    protected open fun compareItems(item1: TItem, item2: TItem): Int =
        throw NotImplementedError("Must override compareItems() if sortItemsInSection() returns true")

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

    fun removeSection(key: TSectionKey) {
        sectionsMap[key]?.let { section ->
            removeSection(section)
        }
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

    fun getAllSectionKeys(): List<TSectionKey> = sections.map { section ->
        section.key
    }

    fun collapseSection(sectionKey: TSectionKey): Boolean = collapseSection(getSectionForKey(sectionKey))
    fun expandSection(sectionKey: TSectionKey): Boolean = expandSection(getSectionForKey(sectionKey))

    fun toggleExpandSection(sectionKey: TSectionKey): Boolean {
        val section = getSectionForKey(sectionKey)
        return if (section.collapsed) {
            expandSection(section)
        } else {
            !collapseSection(section)
        }
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

    fun collapseAllSections() {
        sections.forEach { section ->
            collapseSection(section)
        }
    }

    fun expandAllSections() {
        sections.forEach { section ->
            expandSection(section)
        }
    }

    fun getItemsInSection(sectionKey: TSectionKey): List<TItem> {
        return getSectionForKey(sectionKey).items
    }

    private fun getSectionForKey(key: TSectionKey): Section {
        return sectionsMap.getOrElse(key) {
            throw IllegalArgumentException("No section found for section key: $key")
        }
    }

    fun findSectionKeyForAdapterPosition(position: Int): TSectionKey = findSectionForAdapterPosition(position).key

    private fun findSectionForAdapterPosition(position: Int): Section {
        if (position >= globalHeaderCount && !sections.isEmpty() && position >= sections.first().adapterPosition) {
            sections.forEach { section ->
                if (position < section.adapterPosition + section.size) {
                    return section
                }
            }
        }
        throw IllegalArgumentException("Can't find section key. Invalid adapter position: $position")
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


    // region ViewHolders

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        open fun bind(adapterPosition: Int) {}

        open fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            bind(adapterPosition)
        }
    }

    abstract inner class SectionItemViewHolder(itemView: View) : ViewHolder(itemView) {

        override fun bind(adapterPosition: Int) {
            val section = findSectionForAdapterPosition(adapterPosition)
            bind(adapterPosition, adapterPosition - section.adapterPosition, section.key)
        }

        abstract fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey)

        override fun partialBind(adapterPosition: Int, payloads: MutableList<Any>) {
            val section = findSectionForAdapterPosition(adapterPosition)
            partialBind(adapterPosition, adapterPosition - section.adapterPosition, section.key, payloads)
        }

        open fun partialBind(
            adapterPosition: Int,
            sectionPosition: Int,
            sectionKey: TSectionKey,
            payloads: MutableList<Any>
        ) {
            bind(adapterPosition, sectionPosition, sectionKey)
        }

        protected fun getSectionKey(): TSectionKey? {
            return if (adapterPosition >= 0) findSectionKeyForAdapterPosition(adapterPosition) else null
        }
    }

    abstract inner class ItemViewHolder(itemView: View) : SectionItemViewHolder(itemView) {

        override fun bind(adapterPosition: Int, sectionPosition: Int, sectionKey: TSectionKey) {
            val section = getSectionForKey(sectionKey)
            val sectionItemPosition = sectionPosition - section.headerCount
            val item = section.items[sectionItemPosition]
            bind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item)
        }

        abstract fun bind(
            adapterPosition: Int,
            sectionPosition: Int,
            sectionItemPosition: Int,
            sectionKey: TSectionKey,
            item: TItem
        )

        override fun partialBind(
            adapterPosition: Int,
            sectionPosition: Int,
            sectionKey: TSectionKey,
            payloads: MutableList<Any>
        ) {
            val section = getSectionForKey(sectionKey)
            val sectionItemPosition = sectionPosition - section.headerCount
            val item = section.items[sectionItemPosition]
            partialBind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item, payloads)
        }

        open fun partialBind(
            adapterPosition: Int,
            sectionPosition: Int,
            sectionItemPosition: Int,
            sectionKey: TSectionKey,
            item: TItem,
            payloads: MutableList<Any>
        ) {
            bind(adapterPosition, sectionPosition, sectionItemPosition, sectionKey, item)
        }

        protected fun getItem(): TItem? = if (adapterPosition >= 0) getItem(adapterPosition) else null
    }

    // endregion
}
