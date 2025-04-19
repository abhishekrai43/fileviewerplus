import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reminderAt: Long? = null,
    val repeat: RepeatType = RepeatType.NEVER,
    val color: NoteColor = NoteColor.YELLOW,
    val isPassword: Boolean = false
)

enum class NoteColor {
    YELLOW, BLUE, GREEN, RED
}

enum class RepeatType {
    NEVER, DAILY, WEEKLY
}
