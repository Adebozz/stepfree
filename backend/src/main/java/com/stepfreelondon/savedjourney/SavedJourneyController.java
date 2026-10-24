package com.stepfreelondon.savedjourney;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stepfreelondon.auth.AuthUser;
import com.stepfreelondon.savedjourney.dto.CreateSavedJourneyRequest;
import com.stepfreelondon.savedjourney.dto.SavedJourneyDto;
import com.stepfreelondon.savedjourney.dto.UpdateSavedJourneyRequest;

/** The signed-in user's saved journeys. The owner always comes from the session, never from the request. */
@Tag(name = "Saved journeys")
@RestController
@RequestMapping("/api/v1/saved-journeys")
class SavedJourneyController {

    private final SavedJourneyService service;

    SavedJourneyController(SavedJourneyService service) {
        this.service = service;
    }

    @Operation(summary = "List saved journeys, newest first")
    @GetMapping
    List<SavedJourneyDto> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.id());
    }

    @Operation(summary = "One saved journey")
    @GetMapping("/{id}")
    SavedJourneyDto get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.get(user.id(), id);
    }

    @Operation(summary = "Save a journey", description = "Stations are checked against TfL. 409 if the name is taken "
            + "or the saved-journey limit is reached.")
    @PostMapping
    ResponseEntity<SavedJourneyDto> create(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody CreateSavedJourneyRequest body) {
        SavedJourneyDto created = service.create(user.id(), body);
        return ResponseEntity.created(URI.create("/api/v1/saved-journeys/" + created.id())).body(created);
    }

    @Operation(summary = "Rename or toggle monitoring")
    @PatchMapping("/{id}")
    SavedJourneyDto update(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                           @Valid @RequestBody UpdateSavedJourneyRequest body) {
        return service.update(user.id(), id, body);
    }

    @Operation(summary = "Delete a saved journey")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
