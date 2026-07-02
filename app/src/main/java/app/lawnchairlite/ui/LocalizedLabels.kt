package app.lawnchairlite.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.lawnchairlite.R
import app.lawnchairlite.data.BadgeStyle
import app.lawnchairlite.data.CategoryRuleType
import app.lawnchairlite.data.ClockStyle
import app.lawnchairlite.data.DockStyle
import app.lawnchairlite.data.DrawerCategory
import app.lawnchairlite.data.DrawerSort
import app.lawnchairlite.data.DrawerTab
import app.lawnchairlite.data.GestureAction
import app.lawnchairlite.data.HapticLevel
import app.lawnchairlite.data.IconShape
import app.lawnchairlite.data.IconSize
import app.lawnchairlite.data.LabelSize
import app.lawnchairlite.data.LabelStyle
import app.lawnchairlite.data.LabelWeight
import app.lawnchairlite.data.PageIndicatorStyle
import app.lawnchairlite.data.PageTransition
import app.lawnchairlite.data.SearchBarStyle
import app.lawnchairlite.data.SearchEngine
import app.lawnchairlite.data.ThemeMode

@StringRes internal fun IconShape.labelRes(): Int = when (this) {
    IconShape.NONE -> R.string.enum_icon_shape_default
    IconShape.SQUIRCLE -> R.string.enum_icon_shape_squircle
    IconShape.CIRCLE -> R.string.enum_icon_shape_circle
    IconShape.SQUARE -> R.string.enum_icon_shape_square
    IconShape.TEARDROP -> R.string.enum_icon_shape_teardrop
    IconShape.HEXAGON -> R.string.enum_icon_shape_hexagon
    IconShape.DIAMOND -> R.string.enum_icon_shape_diamond
}

@StringRes internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.MIDNIGHT -> R.string.enum_theme_midnight
    ThemeMode.GLASS -> R.string.enum_theme_glass
    ThemeMode.OLED -> R.string.enum_theme_oled
    ThemeMode.MOCHA -> R.string.enum_theme_mocha
    ThemeMode.AURORA -> R.string.enum_theme_aurora
    ThemeMode.NEON -> R.string.enum_theme_neon
}

@StringRes internal fun IconSize.labelRes(): Int = when (this) {
    IconSize.SMALL -> R.string.enum_icon_size_small
    IconSize.MEDIUM -> R.string.enum_icon_size_medium
    IconSize.LARGE -> R.string.enum_icon_size_large
    IconSize.XLARGE -> R.string.enum_icon_size_xlarge
}

@StringRes internal fun GestureAction.labelRes(): Int = when (this) {
    GestureAction.NONE -> R.string.enum_gesture_none
    GestureAction.LOCK_SCREEN -> R.string.enum_gesture_lock_screen
    GestureAction.NOTIFICATION_SHADE -> R.string.enum_gesture_notification_shade
    GestureAction.APP_DRAWER -> R.string.enum_gesture_app_drawer
    GestureAction.SETTINGS -> R.string.enum_gesture_settings
    GestureAction.KILL_APPS -> R.string.enum_gesture_kill_apps
    GestureAction.FLASHLIGHT -> R.string.enum_gesture_flashlight
    GestureAction.EDIT_MODE -> R.string.enum_gesture_edit_mode
    GestureAction.RECENT_APP -> R.string.enum_gesture_recent_app
    GestureAction.LAUNCH_APP -> R.string.enum_gesture_launch_app
}

@StringRes internal fun PageIndicatorStyle.labelRes(): Int = when (this) {
    PageIndicatorStyle.DOTS -> R.string.enum_page_indicator_dots
    PageIndicatorStyle.LINE -> R.string.enum_page_indicator_line
    PageIndicatorStyle.HIDDEN -> R.string.enum_page_indicator_hidden
}

@StringRes internal fun LabelWeight.labelRes(): Int = when (this) {
    LabelWeight.LIGHT -> R.string.enum_label_weight_light
    LabelWeight.REGULAR -> R.string.enum_label_weight_regular
    LabelWeight.BOLD -> R.string.enum_label_weight_bold
}

@StringRes internal fun ClockStyle.labelRes(): Int = when (this) {
    ClockStyle.LARGE -> R.string.enum_clock_large
    ClockStyle.COMPACT -> R.string.enum_clock_compact
    ClockStyle.MINIMAL -> R.string.enum_clock_minimal
}

@StringRes internal fun DrawerSort.labelRes(): Int = when (this) {
    DrawerSort.NAME -> R.string.enum_drawer_sort_name
    DrawerSort.REVERSE_NAME -> R.string.enum_drawer_sort_reverse_name
    DrawerSort.MOST_USED -> R.string.enum_drawer_sort_most_used
    DrawerSort.RECENT_INSTALL -> R.string.enum_drawer_sort_recent_install
}

@StringRes internal fun LabelStyle.labelRes(): Int = when (this) {
    LabelStyle.SHOWN -> R.string.enum_label_style_shown
    LabelStyle.HIDDEN -> R.string.enum_label_style_hidden
    LabelStyle.HOME_ONLY -> R.string.enum_label_style_home_only
    LabelStyle.DRAWER_ONLY -> R.string.enum_label_style_drawer_only
}

@StringRes internal fun PageTransition.labelRes(): Int = when (this) {
    PageTransition.SLIDE -> R.string.enum_page_transition_slide
    PageTransition.CUBE -> R.string.enum_page_transition_cube
    PageTransition.STACK -> R.string.enum_page_transition_stack
    PageTransition.FADE -> R.string.enum_page_transition_fade
    PageTransition.CAROUSEL -> R.string.enum_page_transition_carousel
    PageTransition.DEPTH -> R.string.enum_page_transition_depth
}

@StringRes internal fun BadgeStyle.labelRes(): Int = when (this) {
    BadgeStyle.COUNT -> R.string.enum_badge_count
    BadgeStyle.DOT -> R.string.enum_badge_dot
    BadgeStyle.HIDDEN -> R.string.enum_badge_hidden
}

@StringRes internal fun DrawerCategory.labelRes(): Int = when (this) {
    DrawerCategory.ALL -> R.string.enum_drawer_category_all
    DrawerCategory.GAMES -> R.string.enum_drawer_category_games
    DrawerCategory.SOCIAL -> R.string.enum_drawer_category_social
    DrawerCategory.MEDIA -> R.string.enum_drawer_category_media
    DrawerCategory.TOOLS -> R.string.enum_drawer_category_tools
    DrawerCategory.WORK -> R.string.enum_drawer_category_work
    DrawerCategory.OTHER -> R.string.enum_drawer_category_other
}

@StringRes internal fun DrawerTab.labelRes(): Int = when (this) {
    DrawerTab.ALL -> R.string.enum_drawer_tab_all
    DrawerTab.RECENT -> R.string.enum_drawer_tab_recent
    DrawerTab.FAVORITES -> R.string.enum_drawer_tab_favorites
    DrawerTab.WORK -> R.string.enum_drawer_tab_work
}

@StringRes internal fun CategoryRuleType.labelRes(): Int = when (this) {
    CategoryRuleType.APP_NAME_REGEX -> R.string.enum_category_rule_type_app_name_regex
    CategoryRuleType.PACKAGE_PREFIX -> R.string.enum_category_rule_type_package_prefix
    CategoryRuleType.INSTALL_SOURCE -> R.string.enum_category_rule_type_install_source
}

@StringRes internal fun DockStyle.labelRes(): Int = when (this) {
    DockStyle.SOLID -> R.string.enum_dock_style_solid
    DockStyle.PILL -> R.string.enum_dock_style_pill
    DockStyle.FLOATING -> R.string.enum_dock_style_floating
    DockStyle.TRANSPARENT -> R.string.enum_dock_style_transparent
}

@StringRes internal fun SearchBarStyle.labelRes(): Int = when (this) {
    SearchBarStyle.PILL -> R.string.enum_search_bar_style_pill
    SearchBarStyle.BAR -> R.string.enum_search_bar_style_bar
    SearchBarStyle.MINIMAL -> R.string.enum_search_bar_style_minimal
    SearchBarStyle.HIDDEN -> R.string.enum_search_bar_style_hidden
}

@StringRes internal fun HapticLevel.labelRes(): Int = when (this) {
    HapticLevel.OFF -> R.string.enum_haptic_off
    HapticLevel.LIGHT -> R.string.enum_haptic_light
    HapticLevel.MEDIUM -> R.string.enum_haptic_medium
    HapticLevel.STRONG -> R.string.enum_haptic_strong
}

@StringRes internal fun LabelSize.labelRes(): Int = when (this) {
    LabelSize.SMALL -> R.string.enum_label_size_small
    LabelSize.MEDIUM -> R.string.enum_label_size_medium
    LabelSize.LARGE -> R.string.enum_label_size_large
}

@StringRes internal fun SearchEngine.labelRes(): Int = when (this) {
    SearchEngine.GOOGLE -> R.string.enum_search_engine_google
    SearchEngine.DUCKDUCKGO -> R.string.enum_search_engine_duckduckgo
    SearchEngine.BING -> R.string.enum_search_engine_bing
    SearchEngine.BRAVE -> R.string.enum_search_engine_brave
    SearchEngine.STARTPAGE -> R.string.enum_search_engine_startpage
}

@Composable internal fun IconShape.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun ThemeMode.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun IconSize.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun GestureAction.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun PageIndicatorStyle.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun LabelWeight.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun ClockStyle.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun DrawerSort.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun LabelStyle.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun PageTransition.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun BadgeStyle.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun DrawerCategory.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun DrawerTab.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun CategoryRuleType.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun DockStyle.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun SearchBarStyle.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun HapticLevel.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun LabelSize.localizedLabel(): String = stringResource(labelRes())
@Composable internal fun SearchEngine.localizedLabel(): String = stringResource(labelRes())

internal fun Context.localizedLabel(value: DrawerCategory): String = getString(value.labelRes())
internal fun Context.localizedLabel(value: DrawerSort): String = getString(value.labelRes())
internal fun Context.localizedLabel(value: DrawerTab): String = getString(value.labelRes())
