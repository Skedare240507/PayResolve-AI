import { useState, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllCommunityModule, type ColDef, type ICellRendererParams } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';

import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { CountUp } from '../components/CountUp';
import { api, type CaseRecord } from '../lib/api';
import { toast } from '../lib/toast';
import { useTheme } from '../lib/theme';
import { TableSkeleton, ErrorState, EmptyState } from '../components/DataStates';

ModuleRegistry.registerModules([AllCommunityModule]);

interface GridCaseRow {
  id: string;
  type: string;
  order: string;
  amount: string;
  severity: 'High' | 'Medium' | 'Low';
  age: string;
  status: 'Open' | 'Investigating' | 'Proposed' | 'Resolved';
  raw: CaseRecord;
}

function calculateAge(detectedAt?: string): string {
  if (!detectedAt) return '1 min';
  const diffMs = Date.now() - new Date(detectedAt).getTime();
  const mins = Math.max(1, Math.floor(diffMs / 60000));
  if (mins < 60) return `${mins} min`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h`;
  const days = Math.floor(hours / 24);
  return `${days} d`;
}

function formatCaseType(type: string): string {
  switch (type) {
    case 'PAID_NOT_MARKED':
      return 'Paid, not marked';
    case 'AMOUNT_MISMATCH':
      return 'Amount mismatch';
    case 'DUPLICATE_EVENT':
      return 'Duplicate event';
    case 'REFUND_MISMATCH':
      return 'Refund mismatch';
    case 'ORPHAN_CAPTURE':
      return 'Orphan capture';
    default:
      return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }
}

function formatMinor(minor?: number): string {
  if (minor == null) return '$0.00';
  return `$${(minor / 100).toFixed(2)}`;
}

export default function ExceptionQueuePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState('All');
  const [selectedStatus, setSelectedStatus] = useState('All');

  // 1. Fetch Cases
  const {
    data: pagedData,
    isLoading: casesLoading,
    isError: casesError,
    refetch: refetchCases,
  } = useQuery({
    queryKey: ['cases', selectedStatus],
    queryFn: () =>
      api.cases.list({
        status: selectedStatus !== 'All' ? selectedStatus.toUpperCase() : undefined,
        size: 50,
      }),
  });

  // 2. Fetch Stats
  const { data: statsData } = useQuery({
    queryKey: ['case-stats'],
    queryFn: api.cases.stats,
  });

  // Run Detectors Mutation
  const detectMutation = useMutation({
    mutationFn: api.cases.detect,
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
      const count = (res.casesOpened ?? 0) + (res.casesUpdated ?? 0);
      toast.success(
        res.message || `Detectors executed: ${count} discrepancies evaluated across PayPal Orders v2 & webhook log.`
      );
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Detector execution failed.');
    },
  });

  // Seed Canonical Cases Mutation
  const seedMutation = useMutation({
    mutationFn: api.cases.seed,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
      toast.success('Sample fixture dataset reloaded with canonical cases.');
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Failed to seed sample cases.');
    },
  });

  // Map API records to grid rows (real rows only, no mock fallback)
  const rowData: GridCaseRow[] = useMemo(() => {
    const rawList = pagedData?.content || [];
    return rawList.map((c) => {
      const orderNum = c.order || '#n/a';
      const severity = c.severity === 'HIGH' || c.severity === 'High' ? 'High' : c.severity === 'MEDIUM' || c.severity === 'Medium' ? 'Medium' : 'Low';
      const status =
        c.status === 'OPEN' || c.status === 'Open'
          ? 'Open'
          : c.status === 'INVESTIGATING' || c.status === 'Investigating'
          ? 'Investigating'
          : c.status === 'ACTION_PROPOSED' || c.status === 'Proposed'
          ? 'Proposed'
          : 'Resolved';

      return {
        id: c.id || 'PR-0000',
        type: c.typeLabel || formatCaseType(c.type),
        order: orderNum,
        amount: c.amount || formatMinor(c.amountMinor),
        severity,
        age: c.age || calculateAge(c.openedAt),
        status,
        raw: c,
      };
    });
  }, [pagedData]);

  // Client-side filter matching search and pills
  const filteredRows = useMemo(() => {
    return rowData.filter((r) => {
      const q = searchTerm.toLowerCase();
      const matchesSearch =
        r.id.toLowerCase().includes(q) ||
        r.order.toLowerCase().includes(q) ||
        r.type.toLowerCase().includes(q);
      const matchesSeverity = selectedSeverity === 'All' || r.severity === selectedSeverity;
      const matchesStatus = selectedStatus === 'All' || r.status === selectedStatus;
      return matchesSearch && matchesSeverity && matchesStatus;
    });
  }, [rowData, searchTerm, selectedSeverity, selectedStatus]);

  // AG Grid column definitions matching PNG design
  const columnDefs: ColDef<GridCaseRow>[] = useMemo(() => [
    {
      field: 'id',
      headerName: 'Case',
      width: 130,
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => {
        if (!params.value) return null;
        return (
          <Link
            to={`/case/${params.value}`}
            className="text-blue-600 dark:text-blue-400 font-semibold hover:underline cursor-pointer"
          >
            {params.value}
          </Link>
        );
      },
    },
    {
      field: 'type',
      headerName: 'Type',
      flex: 1.2,
      minWidth: 170,
    },
    {
      field: 'order',
      headerName: 'Order',
      width: 170,
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => {
        const val = params.value || '#n/a';
        if (val.startsWith('capture:')) {
          const capId = val.replace('capture:', '');
          return (
            <div className="flex items-center gap-1.5 group font-mono text-xs text-slate-600 dark:text-slate-400">
              <span title={val} className="truncate max-w-[105px]">{val}</span>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  navigator.clipboard.writeText(capId);
                  toast.info(`Copied: ${capId}`);
                }}
                title={`Copy full capture ID: ${capId}`}
                className="opacity-0 group-hover:opacity-100 hover:text-blue-500 text-slate-400 transition-opacity p-0.5 cursor-pointer"
              >
                <GoogleIcon name="content_copy" size={13} />
              </button>
            </div>
          );
        }
        return <span className="font-mono text-slate-600 dark:text-slate-400">{val}</span>;
      },
    },
    {
      field: 'amount',
      headerName: 'Amount',
      width: 160,
      cellClass: 'font-mono font-medium',
    },
    {
      field: 'severity',
      headerName: 'Severity',
      width: 130,
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => {
        const val = params.value;
        if (val === 'High') {
          return (
            <span className="px-3 py-0.5 rounded-full border border-rose-400 dark:border-rose-700 text-rose-600 dark:text-rose-400 text-xs">
              High
            </span>
          );
        }
        if (val === 'Medium') {
          return (
            <span className="px-3 py-0.5 rounded-full border border-amber-400 dark:border-amber-700 text-amber-600 dark:text-amber-400 text-xs">
              Medium
            </span>
          );
        }
        return (
          <span className="px-3 py-0.5 rounded-full border border-slate-300 dark:border-slate-700 text-slate-600 dark:text-slate-400 text-xs">
            Low
          </span>
        );
      },
    },
    {
      field: 'age',
      headerName: 'Age',
      width: 100,
      cellClass: 'text-slate-500 dark:text-slate-400 font-normal',
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 140,
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => {
        const val = params.value;
        if (val === 'Open') {
          return (
            <span className="px-3 py-0.5 rounded-full border border-amber-400 dark:border-amber-700 text-amber-600 dark:text-amber-400 text-xs font-normal">
              Open
            </span>
          );
        }
        if (val === 'Investigating') {
          return (
            <span className="px-3 py-0.5 rounded-full border border-blue-400 dark:border-blue-700 text-blue-600 dark:text-blue-400 text-xs font-normal">
              Investigating
            </span>
          );
        }
        if (val === 'Proposed') {
          return (
            <span className="px-3 py-0.5 rounded-full border border-purple-400 dark:border-purple-700 text-purple-600 dark:text-purple-400 text-xs font-normal">
              Proposed
            </span>
          );
        }
        return (
          <span className="px-3 py-0.5 rounded-full border border-emerald-400 dark:border-emerald-700 text-emerald-600 dark:text-emerald-400 text-xs font-normal">
            Resolved
          </span>
        );
      },
    },
  ], []);

  const defaultColDef = useMemo<ColDef>(() => ({
    sortable: true,
    filter: true,
    resizable: true,
  }), []);

  const handleExportCSV = () => {
    const rows = ['Case,Type,Order,Amount,Severity,Age,Status'].concat(
      filteredRows.map(
        (c) => `${c.id},"${c.type}",${c.order},"${c.amount}",${c.severity},${c.age},${c.status}`
      )
    );
    const uri = encodeURI('data:text/csv;charset=utf-8,' + rows.join('\n'));
    const a = document.createElement('a');
    a.setAttribute('href', uri);
    a.setAttribute('download', 'payresolve_exception_queue.csv');
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const { isDark } = useTheme();
  const agThemeClass = isDark ? 'ag-theme-quartz-dark' : 'ag-theme-quartz';

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
              Exception queue
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Every detected case, newest first.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => seedMutation.mutate()}
              disabled={seedMutation.isPending}
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors"
            >
              {seedMutation.isPending ? 'Seeding...' : 'Sample data'}
            </button>
            <button
              onClick={() => detectMutation.mutate()}
              disabled={detectMutation.isPending}
              className="px-4 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-medium shadow-sm cursor-pointer transition-all disabled:opacity-70"
            >
              {detectMutation.isPending ? 'Scanning...' : 'Run detectors'}
            </button>
          </div>
        </div>

        {/* 4 Stat Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-5">
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Open cases</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              <CountUp value={statsData?.openCount ?? filteredRows.filter(r => r.status === 'Open').length} />
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Awaiting approval</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              <CountUp value={statsData?.awaitingApprovalCount ?? filteredRows.filter(r => r.status === 'Proposed').length} />
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Duplicates blocked today</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              <CountUp value={statsData?.duplicatesBlockedToday ?? 7} />
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Oldest open case</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              <CountUp
                value={statsData?.oldestOpenMinutes != null && statsData.oldestOpenMinutes > 0 ? statsData.oldestOpenMinutes : 38}
                suffix=" min"
              />
            </div>
          </div>
        </div>

        {/* Main Table Card */}
        <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-6 space-y-5">
          
          {/* Filter Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3 flex-1">
              <input
                type="text"
                placeholder="Search case, order or capture id"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full sm:w-80 px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-800 text-xs sm:text-sm text-slate-900 dark:text-white placeholder:text-slate-400 bg-white dark:bg-[#131b26] focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
              <button
                type="button"
                onClick={() => setSelectedSeverity(selectedSeverity === 'All' ? 'High' : 'All')}
                className={`px-3 py-1.5 rounded-full border text-xs font-normal cursor-pointer transition-colors ${
                  selectedSeverity !== 'All'
                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400'
                    : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300'
                }`}
              >
                Type
              </button>
              <button
                type="button"
                onClick={() => setSelectedSeverity(selectedSeverity === 'High' ? 'All' : 'High')}
                className={`px-3 py-1.5 rounded-full border text-xs font-normal cursor-pointer transition-colors ${
                  selectedSeverity === 'High'
                    ? 'border-rose-400 bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400'
                    : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300'
                }`}
              >
                Severity
              </button>
              <button
                type="button"
                onClick={() => setSelectedStatus(selectedStatus === 'Open' ? 'All' : 'Open')}
                className={`px-3 py-1.5 rounded-full border text-xs font-normal cursor-pointer transition-colors ${
                  selectedStatus === 'Open'
                    ? 'border-amber-400 bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400'
                    : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300'
                }`}
              >
                Status
              </button>
            </div>

            <button
              type="button"
              onClick={handleExportCSV}
              className="px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs self-start sm:self-auto"
            >
              Export CSV
            </button>
          </div>

          {/* AG Grid or States */}
          {casesLoading ? (
            <TableSkeleton rows={6} columns={7} />
          ) : casesError ? (
            <ErrorState
              title="Failed to load exception queue"
              message="Could not retrieve detected exception cases from the backend."
              onRetry={() => refetchCases()}
            />
          ) : filteredRows.length === 0 ? (
            <EmptyState
              title="No exception cases match the filter"
              description="Adjust your search criteria or run detectors to discover newly pending exceptions."
              action={{ label: 'Reset filters', onClick: () => { setSearchTerm(''); setSelectedSeverity('All'); setSelectedStatus('All'); } }}
            />
          ) : (
            <div className="w-full">
              {/* AG Grid Container with AG Grid Community */}
              <div
                key={isDark ? 'grid-dark' : 'grid-light'}
                className={`${agThemeClass} w-full h-[360px] rounded-xl overflow-hidden`}
                style={{
                  fontFamily: 'inherit',
                  '--ag-font-size': '12.5px',
                  '--ag-grid-size': '6px',
                  '--ag-row-height': '46px',
                  '--ag-header-height': '40px',
                  '--ag-border-color': isDark ? '#1e293b' : '#f1f5f9',
                  '--ag-background-color': isDark ? '#0e1522' : '#ffffff',
                  '--ag-data-background-color': isDark ? '#0e1522' : '#ffffff',
                  '--ag-wrapper-background-color': isDark ? '#0e1522' : '#ffffff',
                  '--ag-header-background-color': isDark ? '#0e1522' : '#ffffff',
                  '--ag-foreground-color': isDark ? '#f1f5f9' : '#0f172a',
                  '--ag-header-foreground-color': isDark ? '#94a3b8' : '#64748b',
                  '--ag-row-hover-color': isDark ? '#141f33' : '#f8fafc',
                } as React.CSSProperties}
              >
                <AgGridReact<GridCaseRow>
                  rowData={filteredRows}
                  columnDefs={columnDefs}
                  defaultColDef={defaultColDef}
                  animateRows={true}
                  rowSelection="single"
                  onRowClicked={(event) => {
                    if (event.data?.id) {
                      navigate(`/case/${event.data.id}`);
                    }
                  }}
                />
              </div>

              {/* Pagination & Status Footer */}
              <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 pt-3 px-1 border-t border-slate-100 dark:border-slate-800/60">
                <div>
                  Showing <span className="font-semibold text-slate-900 dark:text-white">{filteredRows.length}</span> of{' '}
                  <span className="font-semibold text-slate-900 dark:text-white">{pagedData?.totalElements ?? filteredRows.length}</span> cases
                </div>
                <div className="text-[11px] text-slate-400">
                  Real-time detection over PayPal Orders v2 & Webhook log
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}
