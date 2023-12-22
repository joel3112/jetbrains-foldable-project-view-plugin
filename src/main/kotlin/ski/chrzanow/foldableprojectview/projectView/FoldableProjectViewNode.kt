package ski.chrzanow.foldableprojectview.projectView

import com.intellij.icons.AllIcons.General.CollapseComponent
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle
import ski.chrzanow.foldableprojectview.settings.Rule

class FoldableProjectViewNode(
    project: Project,
    settings: ViewSettings?,
    private val children: Set<AbstractTreeNode<*>>,
    private val rule: Rule,
    private val textAttributes: SimpleTextAttributes,
) : ProjectViewNode<String>(
    project,
    FoldableProjectViewBundle.message("foldableProjectView.name") + rule.name,
    settings
) {

    override fun update(presentation: PresentationData) {
        presentation.apply {
            val text = FoldableProjectViewBundle.message("foldableProjectView.node", rule.name, children.size)
            val toolTip = children.mapNotNull { it.name }.joinToString(", ")
            addText(ColoredFragment(text, toolTip, textAttributes))
            setIcon(CollapseComponent)
        }
    }

    override fun getChildren() = children

    override fun computeBackgroundColor() = rule.background

    override fun contains(file: VirtualFile) = children.firstOrNull {
        it is ProjectViewNode && it.virtualFile == file
    } != null
}
