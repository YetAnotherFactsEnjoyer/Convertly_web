export type UserRole = "USER" | "ADMIN";

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  createdAt: string;
}

export interface AuthResponse {
  user: UserResponse;
  token: string;
  expiresAt: string;
}

export type ApiProjectStatus = "DRAFT" | "READY" | "PROCESSING" | "ERROR" | "ARCHIVED";

export interface ProjectRequest {
  name: string;
  description?: string;
  type?: string;
  status?: ApiProjectStatus;
  favorite?: boolean;
  source?: string;
  instruction?: string;
  currentOutput?: string;
  optionsJson?: string;
}

export interface ProjectResponse {
  id: string;
  name: string;
  description: string | null;
  type?: string | null;
  status?: ApiProjectStatus | null;
  favorite: boolean;
  source: string | null;
  instruction: string | null;
  currentOutput: string | null;
  optionsJson: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ToolRunOptions {
  language: string;
  tone: string;
  length: string;
  outputFormat: string;
  preserveFormatting: boolean;
}

export interface ToolRunRequest {
  source: string;
  instruction: string;
  options: ToolRunOptions;
}

export interface ToolRunResponse {
  source: string;
  instruction: string;
  output: string;
  originalOutput: string;
  options: ToolRunOptions;
  status: "completed" | "failed" | "cancelled";
}

export interface ApiAuth {
  token: string;
}
