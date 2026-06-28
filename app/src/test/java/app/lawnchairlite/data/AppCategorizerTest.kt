package app.lawnchairlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategorizerTest {

    private fun app(label: String, packageName: String, installSource: String = "") = AppInfo(
        label = label,
        packageName = packageName,
        activityName = "$packageName.Main",
        icon = null,
        installSource = installSource,
    )

    @Test
    fun nameRegexRuleOverridesAutomaticCategory() {
        val rules = listOf(
            AppCategoryRule(
                type = CategoryRuleType.APP_NAME_REGEX,
                pattern = "youtube",
                category = DrawerCategory.WORK,
            ),
        )

        val result = AppCategorizer.categorize(app("YouTube", "com.google.android.youtube"), rules)

        assertEquals(DrawerCategory.WORK, result)
    }

    @Test
    fun packagePrefixRuleOverridesAutomaticCategory() {
        val rules = listOf(
            AppCategoryRule(
                type = CategoryRuleType.PACKAGE_PREFIX,
                pattern = "com.vendor.internal",
                category = DrawerCategory.TOOLS,
            ),
        )

        val result = AppCategorizer.categorize(app("Video Chat", "com.vendor.internal.chat"), rules)

        assertEquals(DrawerCategory.TOOLS, result)
    }

    @Test
    fun installSourceRuleOverridesAutomaticCategory() {
        val rules = listOf(
            AppCategoryRule(
                type = CategoryRuleType.INSTALL_SOURCE,
                pattern = "com.android.vending",
                category = DrawerCategory.GAMES,
            ),
        )

        val result = AppCategorizer.categorize(
            app("Office Docs", "com.example.docs", installSource = "com.android.vending"),
            rules,
        )

        assertEquals(DrawerCategory.GAMES, result)
    }

    @Test
    fun invalidRegexRuleFallsBackToAutomaticCategory() {
        val rules = listOf(
            AppCategoryRule(
                type = CategoryRuleType.APP_NAME_REGEX,
                pattern = "[",
                category = DrawerCategory.WORK,
            ),
        )

        val result = AppCategorizer.categorize(app("Camera", "com.example.camera"), rules)

        assertEquals(DrawerCategory.MEDIA, result)
    }

    @Test
    fun disabledRuleFallsBackToAutomaticCategory() {
        val rules = listOf(
            AppCategoryRule(
                type = CategoryRuleType.PACKAGE_PREFIX,
                pattern = "com.example",
                category = DrawerCategory.WORK,
                enabled = false,
            ),
        )

        val result = AppCategorizer.categorize(app("Calendar", "com.example.calendar"), rules)

        assertEquals(DrawerCategory.TOOLS, result)
    }
}
