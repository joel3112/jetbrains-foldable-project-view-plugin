package ski.chrzanow.foldableprojectview

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.ComponentPredicate
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty1

fun <T : FoldableProjectState> Cell<JBCheckBox>.bindSelected(
    graphProperty: ObservableMutableProperty<T>,
    property: KMutableProperty1<T, Boolean>
) =
    bindSelected(with(graphProperty) {
        transform(
            { it.let(property::get) },
            { value -> get().also { property.set(it, value) } }
        )
    })

fun <T : FoldableProjectState> Cell<JTextComponent>.bindText(
    graphProperty: ObservableMutableProperty<T>,
    property: KMutableProperty1<T, String?>
) =
    bindText(with(graphProperty) {
        transform(
            { it?.let(property::get).orEmpty() },
            { value -> get()?.apply { property.set(this, value) }!! },
        )
    })

fun <T : FoldableProjectState> ObservableMutableProperty<T>.createPredicate(property: KMutableProperty1<T, Boolean>) =
    object : ComponentPredicate() {

        private val observableProperty = transform(property)

        override fun invoke() = observableProperty.get()

        override fun addListener(listener: (Boolean) -> Unit) = observableProperty.afterChange(listener)
    }
