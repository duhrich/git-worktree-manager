package com.github.gitworktreemanager

data class WorktreeInfo(
    val path: String,
    val branch: String,
    val isMain: Boolean,
    val isCurrent: Boolean
) {
    val displayName: String
        get() = path.substringAfterLast('/')
}
