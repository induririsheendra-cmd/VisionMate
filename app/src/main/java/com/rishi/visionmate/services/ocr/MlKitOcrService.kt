package com.rishi.visionmate.services.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MlKitOcrService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        val rawText = visionText.text.trim()
                        if (rawText.isNotBlank()) {
                            continuation.resume(Result.success(rawText))
                        } else {
                            continuation.resume(
                                Result.success("No clear text was detected in the image. Please align your camera closer to the document or sign.")
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MlKitOcrService", "OCR Text Extraction failed", e)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }
        } catch (e: Exception) {
            Log.e("MlKitOcrService", "Error processing image for OCR", e)
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }
}
