package com.convertly.backend;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.convertly.backend.dto.AuthDtos.AuthResponse;
import com.convertly.backend.dto.AuthDtos.LoginRequest;
import com.convertly.backend.dto.AuthDtos.RegisterRequest;
import com.convertly.backend.dto.ProjectDtos.ProjectRequest;
import com.convertly.backend.dto.ProjectDtos.ProjectResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "convertly.jwt.secret=convertly-test-jwt-secret-with-more-than-32-characters",
    "convertly.jwt.expires-in=PT30M",
    "logging.level.root=WARN",
    "logging.level.org.springframework=WARN",
    "logging.level.org.hibernate=WARN"
})
class AuthJwtIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerReturnsUserAndToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest("register"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value("register@example.test"))
            .andExpect(jsonPath("$.user.displayName").value(displayName("register")))
            .andExpect(jsonPath("$.token", notNullValue()))
            .andExpect(jsonPath("$.expiresAt", notNullValue()))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void loginReturnsTokenForCorrectPassword() throws Exception {
        register("login-ok");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("login-ok@example.test", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("login-ok@example.test"))
            .andExpect(jsonPath("$.token", notNullValue()))
            .andExpect(jsonPath("$.token", not("")));
    }

    @Test
    void loginRejectsBadPassword() throws Exception {
        register("login-bad");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("login-bad@example.test", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void projectsRejectAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void projectsAllowAccessWithValidToken() throws Exception {
        AuthResponse auth = register("project-access");

        mockMvc.perform(get("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer(auth)))
            .andExpect(status().isOk());
    }

    @Test
    void userCannotAccessProjectOwnedByAnotherUser() throws Exception {
        AuthResponse owner = register("project-owner");
        AuthResponse otherUser = register("project-other");

        ProjectResponse project = createProject(owner, "Private Project");

        mockMvc.perform(get("/api/projects/{projectId}", project.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(otherUser)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"));
    }

    private AuthResponse register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest(prefix))))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private RegisterRequest registerRequest(String prefix) {
        return new RegisterRequest(prefix + "@example.test", "password123", displayName(prefix));
    }

    private String displayName(String prefix) {
        return UUID.nameUUIDFromBytes(prefix.getBytes()).toString().substring(0, 8) + " User";
    }

    private ProjectResponse createProject(AuthResponse auth, String name) throws Exception {
        ProjectRequest request = new ProjectRequest(name, "Private", "Custom Workspace", null, false, "", "", "", "");
        MvcResult result = mockMvc.perform(post("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), ProjectResponse.class);
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
