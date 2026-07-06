package com.cevague.vindex.ai

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.File

/**
 * Validation d'un fichier .onnx à l'import (phase 2 §4.1) : la session doit
 * s'ouvrir et exposer au moins une entrée et une sortie. L'inférence de fumée
 * sur image de test arrive avec le moteur d'inférence (lot suivant).
 */
object OnnxValidator {

    /** Renvoie un message d'erreur, ou null si le modèle est valide. */
    fun validate(file: File): String? {
        return try {
            val env = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                env.createSession(file.absolutePath, options).use { session ->
                    when {
                        session.inputInfo.isEmpty() -> "aucune entrée (${file.name})"
                        session.outputInfo.isEmpty() -> "aucune sortie (${file.name})"
                        else -> null
                    }
                }
            }
        } catch (e: OrtException) {
            "${file.name} : ${e.message}"
        }
    }
}
