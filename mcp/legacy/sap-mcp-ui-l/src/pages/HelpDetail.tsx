import { useState, useEffect } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Markdown } from '../components/Markdown';
import { api } from '../services/api';
import type { KnowledgeEntry } from '../types';

export const HelpDetail = () => {
  const { uid } = useParams<{ uid: string }>();
  const [searchParams] = useSearchParams();

  const [state, setState] = useState<{
    entry: KnowledgeEntry | null;
    loading: boolean;
  }>({ entry: null, loading: true });

  useEffect(() => {
    let cancelled = false;
    const lookup = uid
      ? api.getKnowledgeEntry(uid)
      : Promise.resolve<KnowledgeEntry | null>(null);
    lookup.then((entry) => {
      if (cancelled) return;
      setState({ entry, loading: false });
    });
    return () => {
      cancelled = true;
    };
  }, [uid]);

  const qs = searchParams.toString();
  const preservedQs = qs ? `?${qs}` : '';
  const backHref = `/help${preservedQs}`;

  if (state.loading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="flex items-center justify-center py-20">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
              <p className="mt-4 text-gray-600 dark:text-gray-400">Loading...</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (state.entry === null) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-12 text-center border border-gray-200 dark:border-gray-700">
            <h2 className="text-2xl font-semibold text-gray-900 dark:text-white mb-2">
              Help entry not found
            </h2>
            <p className="text-gray-600 dark:text-gray-400 mb-6">
              This entry may have been moved or removed.
            </p>
            <Link
              to={backHref}
              aria-label="Back to Help"
              className="text-blue-600 dark:text-blue-400 hover:underline font-semibold"
            >
              &larr; Back to Help
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const entry = state.entry;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Link
          to={backHref}
          aria-label="Back to Help"
          className="inline-block mb-6 text-sm font-medium text-blue-600 dark:text-blue-400 hover:underline"
        >
          &larr; Back to Help
        </Link>

        <article>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-4">
            {entry.title}
          </h1>

          <div className="mb-4">
            <span className="inline-block px-2 py-0.5 rounded-full text-xs font-medium capitalize bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">
              {entry.category}
            </span>
          </div>

          {entry.imageUrl && (
            <img
              src={entry.imageUrl}
              alt={entry.title}
              loading="lazy"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
              className="block my-4 w-full max-w-2xl rounded-lg"
            />
          )}

          <div className="max-w-3xl text-gray-700 dark:text-gray-300">
            <Markdown>{entry.body}</Markdown>
          </div>
        </article>
      </div>
    </div>
  );
};
