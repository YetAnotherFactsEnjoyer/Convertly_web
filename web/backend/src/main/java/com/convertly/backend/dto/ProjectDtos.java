package com.convertly.backend.dto;

import com.convertly.backend.entity.Project;
import com.convertly.backend.entity.Project.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 120) String type,
        Status status,
        Boolean favorite,
        @Size(max = 200000) String source,
        @Size(max = 10000) String instruction,
        @Size(max = 200000) String currentOutput,
        @Size(max = 20000) String optionsJson
    ) {
    }

    public record ProjectUpdateRequest(
        @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 120) String type,
        Status status,
        Boolean favorite,
        @Size(max = 200000) String source,
        @Size(max = 10000) String instruction,
        @Size(max = 200000) String currentOutput,
        @Size(max = 20000) String optionsJson
    ) {
    }

    public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String type,
        Status status,
        boolean favorite,
        String source,
        String instruction,
        String currentOutput,
        String optionsJson,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ProjectResponse from(Project project) {
            Instant updatedAt = project.getUpdatedAt() == null ? project.getCreatedAt() : project.getUpdatedAt();

            return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getType() == null || project.getType().isBlank() ? "Custom Workspace" : project.getType(),
                project.getStatus() == null ? Status.DRAFT : project.getStatus(),
                project.isFavorite(),
                project.getSource(),
                project.getInstruction(),
                project.getCurrentOutput(),
                project.getOptionsJson(),
                project.getCreatedAt(),
                updatedAt
            );
        }
    }
}
