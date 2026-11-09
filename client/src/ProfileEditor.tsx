import { useEffect, useState } from 'react';
import { apiRequest, ApiError, csrfToken, type CompetencyPage, type Profile, type ProfileEntry } from './api';

type Props = {
  profile: Profile | null;
  suggestedName: string | null;
  suggestedEmail: string;
  onSaved: (profile: Profile) => void;
};

type ContactEntry = { name: string; description: string | null };

const languageLevels = [
  ['A1', 'A1 — Beginner'],
  ['A2', 'A2 — Elementary'],
  ['B1', 'B1 — Intermediate'],
  ['B2', 'B2 — Upper intermediate'],
  ['C1', 'C1 — Advanced'],
  ['C2', 'C2 — Proficient'],
] as const;
function emptyProfile(name: string | null, email: string): Profile {
  return {
    name: name ?? '',
    contactEmail: email,
    contactPhone: null,
    competencies: [],
    languages: [],
    education: [],
    workExperience: [],
    skills: [],
    softSkills: [],
    socioemotionalFactors: [],
    professionalContacts: [],
  };
}

function profileCopy(profile: Profile | null, name: string | null, email: string): Profile {
  if (!profile) {
    return emptyProfile(name, email);
  }
  return structuredClone(profile);
}

export function ProfileEditor({ profile, suggestedName, suggestedEmail, onSaved }: Props) {
  const [draft, setDraft] = useState(() => profileCopy(profile, suggestedName, suggestedEmail));
  const [suggestedCompetencies, setSuggestedCompetencies] = useState<{ id: string; name: string }[]>([]);
  const [competencyLoadError, setCompetencyLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraft(profileCopy(profile, suggestedName, suggestedEmail));
  }, [profile, suggestedName, suggestedEmail]);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<CompetencyPage>('/api/competencies?limit=100', { signal: controller.signal })
      .then((page) => {
        setSuggestedCompetencies(page.competencies.map((item) => ({
          id: item.competencyId,
          name: item.name,
        })));
        setCompetencyLoadError(null);
      })
      .catch((cause: unknown) => {
        if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setSuggestedCompetencies([]);
          setCompetencyLoadError(cause instanceof Error
            ? cause.message : 'The competency catalog could not be loaded.');
        }
      });
    return () => controller.abort();
  }, []);

  const update = <K extends keyof Profile>(field: K, value: Profile[K]) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };

  const addEntry = (field: 'education' | 'workExperience' | 'skills' | 'softSkills') => {
    update(field, [...draft[field], { name: '', description: null, startDate: null, endDate: null }]);
  };

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const token = await csrfToken();
      const saved = await apiRequest<Profile>('/api/profile', {
        method: 'PUT',
        headers: { 'X-XSRF-TOKEN': token },
        body: JSON.stringify(draft),
      });
      onSaved(saved);
    } catch (cause: unknown) {
      setError(cause instanceof ApiError
        ? cause.message
        : cause instanceof Error ? cause.message : 'Profile could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="profile-form" onSubmit={(event) => void save(event)}>
      <header className="section-heading">
        <p className="eyebrow">Your profile</p>
        <h1>{profile ? 'Keep your experience up to date' : 'Create your employment profile'}</h1>
        <p>Required fields are marked. Your profile is private to your account and helps personalize opportunity recommendations.</p>
      </header>

      <section className="form-section" aria-labelledby="contact-heading">
        <h2 id="contact-heading">Contact information</h2>
        <div className="form-grid">
          <label>
            Full name <span aria-hidden="true">*</span>
            <input required maxLength={150} value={draft.name}
              onChange={(event) => update('name', event.target.value)} />
          </label>
          <label>
            Contact email <span aria-hidden="true">*</span>
            <input required type="email" maxLength={254} value={draft.contactEmail}
              onChange={(event) => update('contactEmail', event.target.value)} />
          </label>
          <label>
            Phone <span className="optional">(optional)</span>
            <input type="tel" maxLength={40} value={draft.contactPhone ?? ''}
              onChange={(event) => update('contactPhone', event.target.value || null)} />
          </label>
        </div>
      </section>

      <section className="form-section" aria-labelledby="competencies-heading">
        <div className="section-title-row">
          <div>
            <h2 id="competencies-heading">Competencies</h2>
            <p>Rate each competency on the same 1–5 scale. Competencies are needed for recommendations.</p>
          </div>
          <button className="button button-secondary" type="button" disabled={!suggestedCompetencies.length} onClick={() =>
            update('competencies', [...draft.competencies, { competencyId: '', score: 3 }])}>
            Add competency
          </button>
        </div>
        {competencyLoadError ? <p className="alert alert-error" role="alert">{competencyLoadError}</p> : null}
        {draft.competencies.length === 0 ? (
          <p className="empty-note">
            {suggestedCompetencies.length
              ? 'Add a competency to personalize matches.'
              : 'No competencies are currently available in the catalog.'}
          </p>
        ) : null}
        {draft.competencies.map((competency, index) => (
          <div className="repeat-row competency-row" key={`competency-${index}`}>
            <label>
              Competency
              <select required value={competency.competencyId}
                onChange={(event) => update('competencies', draft.competencies.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, competencyId: event.target.value } : item))}>
                <option value="">Select a competency</option>
                {suggestedCompetencies.map((item) => (
                  <option key={item.id} value={item.id}
                    disabled={draft.competencies.some((selected, selectedIndex) =>
                      selectedIndex !== index && selected.competencyId === item.id)}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Level (1–5)
              <select value={competency.score} onChange={(event) => update('competencies', draft.competencies.map((item, itemIndex) =>
                itemIndex === index ? { ...item, score: Number(event.target.value) } : item))}>
                {[1, 2, 3, 4, 5].map((score) => <option key={score} value={score}>{score}</option>)}
              </select>
            </label>
            <button className="icon-button" type="button" aria-label={`Remove competency ${index + 1}`}
              onClick={() => update('competencies', draft.competencies.filter((_, itemIndex) => itemIndex !== index))}>
              Remove
            </button>
          </div>
        ))}
      </section>

      <section className="form-section" aria-labelledby="languages-heading">
        <div className="section-title-row">
          <div>
            <h2 id="languages-heading">Languages <span className="optional">(optional)</span></h2>
            <p>Language proficiency uses CEFR levels and is separate from competency scores.</p>
          </div>
          <button className="button button-secondary" type="button" onClick={() =>
            update('languages', [...draft.languages, { name: '', level: 'B1' }])}>
            Add language
          </button>
        </div>
        {draft.languages.map((language, index) => (
          <div className="repeat-row" key={`language-${index}`}>
            <label>Language
              <input required maxLength={100} value={language.name}
                onChange={(event) => update('languages', draft.languages.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, name: event.target.value } : item))} />
            </label>
            <label>CEFR level
              <select value={language.level} onChange={(event) => update('languages', draft.languages.map((item, itemIndex) =>
                itemIndex === index ? { ...item, level: event.target.value } : item))}>
                {languageLevels.map(([level, label]) => (
                  <option key={level} value={level}>{label}</option>
                ))}
              </select>
            </label>
            <button className="icon-button" type="button" aria-label={`Remove language ${index + 1}`}
              onClick={() => update('languages', draft.languages.filter((_, itemIndex) => itemIndex !== index))}>Remove</button>
          </div>
        ))}
      </section>

      <EntrySection title="Education" field="education" entries={draft.education}
        onChange={(entries) => update('education', entries)} onAdd={() => addEntry('education')} />
      <EntrySection title="Work experience" field="workExperience" entries={draft.workExperience}
        onChange={(entries) => update('workExperience', entries)} onAdd={() => addEntry('workExperience')} />
      <EntrySection title="Skills" field="skills" entries={draft.skills}
        onChange={(entries) => update('skills', entries)} onAdd={() => addEntry('skills')} />
      <EntrySection title="Soft skills" field="softSkills" entries={draft.softSkills}
        onChange={(entries) => update('softSkills', entries)} onAdd={() => addEntry('softSkills')} />

      <ContactSection title="Socioemotional factors (optional)" description="Describe factors you choose to share. No numeric scores are collected."
        entries={draft.socioemotionalFactors}
        onChange={(entries) => update('socioemotionalFactors', entries)}
        onAdd={() => update('socioemotionalFactors', [...draft.socioemotionalFactors,
          { name: '', description: null, evaluationMethod: null }])}
        evaluationMethod />
      <ContactSection title="Professional contacts (optional)" description="These are people or groups you know professionally, not social media accounts."
        entries={draft.professionalContacts}
        onChange={(entries) => update('professionalContacts', entries)}
        onAdd={() => update('professionalContacts', [...draft.professionalContacts, { name: '', description: null }])} />

      {error ? <p className="alert alert-error" role="alert">{error}</p> : null}
      <div className="form-actions">
        <button className="button button-primary" type="submit" disabled={saving}>
          {saving ? 'Saving profile…' : profile ? 'Save changes' : 'Create profile'}
        </button>
        <p className="form-hint">Only your profile is updated. Optional sections can be left blank.</p>
      </div>
    </form>
  );
}

type EntrySectionProps = {
  title: string;
  field: string;
  entries: ProfileEntry[];
  onChange: (entries: ProfileEntry[]) => void;
  onAdd: () => void;
};

function EntrySection({ title, field, entries, onChange, onAdd }: EntrySectionProps) {
  return (
    <section className="form-section" aria-labelledby={`${field}-heading`}>
      <div className="section-title-row">
        <div>
          <h2 id={`${field}-heading`}>{title} <span className="optional">(optional)</span></h2>
          <p>Add only the details you want to share.</p>
        </div>
        <button className="button button-secondary" type="button" onClick={onAdd}>Add</button>
      </div>
      {entries.map((entry, index) => (
        <div className="entry-card" key={`${field}-${index}`}>
          <div className="section-title-row">
            <strong>{title} {index + 1}</strong>
            <button className="icon-button" type="button" onClick={() =>
              onChange(entries.filter((_, itemIndex) => itemIndex !== index))}>
              Remove
            </button>
          </div>
          <div className="form-grid">
            <label>Name
              <input required maxLength={150} value={entry.name}
                onChange={(event) => onChange(entries.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, name: event.target.value } : item))} />
            </label>
            <label>Description
              <input maxLength={2000} value={entry.description ?? ''}
                onChange={(event) => onChange(entries.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, description: event.target.value || null } : item))} />
            </label>
            <label>Start date
              <input type="date" value={entry.startDate ?? ''}
                onChange={(event) => onChange(entries.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, startDate: event.target.value || null } : item))} />
            </label>
            <label>End date
              <input type="date" value={entry.endDate ?? ''}
                onChange={(event) => onChange(entries.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, endDate: event.target.value || null } : item))} />
            </label>
          </div>
        </div>
      ))}
    </section>
  );
}

type ContactSectionProps<T extends ContactEntry> = {
  title: string;
  description: string;
  entries: T[];
  onChange: (entries: T[]) => void;
  onAdd: () => void;
  evaluationMethod?: boolean;
};

function ContactSection<T extends ContactEntry>({
  title, description, entries, onChange, onAdd, evaluationMethod,
}: ContactSectionProps<T>) {
  return (
    <section className="form-section" aria-label={title}>
      <div className="section-title-row">
        <div><h2>{title}</h2><p>{description}</p></div>
        <button className="button button-secondary" type="button" onClick={onAdd}>Add</button>
      </div>
      {entries.map((entry, index) => (
        <div className="entry-card" key={`${title}-${index}`}>
          <div className="section-title-row">
            <strong>{title} {index + 1}</strong>
            <button className="icon-button" type="button" onClick={() =>
              onChange(entries.filter((_, itemIndex) => itemIndex !== index))}>Remove</button>
          </div>
          <div className="form-grid">
            <label>Name
              <input required maxLength={150} value={entry.name}
                onChange={(event) => onChange(entries.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, name: event.target.value } : item))} />
            </label>
            <label>Description
              <input maxLength={2000} value={entry.description ?? ''}
                onChange={(event) => onChange(entries.map((item, itemIndex) =>
                  itemIndex === index ? { ...item, description: event.target.value || null } : item))} />
            </label>
            {evaluationMethod && 'evaluationMethod' in entry ? (
              <label>Evaluation method <span className="optional">(optional)</span>
                <input maxLength={200} value={String(entry.evaluationMethod ?? '')}
                  onChange={(event) => onChange(entries.map((item, itemIndex) =>
                    itemIndex === index && 'evaluationMethod' in item
                      ? { ...item, evaluationMethod: event.target.value || null } : item))} />
              </label>
            ) : null}
          </div>
        </div>
      ))}
    </section>
  );
}
