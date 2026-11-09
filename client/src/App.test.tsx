import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { App } from './App';

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

function authenticatedUser() {
  return { email: 'candidate@example.com', name: 'Example Candidate', pictureUrl: null };
}

describe('application flows', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/');
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => cleanup());

  it('shows the signed-out state and reports API availability', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/health') {
        return jsonResponse({ status: 'UP', service: 'empleabilidadSpring' });
      }
      return jsonResponse({ code: 'unauthenticated', message: 'Sign in is required.' }, 401);
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: /find the work that fits/i })).toBeInTheDocument();
    expect(await screen.findByRole('status')).toHaveTextContent('Application service up');
    expect(screen.getByText(/Your sign-in is handled by Google/)).toBeInTheDocument();
  });

  it('shows ranked recommendations with their match explanations', async () => {
    window.history.replaceState({}, '', '/recommendations');
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/health') {
        return jsonResponse({ status: 'UP', service: 'empleabilidadSpring' });
      }
      if (String(input) === '/api/auth/me') {
        return jsonResponse(authenticatedUser());
      }
      return jsonResponse({
        status: 'READY',
        missingProfileFields: [],
        recommendations: [{
          offerId: 'offer-public-id',
          title: 'Backend developer',
          employerName: 'Example employer',
          location: 'Guadalajara',
          modality: 'Hybrid',
          mandatoryCompetenciesMatched: 1,
          mandatoryCompetenciesTotal: 1,
          mandatoryLevelsMet: 1,
          mandatoryLevelsComparable: 1,
          competenciesMatched: 1,
          competenciesTotal: 1,
          matchedCompetencies: ['Java'],
          reasons: ['Your competency level 4/5 meets the expected level 4/5 for Java.'],
        }],
      });
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Opportunities to explore' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Backend developer' })).toBeInTheDocument();
    expect(screen.getByText(/meets the expected level 4\/5/i)).toBeInTheDocument();
  });

  it('provides a clear next step for incomplete profiles', async () => {
    window.history.replaceState({}, '', '/recommendations');
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/health') {
        return jsonResponse({ status: 'UP', service: 'empleabilidadSpring' });
      }
      if (String(input) === '/api/auth/me') {
        return jsonResponse(authenticatedUser());
      }
      return jsonResponse({
        status: 'INCOMPLETE_PROFILE',
        recommendations: [],
        missingProfileFields: ['competencies'],
      });
    });

    render(<App />);
    expect(await screen.findByRole('heading', { name: /complete your profile/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Complete my profile' })).toBeInTheDocument();
  });

  it('shows a useful empty state when no current opportunities match', async () => {
    window.history.replaceState({}, '', '/recommendations');
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/health') {
        return jsonResponse({ status: 'UP', service: 'empleabilidadSpring' });
      }
      if (String(input) === '/api/auth/me') {
        return jsonResponse(authenticatedUser());
      }
      return jsonResponse({ status: 'NO_MATCHES', recommendations: [], missingProfileFields: [] });
    });
    render(<App />);
    expect(await screen.findByRole('heading', { name: /no current matches just yet/i })).toBeInTheDocument();
  });

  it('loads safe opportunity details without inventing an application link', async () => {
    window.history.replaceState({}, '', '/opportunities/offer-public-id');
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/health') {
        return jsonResponse({ status: 'UP', service: 'empleabilidadSpring' });
      }
      if (String(input) === '/api/auth/me') {
        return jsonResponse(authenticatedUser());
      }
      return jsonResponse({
        offerId: 'offer-public-id',
        title: 'Backend developer',
        employerName: 'Example employer',
        description: 'Build reliable services.',
        location: 'Guadalajara',
        modality: 'Hybrid',
        expiresOn: null,
        freshness: 'CURRENT',
        requirements: [{ competencyId: 'skill-id', competency: 'Java', expectedLevel: 4,
          expectedLevelScale: 'COMPETENCY_1_TO_5', mandatory: true }],
      });
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Backend developer' })).toBeInTheDocument();
    expect(screen.getByText('Build reliable services.')).toBeInTheDocument();
    expect(screen.getByText(/Expected level 4\/5/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /apply/i })).not.toBeInTheDocument();
  });

  it('returns to the signed-out state when an authenticated API call expires', async () => {
    window.history.replaceState({}, '', '/recommendations');
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/health') {
        return jsonResponse({ status: 'UP', service: 'empleabilidadSpring' });
      }
      if (String(input) === '/api/auth/me') {
        return jsonResponse(authenticatedUser());
      }
      return jsonResponse({ code: 'unauthenticated', message: 'Sign in is required.' }, 401);
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: /find the work that fits/i })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Main navigation' })).not.toBeInTheDocument();
  });
});
