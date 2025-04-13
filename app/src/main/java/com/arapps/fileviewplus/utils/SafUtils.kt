import java.io.File

object SafUtils {
    fun isSafProtected(file: File): Boolean {
        return !file.canRead() || !file.canWrite()
    }
}
