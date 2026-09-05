package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions

class SppRunConfigurationOptions : LocatableRunConfigurationOptions() {
    private var myModuleName by string("")
    private var myProgramArguments by string("")

    var moduleName: String?
        get() = myModuleName
        set(value) {
            myModuleName = value ?: ""
        }

    var programArguments: String?
        get() = myProgramArguments
        set(value) {
            myProgramArguments = value ?: ""
        }
}
