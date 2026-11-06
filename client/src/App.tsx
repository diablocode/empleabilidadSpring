import { useEffect, useState } from 'react';

type HealthResponse = {
  status: string;
  service: string;
};

export function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <main className="page">
      <section className="status-card" aria-labelledby="page-title">
        <p className="eyebrow">Empleabilidad</p>
        <h1 id="page-title">Development environment</h1>
        <p className="description">
          The React client is connected to the Spring Boot application through the local API proxy.
        </p>
        <div className={`status ${error ? 'status-error' : ''}`} role="status" aria-live="polite">
          {health ? (
            <>
              <span className="status-dot" />
              API {health.status.toLowerCase()} · {health.service}
            </>
          ) : error ? (
            `API unavailable: ${error}`
          ) : (
            'Checking the API connection…'
          )}
        </div>
      </section>
    </main>
  );
}
