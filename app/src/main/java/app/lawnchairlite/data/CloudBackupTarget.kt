package app.lawnchairlite.data

data class CloudBackupTarget(
    val uri: String = "",
    val displayName: String = "",
    val lastSuccessAt: Long = 0L,
) {
    val isConfigured: Boolean
        get() = uri.isNotBlank()

    val label: String
        get() = displayName.ifBlank { uri }
}
