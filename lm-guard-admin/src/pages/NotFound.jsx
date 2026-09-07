import React from 'react';
import { Link } from 'react-router-dom';
import { FileQuestion, ArrowLeft } from 'lucide-react';

export default function NotFound() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="panel max-w-md px-8 py-10 text-center">
        <span className="mx-auto mb-5 flex h-11 w-11 items-center justify-center rounded-lg bg-canvas text-ink-400">
          <FileQuestion className="h-5 w-5" strokeWidth={1.75} />
        </span>
        <p className="font-mono text-13 text-ink-400">404</p>
        <h1 className="mt-1 text-section font-semibold text-ink-900">Page not found</h1>
        <p className="mt-2 text-13 leading-relaxed text-ink-500">
          The requested path does not exist in the LM-GUARD console. It may have been moved, or the record may have been
          removed.
        </p>
        <Link to="/dashboard" className="btn-primary btn-lg mt-6">
          <ArrowLeft className="h-4 w-4" strokeWidth={2} /> Return to dashboard
        </Link>
      </div>
    </div>
  );
}
