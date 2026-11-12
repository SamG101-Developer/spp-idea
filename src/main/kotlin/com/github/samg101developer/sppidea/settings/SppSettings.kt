package com.github.samg101developer.sppidea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.io.File

// A named `spp` binary, e.g. "Windows" and "WSL", so that a
// run configuration can pick which one it uses.
@Tag("executable")
data class SppExecutable(var name: String = "", var path: String = "")

// Application-level service that persists the S++ settings
// across IDE restarts and can be accessed from anywhere in
// the IDE (the known `spp` executables, and the default one).
@Service(Service.Level.APP)
@State(name = "SppSettings", storages = [Storage("spp.xml", roamingType = RoamingType.PER_OS)])
class SppSettings : PersistentStateComponent<SppSettings.State> {

  class State {
    @get:XCollection(style = XCollection.Style.v2)
    var executables: MutableList<SppExecutable> = mutableListOf()

    // The name of the executable used when a run configuration
    // does not pick one, and outside of run configurations.
    var defaultExecutable: String = ""

    // The single path stored before there could be several; it is
    // turned into an executable named "Default" when loaded.
    var sppExecutablePath: String = ""
  }

  private var myState = State()

  var executables: List<SppExecutable>
    get() = myState.executables.map { it.copy() }
    set(value) {
      myState.executables = value.map { it.copy() }.toMutableList()
    }

  var defaultExecutable: String
    get() = myState.defaultExecutable
    set(value) {
      myState.defaultExecutable = value
    }

  // The executable with the given name, or the default one when
  // the name is blank. The default falls back to the first one,
  // so a single configured executable needs no extra setup.
  fun find(name: String?): SppExecutable? {
    val wanted = name?.takeIf { it.isNotBlank() } ?: defaultExecutable
    return myState.executables.firstOrNull { it.name == wanted }
      ?: myState.executables.firstOrNull().takeIf { name.isNullOrBlank() }
  }

  override fun getState(): State = myState

  override fun loadState(state: State) {
    if (state.executables.isEmpty() && state.sppExecutablePath.isNotBlank()) {
      state.executables.add(SppExecutable(LEGACY_NAME, state.sppExecutablePath))
      state.defaultExecutable = LEGACY_NAME
    }
    state.sppExecutablePath = ""
    myState = state
  }

  companion object {
    private const val LEGACY_NAME = "Default"

    @JvmStatic
    fun getInstance(): SppSettings = service()
  }
}

// Returns the path of the named `spp` executable (or of the
// default one, if the name is blank), or `null` if it isn't
// configured or doesn't point at an executable file.
fun resolveSppExecutable(name: String? = null): String? {
  val path = SppSettings.getInstance().find(name)?.path?.takeIf { it.isNotBlank() } ?: return null
  val file = File(path)
  return if (file.isFile && file.canExecute()) path else null
}

// The message shown when resolveSppExecutable(name) fails.
fun sppExecutableMissingMessage(name: String? = null): String =
  if (name.isNullOrBlank()) "The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++."
  else "The S++ executable '$name' is missing or not executable. Check it in Settings | Languages & Frameworks | S++."
