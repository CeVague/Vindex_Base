package com.cevague.vindex.ai

/**
 * Contrat commun des tokenizers d'encodeurs texte : le type est choisi par
 * `config.json` (`text.tokenizer`), le moteur ne connaît que cette interface.
 */
interface TextTokenizer {

    class Encoded(val ids: LongArray, val attentionMask: LongArray)

    fun encode(text: String): Encoded
}
