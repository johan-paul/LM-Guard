import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { inspectionService } from '../services/inspectionService';

/**
 * Generic async resource hook.
 * `deps` controls refetching; `loader` receives the current deps object.
 */
export function useResource(loader, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const mounted = useRef(true);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await loaderRef.current();
      if (mounted.current) setData(result);
      return result;
    } catch (err) {
      if (mounted.current) setError(err.message || 'Request failed');
      return null;
    } finally {
      if (mounted.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (immediate) run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run, immediate]);

  return { data, loading, error, refetch: run, setData };
}

/* ------------------------------------------------------------------ */
/* Domain hooks                                                        */
/* ------------------------------------------------------------------ */

export function useStats() {
  const { data, loading, error, refetch } = useResource(() => inspectionService.getStats(), []);
  return { stats: data, loading, error, refetch };
}

export function useInspections(filters = {}) {
  const key = JSON.stringify(filters);
  const { data, loading, error, refetch } = useResource(() => inspectionService.getInspections(filters), [key]);
  return { inspections: data || [], loading, error, refetch };
}

export function useInspection(id) {
  const { data, loading, error, refetch } = useResource(() => inspectionService.getInspectionById(id), [id]);
  return { inspection: data, loading, error, refetch };
}

export function useViolations(filters = {}) {
  const key = JSON.stringify(filters);
  const { data, loading, error, refetch } = useResource(() => inspectionService.getViolations(filters), [key]);
  return { violations: data || [], loading, error, refetch };
}

/** The full set of "violation type" filter values, independent of any active filter - so the
 * dropdown's own option list doesn't shrink to just whatever is currently selected. */
export function useViolationTypes() {
  const { data } = useResource(() => inspectionService.getViolationTypes(), []);
  return data || [];
}

export function useViolation(id) {
  const { data, loading, error, refetch, setData } = useResource(() => inspectionService.getViolationById(id), [id]);
  return { violation: data, loading, error, refetch, setViolation: setData };
}

export function useProducts(filters = {}) {
  const key = JSON.stringify(filters);
  const { data, loading, error, refetch } = useResource(() => inspectionService.getProducts(filters), [key]);
  return { products: data || [], loading, error, refetch };
}

export function useProduct(id) {
  const { data, loading, error, refetch } = useResource(() => inspectionService.getProductById(id), [id]);
  return { product: data, loading, error, refetch };
}

export function useProductHistory(filters = {}) {
  const key = JSON.stringify(filters);
  const { data, loading, error, refetch } = useResource(() => inspectionService.getProductHistory(filters), [key]);
  return { history: data || [], loading, error, refetch };
}

export function useRiskQueue(limit) {
  const { data, loading, error, refetch } = useResource(() => inspectionService.getRiskQueue(limit), [limit]);
  return { queue: data || [], loading, error, refetch };
}

export function useRepeatOffenders() {
  const { data, loading, error } = useResource(() => inspectionService.getRepeatOffenders(), []);
  return { offenders: data || [], loading, error };
}

export function useRiskFactors() {
  const { data, loading, error } = useResource(() => inspectionService.getRiskFactors(), []);
  return { factors: data || [], loading, error };
}

export function useRules(filters = {}) {
  const key = JSON.stringify(filters);
  const { data, loading, error, refetch } = useResource(() => inspectionService.getRules(filters), [key]);
  return { ruleset: data?.ruleset, categories: data?.categories || [], rules: data?.rules || [], loading, error, refetch };
}

export function useAnalytics() {
  const { data, loading, error } = useResource(() => inspectionService.getAnalytics(), []);
  return { analytics: data, loading, error };
}

export function useInspectors(filters = {}) {
  const key = JSON.stringify(filters);
  const { data, loading, error, refetch } = useResource(() => inspectionService.getInspectors(filters), [key]);
  return { inspectors: data || [], loading, error, refetch };
}

export function useZoneSummary() {
  const { data, loading, error, refetch } = useResource(() => inspectionService.getZoneSummary(), []);
  return { zones: data || [], loading, error, refetch };
}

export function useReferenceData() {
  const { data, loading } = useResource(() => inspectionService.getReferenceData(), []);
  return { reference: data, loading };
}

/* ------------------------------------------------------------------ */
/* UI helpers                                                          */
/* ------------------------------------------------------------------ */

/** Debounces a fast-changing value (search inputs). */
export function useDebounced(value, ms = 250) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

/** Client-side pagination over an already-loaded row set. */
export function usePagination(rows, pageSize = 10) {
  const [page, setPage] = useState(1);
  const total = rows.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  useEffect(() => {
    setPage(1);
  }, [total, pageSize]);

  const paged = useMemo(() => rows.slice((page - 1) * pageSize, page * pageSize), [rows, page, pageSize]);

  return {
    page,
    pageCount,
    total,
    pageSize,
    rows: paged,
    from: total === 0 ? 0 : (page - 1) * pageSize + 1,
    to: Math.min(page * pageSize, total),
    next: () => setPage((p) => Math.min(pageCount, p + 1)),
    prev: () => setPage((p) => Math.max(1, p - 1)),
    goTo: setPage,
  };
}
