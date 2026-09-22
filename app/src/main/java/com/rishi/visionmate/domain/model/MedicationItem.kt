package com.rishi.visionmate.domain.model

data class MedicationItem(
    val id: String,
    val name: String,
    val dosage: String,
    val instructions: String,
    val rawOcrText: String,
    val isConfirmedByUser: Boolean = false
)
