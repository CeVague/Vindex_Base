package com.cevague.vindex.ai

import com.cevague.vindex.data.database.entity.AiModel
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
    val channelOrder: String?,
    val normalization: String?,
    val mean: List<Double>?,
    val std: List<Double>?,
    val tokenizer: String?,
    val contextLength: Int?,
    /** Langues comprises par l'encodeur texte (codes ISO 639-1). */
    val languages: List<String>,
    /** Gabarit optionnel appliqué au texte libre (« a photo of {} »). */
    val promptTemplate: String?,
    /** Redimensionnement image : centre recadré (CLIP) ou écrasé en carré (SigLIP). */
    val resizeMode: String,
    /** Seuil de pertinence propre au modèle (les échelles varient : CLIP ≠ SigLIP). */
    val similarityFloor: Float?,
    /** JSON d'origine, stocké verbatim dans ai_models.config_json. */
    val rawJson: String,
    /** Configuration pour la détection de visage **/
    val detection: DetectionConfig?,
    /** Configuration pour la traduction de requêtes (seq2seq ONNX). */
    val translation: TranslationConfig?
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
        const val FILE_TOKENIZER_JSON = "tokenizer_json"
        const val FILE_FACE_EMBEDDER = "face_embedder"
        const val FILE_FACE_DETECTOR = "face_detector"
        const val FILE_TRANSLATION_ENCODER = "translation_encoder"
        const val FILE_TRANSLATION_DECODER = "translation_decoder"


        // Tokenizers implémentés
        const val TOKENIZER_CLIP_BPE = "clip_bpe"
        const val TOKENIZER_SENTENCEPIECE = "sentencepiece"

        // Modes de redimensionnement image
        const val RESIZE_CENTER_CROP = "center_crop"
        const val RESIZE_SQUASH = "squash"

        // Modes de gestion des couleurs dans l'image
        const val CHANNEL_RGB = "rgb"
        const val CHANNEL_BGR = "bgr"

        // Modes de normalisation de l'image
        const val NORM_MEAN_STD = "mean_std"
        const val NORM_NONE = "none"

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
                channelOrder = image?.optString("channel_order")?.takeIf { it.isNotBlank() }
                    ?: CHANNEL_RGB,
                normalization = image?.optString("normalization")?.takeIf { it.isNotBlank() }
                    ?: NORM_MEAN_STD,
                mean = image?.optJSONArray("mean")?.toDoubleList(),
                std = image?.optJSONArray("std")?.toDoubleList(),
                tokenizer = text?.optString("tokenizer")?.takeIf { it.isNotBlank() },
                contextLength = text?.optInt("context_length")?.takeIf { it > 0 },
                languages = text?.optJSONArray("languages")?.toStringList() ?: listOf("en"),
                promptTemplate = text?.optString("prompt_template")?.takeIf { it.isNotBlank() },
                resizeMode = image?.optString("resize")?.takeIf { it.isNotBlank() }
                    ?: RESIZE_CENTER_CROP,
                similarityFloor = root.optDouble("similarity_floor")
                    .takeIf { !it.isNaN() }?.toFloat(),
                rawJson = json,
                detection = root.optJSONObject("detection")?.let { parseDetection(it) },
                translation = root.optJSONObject("translation")?.let { parseTranslation(it) }
            )
            config.validate()
            return config
        }

        private fun parseTranslation(translation: JSONObject): TranslationConfig {
            return TranslationConfig(
                sourceLanguages = translation.optJSONArray("source_languages")?.toStringList()
                    ?: emptyList(),
                targetLanguage = translation.optString("target_language"),
                decoderStartTokenId = translation.optInt("decoder_start_token_id", -1),
                eosTokenId = translation.optInt("eos_token_id", -1),
                maxOutputTokens = translation.optInt("max_output_tokens")
                    .takeIf { it > 0 } ?: DEFAULT_MAX_OUTPUT_TOKENS
            )
        }

        /** Une requête traduite tient largement là-dedans ; borne la boucle de décodage. */
        const val DEFAULT_MAX_OUTPUT_TOKENS = 48

        private fun parseDetection(detection: JSONObject): DetectionConfig {
            return DetectionConfig(
                strides = detection.optJSONArray("strides")?.toIntList() ?: emptyList(),
                classLayer = detection.optString("class_layer"),
                objectLayer = detection.optString("object_layer"),
                boxLayer = detection.optString("box_layer"),
                landmarkLayer = detection.optString("landmark_layer"),
                landmarks = detection.optJSONArray("landmarks")?.toStringList() ?: emptyList(),
                scoreThreshold = detection.optDouble("score_threshold").toFloat(),
                nmsThreshold = detection.optDouble("nms_threshold").toFloat(),
                minFaceSize = detection.optInt("min_face_size").takeIf { it > 0 }
            )
        }

        private fun ModelConfig.validate() {
            when (modelType) {
                AiModel.TYPE_CLIP -> {
                    requireFile(FILE_IMAGE_ENCODER)
                    requireFile(FILE_TEXT_ENCODER)
                    requireNotNull(embeddingDim) { "embedding_dim manquant" }
                    requireNotNull(inputSize) { "image.input_size manquant" }
                    require(mean?.size == 3) { "image.mean doit avoir 3 valeurs" }
                    require(std?.size == 3) { "image.std doit avoir 3 valeurs" }
                    requireNotNull(contextLength) { "text.context_length manquant" }
                    when (tokenizer) {
                        TOKENIZER_CLIP_BPE -> {
                            requireFile(FILE_TOKENIZER_VOCAB)
                            requireFile(FILE_TOKENIZER_MERGES)
                        }

                        TOKENIZER_SENTENCEPIECE -> requireFile(FILE_TOKENIZER_JSON)
                        null -> throw IllegalArgumentException("text.tokenizer manquant")
                        else -> throw IllegalArgumentException("tokenizer inconnu : $tokenizer")
                    }
                    require(resizeMode == RESIZE_CENTER_CROP || resizeMode == RESIZE_SQUASH) {
                        "image.resize inconnu : $resizeMode"
                    }
                }

                AiModel.TYPE_FACE_DETECTION -> {
                    requireFile(FILE_FACE_DETECTOR)
                    requireNotNull(inputSize) { "image.input_size manquant" }
                    require(normalization == NORM_NONE) { "image.normalization doit être none" }
                    require(mean.isNullOrEmpty()) { "image.mean doit être vide" }
                    require(std.isNullOrEmpty()) { "image.std doit être vide" }
                    requireNotNull(detection) { "detection manquant" }
                    require(detection.strides.isNotEmpty()) { "detection.strides doit contenir au moins une valeur" }
                    require(detection.classLayer.isNotBlank()) { "detection.class_layer manquant" }
                    require(detection.objectLayer.isNotBlank()) { "detection.object_layer manquant" }
                    require(detection.boxLayer.isNotBlank()) { "detection.box_layer manquant" }
                    require(detection.landmarkLayer.isNotBlank()) { "detection.landmark_layer manquant" }
                    require(detection.landmarks.size == 5) { "detection.landmarks doit contenir 5 valeurs" }
                    require(detection.scoreThreshold in 0f..1f) { "detection.score_threshold doit être entre 0 et 1" }
                    require(detection.nmsThreshold in 0f..1f) { "detection.nms_threshold doit être entre 0 et 1" }
                }

                AiModel.TYPE_FACE_EMBEDDING -> {
                    requireFile(FILE_FACE_EMBEDDER)
                    requireNotNull(embeddingDim) { "embedding_dim manquant" }
                    requireNotNull(inputSize) { "image.input_size manquant" }
                    require(mean?.size == 3) { "image.mean doit avoir 3 valeurs" }
                    require(std?.size == 3) { "image.std doit avoir 3 valeurs" }

                }

                AiModel.TYPE_TRANSLATION -> {
                    requireFile(FILE_TRANSLATION_ENCODER)
                    requireFile(FILE_TRANSLATION_DECODER)
                    requireFile(FILE_TOKENIZER_JSON)
                    requireNotNull(translation) { "translation manquant" }
                    require(translation.sourceLanguages.isNotEmpty()) {
                        "translation.source_languages doit contenir au moins une langue"
                    }
                    require(translation.targetLanguage.isNotBlank()) {
                        "translation.target_language manquant"
                    }
                    // Les conventions seq2seq varient trop pour être devinées :
                    // les ids sont déclarés par le modèle, pas déduits.
                    require(translation.decoderStartTokenId >= 0) {
                        "translation.decoder_start_token_id manquant"
                    }
                    require(translation.eosTokenId >= 0) {
                        "translation.eos_token_id manquant"
                    }
                }

                else -> throw IllegalArgumentException("model_type inconnu : $modelType")
            }
        }

        private fun ModelConfig.requireFile(role: String) =
            require(role in files) { "files.$role manquant" }

        private fun JSONArray.toDoubleList(): List<Double> =
            (0 until length()).map { getDouble(it) }

        private fun JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }

        private fun JSONArray.toIntList(): List<Int> =
            (0 until length()).map { getInt(it) }
    }
}

/**
 * Config d'un modèle de traduction seq2seq ONNX (encodeur + décodeur exportés
 * séparément, style Marian/OPUS-MT). Les ids spéciaux viennent du config.json
 * HuggingFace du modèle d'origine (`decoder_start_token_id`, `eos_token_id`).
 */
data class TranslationConfig(
    /** Langues source comprises (ISO 639-1) — un modèle bilingue en liste une. */
    val sourceLanguages: List<String>,
    val targetLanguage: String,
    val decoderStartTokenId: Int,
    val eosTokenId: Int,
    val maxOutputTokens: Int
)

data class DetectionConfig(
    val strides: List<Int>,
    val classLayer: String,
    val objectLayer: String,
    val boxLayer: String,
    val landmarkLayer: String,
    val landmarks: List<String>,
    val scoreThreshold: Float,
    val nmsThreshold: Float,
    val minFaceSize: Int?
)
