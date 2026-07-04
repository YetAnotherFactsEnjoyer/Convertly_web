import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useMotionValueEvent, useScroll } from "framer-motion";
import AuthPage, { type AuthMode } from "./components/AuthPage";
import { Dashboard, type Project } from "./components/Interface";
import { ToolInterface, type ToolOptions, type ToolProject } from "./components/ToolInterface";
import { ApiError, api } from "./services/api";
import { Footer } from "./components/landingpage/Footer";
import { HeroSection } from "./components/landingpage/HeroSection";
import { Navbar } from "./components/landingpage/Navbar";
import { TechSection } from "./components/landingpage/TechSection";
import { TutorialSection } from "./components/landingpage/TutorialSection";
import type { CopiedPane, TutorialStep } from "./components/landingpage/types";
import type { AuthResponse, UserResponse } from "./types/api";

interface AuthSession {
  user: UserResponse;
  token: string;
  expiresAt: string;
}

const AUTH_SESSION_KEY = "convertly-auth-session";

const containsPasswordField = (value: unknown): boolean => {
  if (!value || typeof value !== "object") {
    return false;
  }

  return Object.entries(value).some(([key, entry]) => {
    if (key === "password" && typeof entry === "string") {
      return true;
    }

    return containsPasswordField(entry);
  });
};

const isUserResponse = (value: unknown): value is UserResponse => {
  if (!value || typeof value !== "object") {
    return false;
  }

  const user = value as Partial<UserResponse>;
  return (
    typeof user.id === "string" &&
    typeof user.email === "string" &&
    typeof user.displayName === "string" &&
    typeof user.role === "string" &&
    typeof user.createdAt === "string"
  );
};

const isAuthSession = (value: unknown): value is AuthSession => {
  if (!value || typeof value !== "object") {
    return false;
  }

  const session = value as Partial<AuthSession>;
  return (
    isUserResponse(session.user) &&
    typeof session.token === "string" &&
    session.token.length > 0 &&
    typeof session.expiresAt === "string" &&
    !Number.isNaN(new Date(session.expiresAt).getTime())
  );
};

const getStoredSession = (): AuthSession | null => {
  try {
    const rawSession = window.localStorage.getItem(AUTH_SESSION_KEY);
    if (!rawSession) {
      return null;
    }

    const parsedSession = JSON.parse(rawSession) as unknown;
    if (!isAuthSession(parsedSession) || containsPasswordField(parsedSession)) {
      window.localStorage.removeItem(AUTH_SESSION_KEY);
      return null;
    }

    if (new Date(parsedSession.expiresAt).getTime() <= Date.now()) {
      window.localStorage.removeItem(AUTH_SESSION_KEY);
      return null;
    }

    return parsedSession;
  } catch {
    window.localStorage.removeItem(AUTH_SESSION_KEY);
    return null;
  }
};

const DEFAULT_TOOL_OPTIONS: ToolOptions = {
  language: "English",
  tone: "Professional",
  length: "medium",
  outputFormat: "text",
  preserveFormatting: true,
};

const parseToolOptions = (value: string): ToolOptions => {
  if (!value) {
    return DEFAULT_TOOL_OPTIONS;
  }

  try {
    return {
      ...DEFAULT_TOOL_OPTIONS,
      ...(JSON.parse(value) as Partial<ToolOptions>),
    };
  } catch {
    return DEFAULT_TOOL_OPTIONS;
  }
};

const toToolProject = (project: Project): ToolProject => ({
  id: project.id,
  name: project.name,
  status:
    project.status === "archived"
      ? "draft"
      : project.status,
  source: project.source || project.description,
  instruction: project.instruction || `Process this project and produce a clean result.`,
  currentOutput: project.currentOutput,
  options: parseToolOptions(project.optionsJson),
  runs: [],
  updatedAt: project.updatedAt,
  isFavorite: project.isFavorite,
});

export default function ConvertlyLanding() {
  const tutorialRef = useRef<HTMLElement | null>(null);
  const scrollLockTimeoutRef = useRef<number | null>(null);
  const [authSession, setAuthSession] = useState<AuthSession | null>(getStoredSession);
  const [isVerifyingSession, setIsVerifyingSession] = useState(() => getStoredSession() !== null);
  const [activeToolProject, setActiveToolProject] = useState<ToolProject | null>(null);
  const [activeStep, setActiveStep] = useState<TutorialStep>(0);
  const [activeNavStep, setActiveNavStep] = useState<TutorialStep | null>(null);
  const [inputText, setInputText] = useState(
    "id,name,role,email\n1,Alice Dupont,Admin,alice@convertly.dev\n2,Bob Martin,User,bob@convertly.dev\n3,Charlie Roux,Editor,charlie@convertly.dev"
  );
  const [outputText, setOutputText] = useState("");
  const [copiedPane, setCopiedPane] = useState<CopiedPane>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>("signin");

  useEffect(() => {
    if (!authSession) {
      setIsVerifyingSession(false);
      return;
    }

    let isMounted = true;

    setIsVerifyingSession(true);

    if (new Date(authSession.expiresAt).getTime() <= Date.now()) {
      window.localStorage.removeItem(AUTH_SESSION_KEY);
      setActiveToolProject(null);
      setAuthSession(null);
      setAuthMode("signin");
      setAuthOpen(true);
      setIsVerifyingSession(false);
      return;
    }

    api.me({ token: authSession.token })
      .then(() => {
        if (isMounted) {
          setIsVerifyingSession(false);
        }
      })
      .catch((error) => {
        if (!isMounted) {
          return;
        }

        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          window.localStorage.removeItem(AUTH_SESSION_KEY);
          setActiveToolProject(null);
          setAuthSession(null);
          setAuthMode("signin");
          setAuthOpen(true);
        }

        setIsVerifyingSession(false);
      });

    return () => {
      isMounted = false;
    };
  }, [authSession]);

  useLayoutEffect(() => {
    const previousScrollRestoration = window.history.scrollRestoration;

    window.history.scrollRestoration = "manual";

    if (window.location.hash) {
      window.history.replaceState(null, document.title, window.location.pathname + window.location.search);
    }

    window.scrollTo(0, 0);

    return () => {
      if (scrollLockTimeoutRef.current !== null) {
        window.clearTimeout(scrollLockTimeoutRef.current);
      }

      window.history.scrollRestoration = previousScrollRestoration;
    };
  }, []);

  const { scrollYProgress } = useScroll({
    target: tutorialRef,
    offset: ["start start", "end end"],
  });

  useMotionValueEvent(scrollYProgress, "change", (latest) => {
    if (scrollLockTimeoutRef.current !== null) {
      return;
    }

    if (latest <= 0 || latest >= 1) {
      setActiveNavStep(null);
      return;
    }

    const nextStep: TutorialStep = latest < 0.33 ? 0 : latest < 0.66 ? 1 : 2;
    setActiveStep(nextStep);
    setActiveNavStep(nextStep);
  });

  const openAuth = (mode: AuthMode) => {
    setAuthMode(mode);
    setAuthOpen(true);
  };

  const handleAuthenticated = (auth: AuthResponse) => {
    const nextSession: AuthSession = {
      user: auth.user,
      token: auth.token,
      expiresAt: auth.expiresAt,
    };

    window.localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(nextSession));
    setIsVerifyingSession(false);
    setAuthSession(nextSession);
    setAuthOpen(false);
  };

  const handleLogout = () => {
    window.localStorage.removeItem(AUTH_SESSION_KEY);
    setIsVerifyingSession(false);
    setActiveToolProject(null);
    setAuthSession(null);
  };

  const handleSessionExpired = () => {
    window.localStorage.removeItem(AUTH_SESSION_KEY);
    setIsVerifyingSession(false);
    setActiveToolProject(null);
    setAuthSession(null);
    setAuthMode("signin");
    setAuthOpen(true);
  };

  const scrollToTutorialStep = (step: TutorialStep) => {
    const section = tutorialRef.current;

    if (!section) {
      return;
    }

    const progressByStep: Record<TutorialStep, number> = {
      0: 0.16,
      1: 0.5,
      2: 0.84,
    };
    const scrollableDistance = section.offsetHeight - window.innerHeight;

    if (scrollLockTimeoutRef.current !== null) {
      window.clearTimeout(scrollLockTimeoutRef.current);
    }

    setActiveStep(step);
    setActiveNavStep(step);
    window.scrollTo({
      top: section.offsetTop + scrollableDistance * progressByStep[step],
      behavior: "smooth",
    });

    scrollLockTimeoutRef.current = window.setTimeout(() => {
      scrollLockTimeoutRef.current = null;
    }, 700);
  };

  const handleCopy = (pane: "input" | "output") => {
    const textToCopy = pane === "input" ? inputText : outputText;

    navigator.clipboard.writeText(textToCopy);
    setCopiedPane(pane);
    window.setTimeout(() => setCopiedPane(null), 2000);
  };

  const handleClear = () => {
    setInputText("");
    setOutputText("");
  };

  const handleConvert = () => {
    if (!inputText.trim()) {
      return;
    }

    setIsProcessing(true);
    window.setTimeout(() => {
      try {
        const lines = inputText.trim().split("\n");
        const separator = lines[0].includes(";") ? ";" : ",";
        const headers = lines[0].split(separator);
        const json = lines.slice(1).map((line) => {
          const values = line.split(separator);

          return headers.reduce((obj, header, index) => {
            obj[header.trim()] = values[index]?.trim() || "";
            return obj;
          }, {} as Record<string, string>);
        });

        setOutputText(JSON.stringify(json, null, 2));
      } catch {
        setOutputText("// Erreur de conversion. Vérifiez le format du fichier d'entrée.");
      }

      setIsProcessing(false);
    }, 800);
  };

  const wordCount = inputText.trim() === "" ? 0 : inputText.trim().split(/\s+/).length;
  const charCount = inputText.length;
  const apiAuth = useMemo(
    () => (authSession ? { token: authSession.token } : null),
    [authSession?.token]
  );

  if (authSession && apiAuth) {
    if (isVerifyingSession) {
      return (
        <div className="grid min-h-screen place-items-center bg-slate-50 font-sans text-black">
          <div className="text-center">
            <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-4 border-purple-200 border-t-purple-600" />
            <p className="text-sm font-semibold text-black/60">Checking session...</p>
          </div>
        </div>
      );
    }

    if (activeToolProject) {
      return (
        <ToolInterface
          key={activeToolProject.id}
          initialProject={activeToolProject}
          auth={apiAuth}
          onBack={() => setActiveToolProject(null)}
        />
      );
    }

    return (
      <Dashboard
        user={authSession.user}
        auth={apiAuth}
        onLogout={handleLogout}
        onSessionExpired={handleSessionExpired}
        onProjectOpen={(project) => setActiveToolProject(toToolProject(project))}
      />
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 font-sans text-slate-900">
      <Navbar
        activeNavStep={activeNavStep}
        onAuthOpen={openAuth}
        onTutorialStepSelect={scrollToTutorialStep}
      />

      <AuthPage
        open={authOpen}
        mode={authMode}
        onModeChange={setAuthMode}
        onClose={() => setAuthOpen(false)}
        onAuthenticated={handleAuthenticated}
      />

      <HeroSection />
      <TutorialSection
        activeStep={activeStep}
        charCount={charCount}
        copiedPane={copiedPane}
        inputText={inputText}
        isProcessing={isProcessing}
        outputText={outputText}
        tutorialRef={tutorialRef}
        wordCount={wordCount}
        onClear={handleClear}
        onConvert={handleConvert}
        onCopy={handleCopy}
        onInputTextChange={setInputText}
      />
      <TechSection />
      <Footer />
    </div>
  );
}
