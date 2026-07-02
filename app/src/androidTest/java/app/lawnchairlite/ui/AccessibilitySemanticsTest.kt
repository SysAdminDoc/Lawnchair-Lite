package app.lawnchairlite.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchPillExposesButtonSemantics() {
        compose.setContent {
            CompositionLocalProvider(LocalLauncherColors provides MidnightColors) {
                SearchPill(onClick = {})
            }
        }

        compose.onNodeWithContentDescription("Search apps")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun drawerSearchExposesSearchLabel() {
        compose.setContent {
            CompositionLocalProvider(LocalLauncherColors provides MidnightColors) {
                DrawerSearch(query = "", onQueryChange = {})
            }
        }

        compose.onNodeWithContentDescription("Search apps")
            .assertIsDisplayed()
    }

    @Test
    fun homeSpaceMenuExposesActionRows() {
        compose.setContent {
            CompositionLocalProvider(LocalLauncherColors provides MidnightColors) {
                HomeSpaceMenuOverlay(
                    onEditMode = {},
                    onAddWidget = {},
                    onAddPage = {},
                    onRemovePage = {},
                    canRemovePage = false,
                    onWallpaper = {},
                    onSettings = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Add Widget")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        compose.onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
