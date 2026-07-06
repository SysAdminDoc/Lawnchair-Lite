package app.lawnchairlite.data

data class BackupScheduleState(
    val enabled: Boolean = false,
    val lastSuccessAt: Long = 0L,
    val lastPath: String = "",
)
