package com.cevague.vindex.ai

import java.io.File

/**
 * Tokenizer SentencePiece-BPE au format HuggingFace `tokenizer.json`, tel
 * qu'utilisé par SigLIP 2 (tokenizer Gemma, vocab 256k). Reproduit exactement
 * ce que déclare le fichier du modèle — conventions VALIDÉES par le banc PC
 * (SiglipBenchPc) sur les exports réels, en comparant les variantes :
 *
 * - normalizer `Replace " " → "▁"` uniquement : PAS de minuscules, PAS de
 *   dummy prefix, PAS de <bos> (tokenizer_config : add_bos_token=false) ;
 * - BPE glouton par rangs de fusion sur la séquence entière (le pre_tokenizer
 *   Split(" ") est sans effet après le Replace) ;
 * - `byte_fallback` : caractère hors vocabulaire → tokens `<0xNN>` ;
 * - post-processing : `<eos>` immédiatement après les tokens, puis padding
 *   `<pad>` (id 0) jusqu'à `context_length` (64). Le pooler SigLIP lit la
 *   dernière position (du padding) : c'est voulu, l'attention est
 *   bidirectionnelle et non masquée, la position finale agrège tout.
 *
 * Masque d'attention : tout à 1, padding compris — les modèles texte SigLIP
 * sont entraînés sans masquage du padding (l'export deux-tours n'a d'ailleurs
 * pas d'entrée attention_mask).
 */
class SentencePieceBpeTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
    private val contextLength: Int
) : TextTokenizer {

    private val ranks: Map<Pair<String, String>, Int> =
        merges.withIndex().associate { (i, pair) -> pair to i }

    private val eosId = vocab.getValue(END_OF_SEQUENCE)
    private val padId = vocab.getValue(PAD)

    private val bpeCache = HashMap<String, List<String>>()

    override fun encode(text: String): TextTokenizer.Encoded {
        val normalized = text.replace(WHITESPACE, " ").trim().replace(' ', WORD_BOUNDARY)

        val ids = mutableListOf<Int>()
        for (piece in bpe(normalized)) {
            if (ids.size >= contextLength - 1) break
            val id = vocab[piece]
            if (id != null) {
                ids.add(id)
            } else {
                // byte_fallback : la pièce (forcément un caractère isolé, les
                // pièces multi-caractères viennent des fusions donc du vocab)
                // est émise octet par octet en tokens <0xNN>.
                for (b in piece.toByteArray(Charsets.UTF_8)) {
                    if (ids.size >= contextLength - 1) break
                    vocab[byteToken(b)]?.let { ids.add(it) }
                }
            }
        }
        ids.add(eosId)

        val out = LongArray(contextLength) { padId.toLong() }
        for (i in ids.indices) out[i] = ids[i].toLong()
        // Tout à 1, padding compris (cf. doc de classe).
        val mask = LongArray(contextLength) { 1L }
        return TextTokenizer.Encoded(out, mask)
    }

    /** BPE glouton par rangs sur la séquence entière. */
    private fun bpe(sequence: String): List<String> {
        if (sequence.isEmpty()) return emptyList()
        bpeCache[sequence]?.let { return it }

        var word: MutableList<String> = sequence.map { it.toString() }.toMutableList()

        while (word.size > 1) {
            var best: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until word.size - 1) {
                val rank = ranks[word[i] to word[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    best = word[i] to word[i + 1]
                }
            }
            val (first, second) = best ?: break
            val merged = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    merged.add(first + second)
                    i += 2
                } else {
                    merged.add(word[i])
                    i++
                }
            }
            word = merged
        }
        bpeCache[sequence] = word
        return word
    }

    companion object {
        const val END_OF_SEQUENCE = "<eos>"
        const val PAD = "<pad>"
        const val WORD_BOUNDARY = '▁'

        private val WHITESPACE = Regex("\\s+")

        private fun byteToken(b: Byte): String =
            "<0x%02X>".format(b.toInt() and 0xFF)

        /** Charge vocab et merges depuis un `tokenizer.json` HuggingFace. */
        fun fromTokenizerJson(file: File, contextLength: Int): SentencePieceBpeTokenizer {
            val json = file.readText()
            return SentencePieceBpeTokenizer(
                parseModelVocab(json),
                parseModelMerges(json),
                contextLength
            )
        }

        /**
         * Extrait la map plate `model.vocab` du tokenizer.json. Réutilise le
         * parser de map plate de [ClipBpeTokenizer] en le positionnant sur la
         * section (le fichier fait ~33 Mo, un DOM JSON complet serait ruineux).
         */
        fun parseModelVocab(json: String): Map<String, Int> {
            val modelIdx = json.indexOf("\"model\":")
            require(modelIdx >= 0) { "section model absente du tokenizer.json" }
            val vocabIdx = json.indexOf("\"vocab\":", modelIdx)
            require(vocabIdx >= 0) { "section model.vocab absente" }
            return ClipBpeTokenizer.parseVocabJson(json.substring(vocabIdx))
        }

        /**
         * Extrait `model.merges` : tableau de paires `["a", "b"]` (format
         * tokenizers récent) ou de chaînes `"a b"` (ancien format).
         */
        fun parseModelMerges(json: String): List<Pair<String, String>> {
            val modelIdx = json.indexOf("\"model\":")
            val mergesIdx = json.indexOf("\"merges\":", modelIdx)
            require(mergesIdx >= 0) { "section model.merges absente" }

            val merges = mutableListOf<Pair<String, String>>()
            var i = json.indexOf('[', mergesIdx) + 1
            var depth = 1
            var pair = mutableListOf<String>()
            while (i < json.length && depth > 0) {
                when (json[i]) {
                    '[' -> {
                        depth++
                        pair = mutableListOf()
                        i++
                    }
                    ']' -> {
                        if (depth == 2 && pair.size == 2) merges.add(pair[0] to pair[1])
                        depth--
                        i++
                    }
                    '"' -> {
                        val (value, next) = readJsonString(json, i)
                        if (depth == 2) {
                            pair.add(value)
                        } else {
                            // ancien format : "a b" directement dans le tableau
                            val space = value.indexOf(' ')
                            if (space > 0) {
                                merges.add(value.substring(0, space) to value.substring(space + 1))
                            }
                        }
                        i = next
                    }
                    else -> i++
                }
            }
            return merges
        }

        /** Lit une chaîne JSON à partir du guillemet ouvrant ; renvoie (valeur, index après). */
        private fun readJsonString(json: String, openQuote: Int): Pair<String, Int> {
            val sb = StringBuilder()
            var i = openQuote + 1
            while (json[i] != '"') {
                val c = json[i]
                if (c == '\\') {
                    i++
                    when (val esc = json[i]) {
                        'u' -> {
                            sb.append(json.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            return sb.toString() to i + 1
        }
    }
}
