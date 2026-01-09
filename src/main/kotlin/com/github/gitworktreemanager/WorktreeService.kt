package com.github.gitworktreemanager

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
class WorktreeService(private val project: Project) {

    fun listWorktrees(): List<WorktreeInfo> {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        if (repositories.isEmpty()) return emptyList()

        val repo = repositories.first()
        val root = repo.root

        val commandLine = GeneralCommandLine("git", "worktree", "list", "--porcelain")
            .withWorkDirectory(root.path)

        val output: ProcessOutput = try {
            ExecUtil.execAndGetOutput(commandLine)
        } catch (e: Exception) {
            return emptyList()
        }

        if (output.exitCode != 0) return emptyList()

        return parseWorktreeOutput(output.stdoutLines, project.basePath ?: "")
    }

    private fun parseWorktreeOutput(output: List<String>, currentPath: String): List<WorktreeInfo> {
        val worktrees = mutableListOf<WorktreeInfo>()
        var path: String? = null
        var branch: String? = null
        var isMain = false

        fun saveCurrentEntry() {
            if (path != null) {
                worktrees.add(
                    WorktreeInfo(
                        path = path!!,
                        branch = branch ?: "(unknown)",
                        isMain = isMain || worktrees.isEmpty(),
                        isCurrent = path == currentPath
                    )
                )
                path = null
                branch = null
                isMain = false
            }
        }

        for (line in output) {
            when {
                line.startsWith("worktree ") -> {
                    saveCurrentEntry()
                    path = line.removePrefix("worktree ")
                }
                line.startsWith("branch ") -> {
                    branch = line.removePrefix("branch refs/heads/")
                }
                line == "bare" -> {
                    isMain = true
                }
                line == "detached" -> {
                    branch = "(detached HEAD)"
                }
                line.isEmpty() -> {
                    saveCurrentEntry()
                }
            }
        }

        // Handle last entry if no trailing newline
        saveCurrentEntry()

        return worktrees
    }
}
