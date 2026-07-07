package com.cevague.vindex.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Banc de validation PC des exports SigLIP2 int8 (diagnostic hors appareil).
 * Écrit ses résultats dans bench_results.txt à côté des modèles. Ignoré si les
 * fichiers ne sont pas présents (dossier scratchpad local, non commité).
 */
class SiglipBenchPc {

    private val dir = File(
        "C:\\Users\\ADMINI~1\\AppData\\Local\\Temp\\claude\\C--Users-Administrateur-AndroidStudioProjects-Vindex\\e24bef3a-4134-42a9-9af2-3dae3ce3919c\\scratchpad"
    )
    private val modelDir = File(dir, "SigLIP2-base-384")
    private val report = StringBuilder()

    @Test
    fun bench() {
        assumeTrue(modelDir.exists() && File(dir, "bench_cat.rgb").exists())
        val env = OrtEnvironment.getEnvironment()

        val textSession = env.createSession(File(modelDir, "text_encoder.onnx").absolutePath)
        val visionSession = env.createSession(File(modelDir, "image_encoder.onnx").absolutePath)

        report.appendLine("=== TEXT MODEL I/O ===")
        textSession.inputInfo.forEach { (k, v) -> report.appendLine("  in : $k -> ${v.info}") }
        textSession.outputInfo.forEach { (k, v) -> report.appendLine("  out: $k -> ${v.info}") }
        report.appendLine("=== VISION MODEL I/O ===")
        visionSession.inputInfo.forEach { (k, v) -> report.appendLine("  in : $k -> ${v.info}") }
        visionSession.outputInfo.forEach { (k, v) -> report.appendLine("  out: $k -> ${v.info}") }

        val json = File(modelDir, "tokenizer.json").readText()
        val vocab = SentencePieceBpeTokenizer.parseModelVocab(json)
        val merges = SentencePieceBpeTokenizer.parseModelMerges(json)
        val ranks = merges.withIndex().associate { (i, p) -> p to i }

        fun bpe(seq: String): List<String> {
            var word = seq.map { it.toString() }.toMutableList()
            while (word.size > 1) {
                var best: Pair<String, String>? = null
                var bestRank = Int.MAX_VALUE
                for (i in 0 until word.size - 1) {
                    val r = ranks[word[i] to word[i + 1]] ?: continue
                    if (r < bestRank) { bestRank = r; best = word[i] to word[i + 1] }
                }
                val (a, b) = best ?: break
                val m = mutableListOf<String>()
                var i = 0
                while (i < word.size) {
                    if (i < word.size - 1 && word[i] == a && word[i + 1] == b) { m.add(a + b); i += 2 }
                    else { m.add(word[i]); i++ }
                }
                word = m
            }
            return word
        }

        fun pieceIds(normalized: String): List<Int> = bpe(normalized).flatMap { piece ->
            vocab[piece]?.let { listOf(it) }
                ?: piece.toByteArray(Charsets.UTF_8)
                    .map { b -> vocab["<0x%02X>".format(b.toInt() and 0xFF)] }
                    .filterNotNull()
        }

        val eos = vocab.getValue("<eos>")
        val bos = vocab.getValue("<bos>")
        val ctx = 64

        // Variante A : fidèle au tokenizer.json (pas de bos, pas de lowercase,
        // pas de dummy prefix, eos collé aux tokens, pad 0 ensuite)
        fun idsA(text: String): LongArray {
            val ids = pieceIds(text.trim().replace(Regex("\\s+"), " ").replace(' ', '▁'))
                .take(ctx - 1) + eos
            return LongArray(ctx) { i -> (ids.getOrNull(i) ?: 0).toLong() }
        }

        // Variante B : implémentation actuelle de l'app (lowercase + dummy
        // prefix + bos, eos épinglé en dernière position, pad 0 entre les deux)
        fun idsB(text: String): LongArray {
            val norm = "▁" + text.lowercase().trim().replace(Regex("\\s+"), " ").replace(' ', '▁')
            val ids = (listOf(bos) + pieceIds(norm)).take(ctx - 1)
            val out = LongArray(ctx)
            ids.forEachIndexed { i, v -> out[i] = v.toLong() }
            out[ctx - 1] = eos.toLong()
            return out
        }

        fun runText(ids: LongArray, maskAllOnes: Boolean): Map<String, FloatArray> {
            val inputs = mutableMapOf<String, OnnxTensor>()
            val idsName = textSession.inputInfo.keys.first { it.contains("input_ids") }
            inputs[idsName] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ctx.toLong()))
            textSession.inputInfo.keys.firstOrNull { it.contains("attention_mask") }?.let { maskName ->
                val mask = if (maskAllOnes) LongArray(ctx) { 1L }
                else LongArray(ctx) { i -> if (ids[i] != 0L) 1L else 0L }
                inputs[maskName] = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, ctx.toLong()))
            }
            val outputs = mutableMapOf<String, FloatArray>()
            textSession.run(inputs).use { result ->
                for (entry in result) {
                    val t = entry.value as OnnxTensor
                    val shape = t.info.shape
                    val dim = shape.last().toInt()
                    val arr = FloatArray(dim)
                    // 3D : première position ; 2D : le vecteur
                    t.floatBuffer.get(arr)
                    outputs[entry.key + shape.contentToString()] = l2(arr)
                }
            }
            inputs.values.forEach { it.close() }
            return outputs
        }

        // Fichier .rgb : 384×384 pixels RGB entrelacés, déjà écrasés en carré.
        fun imageTensor(file: File): OnnxTensor {
            val bytes = file.readBytes()
            val size = 384
            val area = size * size
            val floats = FloatArray(3 * area)
            for (i in 0 until area) {
                floats[i] = ((bytes[i * 3].toInt() and 0xFF) / 255f - 0.5f) / 0.5f
                floats[area + i] = ((bytes[i * 3 + 1].toInt() and 0xFF) / 255f - 0.5f) / 0.5f
                floats[2 * area + i] = ((bytes[i * 3 + 2].toInt() and 0xFF) / 255f - 0.5f) / 0.5f
            }
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), longArrayOf(1, 3, 384, 384))
        }

        fun runVision(file: File): Map<String, FloatArray> {
            val inputName = visionSession.inputInfo.keys.first()
            val tensor = imageTensor(file)
            val outputs = mutableMapOf<String, FloatArray>()
            visionSession.run(mapOf(inputName to tensor)).use { result ->
                for (entry in result) {
                    val t = entry.value as OnnxTensor
                    val shape = t.info.shape
                    val dim = shape.last().toInt()
                    val arr = FloatArray(dim)
                    t.floatBuffer.get(arr)
                    outputs[entry.key + shape.contentToString()] = l2(arr)
                }
            }
            tensor.close()
            return outputs
        }

        val catOut = runVision(File(dir, "bench_cat.rgb"))
        val dogOut = runVision(File(dir, "bench_dog.rgb"))

        val texts = listOf("a cat", "a dog", "a car", "chat", "un chat", "une voiture")
        for (variant in listOf("A-onesMask", "A-realMask", "B-onesMask")) {
            report.appendLine("\n=== VARIANTE $variant ===")
            for (text in texts) {
                val ids = if (variant.startsWith("A")) idsA(text) else idsB(text)
                val textOut = runText(ids, maskAllOnes = variant.endsWith("onesMask"))
                report.appendLine("texte '$text' ids=${ids.take(8)}")
                for ((tKey, tVec) in textOut) {
                    for ((iKey, iVec) in catOut) {
                        if (tVec.size != iVec.size) continue
                        val simCat = dot(tVec, iVec)
                        val simDog = dot(tVec, dogOut.getValue(iKey))
                        report.appendLine(
                            "  [$tKey x $iKey] cat=%.4f dog=%.4f".format(simCat, simDog)
                        )
                    }
                }
            }
        }

        File(dir, "bench_results.txt").writeText(report.toString())
        textSession.close()
        visionSession.close()
    }

    private fun l2(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x * x.toDouble()
        val n = sqrt(s).toFloat()
        if (n > 0) for (i in v.indices) v[i] /= n
        return v
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
