package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedIconBitmapCacheTest {

    @Test
    fun evictsLeastRecentlyUsedEntryAndRecyclesIt() {
        val cache = BoundedIconBitmapCache<FakeIconEntry>(maxBytes = 8)
        val first = FakeIconEntry(4)
        val second = FakeIconEntry(4)
        val third = FakeIconEntry(4)

        assertTrue(cache.put("first", first))
        assertTrue(cache.put("second", second))
        assertSame(first, cache.get("first"))
        assertTrue(cache.put("third", third))

        assertSame(first, cache.get("first"))
        assertNull(cache.get("second"))
        assertSame(third, cache.get("third"))
        assertFalse(first.recycled)
        assertTrue(second.recycled)
        assertFalse(third.recycled)
        assertEquals(8, cache.sizeBytes)
        assertEquals(2, cache.entryCount)
    }

    @Test
    fun replacingEntryRecyclesOldEntry() {
        val cache = BoundedIconBitmapCache<FakeIconEntry>(maxBytes = 8)
        val old = FakeIconEntry(4)
        val replacement = FakeIconEntry(6)

        assertTrue(cache.put("icon", old))
        assertTrue(cache.put("icon", replacement))

        assertTrue(old.recycled)
        assertFalse(replacement.recycled)
        assertSame(replacement, cache.get("icon"))
        assertEquals(6, cache.sizeBytes)
    }

    @Test
    fun oversizedEntryIsRejectedAndRecycled() {
        val cache = BoundedIconBitmapCache<FakeIconEntry>(maxBytes = 8)
        val oversized = FakeIconEntry(12)

        assertFalse(cache.put("huge", oversized))

        assertTrue(oversized.recycled)
        assertNull(cache.get("huge"))
        assertEquals(0, cache.sizeBytes)
        assertEquals(0, cache.entryCount)
    }

    @Test
    fun clearRecyclesAllCachedEntries() {
        val cache = BoundedIconBitmapCache<FakeIconEntry>(maxBytes = 16)
        val first = FakeIconEntry(4)
        val second = FakeIconEntry(4)
        cache.put("first", first)
        cache.put("second", second)

        cache.clear()

        assertTrue(first.recycled)
        assertTrue(second.recycled)
        assertEquals(0, cache.sizeBytes)
        assertEquals(0, cache.entryCount)
    }

    private class FakeIconEntry(override val bytes: Int) : RecyclableIconEntry {
        var recycled = false
            private set

        override fun recycle() {
            recycled = true
        }
    }
}
