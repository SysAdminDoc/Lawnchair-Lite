package app.lawnchairlite.data

import org.json.JSONObject

data class BackupImportPreview(
    val schemaVersion: Int,
    val appVersion: String?,
    val canImport: Boolean,
    val sections: List<String>,
    val privateSections: List<String>,
    val omittedPrivateSections: List<String>,
    val unknownFields: List<String>,
    val skippedFields: List<String>,
    val warning: String? = null,
    val error: String? = null,
) {
    val sectionSummary: String
        get() = sections.joinToString(", ").ifBlank { "No supported sections" }

    companion object {
        const val CURRENT_SCHEMA = 1

        private val knownFields = setOf(
            "version", "schema", "exported_at", "backup_options", "omitted_private_sections",
            "theme", "icon_shape", "icon_size", "icon_pack", "themed_icons", "icon_shadow", "grayscale_icons",
            "accent_override", "wallpaper_dim", "page_transition", "badge_style", "label_style", "label_size",
            "label_weight", "grid_cols", "grid_rows", "grid_padding_h", "grid_padding_v", "home_grid",
            "dock_grid", "dock_count", "dock_style", "dock_labels", "dock_label_opacity", "hide_dock",
            "show_dock_search", "dock_swipe_apps", "search_bar_style", "show_clock", "clock_style",
            "show_notif_badges", "auto_place_new", "hide_status_bar", "home_locked", "wallpaper_parallax",
            "haptic_level", "drawer_sort", "drawer_columns", "drawer_opacity", "drawer_categories",
            "category_rules", "drawer_section_headers", "drawer_animation", "show_suggestions", "search_engine",
            "favorite_apps", "folder_columns", "double_tap", "swipe_down", "swipe_up_action", "triple_tap",
            "pinch_action", "dock_tap_action", "widgets", "search_history", "suggestion_usage", "app_usage",
            "hidden_apps", "custom_labels",
        )

        fun fromJson(json: String): BackupImportPreview = runCatching {
            val obj = JSONObject(json)
            fromFields(obj.toFieldMap())
        }.getOrElse {
            BackupImportPreview(
                schemaVersion = -1,
                appVersion = null,
                canImport = false,
                sections = emptyList(),
                privateSections = emptyList(),
                omittedPrivateSections = emptyList(),
                unknownFields = emptyList(),
                skippedFields = emptyList(),
                error = "Invalid backup JSON",
            )
        }

        fun fromFields(fields: Map<String, Any?>): BackupImportPreview {
            val schema = parseSchema(fields)
            val unknown = fields.keys.filterNot { it in knownFields }.sorted()
            val skipped = invalidEnumFields(fields)
            val omittedPrivate = stringList(fields["omitted_private_sections"])
            val privateSections = listOfNotNull(
                "Search history".takeIf { fields.containsKey("search_history") },
                "Usage and recents".takeIf { fields.containsKey("suggestion_usage") || fields.containsKey("app_usage") },
                "Hidden apps".takeIf { fields.containsKey("hidden_apps") },
            )
            val sections = sectionGroups(fields)
            val error = when {
                schema > CURRENT_SCHEMA -> "Backup schema $schema is newer than supported schema $CURRENT_SCHEMA"
                sections.isEmpty() -> "No supported launcher sections found"
                else -> null
            }
            val warning = when {
                schema == 0 -> "Legacy backup without schema metadata"
                unknown.isNotEmpty() -> "Unknown fields will be ignored"
                skipped.isNotEmpty() -> "Invalid values will be skipped"
                else -> null
            }
            return BackupImportPreview(
                schemaVersion = schema,
                appVersion = fields["version"].asString().takeIf { it.isNotBlank() },
                canImport = error == null,
                sections = sections,
                privateSections = privateSections,
                omittedPrivateSections = omittedPrivate,
                unknownFields = unknown,
                skippedFields = skipped,
                warning = warning,
                error = error,
            )
        }

        private fun parseSchema(fields: Map<String, Any?>): Int {
            if (!fields.containsKey("schema")) return 0
            return when (val raw = fields["schema"]) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull() ?: CURRENT_SCHEMA + 1
                else -> CURRENT_SCHEMA + 1
            }
        }

        private fun sectionGroups(fields: Map<String, Any?>): List<String> = buildList {
            addIfPresent(fields, "Appearance", "theme", "icon_shape", "icon_size", "icon_pack", "themed_icons", "icon_shadow", "grayscale_icons", "accent_override", "wallpaper_dim", "page_transition", "badge_style", "label_style", "label_size", "label_weight")
            addIfPresent(fields, "Layout & widgets", "grid_cols", "grid_rows", "grid_padding_h", "grid_padding_v", "home_grid", "dock_grid", "dock_count", "dock_style", "dock_labels", "dock_label_opacity", "hide_dock", "widgets")
            addIfPresent(fields, "Drawer & search", "drawer_sort", "drawer_columns", "drawer_opacity", "drawer_categories", "category_rules", "drawer_section_headers", "drawer_animation", "show_suggestions", "search_engine", "favorite_apps", "folder_columns")
            addIfPresent(fields, "Gestures", "double_tap", "swipe_down", "swipe_up_action", "triple_tap", "pinch_action", "dock_tap_action", "dock_swipe_apps")
            addIfPresent(fields, "Feature settings", "show_clock", "clock_style", "show_notif_badges", "auto_place_new", "hide_status_bar", "home_locked", "wallpaper_parallax", "haptic_level", "search_bar_style", "show_dock_search")
            addIfPresent(fields, "Private data", "search_history", "suggestion_usage", "app_usage", "hidden_apps")
            addIfPresent(fields, "Custom labels", "custom_labels")
        }

        private fun MutableList<String>.addIfPresent(fields: Map<String, Any?>, label: String, vararg keys: String) {
            if (keys.any { fields.containsKey(it) }) add(label)
        }

        private fun invalidEnumFields(fields: Map<String, Any?>): List<String> = buildList {
            addInvalidEnum(fields, "theme", ThemeMode.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "icon_shape", IconShape.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "icon_size", IconSize.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "drawer_sort", DrawerSort.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "label_style", LabelStyle.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "page_transition", PageTransition.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "badge_style", BadgeStyle.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "dock_style", DockStyle.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "search_bar_style", SearchBarStyle.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "haptic_level", HapticLevel.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "label_size", LabelSize.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "double_tap", GestureAction.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "swipe_down", GestureAction.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "triple_tap", GestureAction.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "pinch_action", GestureAction.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "dock_tap_action", GestureAction.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "swipe_up_action", GestureAction.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "clock_style", ClockStyle.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "page_indicator_style", PageIndicatorStyle.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "label_weight", LabelWeight.entries.map { it.name }.toSet())
            addInvalidEnum(fields, "search_engine", SearchEngine.entries.map { it.name }.toSet())
        }

        private fun MutableList<String>.addInvalidEnum(fields: Map<String, Any?>, key: String, allowed: Set<String>) {
            val value = fields[key].asString().takeIf { it.isNotBlank() } ?: return
            if (value !in allowed) add(key)
        }

        private fun JSONObject.toFieldMap(): Map<String, Any?> =
            keys().asSequence().associateWith { key -> opt(key) }

        private fun Any?.asString(): String = when (this) {
            null -> ""
            JSONObject.NULL -> ""
            else -> toString()
        }

        private fun stringList(value: Any?): List<String> = when (value) {
            is Iterable<*> -> value.mapNotNull { it.asString().takeIf { text -> text.isNotBlank() } }
            is org.json.JSONArray -> (0 until value.length()).mapNotNull { index -> value.optString(index).takeIf { it.isNotBlank() } }
            else -> emptyList()
        }
    }
}
