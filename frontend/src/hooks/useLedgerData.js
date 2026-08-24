import { useEffect, useRef, useState } from 'react';
import * as ledgerApi from '../api/ledgerApi';
import * as managementApi from '../api/managementApi';
import { hasLedgerFilters, isAllFilterValue } from '../screens/LedgerScreen';
import { formatDate } from '../utils/format';
import { toNumber } from '../utils/numberValues';
import { trimToEmpty } from '../utils/stringValues';

const emptyData = {
  assets: [],
  categories: [],
  transactions: [],
  summary: null,
  assetSummary: null
};

const emptySearchResult = {
  items: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  sort: 'DATE_DESC'
};

// Only free-text fields need debouncing while the user is typing. Discrete picks
// (dropdowns, pagination, sort) should search immediately - there is no typing to wait out.
const DEBOUNCED_FILTER_KEYS = ['query', 'minAmount', 'maxAmount'];
const SEARCH_DEBOUNCE_MS = 250;

export function useLedgerData({ month, ledgerFilters, statsRange }) {
  const [data, setData] = useState(emptyData);
  const [members, setMembers] = useState([]);
  const [searchResult, setSearchResult] = useState(null);
  const [yearlySummary, setYearlySummary] = useState(null);
  const [yearlyBudgetSummary, setYearlyBudgetSummary] = useState(null);
  const [rangeSummary, setRangeSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  // Guards against out-of-order responses: only the most recently issued search request
  // is allowed to update state, so a slow/stale request can never clobber a newer one.
  const searchRequestIdRef = useRef(0);
  const previousFiltersRef = useRef(ledgerFilters);

  function nextSearchRequestId() {
    searchRequestIdRef.current += 1;
    return searchRequestIdRef.current;
  }

  async function runSearch(requestId) {
    try {
      const result = await loadSearchTransactions();
      if (searchRequestIdRef.current === requestId) setSearchResult(result);
    } catch (error) {
      console.error(error);
      if (searchRequestIdRef.current === requestId) setSearchResult(emptySearchResult);
    }
  }

  async function reload() {
    setLoading(true);
    const requestId = nextSearchRequestId();
    const [{ bootstrap, assetSummary, members: loadedMembers }] = await Promise.all([
      ledgerApi.getDashboard(month),
      runSearch(requestId)
    ]);
    setMembers(loadedMembers);
    setData({ ...bootstrap, assetSummary });
    setLoading(false);
  }

  async function reloadMembers() {
    setMembers(await managementApi.getMembers());
  }

  useEffect(() => {
    reload().catch((error) => {
      console.error(error);
      setLoading(false);
    });
  }, [month]);

  useEffect(() => {
    const year = toNumber(month.slice(0, 4));
    Promise.all([
      ledgerApi.getYearlySummary(year).then(setYearlySummary),
      managementApi.getYearlyBudgetSummary(year).then(setYearlyBudgetSummary)
    ]).catch((error) => console.error(error));
  }, [month]);

  useEffect(() => {
    if (!statsRange.startDate || !statsRange.endDate || statsRange.endDate < statsRange.startDate) {
      setRangeSummary(null);
      return;
    }
    ledgerApi.getRangeSummary(statsRange.startDate, statsRange.endDate)
      .then(setRangeSummary)
      .catch((error) => console.error(error));
  }, [statsRange.startDate, statsRange.endDate]);

  useEffect(() => {
    const previousFilters = previousFiltersRef.current;
    previousFiltersRef.current = ledgerFilters;
    const changedKeys = Object.keys(ledgerFilters).filter((key) => ledgerFilters[key] !== previousFilters[key]);
    const isTextEdit = changedKeys.length > 0 && changedKeys.every((key) => DEBOUNCED_FILTER_KEYS.includes(key));

    const requestId = nextSearchRequestId();
    // Clear stale results immediately so the UI never shows the previous filter's
    // (possibly server-paginated, now mismatched) items mislabeled under the new filter state.
    setSearchResult(null);
    const timer = window.setTimeout(() => runSearch(requestId), isTextEdit ? SEARCH_DEBOUNCE_MS : 0);
    return () => window.clearTimeout(timer);
  }, [ledgerFilters]);

  async function loadSearchTransactions() {
    if (!hasLedgerFilters(ledgerFilters)) return null;
    if (ledgerFilters.minAmount && ledgerFilters.maxAmount
      && toNumber(ledgerFilters.maxAmount) < toNumber(ledgerFilters.minAmount)) return emptySearchResult;

    const params = new URLSearchParams();
    params.set('startDate', `${month}-01`);
    params.set(
      'endDate',
      formatDate(new Date(toNumber(month.slice(0, 4)), toNumber(month.slice(5, 7)), 0))
    );
    const query = trimToEmpty(ledgerFilters.query);
    if (query) params.set('query', query);
    if (ledgerFilters.type !== 'ALL') params.set('type', ledgerFilters.type);
    ['categoryId', 'consumptionScope', 'consumerMemberId', 'assetId', 'minAmount', 'maxAmount'].forEach((key) => {
      if (!isAllFilterValue(ledgerFilters[key])) {
        params.set(key, key === 'minAmount' || key === 'maxAmount' ? String(toNumber(ledgerFilters[key])) : ledgerFilters[key]);
      }
    });
    params.set('page', String(ledgerFilters.page || 0));
    params.set('size', String(ledgerFilters.size || 50));
    params.set('sort', ledgerFilters.sort || 'DATE_DESC');
    const result = await ledgerApi.searchTransactions(params);
    if (!result.ok) return emptySearchResult;
    if (Array.isArray(result.data)) {
      return {
        ...emptySearchResult,
        items: result.data,
        totalElements: result.data.length,
        totalPages: result.data.length > 0 ? 1 : 0
      };
    }
    return result.data;
  }

  return {
    data,
    loading,
    members,
    rangeSummary,
    reload,
    reloadMembers,
    searchResult,
    searchTransactions: searchResult?.items ?? null,
    yearlyBudgetSummary,
    yearlySummary
  };
}
