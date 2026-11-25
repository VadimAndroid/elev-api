package com.example.elevapi

import jakarta.validation.constraints.NotBlank

data class OpprettElevRequest(
    @field:NotBlank val navn: String,
    @field:NotBlank val klasse: String,
)
