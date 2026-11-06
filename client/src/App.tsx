import { useCallback, useEffect, useRef, useState } from 'react';

type HealthResponse = {
  status: string;
  service: string;
};

type CurrentUser = {
  email: string;
  name: string | null;
  pictureUrl: string | null;
};

type CsrfResponse = {
  token: string;
};

type ApiErrorResponse = {
  code: string;
  message: string;
};

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? '';

export function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [authError, setAuthError] = useState<string | null>(null);
  const [buttonReady, setButtonReady] = useState(false);
  const signInButton = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();

    fetch('/api/health', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`The API responded with HTTP ${response.status}.`);
        }
        return (await response.json()) as HealthResponse;
      })
      .then(setHealth)
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === 'AbortError') {
          return;
        }
        setError(cause instanceof Error ? cause.message : 'The API health check failed.');
      });

    return () => controller.abort();
  }, []);

  const refreshCurrentUser = useCallback(async () => {
    const response = await fetch('/api/auth/me');
    if (response.status === 401) {
      setUser(null);
      return;
    }
    if (!response.ok) {
      throw new Error(`The authentication API responded with HTTP ${response.status}.`);
    }
    setUser((await response.json()) as CurrentUser);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/auth/me', { signal: controller.signal })
      .then(async (response) => {
        if (response.status === 401) {
          return null;
        }
        if (!response.ok) {
          throw new Error(`The authentication API responded with HTTP ${response.status}.`);
        }
        return (await response.json()) as CurrentUser;
      })
      .then((currentUser) => setUser(currentUser))
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === 'AbortError') {
          return;
        }
        setAuthError(cause instanceof Error ? cause.message : 'Unable to check the current session.');
      })
      .finally(() => setAuthLoading(false));

    return () => controller.abort();
  }, []);

  const signIn = useCallback(async (credential: string) => {
    setAuthError(null);
    setAuthLoading(true);
    try {
      const csrfResponse = await fetch('/api/auth/csrf');
      if (!csrfResponse.ok) {
        throw new Error(`Could not prepare secure sign-in (HTTP ${csrfResponse.status}).`);
      }
      const { token } = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch('/api/auth/google', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': token,
        },
        body: JSON.stringify({ credential }),
      });
      if (!response.ok) {
        const apiError = (await response.json().catch(() => null)) as ApiErrorResponse | null;
        throw new Error(apiError?.message ?? `Google sign-in failed (HTTP ${response.status}).`);
      }
      await refreshCurrentUser();
    } catch (cause: unknown) {
      setAuthError(cause instanceof Error ? cause.message : 'Google sign-in failed. Please try again.');
    } finally {
      setAuthLoading(false);
    }
  }, [refreshCurrentUser]);

  useEffect(() => {
    if (user || authLoading || !googleClientId || !signInButton.current) {
      return;
    }

    let active = true;
    let attempts = 0;
    let timer: number | undefined;

    const renderGoogleButton = () => {
      if (!active || !signInButton.current) {
        return;
      }
      const google = window.google;
      if (!google) {
        attempts += 1;
        if (attempts < 30) {
          timer = window.setTimeout(renderGoogleButton, 100);
        } else {
          setAuthError('Google sign-in could not load. Check your internet connection and reload.');
        }
        return;
      }
      signInButton.current.replaceChildren();
      google.accounts.id.initialize({
        client_id: googleClientId,
        callback: (response) => {
          if (response.credential) {
            void signIn(response.credential);
          } else {
            setAuthError('Google did not return a sign-in credential. Please try again.');
          }
        },
        auto_select: false,
        cancel_on_tap_outside: true,
      });
      google.accounts.id.renderButton(signInButton.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text: 'signin_with',
        shape: 'rectangular',
        width: Math.min(signInButton.current.clientWidth || 320, 360),
      });
      setButtonReady(true);
    };

    renderGoogleButton();
    return () => {
      active = false;
      if (timer !== undefined) {
        window.clearTimeout(timer);
      }
    };
  }, [authLoading, signIn, user]);

  const signOut = async () => {
    setAuthError(null);
    setAuthLoading(true);
    try {
      const csrfResponse = await fetch('/api/auth/csrf');
      if (!csrfResponse.ok) {
        throw new Error(`Could not prepare secure sign-out (HTTP ${csrfResponse.status}).`);
      }
      const { token } = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': token },
      });
      if (!response.ok) {
        throw new Error(`Sign-out failed (HTTP ${response.status}).`);
      }
      window.google?.accounts.id.disableAutoSelect();
      setUser(null);
      setButtonReady(false);
    } catch (cause: unknown) {
      setAuthError(cause instanceof Error ? cause.message : 'Sign-out failed. Please try again.');
    } finally {
      setAuthLoading(false);
    }
  };

  return (
    <main className="page">
      <section className="status-card" aria-labelledby="page-title">
        <p className="eyebrow">Empleabilidad</p>
        <h1 id="page-title">Your next opportunity starts here</h1>
        <p className="description">Sign in with Google to continue to your employment profile.</p>
        <div className={`status ${error ? 'status-error' : ''}`} role="status" aria-live="polite">
          {health ? (
            <>
              <span className="status-dot" />
              Application API {health.status.toLowerCase()}
            </>
          ) : error ? (
            `Application API unavailable: ${error}`
          ) : (
            'Checking application API…'
          )}
        </div>
        <div className="auth-panel" aria-live="polite">
          {authLoading ? (
            <p className="auth-message">Checking your sign-in…</p>
          ) : user ? (
            <div className="signed-in">
              {user.pictureUrl ? <img className="avatar" src={user.pictureUrl} alt="" /> : null}
              <div className="user-details">
                <strong>{user.name || 'Signed in with Google'}</strong>
                <span>{user.email}</span>
              </div>
              <button className="sign-out" type="button" onClick={() => void signOut()}>
                Sign out
              </button>
            </div>
          ) : googleClientId ? (
            <>
              <div className="google-button" ref={signInButton} />
              {!buttonReady ? <p className="auth-message">Loading Google sign-in…</p> : null}
            </>
          ) : (
            <p className="configuration-message">
              Google sign-in is not configured. Set <code>VITE_GOOGLE_CLIENT_ID</code> in
              <code> client/.env.local</code>, then restart the React development server.
            </p>
          )}
          {authError ? <p className="auth-error" role="alert">{authError}</p> : null}
        </div>
      </section>
    </main>
  );
}
