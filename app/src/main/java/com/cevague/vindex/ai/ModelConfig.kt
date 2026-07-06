package com.cevague.vindex.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Config d'accompagnement d'un modèle importé (`config.json` à la racine du
 * dossier du modèle), recopiée telle quelle dans `ai_models.config_json`.
 *
 * Schéma pensé pour rester universel :
 * - `files` associe un **rôle** (image_encoder, text_encoder, tokenizer_vocab…)
 *   à un nom de fichier du dossier — de nouveaux rôles s'ajoutent sans casser
 *   les configs existantes ;
 * - `text.tokenizer` et `text.languages` déclarent comment et dans quelles
 *   langues l'encodeur texte comprend les requêtes : un CLIP anglais dit
 *   `["en"]` (traduction nécessaire pour le français), un encodeur multilingue
 *   listera ses langues et court-circuitera la traduction. Le pipeline décide
 *   à la requête, rien n'est câblé en dur dans l'app.
 *
 * Exemple (CLIP ViT-B/32) :
 * ```json
 * {
 *   "schema_version": 1,
 *   "model_type": "clip",
 *   "display_name": "CLIP ViT-B/32 (OpenAI)",
 *   "files": {
 *     "image_encoder": "image_encoder.onnx",
 *     "text_encoder": "text_encoder.onnx",
 *     "tokenizer_vocab": "vocab.json",
 *     "tokenizer_merges": "merges.txt"
 *   },
 *   "embedding_dim": 512,
 *   "image": {
 *     "input_size": 224,
 *     "interpolation": "bicubic",
 *     "mean": [0.48145466, 0.4578275, 0.40821073],
 *     "std": [0.26862954, 0.26130258, 0.27577711]
 *   },
 *   "text": {
 *     "tokenizer": "clip_bpe",
 *     "context_length": 77,
 *     "languages": ["en"]
 *   }
 * }
 * ```
 */
data class ModelConfig(
    val schemaVersion: Int,
    val modelType: String,
    val displayName: String?,
    /** Rôle → nom de fichier relatif au dossier du modèle. */
    val files: Map<String, String>,
    val embeddingDim: Int?,
    val inputSize: Int?,
    val interpolation: String?,
    val mean: List<Double>?,
    val std: List<Double>?,
    val tokenizer: String?,
    val contextLength: Int?,
    /** Langues comprises par l'encodeur texte (codes ISO 639-1). */
    val languages: List<String>,
    /** JSON d'origine, stocké verbatim dans ai_models.config_json. */
    val rawJson: String
) {

    /** L'encodeur texte comprend-il [languageCode] sans traduction préalable ? */
    fun supportsLanguage(languageCode: String): Boolean =
        languages.any { it.equals(languageCode, ignoreCase = true) }

    companion object {
        const val CONFIG_FILE_NAME = "config.json"

        // Rôles de fichiers connus (extensibles par les phases futures)
        const val FILE_IMAGE_ENCODER = "image_encoder"
        const val FILE_TEXT_ENCODER = "text_encoder"
        const val FILE_TOKENIZER_VOCAB = "tokenizer_vocab"
        const val FILE_TOKENIZER_MERGES = "tokenizer_merges"

        // Tokenizers déclarables ; seuls les implémentés sont acceptés à
        // l'inférence, mais la config en accepte d'autres (universalité).
        const val TOKENIZER_CLIP_BPE = "clip_bpe"
        const val TOKENIZER_SENTENCEPIECE = "sentencepiece"

        /**
         * Parse et valide un config.json. Lance [IllegalArgumentException] avec
         * un message exploitable si un champ obligatoire manque ou est incohérent.
         */
        fun parse(json: String): ModelConfig {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                throw IllegalArgumentException("JSON invalide : ${e.message}")
            }

            val modelType = root.optString("model_type")
            require(modelType.isNotBlank()) { "champ model_type manquant" }

            val filesObj = root.optJSONObject("files")
                ?: throw IllegalArgumentException("champ files manquant")
            val files = buildMap {
                for (role in filesObj.keys()) put(role, filesObj.getString(role))
            }
            require(files.isNotEmpty()) { "files est vide" }

            val image = root.optJSONObject("image")
            val text = root.optJSONObject("text")

            val config = ModelConfig(
                schemaVersion = root.optInt("schema_version", 1),
                modelType = modelType,
                displayName = root.optString("display_name").takeIf { it.isNotBlank() },
                files = files,
                embeddingDim = root.optInt("embedding_dim").takeIf { it > 0 },
                inputSize = image?.optInt("input_size")?.takeIf { it > 0 },
                interpolation = image?.optString("interpolation")?.takeIf { it.isNotBlank() },
                mean = image?.optJSONArray("mean")?.toDoubleList(),
                std = image?.optJSONArray("std")?.toDoubleList(),
                tokenizer = text?.optString("tokenizer")?.takeIf { it.isNotBlank() },
                contextLength = text?.optInt("context_length")?.takeIf { it > 0 },
                languages = text?.optJSONArray("languages")?.toStringList() ?: listOf("en"),
                rawJson = json
            )
            config.validate()
            return config
        }

        private fun ModelConfig.validate() {
            if (modelType == "clip") {
                requireFile(FILE_IMAGE_ENCODER)
                requireFile(FILE_TEXT_ENCODER)
                requireNotNull(embeddingDim) { "embedding_dim manquant" }
                requireNotNull(inputSize) { "image.input_size manquant" }
                require(mean?.size == 3) { "image.mean doit avoir 3 valeurs" }
                require(std?.size == 3) { "image.std doit avoir 3 valeurs" }
                requireNotNull(tokenizer) { "text.tokenizer manquant" }
                requireNotNull(contextLength) { "text.context_length manquant" }
            }
        }

        private fun ModelConfig.requireFile(role: String) =
            require(role in files) { "files.$role manquant" }

        private fun JSONArray.toDoubleList(): List<Double> =
            (0 until length()).map { getDouble(it) }

        private fun JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }
    }
}
