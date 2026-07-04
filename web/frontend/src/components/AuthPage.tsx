import { AnimatePresence, motion } from 'framer-motion';
import { ArrowRight, Github, X } from 'lucide-react';
import { FormEvent, useEffect, useState } from 'react';
import { ApiError, api } from '../services/api';
import type { AuthResponse } from '../types/api';

export type AuthMode = 'signin' | 'signup';

interface AuthPageProps {
  open: boolean;
  mode?: AuthMode;
  onModeChange: (mode: AuthMode) => void;
  onClose: () => void;
  onAuthenticated: (auth: AuthResponse) => void;
}

const isAuthResponse = (value: unknown): value is AuthResponse => {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const auth = value as Partial<AuthResponse>;
  return (
    Boolean(auth.user) &&
    typeof auth.token === 'string' &&
    auth.token.length > 0 &&
    typeof auth.expiresAt === 'string'
  );
};

export default function AuthPage({
  open,
  mode = 'signin',
  onModeChange,
  onClose,
  onAuthenticated,
}: AuthPageProps) {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    setError('');
  }, [mode]);

  useEffect(() => {
    if (!open) {
      setError('');
      setIsSubmitting(false);
    }
  }, [open]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedEmail = email.trim().toLowerCase();
    const trimmedDisplayName = displayName.trim();

    if (!trimmedEmail || !password) {
      setError('Email et mot de passe sont requis.');
      return;
    }

    if (mode === 'signup' && !trimmedDisplayName) {
      setError('Le nom complet est requis.');
      return;
    }

    if (mode === 'signup' && password.length < 8) {
      setError('Le mot de passe doit contenir au moins 8 caractères.');
      return;
    }

    setIsSubmitting(true);
    setError('');

    try {
      const auth =
        mode === 'signin'
          ? await api.login({ email: trimmedEmail, password })
          : await api.register({
              email: trimmedEmail,
              password,
              displayName: trimmedDisplayName,
            });

      if (!isAuthResponse(auth)) {
        throw new Error('Invalid auth response from backend. Restart Spring Boot with the latest JWT code.');
      }

      onAuthenticated(auth);
      setDisplayName('');
      setEmail('');
      setPassword('');
    } catch (requestError) {
      if (requestError instanceof ApiError) {
        setError(requestError.message);
      } else if (requestError instanceof Error) {
        setError(requestError.message);
      } else {
        setError("Impossible de joindre l'API. Vérifiez que le backend tourne sur localhost:8080.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          onClick={onClose}
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 backdrop-blur-sm px-4 py-8"
        >
          <motion.div
            initial={{ opacity: 0, y: 24, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.96 }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
            onClick={(event) => event.stopPropagation()}
            className="relative w-full max-w-md overflow-hidden rounded-2xl border border-black/10 bg-white shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-black/10 px-6 py-5">
              <div className="flex items-center gap-2 text-purple-600">
                <img src="/transfer.png" alt="Convertly logo" className="h-6 w-6 object-contain" />
                <span className="text-lg font-bold text-black">Convertly</span>
              </div>
              <button
                onClick={onClose}
                aria-label="Fermer"
                className="text-black/40 transition-colors hover:text-black"
              >
                <X size={20} />
              </button>
            </div>

            <div className="flex border-b border-black/10 px-6">
              <button
                onClick={() => onModeChange('signin')}
                className={`flex-1 border-b-2 py-3 text-sm font-semibold transition-colors ${
                  mode === 'signin' ? 'border-purple-600 text-black' : 'border-transparent text-black/40 hover:text-black'
                }`}
              >
                Se connecter
              </button>
              <button
                onClick={() => onModeChange('signup')}
                className={`flex-1 border-b-2 py-3 text-sm font-semibold transition-colors ${
                  mode === 'signup' ? 'border-purple-600 text-black' : 'border-transparent text-black/40 hover:text-black'
                }`}
              >
                S'inscrire
              </button>
            </div>

            <div className="px-6 py-6">
              <AnimatePresence mode="wait">
                <motion.div
                  key={mode}
                  initial={{ opacity: 0, x: mode === 'signup' ? 16 : -16 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: mode === 'signup' ? -16 : 16 }}
                  transition={{ duration: 0.2 }}
                >
                  <form onSubmit={handleSubmit}>
                    {mode === 'signup' && (
                      <div className="mb-4">
                        <label htmlFor="auth-name" className="mb-1.5 block text-xs font-semibold text-black/70">
                          Nom complet
                        </label>
                        <input
                          id="auth-name"
                          type="text"
                          value={displayName}
                          onChange={(event) => setDisplayName(event.target.value)}
                          placeholder="Alice Dupont"
                          className="w-full rounded-lg border border-black/15 px-3 py-2.5 text-sm text-black outline-none transition-colors placeholder:text-black/30 focus:border-purple-600 focus:ring-1 focus:ring-purple-600"
                        />
                      </div>
                    )}
                    <div className="mb-4">
                      <label htmlFor="auth-email" className="mb-1.5 block text-xs font-semibold text-black/70">
                        Email
                      </label>
                      <input
                        id="auth-email"
                        type="email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        placeholder="alice@convertly.dev"
                        className="w-full rounded-lg border border-black/15 px-3 py-2.5 text-sm text-black outline-none transition-colors placeholder:text-black/30 focus:border-purple-600 focus:ring-1 focus:ring-purple-600"
                      />
                    </div>
                    <div className="mb-2">
                      <div className="mb-1.5 flex items-center justify-between">
                        <label htmlFor="auth-password" className="text-xs font-semibold text-black/70">
                          Mot de passe
                        </label>
                        {mode === 'signin' && (
                          <a href="#" className="text-xs font-semibold text-purple-600 transition-colors hover:text-black">
                            Oublié ?
                          </a>
                        )}
                      </div>
                      <input
                        id="auth-password"
                        type="password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        placeholder="••••••••"
                        className="w-full rounded-lg border border-black/15 px-3 py-2.5 text-sm text-black outline-none transition-colors placeholder:text-black/30 focus:border-purple-600 focus:ring-1 focus:ring-purple-600"
                      />
                    </div>

                    {error && (
                      <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700">
                        {error}
                      </p>
                    )}

                    <button
                      type="submit"
                      disabled={isSubmitting}
                      className="mt-4 flex w-full items-center justify-center gap-2 rounded-lg bg-purple-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-black disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {isSubmitting
                        ? 'Connexion...'
                        : mode === 'signin'
                          ? 'Se connecter'
                          : 'Créer mon compte'}
                      <ArrowRight size={16} />
                    </button>
                  </form>
                </motion.div>
              </AnimatePresence>

              <div className="my-6 flex items-center gap-3">
                <div className="h-px flex-1 bg-black/10" />
                <span className="text-[11px] font-semibold uppercase tracking-wider text-black/30">ou</span>
                <div className="h-px flex-1 bg-black/10" />
              </div>

              <button className="flex w-full items-center justify-center gap-2 rounded-lg border border-black/15 px-4 py-2.5 text-sm font-semibold text-black transition-colors hover:bg-black hover:text-white">
                <Github size={16} />
                Continuer avec GitHub
              </button>

              <p className="mt-6 text-center text-xs text-black/50">
                {mode === 'signin' ? (
                  <>
                    Pas encore de compte ?{' '}
                    <button onClick={() => onModeChange('signup')} className="font-semibold text-purple-600 transition-colors hover:text-black">
                      S'inscrire
                    </button>
                  </>
                ) : (
                  <>
                    Déjà un compte ?{' '}
                    <button onClick={() => onModeChange('signin')} className="font-semibold text-purple-600 transition-colors hover:text-black">
                      Se connecter
                    </button>
                  </>
                )}
              </p>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
