package com.cevague.vindex.ai

import java.io.File

/**
 * Tokenizer BPE de CLIP (phase 2 §4.5), équivalent du `SimpleTokenizer` du
 * dépôt openai/CLIP : minuscules, découpage par la regex CLIP, encodage
 * octets → caractères unicode (table GPT-2), fusions BPE avec marqueur de fin
 * de mot `</w>`, encadrement `<|startoftext|>` / `<|endoftext|>`, padding à
 * zéro jusqu'à `context_length` (77) avec masque d'attention.
 *
 * Le nettoyage ftfy/html du dépôt d'origine est omis : les requêtes sont
 * saisies au clavier, pas extraites de corpus web.
 */
class ClipBpeTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
    private val contextLength: Int
) : TextTokenizer {

    private val ranks: Map<Pair<String, String>, Int> =
        merges.withIndex().associate { (i, pair) -> pair to i }

    private val sotId = vocab.getValue(START_OF_TEXT)
    private val eotId = vocab.getValue(END_OF_TEXT)

    private val bpeCache = HashMap<String, List<String>>()

    override fun encode(text: String): TextTokenizer.Encoded {
        val clean = text.replace(WHITESPACE, " ").trim().lowercase()

        val ids = mutableListOf(sotId)
        for (match in PATTERN.findAll(clean)) {
            if (ids.size >= contextLength) break
            val byteEncoded = buildString {
                for (b in match.value.toByteArray(Charsets.UTF_8)) {
                    append(BYTE_ENCODER.getValue(b.toInt() and 0xFF))
                }
            }
            for (piece in bpe(byteEncoded)) {
                // Pièce hors vocabulaire (théoriquement impossible : tous les
                // octets simples y figurent) : ignorée plutôt que crash.
                vocab[piece]?.let { ids.add(it) }
            }
        }
        ids.add(eotId)

        val out = LongArray(contextLength)
        val mask = LongArray(contextLength)
        val n = minOf(ids.size, contextLength)
        for (i in 0 until n) {
            out[i] = ids[i].toLong()
            mask[i] = 1L
        }
        if (ids.size > contextLength) out[contextLength - 1] = eotId.toLong()
        return TextTokenizer.Encoded(out, mask)
    }

    /** Fusions BPE d'un mot déjà encodé en caractères-octets. */
    private fun bpe(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        bpeCache[token]?.let { return it }

        var word: MutableList<String> = buildList {
            token.dropLast(1).forEach { add(it.toString()) }
            add(token.last() + END_OF_WORD)
        }.toMutableList()

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
        bpeCache[token] = word
        return word
    }

    companion object {
        const val START_OF_TEXT = "<|startoftext|>"
        const val END_OF_TEXT = "<|endoftext|>"
        private const val END_OF_WORD = "</w>"

        private val WHITESPACE = Regex("\\s+")

        // Regex de découpage du SimpleTokenizer CLIP (contractions anglaises,
        // suites de lettres, chiffres isolés, ponctuation).
        private val PATTERN = Regex(
            """<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Table octet → caractère unicode imprimable de GPT-2 : les octets
         * « affichables » restent eux-mêmes, les autres sont décalés à 256+n.
         */
        private val BYTE_ENCODER: Map<Int, Char> = buildMap {
            val printable = (33..126) + (161..172) + (174..255)
            printable.forEach { put(it, it.toChar()) }
            var n = 0
            for (b in 0..255) {
                if (b !in printable) {
                    put(b, (256 + n).toChar())
                    n++
                }
            }
        }

        fun fromFiles(vocabFile: File, mergesFile: File, contextLength: Int): ClipBpeTokenizer =
            ClipBpeTokenizer(
                parseVocabJson(vocabFile.readText()),
                parseMerges(mergesFile.readText()),
                contextLength
            )

        /**
         * Parse un vocab.json (map plate token → id, ~49k entrées) sans
         * dépendance : org.json n'existe pas en test JVM et JSONObject est
         * inutilement coûteux pour une map plate.
         */
        fun parseVocabJson(json: String): Map<String, Int> {
            val map = HashMap<String, Int>(65536)
            var i = json.indexOf('{') + 1
            while (i in 1 until json.length) {
                while (i < json.length && json[i] != '"' && json[i] != '}') i++
                if (i >= json.length || json[i] == '}') break
                i++
                val key = StringBuilder()
                while (json[i] != '"') {
                    val c = json[i]
                    if (c == '\\') {
                        i++
                        when (val esc = json[i]) {
                            'u' -> {
                                key.append(json.substring(i + 1, i + 5).toInt(16).toChar())
                                i += 4
                            }

                            'n' -> key.append('\n')
                            't' -> key.append('\t')
                            'r' -> key.append('\r')
                            'b' -> key.append('\b')
                            'f' -> key.append('\u000C')
                            else -> key.append(esc) // \" \\ \/
                        }
                    } else {
                        key.append(c)
                    }
                    i++
                }
                i++
                while (json[i] != ':') i++
                i++
                while (json[i] == ' ') i++
                val start = i
                while (json[i] == '-' || json[i].isDigit()) i++
                map[key.toString()] = json.substring(start, i).toInt()
            }
            return map
        }

        fun parseMerges(text: String): List<Pair<String, String>> =
            text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val space = line.indexOf(' ')
                    if (space <= 0) null
                    else line.substring(0, space) to line.substring(space + 1).trim()
                }
                .toList()
    }
}
