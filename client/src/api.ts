export type ApiErrorResponse = {
  code: string;
  message: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;

  constructor(
    message: string,
    status: number,
    code: string | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });
  if (response.status === 401) {
    window.dispatchEvent(new Event('app:unauthenticated'));
  }
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as ApiErrorResponse | null;
    throw new ApiError(
      error?.message ?? `The application API responded with HTTP ${response.status}.`,
      response.status,
      error?.code ?? null,
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function csrfToken(): Promise<string> {
  const response = await apiRequest<{ token: string }>('/api/auth/csrf');
  return response.token;
}

export type CurrentUser = {
  email: string;
  name: string | null;
  pictureUrl: string | null;
};

export type ProfileEntry = {
  name: string;
  description: string | null;
  startDate: string | null;
  endDate: string | null;
};

export type Profile = {
  name: string;
  contactEmail: string;
  contactPhone: string | null;
  competencies: { competencyId: string; score: number }[];
  languages: { name: string; level: string }[];
  education: ProfileEntry[];
  workExperience: ProfileEntry[];
  skills: ProfileEntry[];
  softSkills: ProfileEntry[];
  socioemotionalFactors: {
    name: string;
    description: string | null;
    evaluationMethod: string | null;
  }[];
  professionalContacts: { name: string; description: string | null }[];
};

export type Recommendation = {
  offerId: string;
  title: string;
  employerName: string | null;
  location: string | null;
  modality: string | null;
  mandatoryCompetenciesMatched: number;
  mandatoryCompetenciesTotal: number;
  mandatoryLevelsMet: number;
  mandatoryLevelsComparable: number;
  competenciesMatched: number;
  competenciesTotal: number;
  matchedCompetencies: string[];
  reasons: string[];
};

export type Recommendations = {
  status: 'READY' | 'INCOMPLETE_PROFILE' | 'NO_MATCHES';
  recommendations: Recommendation[];
  missingProfileFields: string[];
};

export type Opportunity = {
  offerId: string;
  title: string;
  employerName: string | null;
  description: string | null;
  location: string | null;
  modality: string | null;
  expiresOn: string | null;
  freshness: 'CURRENT' | 'EXPIRED' | 'CLOSED' | 'UNKNOWN_STATUS' | 'INDETERMINATE';
  requirements: {
    competencyId: string | null;
    competency: string | null;
    expectedLevel: number | null;
    expectedLevelScale: string | null;
    mandatory: boolean | null;
  }[];
};

export type OpportunityPage = {
  offset: number;
  limit: number;
  count: number;
  hasMore: boolean;
  opportunities: Opportunity[];
};

export type CompetencyPage = {
  offset: number;
  limit: number;
  count: number;
  hasMore: boolean;
  competencies: { competencyId: string; name: string }[];
};
