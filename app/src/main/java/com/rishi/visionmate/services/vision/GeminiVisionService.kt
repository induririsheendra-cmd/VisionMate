package com.rishi.visionmate.services.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.rishi.visionmate.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiVisionService {

    private val apiKey: String = BuildConfig.GEMINI_API_KEY

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )
    }

    private var chatSession: Chat? = null
    private var lastAnalyzedBitmap: Bitmap? = null

    suspend fun analyzeScene(bitmap: Bitmap, customPrompt: String? = null): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured in local.properties.")
            )
        }

        try {
            lastAnalyzedBitmap = bitmap
            val promptText = customPrompt ?: """
                You are VisionMate, an AI companion helping a blind or visually impaired person understand their surroundings.
                Describe what is visible in this camera image in 2 to 3 concise, natural sentences suitable for speech output.
                - Use clear spatial references (e.g., 'in front of you', 'to your left', 'on the table').
                - Use uncertainty-aware language (e.g., 'appears to be', 'looks like', 'there seems to be').
                - Never claim perfect safety or that 'your path is completely clear'.
                - Focus on key objects, obstacles, people, or furniture nearby.
            """.trimIndent()

            // Initialize new chat session with system context
            chatSession = generativeModel.startChat()

            val inputContent = content {
                image(bitmap)
                text(promptText)
            }

            val response = chatSession?.sendMessage(inputContent)
            val description = response?.text?.trim()

            if (!description.isNullOrBlank()) {
                Result.success(description)
            } else {
                Result.failure(Exception("AI model returned an empty response."))
            }
        } catch (e: Exception) {
            Log.e("GeminiVisionService", "Error analyzing scene", e)
            Result.failure(e)
        }
    }

    suspend fun askFollowUp(userQuestion: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured in local.properties.")
            )
        }

        try {
            val session = chatSession
            if (session != null) {
                val followUpPrompt = """
                    The user is asking a follow-up question about the previously analyzed image/context.
                    User Question: "$userQuestion"
                    Provide a concise, helpful 2-to-3 sentence response suitable for speech output.
                    If the user asks about medicine usage, dosage, or medical advice, answer general information factually but remind them to verify with a healthcare professional or pharmacist.
                """.trimIndent()

                val response = session.sendMessage(followUpPrompt)
                val replyText = response.text?.trim()

                if (!replyText.isNullOrBlank()) {
                    return@withContext Result.success(replyText)
                }
            }

            // Fallback if chatSession was lost but bitmap exists
            val bitmap = lastAnalyzedBitmap
            if (bitmap != null) {
                return@withContext analyzeScene(
                    bitmap,
                    "Answer this question about the image: '$userQuestion' in 2 concise sentences suitable for speech output."
                )
            }

            Result.failure(Exception("No previous image context found. Please capture an image first."))
        } catch (e: Exception) {
            Log.e("GeminiVisionService", "Error asking follow-up question", e)
            Result.failure(e)
        }
    }

    fun resetChat() {
        chatSession = null
        lastAnalyzedBitmap = null
    }
}
