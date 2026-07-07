package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePieceBpeTokenizerTest {

    // Mini-vocabulaire de test ; ▁ = frontière de mot SentencePiece.
    private val vocab = mapOf(
        "<pad>" to 0, "<eos>" to 1, "<bos>" to 2, "<unk>" to 3,
        "▁" to 4, "c" to 5, "a" to 6, "t" to 7,
        "ca" to 8, "cat" to 9, "▁cat" to 10,
        "<0xC3>" to 11, "<0xA9>" to 12
    )
    private val merges = listOf("c" to "a", "ca" to "t", "▁" to "cat")

    private fun tokenizer(context: Int = 8) = SentencePieceBpeTokenizer(vocab, merges, context)

    @Test
    fun `normalisation avec dummy prefix et minuscules`() {
        // "CAT" -> "▁cat" -> [bos, ▁cat, eos, pad, ...]
        val encoded = tokenizer().encode("CAT")
        assertEquals(listOf(2L, 10L, 1L, 0L, 0L, 0L, 0L, 0L), encoded.ids.toList())
    }

    @Test
    fun `fusions sur la sequence entiere avec dummy prefix`() {
        // "cat cat" -> "▁cat▁cat" -> [bos, ▁cat, ▁cat, eos, pad, ...]
        val encoded = tokenizer().encode("cat cat")
        assertEquals(listOf(2L, 10L, 10L, 1L, 0L, 0L, 0L, 0L), encoded.ids.toList())
    }

    @Test
    fun `masque tout a 1 padding compris (convention SigLIP)`() {
        val encoded = tokenizer().encode("cat")
        // Masque de 1 pour [bos, ▁cat, eos] et 0 pour le reste
        assertEquals(listOf(1L, 1L, 1L, 0L, 0L, 0L, 0L, 0L), encoded.attentionMask.toList())
    }

    @Test
    fun `byte fallback pour caractere hors vocabulaire`() {
        // "é" (U+00E9) absent du vocab -> octets UTF-8 C3 A9
        // Normalisé en "▁é". "▁" a l'ID 4.
        val encoded = tokenizer().encode("é")
        // [bos, ▁, <0xC3>, <0xA9>, eos, pad, ...]
        assertEquals(listOf(2L, 4L, 11L, 12L, 1L, 0L, 0L, 0L), encoded.ids.toList())
    }

    @Test
    fun `troncature en gardant bos et eos final`() {
        // context = 4. [bos, ▁cat, ▁cat, eos]
        val encoded = tokenizer(4).encode("cat cat cat cat")
        assertEquals(listOf(2L, 10L, 10L, 1L), encoded.ids.toList())
    }

    @Test
    fun `sticky eos padding`() {
        val encoded = tokenizer(6).encode("cat")
        // [bos, ▁cat, eos, pad, pad, pad]
        assertEquals(listOf(2L, 10L, 1L, 0L, 0L, 0L), encoded.ids.toList())
    }

    // ------------------------------------------------ parseurs tokenizer.json

    @Test
    fun `parse vocab depuis la section model`() {
        val json = """{"version":"1.0","added_tokens":[{"id":0,"content":"<pad>"}],
            "model":{"type":"BPE","vocab":{"<pad>":0,"a":1,"▁cat":2},"merges":[]}}"""
        val vocab = SentencePieceBpeTokenizer.parseModelVocab(json)
        assertEquals(3, vocab.size)
        assertEquals(2, vocab["▁cat"])
    }

    @Test
    fun `parse merges au format tableau de paires`() {
        val json = """{"model":{"vocab":{"a":0},"merges":[["a","b"],["▁","cat"]]}}"""
        val merges = SentencePieceBpeTokenizer.parseModelMerges(json)
        assertEquals(listOf("a" to "b", "▁" to "cat"), merges)
    }

    @Test
    fun `parse merges a l ancien format chaines`() {
        val json = """{"model":{"vocab":{"a":0},"merges":["a b","c d"]}}"""
        val merges = SentencePieceBpeTokenizer.parseModelMerges(json)
        assertEquals(listOf("a" to "b", "c" to "d"), merges)
    }
}
