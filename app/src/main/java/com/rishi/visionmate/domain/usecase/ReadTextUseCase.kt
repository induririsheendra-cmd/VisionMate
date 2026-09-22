package com.rishi.visionmate.domain.usecase

import android.graphics.Bitmap
import com.rishi.visionmate.services.ocr.MlKitOcrService

class ReadTextUseCase(
    private val ocrService: MlKitOcrService = MlKitOcrService()
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<String> {
        return ocrService.extractText(bitmap)
    }
}
