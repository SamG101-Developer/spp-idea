package com.github.samg101developer.sppidea.run

/** The `spp` subcommands the plugin knows how to invoke. */
enum class SppCommand(val cliArg: String) {
    BUILD("build"),
    RUN("run"),
    TEST("test"),
}

/**
 * The kinds of run configuration the plugin offers. There is deliberately no separate "build"
 * configuration: a source configuration is one thing that can be either built or run, the way a
 * CLion target is, so building and running share a module, arguments and a name.
 */
enum class SppConfigurationKind(
    val factoryId: String,
    val displayName: String,
    val runCommand: SppCommand,
    val buildable: Boolean,
    val nameSuffix: String,
) {
    /** `spp run` (which builds first), or `spp build` when launched from the Build action. */
    SOURCE("SOURCE", "S++", SppCommand.RUN, buildable = true, nameSuffix = ""),

    /** `spp test` builds and runs the tests itself, so there is nothing for a Build action to do. */
    TEST("TEST", "S++ Test", SppCommand.TEST, buildable = false, nameSuffix = "-test"),
}
