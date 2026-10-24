package com.stepfreelondon.savedjourney;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface StationRepository extends JpaRepository<Station, UUID> {

    Optional<Station> findByTflStationId(String tflStationId);
}
