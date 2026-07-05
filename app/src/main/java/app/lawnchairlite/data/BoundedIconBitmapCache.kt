package app.lawnchairlite.data

internal interface RecyclableIconEntry {
    val bytes: Int
    fun recycle()
}

internal class BoundedIconBitmapCache<T : RecyclableIconEntry>(
    private val maxBytes: Int,
) {
    private val entries = LinkedHashMap<String, T>(16, 0.75f, true)
    var sizeBytes: Int = 0
        private set

    val entryCount: Int
        get() = entries.size

    @Synchronized
    fun get(key: String): T? = entries[key]

    @Synchronized
    fun put(key: String, value: T): Boolean {
        if (value.bytes <= 0 || value.bytes > maxBytes) {
            value.recycle()
            return false
        }

        entries.remove(key)?.let { old ->
            sizeBytes -= old.bytes
            if (old !== value) old.recycle()
        }

        entries[key] = value
        sizeBytes += value.bytes
        trimToSize()
        return entries[key] === value
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { it.recycle() }
        entries.clear()
        sizeBytes = 0
    }

    private fun trimToSize() {
        val iterator = entries.entries.iterator()
        while (sizeBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            iterator.remove()
            sizeBytes -= eldest.value.bytes
            eldest.value.recycle()
        }
    }
}
