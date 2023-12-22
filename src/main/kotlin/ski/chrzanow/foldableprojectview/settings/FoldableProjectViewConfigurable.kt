package ski.chrzanow.foldableprojectview.settings

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.util.ui.tree.TreeUtil
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.bindSelected
import ski.chrzanow.foldableprojectview.createPredicate
import ski.chrzanow.foldableprojectview.projectView.FoldableTreeStructureProvider
import java.awt.Dimension
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.JComponent

class FoldableProjectViewConfigurable(private val project: Project) : SearchableConfigurable {

    private val settings = project.service<FoldableProjectSettings>()
    private val propertyGraph = PropertyGraph()
    private val settingsProperty = propertyGraph.lazyProperty { FoldableProjectSettings().apply { copyFrom(settings) } }
    private val foldingEnabledPredicate = settingsProperty.createPredicate(FoldableProjectSettings::foldingEnabled)
    private val hideAllGroupsPredicate = settingsProperty.createPredicate(FoldableProjectSettings::hideEmptyGroups)

    private val ruleProperty = propertyGraph
        .lazyProperty<Rule?> { null }
        .apply {
            afterChange {
                ApplicationManager.getApplication().invokeLater {
                    rulesTable.tableView.updateUI()
                }

                settingsProperty.setValue(null, FoldableProjectState::rules, settingsProperty.get())
            }
        }

    private val rulesTable = FoldableRulesTable(settingsProperty)
    private val rulesEditor = FoldableRulesEditor(ruleProperty)

    private val settingsPanel = panel {
        rowsRange {
            row {
                checkBox(message("foldableProjectView.settings.foldingEnabled"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldingEnabled)
                    .comment(message("foldableProjectView.settings.foldingEnabled.comment"), -1)
                    .applyToComponent { setMnemonic('e') }
            }

            row {
                checkBox(message("foldableProjectView.settings.matchDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::matchDirectories)
                    .comment(message("foldableProjectView.settings.matchDirectories.comment"), -1)
                    .applyToComponent { setMnemonic('d') }
                    .enabledIf(foldingEnabledPredicate)
            }

            row {
                checkBox(message("foldableProjectView.settings.foldIgnoredFiles"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldIgnoredFiles)
                    .comment(message("foldableProjectView.settings.foldIgnoredFiles.comment"), -1)
                    .applyToComponent { setMnemonic('h') }
                    .enabledIf(foldingEnabledPredicate)
            }

            row {
                checkBox(message("foldableProjectView.settings.hideEmptyGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideEmptyGroups)
                    .comment(message("foldableProjectView.settings.hideEmptyGroups.comment"), -1)
                    .applyToComponent { setMnemonic('h') }
                    .enabledIf(foldingEnabledPredicate)
            }

            row {
                checkBox(message("foldableProjectView.settings.hideAllGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideAllGroups)
                    .comment(message("foldableProjectView.settings.hideAllGroups.comment"), -1)
                    .applyToComponent { setMnemonic('i') }
                    .enabledIf(foldingEnabledPredicate and hideAllGroupsPredicate.not())

                ContextHelpLabel.create(
                    message("foldableProjectView.settings.hideAllGroups.help"),
                    message("foldableProjectView.settings.hideAllGroups.help.description"),
                ).let(::cell)
            }

            row {
                checkBox(message("foldableProjectView.settings.caseSensitive"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::caseSensitive)
                    .comment(message("foldableProjectView.settings.caseSensitive.comment"), -1)
                    .applyToComponent { setMnemonic('c') }
                    .enabledIf(foldingEnabledPredicate)
            }
        }
    }


    private val projectView by lazy {
        object : ProjectViewPane(project) {
            override fun enableDnD() = Unit

            override fun createStructure() = object : AbstractProjectTreeStructure(project) {
                override fun getProviders() = listOf(FoldableTreeStructureProvider(project).apply {
                    propertyGraph.afterPropagation {
                        updateFromRoot(true)
                    }
                    withState(
                        settingsProperty.get()
                    )
                })
            }
        }
    }

    private val splitter = OnePixelSplitter(false, .6f, .4f, .6f).apply {
        firstComponent = settingsPanel.apply {
            border = createEmptyBorder(10, 10, 10, 30)
        }
        secondComponent = projectView.createComponent().apply {
            border = createEmptyBorder()
            preferredSize = Dimension()
        }
        setHonorComponentsMinimumSize(false)
        TreeUtil.promiseExpand(projectView.tree, 2)
    }

    private val settingsWithRulesPanel = panel {
        row {
            cell(splitter)
                .align(Align.FILL)
        }
        group(message("foldableProjectView.settings.foldingRules")) {
            row {
                cell(rulesTable.component)
                    .align(Align.FILL)
                    .resizableColumn()

                cell(rulesEditor.createPanel())
                    .applyIfEnabled()

                with(rulesTable.tableView) {
                    selectionModel.addListSelectionListener {
                        ruleProperty.set(selectedObject)
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "ski.chrzanow.foldableprojectview.options.FoldableProjectViewConfigurable"
    }

    override fun getId() = ID

    override fun getDisplayName() = message("foldableProjectView.name")

    override fun createComponent(): JComponent {
        return settingsWithRulesPanel
    }

    override fun isModified() = settingsProperty.get() != settings

    override fun reset() {
        settingsProperty.set(settingsProperty.get().apply {
            copyFrom(settings)
        })
    }

    override fun apply() {
        val updated = isModified

        settingsPanel.apply()
        if (updated) {
            settings.copyFrom(settingsProperty.get())
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(FoldableProjectSettingsListener.TOPIC)
                .settingsChanged(settings)
        }
    }
}
