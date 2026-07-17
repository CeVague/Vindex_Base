package com.cevague.vindex.ai

import java.io.File
import java.text.Normalizer

/**
 * Tokenizer SentencePiece **unigram** au format HuggingFace `tokenizer.json`,
 * tel qu'utilisé par les modèles de traduction Marian/OPUS-MT.
 *
 * Troisième famille après les deux BPE : ici le découpage ne suit pas des
 * fusions apprises mais choisit, parmi tous les découpages possibles, celui qui
 * **maximise la somme des log-probabilités** des pièces (programmation
 * dynamique de type Viterbi). Le vocabulaire porte donc un score par pièce, pas
 * des rangs de fusion.
 *
 * Conventions reproduites (Metaspace) : espaces remplacés par `▁`, préfixe `▁`
 * en tête — « chien plage » devient `▁chien▁plage` avant découpage. La
 * normalisation Precompiled de SentencePiece (charsmap NFKC) est approchée par
 * NFKC de `java.text.Normalizer` : fidèle pour du texte de requête courant,
 * seuls des caractères exotiques peuvent différer — ils tombent alors en
 * caractère inconnu, pénalisé mais pas bloquant.
 *
 * Le tokenizer ne pose **aucun token spécial** (ni eos, ni start de décodeur) :
 * c'est le moteur de traduction qui les ajoute, leurs ids venant du config.json
 * du modèle — les conventions seq2seq varient trop pour être devinées ici.
 */
class SentencePieceUnigramTokenizer(
    private val pieces: List<String>,
    private val scores: FloatArray,
    private val unkId: Int
) {
    init {
        require(pieces.size == scores.size) {
            "${pieces.size} pièces pour ${scores.size} scores"
        }
        require(unkId in pieces.indices) { "unk_id $unkId hors vocabulaire" }
    }

    private val idByPiece: Map<String, Int> = buildMap(pieces.size) {
        pieces.forEachIndexed { id, piece -> putIfAbsent(piece, id) }
    }

    private val maxPieceLength = pieces.maxOf { it.length }

    /**
     * Score d'un caractère inconnu : nettement sous le plancher du vocabulaire,
     * pour que le Viterbi ne choisisse le repli que faute de mieux — c'est la
     * convention de SentencePiece (unk = min des scores − marge).
     */
    private val unkScore = scores.min() - UNK_MARGIN

    fun idOf(piece: String): Int? = idByPiece[piece]

    /** Texte → ids de pièces, sans token spécial (cf. doc de classe). */
    fun encode(text: String): List<Int> {
        val sequence = metaspace(text)
        if (sequence.isEmpty()) return emptyList()

        val n = sequence.length
        // best[i] = meilleur score d'un découpage de sequence[0, i)
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val backPiece = IntArray(n + 1) { -1 }
        val backStart = IntArray(n + 1) { -1 }
        best[0] = 0f

        for (end in 1..n) {
            val windowStart = maxOf(0, end - maxPieceLength)
            for (start in windowStart until end) {
                if (best[start] == Float.NEGATIVE_INFINITY) continue
                val id = idByPiece[sequence.substring(start, end)] ?: continue
                val candidate = best[start] + scores[id]
                if (candidate > best[end]) {
                    best[end] = candidate
                    backPiece[end] = id
                    backStart[end] = start
                }
            }
            // Repli caractère inconnu : un pas d'un seul caractère, toujours
            // possible — garantit qu'un découpage existe pour toute entrée.
            if (best[end - 1] != Float.NEGATIVE_INFINITY) {
                val candidate = best[end - 1] + unkScore
                if (candidate > best[end]) {
                    best[end] = candidate
                    backPiece[end] = unkId
                    backStart[end] = end - 1
                }
            }
        }

        val reversed = mutableListOf<Int>()
        var position = n
        while (position > 0) {
            reversed.add(backPiece[position])
            position = backStart[position]
        }
        return reversed.asReversed()
    }

    /** Ids → texte : pièces concaténées, `▁` redevient espace, [skip] omis. */
    fun decode(ids: List<Int>, skip: Set<Int> = emptySet()): String =
        ids.asSequence()
            .filter { it !in skip && it in pieces.indices }
            .joinToString("") { pieces[it] }
            .replace(WORD_BOUNDARY, ' ')
            .trim()

    private fun metaspace(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(WHITESPACE, " ")
            .trim()
        if (normalized.isEmpty()) return ""
        return WORD_BOUNDARY + normalized.replace(' ', WORD_BOUNDARY)
    }

    companion object {
        const val WORD_BOUNDARY = '▁'
        private const val UNK_MARGIN = 10f
        private val WHITESPACE = Regex("\\s+")

        fun fromTokenizerJson(file: File): SentencePieceUnigramTokenizer =
            fromJson(file.readText())

        /**
         * Parse la section `model` d'un tokenizer.json unigram :
         * `{"model": {"type": "Unigram", "unk_id": N, "vocab": [["pièce", score], …]}}`.
         * Parser positionnel sans dépendance, comme les deux autres tokenizers
         * (les fichiers font des Mo, un DOM complet serait ruineux).
         */
        fun fromJson(json: String): SentencePieceUnigramTokenizer {
            val modelIdx = json.indexOf("\"model\":")
            require(modelIdx >= 0) { "section model absente du tokenizer.json" }

            val unkIdx = json.indexOf("\"unk_id\":", modelIdx)
            require(unkIdx >= 0) { "model.unk_id absent (tokenizer non unigram ?)" }
            val unkId = readInt(json, json.indexOf(':', unkIdx) + 1)

            val vocabIdx = json.indexOf("\"vocab\":", modelIdx)
            require(vocabIdx >= 0) { "section model.vocab absente" }

            val pieces = mutableListOf<String>()
            val scores = mutableListOf<Float>()
            var i = json.indexOf('[', vocabIdx) + 1
            var depth = 1
            var piece: String? = null
            while (i < json.length && depth > 0) {
                when (json[i]) {
                    '[' -> {
                        depth++
                        piece = null
                        i++
                    }

                    ']' -> {
                        depth--
                        i++
                    }

                    '"' -> {
                        val (value, next) = readJsonString(json, i)
                        if (depth == 2) piece = value
                        i = next
                    }

                    '-', in '0'..'9' -> {
                        val start = i
                        while (i < json.length && (json[i] == '-' || json[i] == '+' ||
                                    json[i] == '.' || json[i] == 'e' || json[i] == 'E' ||
                                    json[i].isDigit())
                        ) i++
                        if (depth == 2 && piece != null) {
                            pieces.add(piece)
                            scores.add(json.substring(start, i).toFloat())
                            piece = null
                        }
                    }

                    else -> i++
                }
            }
            require(pieces.isNotEmpty()) { "vocab unigram vide" }
            return SentencePieceUnigramTokenizer(pieces, scores.toFloatArray(), unkId)
        }

        private fun readInt(json: String, from: Int): Int {
            var i = from
            while (i < json.length && json[i] == ' ') i++
            val start = i
            while (i < json.length && (json[i] == '-' || json[i].isDigit())) i++
            return json.substring(start, i).toInt()
        }

        /** Lit une chaîne JSON depuis son guillemet ouvrant ; renvoie (valeur, index après). */
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
                        'f' -> sb.append(0x0C.toChar())
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
