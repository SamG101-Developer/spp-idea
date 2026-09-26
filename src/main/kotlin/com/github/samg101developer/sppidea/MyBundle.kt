package com.github.samg101developer.sppidea

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.MyBundle"

// The MyBundle object, which is used to retrieve localised
// messages from the MyBundle.properties file. This is used
// for internationalisation (i18n) of the plugin, allowing
// it to support multiple languages. The messages are defined
// in the MyBundle.properties file, and can be accessed using
// the message() and messagePointer() methods
object MyBundle : DynamicBundle(BUNDLE) {
  // The message() method retrieves a localised message from the
  // MyBundle.properties file, given a key and optional parameters.
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    getMessage(key, *params)

  // The messagePointer() method retrieves a localised message
  // from the MyBundle.properties file, given a key and optional
  // parameters, and returns a lazy message pointer. This is
  // useful for cases where the message may not be needed
  // immediately, and can be deferred until it is actually required.
  @Suppress("unused")
  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    getLazyMessage(key, *params)
}
