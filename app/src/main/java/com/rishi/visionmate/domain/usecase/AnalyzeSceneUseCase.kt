package com.rishi.visionmate.domain.usecase

import android.graphics.Bitmap
import com.rishi.visionmate.services.vision.GeminiVisionService

class AnalyzeSceneUseCase(
    private val visionService: GeminiVisionService = GeminiVisionService()
) {
    suspend operator fun invoke(bitmap: Bitmap, customPrompt: String? = null): Result<String> {
        return visionService.analyzeScene(bitmap, customPrompt)
    }

    suspend fun askFollowUp(userQuestion: String): Result<String> {
        return visionService.askFollowUp(userQuestion)
    }

    fun resetChat() {
        visionService.resetChat()
    }
}
