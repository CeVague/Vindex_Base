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
    val detection: DetectionConfig?
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
                detection = root.optJSONObject("detection")?.let { parseDetection(it) }
            )
            config.validate()
            return config
        }

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
