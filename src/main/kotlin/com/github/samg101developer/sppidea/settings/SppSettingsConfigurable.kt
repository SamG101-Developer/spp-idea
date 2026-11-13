package com.github.samg101developer.sppidea.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.io.File
import javax.swing.DefaultComboBoxModel

// The page in the Settings dialog that lists the `spp`
// executables (e.g. a Windows and a WSL build) and which one
// is the default, found in "Settings | Languages & Frameworks
// | S++". Run configurations pick one of them by name.
class SppSettingsConfigurable : BoundConfigurable("S++") {

  private val model = ListTableModel<SppExecutable>(NameColumn, PathColumn)
  private val table = TableView(model)
  private val defaultModel = DefaultComboBoxModel<String>()
  private val defaultComboBox = ComboBox(defaultModel)

  init {
    model.addTableModelListener { refreshDefaults() }
  }

  // The default can be any of the named executables in the
  // table, as it currently stands.
  private fun refreshDefaults(keep: String? = defaultComboBox.selectedItem as? String) {
    val names = model.items.map { it.name }.filter { it.isNotBlank() }.distinct()
    if (names == (0 until defaultModel.size).map { defaultModel.getElementAt(it) }) return
    defaultModel.removeAllElements()
    defaultModel.addAll(names)
    defaultComboBox.selectedItem = keep?.takeIf { it in names } ?: names.firstOrNull()
  }

  // Adding an executable asks for the binary, and names it after
  // where it lives; the name can then be edited in the table.
  private fun addExecutable() {
    val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle("Select S++ Executable")
      .withDescription("Select an `spp` compiler/toolchain binary.")
    val file = FileChooser.chooseFile(descriptor, null, null) ?: return
    val path = file.toNioPath().toString()
    val base = if (path.startsWith("\\\\wsl") || path.startsWith("//wsl")) "WSL" else "Windows".takeIf { File.separatorChar == '\\' } ?: "Local"
    val taken = model.items.map { it.name }.toSet()
    val name = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base $it" }.first { it !in taken }
    model.addRow(SppExecutable(name, path))
  }

  private fun current(): List<SppExecutable> = model.items.map { it.copy(name = it.name.trim(), path = it.path.trim()) }

  override fun createPanel() = panel {
    row {
      val decorated = ToolbarDecorator.createDecorator(table)
        .setAddAction { addExecutable() }
        .disableUpDownActions()
        .createPanel()
      cell(decorated).align(Align.FILL)
        .label("S++ executables:", com.intellij.ui.dsl.builder.LabelPosition.TOP)
        .comment("Name and path of each <code>spp</code> binary, e.g. a Windows and a WSL build. " +
          "Double-click a cell to edit it. Each S++ run configuration picks one of these.")
    }.resizableRow()
    row("Default:") {
      cell(defaultComboBox).align(AlignX.FILL)
        .comment("Used by run configurations that do not pick one, by code analysis when no S++ run " +
          "configuration is selected, and by <code>spp init</code> for new projects.")
    }

    onReset {
      val settings = SppSettings.getInstance()
      model.items = settings.executables.toMutableList()
      refreshDefaults(settings.find(null)?.name)
    }
    onIsModified {
      val settings = SppSettings.getInstance()
      current() != settings.executables || (defaultComboBox.selectedItem as? String ?: "") != settings.defaultExecutable
    }
    onApply {
      val executables = current()
      executables.forEach {
        if (it.name.isBlank()) throw ConfigurationException("Every S++ executable needs a name.")
        if (!File(it.path).let { f -> f.isFile && f.canExecute() }) {
          throw ConfigurationException("The S++ executable '${it.name}' does not exist or is not executable: ${it.path}")
        }
      }
      executables.groupBy { it.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
        throw ConfigurationException("There is more than one S++ executable named '$it'.")
      }
      val settings = SppSettings.getInstance()
      settings.executables = executables
      settings.defaultExecutable = defaultComboBox.selectedItem as? String ?: ""
    }
  }

  private object NameColumn : ColumnInfo<SppExecutable, String>("Name") {
    override fun valueOf(item: SppExecutable): String = item.name
    override fun isCellEditable(item: SppExecutable): Boolean = true
    override fun setValue(item: SppExecutable, value: String) {
      item.name = value
    }
    override fun getWidth(table: javax.swing.JTable): Int = 120
  }

  private object PathColumn : ColumnInfo<SppExecutable, String>("Path") {
    override fun valueOf(item: SppExecutable): String = item.path
    override fun isCellEditable(item: SppExecutable): Boolean = true
    override fun setValue(item: SppExecutable, value: String) {
      item.path = value
    }
  }
}
