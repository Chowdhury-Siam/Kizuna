package io.github.kakashi812.droidoss.layout

/** One snapshot per gesture/action; capped to bound memory on long editing sessions. */
class EditHistory<T>(private val limit: Int = 80) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun checkpoint(value: T) {
        undo.addLast(value)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }
    fun undo(current: T): T {
        if (!canUndo) return current
        redo.addLast(current)
        return undo.removeLast()
    }
    fun redo(current: T): T {
        if (!canRedo) return current
        undo.addLast(current)
        return redo.removeLast()
    }
}
