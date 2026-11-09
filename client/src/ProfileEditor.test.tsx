import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ProfileEditor } from './ProfileEditor';

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

describe('profile editor', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  it('saves an opaque competency selection with an accessible scale and optional profile sections', async () => {
    const competencyId = 'cddb57e7-62c0-3f98-bba4-e9d429e7ec9e';
    const requests: { url: string; init?: RequestInit }[] = [];
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input);
      requests.push({ url, init });
      if (url === '/api/competencies?limit=100') {
        return jsonResponse({
          competencies: [{ competencyId, name: 'Java' }],
        });
      }
      if (url === '/api/auth/csrf') {
        return jsonResponse({ token: 'csrf-token' });
      }
      if (url === '/api/profile' && init?.method === 'PUT') {
        return jsonResponse(JSON.parse(String(init.body)));
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    const onSaved = vi.fn();

    render(<ProfileEditor profile={null} suggestedName="Candidate" suggestedEmail="candidate@example.com" onSaved={onSaved} />);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Add competency' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Add competency' }));
    await screen.findByRole('option', { name: 'Java' });
    fireEvent.change(screen.getByLabelText('Competency'), { target: { value: competencyId } });
    fireEvent.change(screen.getByLabelText('Level (1–5)'), { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add language' }));
    fireEvent.change(screen.getByLabelText('Language'), { target: { value: 'English' } });
    fireEvent.change(screen.getByLabelText('CEFR level'), { target: { value: 'B2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create profile' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
    const saveRequest = requests.find((request) => request.url === '/api/profile' && request.init?.method === 'PUT');
    expect(saveRequest).toBeDefined();
    expect((saveRequest?.init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-token');
    const body = JSON.parse(String(saveRequest?.init?.body));
    expect(body.competencies).toEqual([{ competencyId, score: 4 }]);
    expect(body.languages).toEqual([{ name: 'English', level: 'B2' }]);
    expect(body.socioemotionalFactors).toEqual([]);
    expect(body.professionalContacts).toEqual([]);
  });

  it('shows a server validation error without reporting a successful save', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input) === '/api/competencies?limit=100') {
        return jsonResponse({ competencies: [] });
      }
      if (String(input) === '/api/auth/csrf') {
        return jsonResponse({ token: 'csrf-token' });
      }
      return jsonResponse({ code: 'invalid_profile', message: 'A selected competency is invalid.' }, 400);
    });
    const onSaved = vi.fn();

    render(<ProfileEditor profile={null} suggestedName="Candidate" suggestedEmail="candidate@example.com" onSaved={onSaved} />);
    fireEvent.click(screen.getByRole('button', { name: 'Create profile' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A selected competency is invalid.');
    expect(onSaved).not.toHaveBeenCalled();
  });
});
