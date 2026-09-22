package com.rishi.visionmate.domain.usecase

import android.content.Context
import com.rishi.visionmate.services.safety.IncidentDetector

class DetectIncidentUseCase(
    context: Context,
    onPotentialIncident: () -> Unit
) {
    private val detector = IncidentDetector(context, onPotentialIncident)

    fun start() {
        detector.startMonitoring()
    }

    fun stop() {
        detector.stopMonitoring()
    }
}
