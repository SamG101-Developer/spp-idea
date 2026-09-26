package com.github.samg101developer.sppidea.run

// The `spp` subcommands the plugin knows how to invoke.
enum class SppCommand(val cliArg: String) {
  BUILD("build"),
  RUN("run"),
  TEST("test"),
}

// The kinds of run configs that the plugin offers. Note
// that there is no separate "build" vs "run" configuration;
// provided with one source, there is a build and run action.
enum class SppConfigurationKind(
  val factoryId: String,
  val displayName: String,
  val runCommand: SppCommand,
  val buildable: Boolean,
  val nameSuffix: String,
) {
  // `spp run` (which builds first), or `spp build` when
  // launched from the Build action.
  SOURCE("SOURCE", "S++", SppCommand.RUN, buildable = true, nameSuffix = ""),

  // `spp test` builds and runs the tests itself, so there
  // is nothing for a Build action to do.
  TEST("TEST", "S++ Test", SppCommand.TEST, buildable = false, nameSuffix = "-test"),
}
