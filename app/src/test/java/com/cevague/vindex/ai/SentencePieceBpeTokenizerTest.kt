package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Conventions validées empiriquement par le banc PC (SiglipBenchPc) contre les
 * exports SigLIP 2 réels : pas de <bos>, pas de minuscules, pas de dummy
 * prefix, <eos> collé aux tokens puis padding <pad>, masque tout à 1.
 * (La variante bos+minuscules+prefix a été mesurée moins discriminante.)
 */
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
    fun `fusions sur la sequence entiere avec frontiere de mot`() {
        // "cat cat" → "cat▁cat" → [cat][▁cat] + eos, pad 0 — pas de bos.
        val encoded = tokenizer().encode("cat cat")
        assertEquals(listOf(9L, 10L, 1L, 0L, 0L, 0L, 0L, 0L), encoded.ids.toList())
    }

    @Test
    fun `casse conservee - pas de minuscules`() {
        // "CAT" : aucun caractère majuscule dans le mini-vocab ni en byte
        // fallback → seuls eos+pads sortent. Si un lowercase se glissait dans
        // la normalisation, on obtiendrait [cat] comme pour "cat".
        val encoded = tokenizer().encode("CAT")
        assertEquals(1L, encoded.ids[0])
    }

    @Test
    fun `masque tout a 1 padding compris (convention SigLIP)`() {
        val encoded = tokenizer().encode("cat")
        assertEquals(List(8) { 1L }, encoded.attentionMask.toList())
    }

    @Test
    fun `byte fallback pour caractere hors vocabulaire`() {
        // "é" (U+00E9) absent du vocab → octets UTF-8 C3 A9, sans préfixe ▁.
        val encoded = tokenizer().encode("é")
        assertEquals(listOf(11L, 12L, 1L), encoded.ids.take(3))
    }

    @Test
    fun `troncature en gardant eos final`() {
        val encoded = tokenizer(4).encode("cat cat cat cat")
        assertEquals(listOf(9L, 10L, 10L, 1L), encoded.ids.toList())
    }

    @Test
    fun `chaine vide`() {
        val encoded = tokenizer().encode("  ")
        assertEquals(1L, encoded.ids[0]) // <eos> seul
        assertEquals(0L, encoded.ids[1]) // padding <pad>
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
