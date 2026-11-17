package com.karandaev.fieldfolio.core.names

data class ResourceIdentity(
  val name: String,
  val displayName: String? = null,
  val hideTitle: Boolean = false,
  val visible: Boolean = true,
) {
  val valid
    get() = ItemNames.valid(name) && (displayName.isNullOrBlank() || ItemNames.valid(displayName))

  fun normalized() =
    copy(name = ItemNames.requireName(name), displayName = ItemNames.display(displayName))
}
