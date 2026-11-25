package com.example.elevapi

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ElevService(
    private val elevProducer: ElevProducer,
    private val elevServiceClient: ElevServiceClient,
) {

    fun opprett(request: OpprettElevRequest): Elev {
        val elev = Elev(
            id = UUID.randomUUID(),
            navn = request.navn.trim(),
            klasse = request.klasse.trim(),
        )
        elevProducer.send(elev)
        return elev
    }

    fun hent(id: UUID): Elev? = elevServiceClient.hent(id)

    fun hentAlle(): List<Elev> = elevServiceClient.hentAlle()
}
