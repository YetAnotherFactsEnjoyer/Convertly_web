import type {
  ApiAuth,
  AuthResponse,
  LoginRequest,
  ProjectRequest,
  ProjectResponse,
  RegisterRequest,
  ToolRunRequest,
  ToolRunResponse,
  UserResponse
} from "../types/api";

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  auth?: ApiAuth
): Promise<T> {
  const headers = new Headers(init.headers);

  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (auth) {
    headers.set("Authorization", `Bearer ${auth.token}`);
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers
    });
  } catch {
    throw new ApiError(0, `Unable to reach backend at ${API_BASE_URL}. Is Spring Boot running?`);
  }

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;

    try {
      const body = (await response.json()) as { message?: string; error?: string };
      message = body.message ?? body.error ?? message;
    } catch {
      const text = await response.text();
      message = text || message;
    }

    if (response.status === 401 || response.status === 403) {
      message = "Your session expired or is invalid. Please sign in again.";
    }

    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  register(payload: RegisterRequest) {
    return apiRequest<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  login(payload: LoginRequest) {
    return apiRequest<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  me(auth: ApiAuth) {
    return apiRequest<UserResponse>("/api/users/me", {}, auth);
  },
  listProjects(auth: ApiAuth) {
    return apiRequest<ProjectResponse[]>("/api/projects", {}, auth);
  },
  getProject(auth: ApiAuth, projectId: string) {
    return apiRequest<ProjectResponse>(`/api/projects/${projectId}`, {}, auth);
  },
  createProject(auth: ApiAuth, payload: ProjectRequest) {
    return apiRequest<ProjectResponse>(
      "/api/projects",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      auth
    );
  },
  updateProject(auth: ApiAuth, projectId: string, payload: Partial<ProjectRequest>) {
    return apiRequest<ProjectResponse>(
      `/api/projects/${projectId}`,
      {
        method: "PUT",
        body: JSON.stringify(payload)
      },
      auth
    );
  },
  deleteProject(auth: ApiAuth, projectId: string) {
    return apiRequest<void>(
      `/api/projects/${projectId}`,
      {
        method: "DELETE"
      },
      auth
    );
  },
  runProjectTool(
    auth: ApiAuth,
    projectId: string,
    payload: ToolRunRequest,
    signal?: AbortSignal
  ) {
    return apiRequest<ToolRunResponse>(
      `/api/projects/${projectId}/run`,
      {
        method: "POST",
        body: JSON.stringify(payload),
        signal
      },
      auth
    );
  }
};

export { ApiError };
