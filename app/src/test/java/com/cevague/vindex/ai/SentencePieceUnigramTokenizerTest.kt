package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du tokenizer unigram (Viterbi) sur vocabulaire synthétique : le
 * découpage doit maximiser la somme des scores, pas suivre le plus long
 * préfixe — c'est ce qui le distingue d'un matching glouton.
 */
class SentencePieceUnigramTokenizerTest {

    private val boundary = SentencePieceUnigramTokenizer.WORD_BOUNDARY

    /** Vocabulaire jouet : ids stables par position. */
    private fun tokenizer(vararg entries: Pair<String, Float>): SentencePieceUnigramTokenizer =
        SentencePieceUnigramTokenizer(
            pieces = listOf("<unk>") + entries.map { it.first },
            scores = floatArrayOf(0f) + entries.map { it.second }.toFloatArray(),
            unkId = 0
        )

    @Test
    fun `le mot entier gagne quand son score bat la somme des morceaux`() {
        val t = tokenizer(
            "${boundary}chien" to -1f,   // id 1
            "${boundary}ch" to -0.5f,    // id 2
            "ien" to -0.7f               // id 3 : somme -1.2 < -1.0
        )
        assertEquals(listOf(1), t.encode("chien"))
    }

    @Test
    fun `le decoupage optimal n est pas le glouton`() {
        // Un glouton plus-long-préfixe prendrait "▁abc" (-5) puis "d" (-5) = -10 ;
        // l'optimal est "▁ab" (-1) + "cd" (-1) = -2.
        val t = tokenizer(
            "${boundary}abc" to -5f,  // id 1
            "d" to -5f,               // id 2
            "${boundary}ab" to -1f,   // id 3
            "cd" to -1f               // id 4
        )
        assertEquals(listOf(3, 4), t.encode("abcd"))
    }

    @Test
    fun `metaspace - chaque mot recoit son marqueur de frontiere`() {
        val t = tokenizer(
            "${boundary}chien" to -1f,  // id 1
            "${boundary}plage" to -1f   // id 2
        )
        assertEquals(listOf(1, 2), t.encode("chien plage"))
        // Espaces multiples et bords : mêmes tokens.
        assertEquals(listOf(1, 2), t.encode("  chien   plage "))
    }

    @Test
    fun `caractere inconnu tombe sur unk sans bloquer le reste`() {
        val t = tokenizer("${boundary}chat" to -1f) // id 1
        val ids = t.encode("chat☂")
        assertEquals(listOf(1, 0), ids)
    }

    @Test
    fun `decode - concatene, retablit les espaces, saute les ids demandes`() {
        val t = tokenizer(
            "${boundary}chien" to -1f,  // id 1
            "${boundary}plage" to -1f,  // id 2
            "</s>" to 0f                // id 3
        )
        assertEquals("chien plage", t.decode(listOf(1, 2, 3), skip = setOf(3)))
        // Un id hors vocabulaire est ignoré plutôt que de planter.
        assertEquals("chien", t.decode(listOf(1, 999)))
    }

    @Test
    fun `round trip encode puis decode`() {
        val t = tokenizer(
            "${boundary}coucher" to -1f,
            "${boundary}de" to -1f,
            "${boundary}soleil" to -1f
        )
        assertEquals("coucher de soleil", t.decode(t.encode("coucher de soleil")))
    }

    @Test
    fun `nfkc - une saisie decomposee retrouve la piece composee`() {
        // "café" avec e + accent combinant (NFD) doit matcher la pièce NFC.
        val t = tokenizer("${boundary}café" to -1f)
        assertEquals(listOf(1), t.encode("café"))
    }

    @Test
    fun `fromJson - vocab unigram parse (scores negatifs, echappements)`() {
        val json = """
            {
              "version": "1.0",
              "model": {
                "type": "Unigram",
                "unk_id": 1,
                "vocab": [
                  ["</s>", 0.0],
                  ["<unk>", -2.5],
                  ["▁chien", -3.25],
                  ["▁plage", -4.5e0]
                ]
              }
            }
        """.trimIndent()
        val t = SentencePieceUnigramTokenizer.fromJson(json)
        assertEquals(0, t.idOf("</s>"))
        assertEquals(2, t.idOf("${boundary}chien"))
        assertEquals(listOf(2, 3), t.encode("chien plage"))
        // Caractère hors vocabulaire → unk_id du fichier (1).
        assertTrue(t.encode("zzz").all { it == 1 })
    }
}
