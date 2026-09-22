package com.rishi.visionmate.services.vision

import android.graphics.Bitmap
import android.util.Log
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

    suspend fun analyzeScene(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured in local.properties.")
            )
        }

        try {
            val promptText = """
                You are VisionMate, an AI companion helping a blind or visually impaired person understand their surroundings.
                Describe what is visible in this camera image in 2 to 3 concise, natural sentences suitable for speech output.
                - Use clear spatial references (e.g., 'in front of you', 'to your left', 'on the table').
                - Use uncertainty-aware language (e.g., 'appears to be', 'looks like', 'there seems to be').
                - Never claim perfect safety or that 'your path is completely clear'.
                - Focus on key objects, obstacles, people, or furniture nearby.
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(promptText)
            }

            val response = generativeModel.generateContent(inputContent)
            val description = response.text?.trim()

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
}
