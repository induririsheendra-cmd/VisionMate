package com.rishi.visionmate.domain.usecase

import com.rishi.visionmate.domain.model.MedicationItem
import java.util.UUID
import java.util.regex.Pattern

class ExtractMedicationUseCase {

    private val mgPattern = Pattern.compile("(?i)(\\d+\\s*(?:mg|mcg|g|ml|tablets?|capsules?))")

    operator fun invoke(rawOcrText: String): MedicationItem {
        val lines = rawOcrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Find line containing dosage (e.g. 500 mg)
        var detectedDosage = "Unspecified dosage"
        var detectedName = "Prescription Item"

        for (line in lines) {
            val matcher = mgPattern.matcher(line)
            if (matcher.find()) {
                detectedDosage = matcher.group(1) ?: "Unspecified dosage"
                val nameCandidate = line.replace(matcher.group(0) ?: "", "").trim()
                if (nameCandidate.length >= 3) {
                    detectedName = nameCandidate
                }
                break
            }
        }

        if (detectedName == "Prescription Item" && lines.isNotEmpty()) {
            detectedName = lines.firstOrNull { it.length in 3..30 } ?: lines[0]
        }

        return MedicationItem(
            id = UUID.randomUUID().toString(),
            name = detectedName,
            dosage = detectedDosage,
            instructions = "Please verify label details with your pharmacist or caregiver before taking.",
            rawOcrText = rawOcrText
        )
    }
}
