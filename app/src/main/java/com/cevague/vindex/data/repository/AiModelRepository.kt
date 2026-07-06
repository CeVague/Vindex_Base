package com.cevague.vindex.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.cevague.vindex.ai.ModelConfig
import com.cevague.vindex.ai.OnnxValidator
import com.cevague.vindex.data.database.dao.AiModelDao
import com.cevague.vindex.data.database.entity.AiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Échec d'import d'un modèle ; [reason] est traduit par l'UI, [detail] complète. */
class ModelImportException(
    val reason: Reason,
    val detail: String? = null
) : Exception("$reason${detail?.let { ": $it" } ?: ""}") {
    enum class Reason { NO_CONFIG, INVALID_CONFIG, MISSING_FILE, INVALID_MODEL, ALREADY_EXISTS, IO_ERROR }
}

/**
 * Gestion des modèles d'IA importés (phase 2 §4.1). Un modèle = un dossier
 * `files/models/<type>/<nom>/` contenant les fichiers déclarés par son
 * config.json ; la config est recopiée dans `ai_models.config_json` à l'import.
 */
@Singleton
class AiModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiModelDao: AiModelDao
) {

    fun getAllModels(): Flow<List<AiModel>> = aiModelDao.getAllModels()

    fun getActiveModel(type: String): Flow<AiModel?> = aiModelDao.getActiveModel(type)

    suspend fun getActiveModelOnce(type: String): AiModel? = aiModelDao.getActiveModelOnce(type)

    /** Activation exclusive par type. */
    suspend fun activate(model: AiModel) {
        aiModelDao.deactivateAllOfType(model.modelType)
        aiModelDao.activate(model.id)
    }

    suspend fun delete(model: AiModel) {
        model.modelPath?.let { path ->
            val dir = File(path)
            // Garde-fou : ne supprime que sous files/models/
            if (dir.canonicalPath.startsWith(modelsRoot().canonicalPath)) {
                dir.deleteRecursively()
            }
        }
        aiModelDao.deleteById(model.id)
    }

    /**
     * Importe le dossier SAF [treeUri] : lecture et validation du config.json,
     * copie des fichiers déclarés, validation des .onnx (session ouvrable),
     * insertion en base. Auto-activation si aucun modèle actif de ce type.
     * Lance [ModelImportException] en cas d'échec, sans laisser de copie partielle.
     */
    suspend fun importFromFolder(treeUri: Uri): AiModel = withContext(Dispatchers.IO) {
        val entries = listFolder(treeUri)
        val configEntry = entries[ModelConfig.CONFIG_FILE_NAME]
            ?: throw ModelImportException(ModelImportException.Reason.NO_CONFIG)

        val rawConfig = readText(configEntry)
        val config = try {
            ModelConfig.parse(rawConfig)
        } catch (e: IllegalArgumentException) {
            throw ModelImportException(ModelImportException.Reason.INVALID_CONFIG, e.message)
        }

        val modelName = config.displayName ?: folderDisplayName(treeUri)
        if (aiModelDao.getByTypeAndName(config.modelType, modelName) != null) {
            throw ModelImportException(ModelImportException.Reason.ALREADY_EXISTS, modelName)
        }

        config.files.values.firstOrNull { it !in entries }?.let { missing ->
            throw ModelImportException(ModelImportException.Reason.MISSING_FILE, missing)
        }

        val destDir = File(modelsRoot(), "${sanitize(config.modelType)}/${sanitize(modelName)}")
        destDir.deleteRecursively()
        try {
            destDir.mkdirs()
            val toCopy = config.files.values.toSet() + ModelConfig.CONFIG_FILE_NAME
            for (fileName in toCopy) {
                copyToFile(entries.getValue(fileName), File(destDir, fileName))
            }
            for (onnxFile in config.files.values.filter { it.endsWith(".onnx") }.toSet()) {
                OnnxValidator.validate(File(destDir, onnxFile))?.let { error ->
                    throw ModelImportException(ModelImportException.Reason.INVALID_MODEL, error)
                }
            }

            val size = destDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val model = AiModel(
                modelType = config.modelType,
                modelName = modelName,
                modelPath = destDir.absolutePath,
                modelSize = size,
                configJson = rawConfig,
                addedAt = System.currentTimeMillis()
            )
            val id = aiModelDao.insert(model)
            val inserted = model.copy(id = id)
            if (aiModelDao.getActiveModelOnce(config.modelType) == null) activate(inserted)
            inserted
        } catch (e: Exception) {
            destDir.deleteRecursively()
            when (e) {
                is CancellationException, is ModelImportException -> throw e
                else -> throw ModelImportException(ModelImportException.Reason.IO_ERROR, e.message)
            }
        }
    }

    private fun modelsRoot(): File = File(context.filesDir, MODELS_DIR)

    /** Fichiers (non-dossiers) à la racine du dossier SAF : nom → URI document. */
    private fun listFolder(treeUri: Uri): Map<String, Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val result = mutableMapOf<String, Uri>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                result[cursor.getString(1)] =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
            }
        }
        return result
    }

    private fun folderDisplayName(treeUri: Uri): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        context.contentResolver.query(
            docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return "model"
    }

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw ModelImportException(ModelImportException.Reason.IO_ERROR, uri.toString())

    private fun copyToFile(uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw ModelImportException(ModelImportException.Reason.IO_ERROR, dest.name)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val MODELS_DIR = "models"
    }
}
