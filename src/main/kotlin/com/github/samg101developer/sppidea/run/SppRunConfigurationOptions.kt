package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

// The options class for the S++ run configuration, which
// stores the configuration's state (module name and program
// arguments).
class SppRunConfigurationOptions : LocatableRunConfigurationOptions() {
  private val moduleNameProperty: StoredProperty<String?> =
    string("").provideDelegate(this, "moduleName")

  private val programArgumentsProperty: StoredProperty<String?> =
    string("").provideDelegate(this, "programArguments")

  var moduleName: String?
    get() = moduleNameProperty.getValue(this)
    set(value) {
      moduleNameProperty.setValue(this, value ?: "")
    }

  var programArguments: String?
    get() = programArgumentsProperty.getValue(this)
    set(value) {
      programArgumentsProperty.setValue(this, value ?: "")
    }
}
