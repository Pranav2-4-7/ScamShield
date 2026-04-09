package com.scamshield.ml

import android.content.Context
import android.util.Log
import com.scamshield.utils.AlertSeverity
import com.scamshield.utils.ScamPattern
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device scam detection using TFLite model + rule-based fallback.
 * No data leaves the device. No cloud APIs called.
 * Inference results are ephemeral - never persisted.
 */
class ScamDetectionEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScamDetectionEngine"
        private const val MODEL_FILE = "scam_detection.tflite"
        private const val VOCAB_FILE = "vocab.txt"
        private const val MAX_SEQUENCE_LENGTH = 128
        private const val INFERENCE_THRESHOLD = 0.65f
    }

    private var interpreter: Interpreter? = null
    private var vocabulary: Map<String, Int> = emptyMap()
    private var isModelLoaded = false

    // Rule-based patterns as fallback (and primary until model loads)
    private val scamPatterns: Map<ScamPattern, List<Regex>> = buildPatternMap()

    fun initialize() {
        loadModel()
        loadVocabulary()
    }

    private fun loadModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = true // Use Neural Network API for hardware acceleration
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.d(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not found, using rule-based detection only: ${e.message}")
            isModelLoaded = false
        }
    }

    private fun loadVocabulary() {
        try {
            val vocabLines = context.assets.open(VOCAB_FILE)
                .bufferedReader()
                .readLines()
            vocabulary = vocabLines.mapIndexed { index, word -> word.lowercase() to index }.toMap()
            Log.d(TAG, "Vocabulary loaded: ${vocabulary.size} tokens")
        } catch (e: Exception) {
            Log.w(TAG, "Vocabulary file not found: ${e.message}")
        }
    }

    /**
     * Analyze a transcript chunk and return detection results.
     * Input text is used only for inference - never stored.
     */
    fun analyze(text: String): DetectionResult {
        if (text.isBlank()) return DetectionResult(0f, emptySet())

        val normalizedText = preprocessText(text)

        // Run ML inference if model is available
        val mlScore = if (isModelLoaded) {
            runMLInference(normalizedText)
        } else {
            0f
        }

        // Always run rule-based detection (complementary to ML)
        val detectedPatterns = runRuleBasedDetection(normalizedText)
        val ruleScore = calculateRuleScore(detectedPatterns)

        // Combine scores (max of both approaches)
        val finalScore = maxOf(mlScore, ruleScore)

        return DetectionResult(
            riskScore = finalScore,
            detectedPatterns = detectedPatterns,
            mlScore = mlScore,
            ruleScore = ruleScore
        )
    }

    private fun runMLInference(text: String): Float {
        val interpreter = this.interpreter ?: return 0f

        return try {
            // Tokenize text to integer IDs
            val tokenIds = tokenize(text)

            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 4).apply {
                order(ByteOrder.nativeOrder())
                for (id in tokenIds) putInt(id)
                // Pad remaining
                repeat(MAX_SEQUENCE_LENGTH - tokenIds.size) { putInt(0) }
                rewind()
            }

            // Prepare output buffer (binary classification: scam/not-scam)
            val outputBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }

            interpreter.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            val score = outputBuffer.float
            Log.d(TAG, "ML inference score: $score")
            score
        } catch (e: Exception) {
            Log.e(TAG, "ML inference error: ${e.message}")
            0f
        }
    }

    private fun tokenize(text: String): List<Int> {
        val words = text.lowercase().split("\\s+".toRegex())
        return words.take(MAX_SEQUENCE_LENGTH).map { word ->
            vocabulary[word] ?: vocabulary["[UNK]"] ?: 1
        }
    }

    private fun preprocessText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun runRuleBasedDetection(text: String): Set<ScamPattern> {
        val detected = mutableSetOf<ScamPattern>()

        for ((pattern, regexList) in scamPatterns) {
            if (regexList.any { it.containsMatchIn(text) }) {
                detected.add(pattern)
                Log.d(TAG, "Rule-based pattern detected: ${pattern.displayName}")
            }
        }

        return detected
    }

    private fun calculateRuleScore(patterns: Set<ScamPattern>): Float {
        if (patterns.isEmpty()) return 0f

        // Weight critical patterns higher
        val criticalPatterns = setOf(
            ScamPattern.OTP_REQUEST,
            ScamPattern.BANK_IMPERSONATION,
            ScamPattern.GOVERNMENT_IMPERSONATION,
            ScamPattern.PERSONAL_INFO_REQUEST
        )

        val criticalCount = patterns.count { it in criticalPatterns }
        val normalCount = patterns.size - criticalCount

        return ((criticalCount * 0.35f) + (normalCount * 0.15f)).coerceAtMost(1.0f)
    }

    fun getSeverity(score: Float): AlertSeverity = when {
        score >= 0.85f -> AlertSeverity.CRITICAL
        score >= 0.65f -> AlertSeverity.HIGH
        score >= 0.40f -> AlertSeverity.MEDIUM
        else -> AlertSeverity.LOW
    }

    private fun buildPatternMap(): Map<ScamPattern, List<Regex>> {
        val opts = setOf(RegexOption.IGNORE_CASE)
        return mapOf(
            ScamPattern.OTP_REQUEST to listOf(
                Regex("\\botp\\b", opts),
                Regex("\\bone.?time.?password\\b", opts),
                Regex("\\bverification.?code\\b", opts),
                Regex("\\bshare.?the.?code\\b", opts),
                Regex("\\benter.?the.?code\\b", opts),
                Regex("\\bcode.?expire\\b", opts),
                Regex("\\bpin.?number\\b", opts),
                Regex("\\bauth.?code\\b", opts)
            ),
            ScamPattern.BANK_IMPERSONATION to listOf(
                Regex("\\bcalling.?from.?(sbi|hdfc|icici|axis|kotak|yes.?bank|pnb|bank)\\b", opts),
                Regex("\\bbank.?(official|officer|manager|executive)\\b", opts),
                Regex("\\baccount.?(blocked|frozen|suspend|deactivat)\\b", opts),
                Regex("\\bkyc.?(update|verify|expire|pending)\\b", opts),
                Regex("\\btransaction.?(fraud|suspicious|alert|block)\\b", opts),
                Regex("\\bcredit.?card.?(block|fraud|suspicious)\\b", opts),
                Regex("\\bunauthorized.?(transaction|access|login)\\b", opts)
            ),
            ScamPattern.GOVERNMENT_IMPERSONATION to listOf(
                Regex("\\b(cbi|ced|income.?tax|customs|narcotics|cyber.?crime).?(officer|official|department)\\b", opts),
                Regex("\\barrested?|arrest.?warrant\\b", opts),
                Regex("\\bpolice.?(complaint|case|fir)\\b", opts),
                Regex("\\bjudge|magistrate|court.?order\\b", opts),
                Regex("\\btelecom.?department|trai\\b", opts),
                Regex("\\baadhaar.?(link|block|suspend|fraud)\\b", opts),
                Regex("\\bdigital.?arrest\\b", opts),
                Regex("\\bmoney.?laundering\\b", opts),
                Regex("\\bnarcotics.?case\\b", opts)
            ),
            ScamPattern.URGENCY_TACTIC to listOf(
                Regex("\\bact.?now|immediately|urgent(ly)?\\b", opts),
                Regex("\\b(last|final).?chance\\b", opts),
                Regex("\\bwithin.?(24|48).?hour\\b", opts),
                Regex("\\bexpire.?(today|tonight|now)\\b", opts),
                Regex("\\bimmediately.?transfer\\b", opts),
                Regex("\\bdo.?not.?tell.?anyone\\b", opts),
                Regex("\\bkeep.?this.?confidential\\b", opts),
                Regex("\\bdon.?t.?disconnect\\b", opts)
            ),
            ScamPattern.PRIZE_SCAM to listOf(
                Regex("\\bwon.?(prize|lottery|reward)\\b", opts),
                Regex("\\bcongratulation.*(prize|win|lucky)\\b", opts),
                Regex("\\blucky.?(draw|winner|number)\\b", opts),
                Regex("\\bclaim.?(prize|reward|amount)\\b", opts),
                Regex("\\bkbc|kaun.?banega.?crorepati\\b", opts)
            ),
            ScamPattern.TECH_SUPPORT to listOf(
                Regex("\\byour.?(device|computer|phone|system).*(virus|hack|comprom|infect)\\b", opts),
                Regex("\\bmicrosoft|apple.?(technician|support|engineer)\\b", opts),
                Regex("\\bremote.?(access|control|assist)\\b", opts),
                Regex("\\banydesk|teamviewer|quick.?support\\b", opts),
                Regex("\\binstall.?app.?right.?now\\b", opts)
            ),
            ScamPattern.REFUND_SCAM to listOf(
                Regex("\\brefund.*(pending|process|transfer)\\b", opts),
                Regex("\\b(excess|extra).?amount.*(deduct|charge)\\b", opts),
                Regex("\\bcashback.*(transfer|pending|credit)\\b", opts),
                Regex("\\bcompensation.*(transfer|receive)\\b", opts)
            ),
            ScamPattern.PERSONAL_INFO_REQUEST to listOf(
                Regex("\\bshare.*(aadhaar|aadhar|pan.?card|passport)\\b", opts),
                Regex("\\bverify.*(date.?of.?birth|dob|address)\\b", opts),
                Regex("\\bconfirm.*(account.?number|ifsc)\\b", opts),
                Regex("\\bgive.*(password|pin|cvv|secret)\\b", opts),
                Regex("\\baadhaar.?number\\b", opts)
            ),
            ScamPattern.KYC_SCAM to listOf(
                Regex("\\bkyc.*(expire|update|verify|pending|incomplete)\\b", opts),
                Regex("\\baccount.*(close|block).*(kyc)\\b", opts),
                Regex("\\bcomplete.?kyc.?(immediately|now|today)\\b", opts),
                Regex("\\bvideo.?kyc\\b", opts)
            ),
            ScamPattern.REMOTE_ACCESS to listOf(
                Regex("\\bdownload.*(anydesk|teamviewer|screenshare)\\b", opts),
                Regex("\\binstall.*(app|application).*(verify|bank|secure)\\b", opts),
                Regex("\\bscreen.?(share|mirror|cast)\\b", opts),
                Regex("\\bgive.?(remote|access|control)\\b", opts)
            )
        )
    }

    fun destroy() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "ScamDetectionEngine destroyed")
    }
}

data class DetectionResult(
    val riskScore: Float,
    val detectedPatterns: Set<ScamPattern>,
    val mlScore: Float = 0f,
    val ruleScore: Float = 0f
)
