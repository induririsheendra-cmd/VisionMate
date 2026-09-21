package com.rishi.visionmate.services.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var isTorchOn: Boolean = false
        private set

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                // Restore torch state if enabled
                if (isTorchOn) {
                    setTorch(true, onError)
                }

                Log.d("CameraManager", "Camera successfully bound to lifecycle")
            } catch (e: Exception) {
                Log.e("CameraManager", "Failed to bind camera lifecycle", e)
                onError("Failed to start camera preview: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setTorch(enable: Boolean, onError: (String) -> Unit) {
        val cam = camera
        if (cam == null) {
            isTorchOn = enable
            return
        }

        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(enable)
            isTorchOn = enable
            Log.d("CameraManager", "Torch state set to: $enable")
        } else {
            onError("Torch/Flashlight is not available on this camera.")
        }
    }

    fun takePicture(
        onImageCaptured: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Camera is not initialized yet.")
            return
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    if (bitmap != null) {
                        onImageCaptured(bitmap)
                    } else {
                        onError("Failed to convert camera frame into image.")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Image capture failed", exception)
                    onError("Failed to capture image: ${exception.localizedMessage}")
                }
            }
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        camera = null
    }
}
