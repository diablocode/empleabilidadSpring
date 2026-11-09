import { useCallback, useEffect, useRef, useState } from 'react';
import {
  apiRequest,
  ApiError,
  csrfToken,
  type CurrentUser,
  type Opportunity,
  type Profile,
  type Recommendations,
} from './api';
import { ProfileEditor } from './ProfileEditor';

type HealthResponse = { status: string; service: string };
type Route = '/profile' | '/recommendations' | `/opportunities/${string}` | '/';

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? '';

function currentRoute(): Route {
  const path = window.location.pathname;
  if (path === '/profile' || path === '/recommendations' || /^\/opportunities\/[^/]+$/.test(path)) {
    return path as Route;
  }
  return '/';
}

function messageFrom(cause: unknown, fallback: string): string {
  return cause instanceof Error ? cause.message : fallback;
}

export function App() {
  const [route, setRoute] = useState<Route>(currentRoute);
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [authError, setAuthError] = useState<string | null>(null);
  const [signInButtonReady, setSignInButtonReady] = useState(false);
  const signInButton = useRef<HTMLDivElement>(null);

  const navigate = useCallback((path: Route) => {
    window.history.pushState({}, '', path);
    setRoute(path);
  }, []);

  useEffect(() => {
    const onPopState = () => setRoute(currentRoute());
    const onUnauthenticated = () => {
      setUser(null);
      setAuthLoading(false);
    };
    window.addEventListener('popstate', onPopState);
    window.addEventListener('app:unauthenticated', onUnauthenticated);
    return () => {
      window.removeEventListener('popstate', onPopState);
      window.removeEventListener('app:unauthenticated', onUnauthenticated);
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<HealthResponse>('/api/health', { signal: controller.signal })
      .then(setHealth)
      .catch((cause: unknown) => {
        if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setHealthError(messageFrom(cause, 'The API health check failed.'));
        }
      });
    apiRequest<CurrentUser>('/api/auth/me', { signal: controller.signal })
      .then(setUser)
      .catch((cause: unknown) => {
        if (cause instanceof ApiError && cause.status === 401) {
          setUser(null);
          return;
        }
        if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setAuthError(messageFrom(cause, 'Unable to check the current session.'));
        }
      })
      .finally(() => setAuthLoading(false));
    return () => controller.abort();
  }, []);

  const signIn = useCallback(async (credential: string) => {
    setAuthError(null);
    setAuthLoading(true);
    try {
      const token = await csrfToken();
      await apiRequest<CurrentUser>('/api/auth/google', {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': token },
        body: JSON.stringify({ credential }),
      });
      setUser(await apiRequest<CurrentUser>('/api/auth/me'));
      navigate('/recommendations');
    } catch (cause: unknown) {
      setAuthError(messageFrom(cause, 'Google sign-in failed. Please try again.'));
    } finally {
      setAuthLoading(false);
    }
  }, [navigate]);

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
      setSignInButtonReady(true);
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
    setAuthLoading(true);
    setAuthError(null);
    try {
      const token = await csrfToken();
      await apiRequest<void>('/api/auth/logout', {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': token },
      });
      window.google?.accounts.id.disableAutoSelect();
      setUser(null);
      setSignInButtonReady(false);
      navigate('/');
    } catch (cause: unknown) {
      setAuthError(messageFrom(cause, 'Sign-out failed. Please try again.'));
    } finally {
      setAuthLoading(false);
    }
  };

  return (
    <div className="app-shell">
      {user ? (
        <header className="site-header">
          <button className="brand" type="button" onClick={() => navigate('/recommendations')}>
            <span className="brand-mark" aria-hidden="true">E</span>
            Empleabilidad
          </button>
          <nav className="main-nav" aria-label="Main navigation">
            <button type="button" aria-current={route === '/recommendations' ? 'page' : undefined}
              onClick={() => navigate('/recommendations')}>Opportunities</button>
            <button type="button" aria-current={route === '/profile' ? 'page' : undefined}
              onClick={() => navigate('/profile')}>My profile</button>
          </nav>
          <div className="account">
            {user.pictureUrl ? <img className="avatar" src={user.pictureUrl} alt="" /> : null}
            <span className="account-name">{user.name || user.email}</span>
            <button className="sign-out" type="button" onClick={() => void signOut()} disabled={authLoading}>
              Sign out
            </button>
          </div>
        </header>
      ) : null}
      {authError ? <div className="global-alert" role="alert">{authError}</div> : null}
      <main className={user ? 'app-content' : 'landing-page'}>
        {authLoading ? (
          <div className="loading-panel" role="status">Checking your sign-in…</div>
        ) : user ? (
          <SignedInView route={route} user={user} navigate={navigate} />
        ) : (
          <section className="landing-card">
            <div className="landing-mark" aria-hidden="true">E</div>
            <p className="eyebrow">Empleabilidad</p>
            <h1>Find the work that fits your next chapter.</h1>
            <p className="landing-description">
              Build a professional profile, explore current opportunities, and see what makes each match relevant.
            </p>
            <div className={`service-status ${healthError ? 'is-error' : ''}`} role="status" aria-live="polite">
              {health ? `Application service ${health.status.toLowerCase()}` :
                healthError ? `Application service unavailable: ${healthError}` : 'Connecting to the application…'}
            </div>
            <div className="sign-in-area">
              {googleClientId ? (
                <>
                  <div className="google-button" ref={signInButton} />
                  {!signInButtonReady ? <p className="muted">Loading secure Google sign-in…</p> : null}
                </>
              ) : (
                <p className="configuration-message">
                  Google sign-in is not configured. Set <code>VITE_GOOGLE_CLIENT_ID</code> in
                  <code> client/.env.local</code>, then restart the React development server.
                </p>
              )}
            </div>
            <p className="privacy-note">Your sign-in is handled by Google. We never ask for your Google password.</p>
          </section>
        )}
      </main>
      <footer className="site-footer">Career guidance built around your experience.</footer>
    </div>
  );
}

type SignedInProps = {
  route: Route;
  user: CurrentUser;
  navigate: (path: Route) => void;
};

function SignedInView({ route, user, navigate }: SignedInProps) {
  if (route === '/profile') {
    return <ProfileView user={user} />;
  }
  if (route === '/recommendations' || route === '/') {
    return <RecommendationsView navigate={navigate} />;
  }
  const offerId = route.slice('/opportunities/'.length);
  return <OpportunityView offerId={offerId} navigate={navigate} />;
}

function ProfileView({ user }: { user: CurrentUser }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<Profile>('/api/profile', { signal: controller.signal })
      .then((value) => {
        setProfile(value);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (cause instanceof ApiError && cause.status === 404) {
          setProfile(null);
          setError(null);
        } else if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setError(messageFrom(cause, 'Your profile could not be loaded.'));
        }
      })
      .finally(() => setLoaded(true));
    return () => controller.abort();
  }, []);

  if (!loaded) {
    return <div className="loading-panel" role="status">Loading your profile…</div>;
  }
  if (error) {
    return <ErrorPanel message={error} />;
  }
  return (
    <div className="content-column">
      {saved ? <p className="alert alert-success" role="status">Your profile was saved.</p> : null}
      <ProfileEditor profile={profile} suggestedName={user.name} suggestedEmail={user.email}
        onSaved={(value) => { setProfile(value); setSaved(true); }} />
    </div>
  );
}

function RecommendationsView({ navigate }: { navigate: (path: Route) => void }) {
  const [data, setData] = useState<Recommendations | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    apiRequest<Recommendations>('/api/recommendations', { signal: controller.signal })
      .then(setData)
      .catch((cause: unknown) => {
        if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setError(messageFrom(cause, 'Recommendations could not be loaded.'));
        }
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [retry]);

  if (loading) {
    return <div className="loading-panel" role="status">Finding opportunities for you…</div>;
  }
  if (error) {
    return <ErrorPanel message={error} onRetry={() => setRetry((value) => value + 1)} />;
  }
  if (!data) {
    return <ErrorPanel message="Recommendations are unavailable right now." onRetry={() => setRetry((value) => value + 1)} />;
  }
  if (data.status === 'INCOMPLETE_PROFILE') {
    return (
      <section className="state-card">
        <div className="state-icon" aria-hidden="true">✦</div>
        <p className="eyebrow">A stronger match starts here</p>
        <h1>Complete your profile to get recommendations</h1>
        <p>Add competency ratings to see current opportunities that match your experience. Optional profile sections can be left blank.</p>
        <button className="button button-primary" type="button" onClick={() => navigate('/profile')}>Complete my profile</button>
      </section>
    );
  }
  if (data.status === 'NO_MATCHES' || data.recommendations.length === 0) {
    return (
      <section className="state-card">
        <div className="state-icon" aria-hidden="true">⌕</div>
        <p className="eyebrow">Your opportunities</p>
        <h1>No current matches just yet</h1>
        <p>There are no current opportunities matching your profile right now. Your profile is saved, and recommendations will update when suitable listings are available.</p>
        <button className="button button-secondary" type="button" onClick={() => navigate('/profile')}>Review my profile</button>
      </section>
    );
  }

  return (
    <section className="recommendations-view">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Picked for your profile</p>
          <h1>Opportunities to explore</h1>
          <p>These are relevance suggestions, not a guarantee of eligibility or employment.</p>
        </div>
        <button className="button button-secondary" type="button" onClick={() => navigate('/profile')}>Edit profile</button>
      </div>
      <div className="recommendation-list">
        {data.recommendations.map((recommendation, index) => (
          <article className="recommendation-card" key={recommendation.offerId}>
            <div className="card-rank" aria-label={`Recommendation ${index + 1}`}>
              {String(index + 1).padStart(2, '0')}
            </div>
            <div className="recommendation-main">
              <p className="card-kicker">{recommendation.employerName || 'Opportunity'}</p>
              <h2>{recommendation.title}</h2>
              <p className="opportunity-meta">
                {[recommendation.location, recommendation.modality].filter(Boolean).join(' · ') || 'Details available'}
              </p>
              <div className="match-summary">
                <span>{recommendation.competenciesMatched} of {recommendation.competenciesTotal} competency matches</span>
                {recommendation.mandatoryCompetenciesTotal > 0 ? (
                  <span>{recommendation.mandatoryCompetenciesMatched} of {recommendation.mandatoryCompetenciesTotal} required competencies</span>
                ) : null}
              </div>
              {recommendation.reasons.length ? (
                <ul className="reason-list">
                  {recommendation.reasons.map((reason, reasonIndex) => <li key={`${reasonIndex}-${reason}`}>{reason}</li>)}
                </ul>
              ) : null}
              <button className="text-button" type="button"
                onClick={() => navigate(`/opportunities/${recommendation.offerId}`)}>
                View opportunity details <span aria-hidden="true">→</span>
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function OpportunityView({ offerId, navigate }: { offerId: string; navigate: (path: Route) => void }) {
  const [opportunity, setOpportunity] = useState<Opportunity | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    apiRequest<Opportunity>(`/api/opportunities/${encodeURIComponent(offerId)}`, { signal: controller.signal })
      .then(setOpportunity)
      .catch((cause: unknown) => {
        if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setError(messageFrom(cause, 'Opportunity details could not be loaded.'));
        }
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [offerId]);

  if (loading) {
    return <div className="loading-panel" role="status">Loading opportunity details…</div>;
  }
  if (error || !opportunity) {
    return <ErrorPanel message={error ?? 'Opportunity not found.'} onBack={() => navigate('/recommendations')} />;
  }
  return (
    <article className="detail-card">
      <button className="back-button" type="button" onClick={() => navigate('/recommendations')}>
        <span aria-hidden="true">←</span> Back to recommendations
      </button>
      <p className="eyebrow">{opportunity.employerName || 'Opportunity'}</p>
      <h1>{opportunity.title}</h1>
      <div className="detail-meta">
        {opportunity.location ? <span>{opportunity.location}</span> : null}
        {opportunity.modality ? <span>{opportunity.modality}</span> : null}
        <span className={`freshness freshness-${opportunity.freshness.toLowerCase()}`}>
          {opportunity.freshness.toLowerCase().replace('_', ' ')}
        </span>
      </div>
      {opportunity.expiresOn ? <p className="expiry-note">Listed through {opportunity.expiresOn}</p> : null}
      {opportunity.description ? <section className="detail-section"><h2>About this opportunity</h2><p>{opportunity.description}</p></section> : null}
      <section className="detail-section">
        <h2>Competency requirements</h2>
        {opportunity.requirements.length ? (
          <ul className="requirement-list">
            {opportunity.requirements.map((requirement, index) => (
              <li key={`${requirement.competencyId ?? index}`}>
                <span>{requirement.competency || 'Competency not specified'}</span>
                <span className="requirement-meta">
                  {requirement.expectedLevel !== null && requirement.expectedLevelScale === 'COMPETENCY_1_TO_5'
                    ? `Expected level ${requirement.expectedLevel}/5` : ''}
                  {requirement.mandatory === true ? 'Required' : requirement.mandatory === false ? 'Preferred' : ''}
                </span>
              </li>
            ))}
          </ul>
        ) : <p>No competency requirements were listed.</p>}
      </section>
      <p className="detail-disclaimer">Review the opportunity details carefully. A recommendation is guidance, not a hiring guarantee.</p>
    </article>
  );
}

function ErrorPanel({ message, onRetry, onBack }: { message: string; onRetry?: () => void; onBack?: () => void }) {
  return (
    <section className="state-card state-card-error">
      <p className="eyebrow">Something went wrong</p>
      <h1>We couldn't load this information</h1>
      <p>{message}</p>
      {onRetry ? <button className="button button-primary" type="button" onClick={onRetry}>Try again</button> : null}
      {onBack ? <button className="button button-secondary" type="button" onClick={onBack}>Back to recommendations</button> : null}
    </section>
  );
}
