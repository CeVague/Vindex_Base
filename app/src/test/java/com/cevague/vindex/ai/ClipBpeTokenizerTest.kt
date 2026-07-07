package com.cevague.vindex.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 * Tests avec le vocabulaire et les merges RÉELS de CLIP (openai/clip-vit-base-
 * patch32, gzippés en ressources de test) : c'est le composant le plus délicat
 * de la phase 2, un tokenizer faux = des embeddings faux en silence.
 */
class ClipBpeTokenizerTest {

    companion object {
        private const val SOT = 49406L
        private const val EOT = 49407L
        private const val CONTEXT = 77

        private lateinit var vocab: Map<String, Int>
        private lateinit var tokenizer: ClipBpeTokenizer

        private fun resource(name: String): String =
            GZIPInputStream(
                ClipBpeTokenizerTest::class.java.getResourceAsStream("/clip/$name.gz")!!
            ).readBytes().decodeToString()

        @JvmStatic
        @BeforeClass
        fun setUp() {
            vocab = ClipBpeTokenizer.parseVocabJson(resource("vocab.json"))
            tokenizer = ClipBpeTokenizer(
                vocab,
                ClipBpeTokenizer.parseMerges(resource("merges.txt")),
                CONTEXT
            )
        }
    }

    @Test
    fun `vocabulaire complet et tokens speciaux aux bons ids`() {
        assertEquals(49408, vocab.size)
        assertEquals(SOT.toInt(), vocab[ClipBpeTokenizer.START_OF_TEXT])
        assertEquals(EOT.toInt(), vocab[ClipBpeTokenizer.END_OF_TEXT])
    }

    @Test
    fun `mots courants fusionnes en tokens pleins`() {
        // Chaque mot courant doit converger vers son entrée « mot</w> » du
        // vocabulaire — vérifie l'algorithme de fusion de bout en bout.
        val encoded = tokenizer.encode("a photo of a cat")
        val expected = longArrayOf(
            SOT,
            vocab.getValue("a</w>").toLong(),
            vocab.getValue("photo</w>").toLong(),
            vocab.getValue("of</w>").toLong(),
            vocab.getValue("a</w>").toLong(),
            vocab.getValue("cat</w>").toLong(),
            EOT
        )
        assertEquals(expected.toList(), encoded.ids.take(expected.size))
    }

    @Test
    fun `sortie de longueur fixe avec padding zero et masque`() {
        val encoded = tokenizer.encode("dog")
        assertEquals(CONTEXT, encoded.ids.size)
        assertEquals(CONTEXT, encoded.attentionMask.size)
        assertEquals(SOT, encoded.ids[0])
        assertEquals(vocab.getValue("dog</w>").toLong(), encoded.ids[1])
        assertEquals(EOT, encoded.ids[2])
        assertEquals(0L, encoded.ids[3])
        assertEquals(listOf(1L, 1L, 1L, 0L), encoded.attentionMask.take(4))
    }

    @Test
    fun `insensible a la casse`() {
        assertEquals(
            tokenizer.encode("a CAT").ids.toList(),
            tokenizer.encode("a cat").ids.toList()
        )
    }

    @Test
    fun `accents et utf-8 multi-octets encodes sans perte ni crash`() {
        val encoded = tokenizer.encode("chien à Noël")
        // Tous les ids émis existent dans le vocabulaire.
        val emitted = encoded.ids.takeWhile { it != 0L }
        assertTrue(emitted.size > 3)
        assertTrue(emitted.all { it in 0..49407L })
        assertEquals(SOT, emitted.first())
        assertEquals(EOT, emitted.last())
    }

    @Test
    fun `texte trop long tronque avec eot final`() {
        val encoded = tokenizer.encode(List(200) { "photo" }.joinToString(" "))
        assertEquals(CONTEXT, encoded.ids.size)
        assertEquals(EOT, encoded.ids[CONTEXT - 1])
        assertEquals(1L, encoded.attentionMask[CONTEXT - 1])
    }

    @Test
    fun `chaine vide`() {
        val encoded = tokenizer.encode("   ")
        assertEquals(SOT, encoded.ids[0])
        assertEquals(EOT, encoded.ids[1])
        assertEquals(0L, encoded.ids[2])
    }

    @Test
    fun `contraction anglaise decoupee par la regex`() {
        // "dog's" → dog + 's (la regex CLIP isole les contractions)
        val encoded = tokenizer.encode("dog's")
        assertEquals(vocab.getValue("dog</w>").toLong(), encoded.ids[1])
        assertEquals(vocab.getValue("'s</w>").toLong(), encoded.ids[2])
    }
}
