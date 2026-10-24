package com.stepfreelondon.savedjourney;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * Every read is scoped by {@code userId}. This extends the bare {@link Repository} rather than
 * {@code JpaRepository}, so inherited methods like {@code findById} don't exist and code in this
 * package can't accidentally load another user's journey.
 */
interface SavedJourneyRepository extends Repository<SavedJourney, UUID> {

    @EntityGraph(attributePaths = {"origin", "destination"})
    List<SavedJourney> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"origin", "destination"})
    Optional<SavedJourney> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    SavedJourney saveAndFlush(SavedJourney journey);

    void delete(SavedJourney journey);
}
