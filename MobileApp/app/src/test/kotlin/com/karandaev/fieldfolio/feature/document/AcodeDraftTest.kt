package com.karandaev.fieldfolio.feature.document

import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AcodeDraftViewModel
import org.junit.Assert.*
import org.junit.Test

class AcodeDraftTest {
  @Test
  fun reopeningTheSameCompositionDoesNotOverwriteUnsavedTextAndFinishClearsIt() {
    val state = AcodeDraftViewModel()
    state.open("original", "a.txt")
    state.text = "private draft"
    state.anchor = 3
    state.open("original", "a.txt")
    assertEquals("private draft", state.text)
    assertEquals(3, state.anchor)
    state.finish()
    assertEquals("", state.text)
    state.open("next", "b.json")
    assertEquals("next", state.text)
    assertEquals("b.json", state.name)
  }
}
