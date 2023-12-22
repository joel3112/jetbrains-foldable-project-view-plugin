package ski.chrzanow.foldableprojectview.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import com.intellij.ui.SimpleTextAttributes
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettings
import ski.chrzanow.foldableprojectview.settings.FoldableProjectSettingsListener
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule

class FoldableTreeStructureProvider(project: Project) : TreeStructureProvider {

    private val settings = project.service<FoldableProjectSettings>()
    private val patternCache = PatternCache.getInstance(project)
    private var previewState: FoldableProjectState? = null
    private val projectView = ProjectView.getInstance(project)
    private val state get() = previewState ?: settings
    private val ignoredStatuses = listOf(FileStatus.IGNORED.id, "IGNORE.PROJECT_VIEW.IGNORED")

    init {
        project.messageBus
            .connect(project)
            .subscribe(FoldableProjectSettingsListener.TOPIC, object : FoldableProjectSettingsListener {
                override fun settingsChanged(settings: FoldableProjectSettings) {
                    refreshProjectView()
                }
            })

        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusesChanged() {
                if (settings.foldIgnoredFiles) {
                    refreshProjectView()
                }
            }
        }, project)
    }

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        viewSettings: ViewSettings?,
    ): Collection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children

        return when {
            !state.foldingEnabled -> children
            parent !is PsiDirectoryNode -> children
            else -> {
                val matched = mutableSetOf<AbstractTreeNode<*>>()
                val rulesSortedAlphabetically = state.rules.sortedBy { it.name }

                rulesSortedAlphabetically.forEach {
                    children.match(it.pattern).run {
                        val matchedByPattern = first.toSet()
                        val matchedByIgnore = second.toSet()

                        when {
                            state.hideAllGroups -> children - matchedByPattern - matchedByIgnore
                            else -> {
                                children -= matchedByPattern
                                if (matchedByPattern.isNotEmpty() || !state.hideEmptyGroups) children += FoldableProjectViewNode(
                                    project,
                                    viewSettings,
                                    matchedByPattern,
                                    it,
                                    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, it.foreground)
                                )
                                children -= matchedByIgnore
                                if (matchedByIgnore.isNotEmpty()) matched += FoldableProjectViewNode(
                                    project,
                                    viewSettings,
                                    matchedByIgnore,
                                    Rule(
                                        FoldableProjectViewBundle.message("foldableProjectView.node.byIgnored")
                                    ),
                                    SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES
                                )
                                children
                            }
                        }
                    }
                }

                children + matched
            }
        }
    }

    fun withState(state: FoldableProjectState) {
        previewState = state
    }

    private fun MutableCollection<AbstractTreeNode<*>>.match(patterns: String) =
        this
            .filter {
                when (it) {
                    is PsiDirectoryNode -> state.matchDirectories
                    is PsiFileNode -> true
                    else -> false
                }
            }.partition {
                when (it) {
                    is ProjectViewNode -> it.virtualFile?.name ?: it.name
                    else -> it.name
                }.caseSensitive().let { name ->
                    patterns
                        .caseSensitive()
                        .split(' ')
                        .any { pattern ->
                            patternCache?.createPattern(pattern, Syntax.GLOB)?.matcher(name)?.matches() ?: false
                        }
                }
            }.apply {
                return Pair(
                    first,
                    second.filter { state.foldIgnoredFiles && (it.fileStatus.id in ignoredStatuses) }.toMutableList()
                )
            }

    private fun String?.caseSensitive() = when {
        this == null -> ""
        state.caseSensitive -> this
        else -> lowercase()
    }

    private fun refreshProjectView() = projectView.currentProjectViewPane?.updateFromRoot(true)
}
