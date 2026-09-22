package com.rishi.visionmate.domain.usecase

import android.graphics.Bitmap
import com.rishi.visionmate.services.vision.GeminiVisionService

class AnalyzeSceneUseCase(
    private val visionService: GeminiVisionService = GeminiVisionService()
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<String> {
        return visionService.analyzeScene(bitmap)
    }
}
