package com.karandaev.fieldfolio.core.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class LibraryPrimitivesTest {
  @Test
  fun cryptoIsAuthenticatedUsesFreshNoncesAndWipesPassword() {
    val password = "long password".toCharArray()
    val key = LibraryCrypto.derive(password, LibraryCrypto.salt())
    assertTrue(password.all { it == '\u0000' })
    val first = LibraryCrypto.seal("private".toByteArray(), key, "node")
    val second = LibraryCrypto.seal("private".toByteArray(), key, "node")
    assertFalse(first.contentEquals(second))
    assertEquals("private", LibraryCrypto.open(first, key, "node").toString(Charsets.UTF_8))
    assertThrows(LibraryPasswordException::class.java) {
      LibraryCrypto.open(first, key, "other node")
    }
    first[first.lastIndex] = (first.last().toInt() xor 1).toByte()
    assertThrows(LibraryPasswordException::class.java) { LibraryCrypto.open(first, key, "node") }
  }

  @Test
  fun archiveEnvelopeProtectsNamesAndRejectsWrongPasswordAndInvalidKdf() {
    val secret = "archive password".toCharArray()
    val bytes = LibraryCrypto.encryptArchive("manifest secret".toByteArray(), secret)
    assertTrue(secret.all { it == '\u0000' })
    assertTrue(LibraryCrypto.protected(bytes))
    assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("manifest secret"))
    assertThrows(LibraryPasswordException::class.java) {
      LibraryCrypto.decryptArchive(bytes, "wrong password".toCharArray())
    }
    assertEquals(
      "manifest secret",
      LibraryCrypto.decryptArchive(bytes, "archive password".toCharArray()).toString(Charsets.UTF_8),
    )
    bytes[8] = 127
    assertThrows(IllegalArgumentException::class.java) {
      LibraryCrypto.decryptArchive(bytes, "archive password".toCharArray())
    }
  }

  @Test
  fun treeNormalizesMixedSelectionsAndValidatesCyclesNamesAndMissingParents() {
    val tree =
      LibraryTree(
        listOf(
          LibraryNode(1, name = "F", folder = true),
          LibraryNode(2, 1, "S", true),
          LibraryNode(3, 2, "R", false),
        )
      )
    assertEquals(listOf(1L), tree.roots(listOf(1, 2, 3, 1)))
    assertEquals(listOf(1L, 2L, 3L), tree.subtree(listOf(1)).map { it.id })
    assertThrows(IllegalArgumentException::class.java) {
      LibraryTree(listOf(LibraryNode(1, 2, "A", true), LibraryNode(2, 1, "B", true)))
    }
    assertThrows(IllegalArgumentException::class.java) {
      LibraryTree(listOf(LibraryNode(1, 9, "A", true)))
    }
    for (name in listOf("", "..", "a/b", "a\\b", " x ", "x:y", "x\n")) assertThrows(
      IllegalArgumentException::class.java
    ) {
      LibraryTree.name(name)
    }
  }

  @Test
  fun zipPreservesFilesAndRejectsTraversalFileAncestorsAndLimits() {
    val expected =
      mapOf("manifest.json" to "{}".toByteArray(), "objects/1.bin" to byteArrayOf(1, 2, 3))
    val decoded = LibraryZip.unpack(LibraryZip.pack(expected))
    assertEquals(expected.keys, decoded.keys)
    expected.forEach { (k, v) -> assertArrayEquals(v, decoded[k]) }
    for (name in listOf("../bad", "/absolute", "a/../b", "a//b", "a\\b", "C:/x")) assertThrows(
      IllegalArgumentException::class.java
    ) {
      LibraryZip.pack(mapOf(name to byteArrayOf()))
    }
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
      for (name in listOf("a", "a/b")) {
        z.putNextEntry(ZipEntry(name))
        z.write(1)
        z.closeEntry()
      }
    }
    assertThrows(IllegalArgumentException::class.java) { LibraryZip.unpack(out.toByteArray()) }
    assertThrows(IllegalArgumentException::class.java) {
      LibraryZip.read(ByteArrayInputStream(ByteArray(100)), 99)
    }
    assertThrows(IllegalArgumentException::class.java) {
      LibraryZip.unpack(LibraryZip.pack(mapOf("a" to ByteArray(100))), 99)
    }
  }
}
