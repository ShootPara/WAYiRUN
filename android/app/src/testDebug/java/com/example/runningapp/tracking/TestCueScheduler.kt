package com.example.runningapp.tracking

internal class TestCueScheduler : CueScheduler {
    private data class Task(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
    private var now = 0L
    private val tasks = mutableListOf<Task>()
    val pendingCount get() = tasks.count { !it.cancelled }
    override fun nowMs() = now
    fun fireCancelledCallbacks() { tasks.filter { it.cancelled }.toList().forEach { it.action() } }
    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val task = Task(now + delayMs, action)
        tasks += task
        return { task.cancelled = true }
    }
    fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val task = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
            tasks.remove(task)
            now = task.at
            task.action()
        }
        now = target
    }
}
