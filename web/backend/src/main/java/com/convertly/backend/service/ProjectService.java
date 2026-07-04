package com.convertly.backend.service;

import com.convertly.backend.dto.ProjectDtos.ProjectRequest;
import com.convertly.backend.dto.ProjectDtos.ProjectUpdateRequest;
import com.convertly.backend.entity.Project;
import com.convertly.backend.entity.Project.Status;
import com.convertly.backend.entity.User;
import com.convertly.backend.exception.ResourceNotFoundException;
import com.convertly.backend.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<Project> listFor(User owner) {
        return projects.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId());
    }

    @Transactional
    public Project create(User owner, ProjectRequest request) {
        Project project = new Project();
        project.setOwner(owner);
        project.setName(request.name().trim());
        project.setDescription(normalizeOptional(request.description()));
        project.setType(normalizeDefault(request.type(), "Custom Workspace"));
        project.setStatus(request.status() == null ? Status.DRAFT : request.status());
        project.setFavorite(Boolean.TRUE.equals(request.favorite()));
        project.setSource(normalizeOptional(request.source()));
        project.setInstruction(normalizeOptional(request.instruction()));
        project.setCurrentOutput(normalizeOptional(request.currentOutput()));
        project.setOptionsJson(normalizeOptional(request.optionsJson()));
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public Project getFor(User owner, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, owner.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    @Transactional
    public void deleteFor(User owner, UUID projectId) {
        Project project = getFor(owner, projectId);
        projects.delete(project);
    }

    @Transactional
    public Project updateFor(User owner, UUID projectId, ProjectUpdateRequest request) {
        Project project = getFor(owner, projectId);

        if (request.name() != null) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(normalizeOptional(request.description()));
        }
        if (request.type() != null) {
            project.setType(normalizeDefault(request.type(), "Custom Workspace"));
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        if (request.favorite() != null) {
            project.setFavorite(request.favorite());
        }
        if (request.source() != null) {
            project.setSource(normalizeOptional(request.source()));
        }
        if (request.instruction() != null) {
            project.setInstruction(normalizeOptional(request.instruction()));
        }
        if (request.currentOutput() != null) {
            project.setCurrentOutput(normalizeOptional(request.currentOutput()));
        }
        if (request.optionsJson() != null) {
            project.setOptionsJson(normalizeOptional(request.optionsJson()));
        }

        return project;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
