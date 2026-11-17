package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.names.ResourceIdentity
import com.karandaev.fieldfolio.core.ui.components.fields.*
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
internal fun rememberNodeIdentity(node: DocumentNode): MutableState<ResourceIdentity> =
  rememberSession(node.id) { mutableStateOf(node.identity) }

@Composable
internal fun NodeIdentityFields(
  value: ResourceIdentity,
  changed: (ResourceIdentity) -> Unit,
  kind: BlockKind,
) =
  ResourceIdentityFields(
    value,
    changed,
    file = kind in setOf(BlockKind.IMAGE, BlockKind.AUDIO, BlockKind.TEXT_FILE),
    presentation = false,
  )

@Composable
internal fun NodeIdentityDialog(
  node: DocumentNode,
  dismiss: () -> Unit,
  creating: Boolean = false,
  save: (DocumentNode) -> Unit,
) =
  ResourceIdentityDialog(
    node.identity,
    dismiss,
    file = node.kind in setOf(BlockKind.IMAGE, BlockKind.AUDIO, BlockKind.TEXT_FILE),
    presentation = creating,
    showVisibility = creating,
    title = if (creating) tr("add") + " · " + node.kind.label() else tr("rename"),
  ) {
    save(it.applyTo(node))
  }
