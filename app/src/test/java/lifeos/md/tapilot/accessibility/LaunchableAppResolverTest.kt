package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchableAppResolverTest {
    @Test
    fun resolvesExactLabelWithoutDependingOnCase() {
        val apps = listOf(LaunchableApp("美团", "com.sankuai.meituan"))
        assertEquals("com.sankuai.meituan", LaunchableAppResolver.resolve(" 美团 ", apps))
        assertEquals("com.sankuai.meituan", LaunchableAppResolver.resolve("美团", apps))
    }

    @Test
    fun returnsNullForUnknownApp() {
        assertNull(LaunchableAppResolver.resolve("不存在", emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAmbiguousAppName() {
        LaunchableAppResolver.resolve(
            "Demo",
            listOf(LaunchableApp("Demo", "one"), LaunchableApp("demo", "two")),
        )
    }
}
