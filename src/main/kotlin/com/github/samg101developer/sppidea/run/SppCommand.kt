package com.github.samg101developer.sppidea.run

/** The `spp` subcommands the plugin knows how to invoke. */
enum class SppCommand(val cliArg: String, val displayName: String) {
    BUILD("build", "S++ Build"),
    RUN("run", "S++ Run"),
    TEST("test", "S++ Test"),
}
