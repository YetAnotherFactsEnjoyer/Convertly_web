package com.convertly.backend.controller;

import com.convertly.backend.dto.ProjectDtos.ProjectRequest;
import com.convertly.backend.dto.ProjectDtos.ProjectResponse;
import com.convertly.backend.dto.ProjectDtos.ProjectUpdateRequest;
import com.convertly.backend.dto.ToolDtos.ToolRunRequest;
import com.convertly.backend.dto.ToolDtos.ToolRunResponse;
import com.convertly.backend.entity.Project.Status;
import com.convertly.backend.entity.User;
import com.convertly.backend.service.InterpreterService;
import com.convertly.backend.service.ProjectService;
import com.convertly.backend.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projects;
    private final UserService users;
    private final InterpreterService interpreter;
    private final ObjectMapper objectMapper;

    public ProjectController(
        ProjectService projects,
        UserService users,
        InterpreterService interpreter,
        ObjectMapper objectMapper
    ) {
        this.projects = projects;
        this.users = users;
        this.interpreter = interpreter;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<ProjectResponse> list(Authentication authentication) {
        User owner = users.requireCurrentUser(authentication);
        return projects.listFor(owner).stream().map(ProjectResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
        Authentication authentication,
        @Valid @RequestBody ProjectRequest request
    ) {
        User owner = users.requireCurrentUser(authentication);
        return ProjectResponse.from(projects.create(owner, request));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(Authentication authentication, @PathVariable UUID projectId) {
        User owner = users.requireCurrentUser(authentication);
        return ProjectResponse.from(projects.getFor(owner, projectId));
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(
        Authentication authentication,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectUpdateRequest request
    ) {
        User owner = users.requireCurrentUser(authentication);
        return ProjectResponse.from(projects.updateFor(owner, projectId, request));
    }

    @PostMapping("/{projectId}/run")
    public ToolRunResponse runTool(
        Authentication authentication,
        @PathVariable UUID projectId,
        @Valid @RequestBody ToolRunRequest request
    ) {
        User owner = users.requireCurrentUser(authentication);
        projects.getFor(owner, projectId);

        ToolRunResponse response = interpreter.run(request);
        projects.updateFor(
            owner,
            projectId,
            new ProjectUpdateRequest(
                null,
                null,
                null,
                Status.READY,
                null,
                request.source(),
                request.instruction(),
                response.output(),
                writeOptionsJson(request)
            )
        );

        return response;
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID projectId) {
        User owner = users.requireCurrentUser(authentication);
        projects.deleteFor(owner, projectId);
    }

    private String writeOptionsJson(ToolRunRequest request) {
        try {
            return objectMapper.writeValueAsString(request.options());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tool options", exception);
        }
    }
}
