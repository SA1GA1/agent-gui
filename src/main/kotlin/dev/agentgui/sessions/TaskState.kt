package dev.agentgui.sessions

/** Automatic compaction is an item inside a turn; it does not release the turn slot. */
fun afterCompaction(state: String): String = if (state == "compacting") "idle" else state
