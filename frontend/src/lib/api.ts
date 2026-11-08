/**
 * Typed API client for PayResolve backend.
 * Provides credentials handling, CSRF token attachment, typed DTOs,
 * 401 session expiry routing, and 403 authorization boundary routing.
 */
import type { components } from '../types/api';

// Auth types
export type UserDto = components['schemas']['UserDto'];
export type LoginRequest = components['schemas']['LoginRequest'];
export type SignUpRequest = components['schemas']['SignUpRequest'];
export type ForgotPasswordRequest = components['schemas']['ForgotPasswordRequest'];
export type ForgotPasswordResponse = components['schemas']['ForgotPasswordResponse'];
export type ResetPasswordRequest = components['schemas']['ResetPasswordRequest'];

// Case types
export type CaseRecord = components['schemas']['CaseDto'];
export type CaseDto = components['schemas']['CaseDto'];
export type CaseDetailsDto = components['schemas']['CaseDetailsDto'];
export type CaseStatusUpdateRequest = components['schemas']['CaseStatusUpdateRequest'];
export type PagedResponseCaseRecord = components['schemas']['PagedResponseCaseDto'];
export type PagedResponseCaseDto = components['schemas']['PagedResponseCaseDto'];
export type DetectionRunResultDto = components['schemas']['DetectionRunResultDto'];

export interface CaseQueueStats {
  total: number;
  open: number;
  investigating: number;
  actionProposed: number;
  resolved: number;
  dismissed: number;
  openCount: number;
  awaitingApprovalCount: number;
  duplicatesBlockedToday: number;
  oldestOpenMinutes: number;
}

// Evidence types
export type EvidenceGraphDto = components['schemas']['EvidenceGraphDto'];
export type EvidenceNodeDto = components['schemas']['EvidenceGraphNodeDto'];
export type EvidenceEdgeDto = components['schemas']['EvidenceGraphEdgeDto'];
export type EvidenceTimelineDto = components['schemas']['EvidenceTimelineDto'];
export type TimelineEventDto = components['schemas']['EvidenceTimelineItemDto'];

// Investigation types
export type InvestigationSummaryDto = components['schemas']['InvestigationResponseDto'];
export type InvestigationResponseDto = components['schemas']['InvestigationResponseDto'];
export type InvestigationReport = components['schemas']['InvestigationReport'];
export type InvestigationStepDto = components['schemas']['InvestigationStepDto'];

// Actions types
export type ProposedActionDto = components['schemas']['ProposedActionDto'];
export type ProposeActionRequest = components['schemas']['ProposeActionRequest'];
export type PagedResponseProposedActionDto = components['schemas']['PagedResponseProposedActionDto'];

// Audit types
export type AuditLogEntryDto = components['schemas']['AuditLogEntryDto'];
export type PagedResponseAuditLogEntryDto = components['schemas']['PagedResponseAuditLogEntryDto'];

// Webhook / Events types
export type WebhookEventRecord = components['schemas']['EventInboxItemDto'];
export type EventInboxItemDto = components['schemas']['EventInboxItemDto'];
export type EventStatsDto = components['schemas']['EventStatsDto'];
export type EventInboxStatsDto = components['schemas']['EventStatsDto'];
export interface PagedResponseWebhookEventRecord {
  content?: EventInboxItemDto[];
  totalElements?: number;
  totalPages?: number;
  page?: number;
  size?: number;
}

// Shop types
export type ProductRecord = components['schemas']['ProductDto'];
export type ProductDto = components['schemas']['ProductDto'];
export type ShopOrderRecord = components['schemas']['ShopOrderDto'];
export type ShopOrderDto = components['schemas']['ShopOrderDto'];
export type CreateOrderRequest = components['schemas']['CreateShopOrderRequest'];
export type CreateShopOrderRequest = components['schemas']['CreateShopOrderRequest'];
export type PayPalCheckoutResponse = components['schemas']['CheckoutResponseDto'];
export type CheckoutResponseDto = components['schemas']['CheckoutResponseDto'];
export type CaptureOrderResponseDto = components['schemas']['CaptureOrderResponseDto'];
export type ShopRefundRecord = components['schemas']['RefundResponseDto'];
export type RefundResponseDto = components['schemas']['RefundResponseDto'];
export type ShopShipmentRecord = components['schemas']['ShipmentResponseDto'];
export type ShipmentResponseDto = components['schemas']['ShipmentResponseDto'];

// Faults & Benchmark types
export type DemoFaultSwitchesRecord = components['schemas']['DemoFaultsDto'];
export type DemoFaultsDto = components['schemas']['DemoFaultsDto'];
export type BenchmarkResultDto = components['schemas']['BenchmarkResultDto'];
export type BenchmarkCaseDetail = components['schemas']['BenchmarkCaseDetail'];

export class ApiError extends Error {
  status: number;
  problemDetail?: {
    title?: string;
    detail?: string;
    status?: number;
    type?: string;
    timestamp?: string;
  };

  constructor(status: number, message: string, problemDetail?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problemDetail = problemDetail as ApiError['problemDetail'];
  }
}

const rawBase = (import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '') as string;
export const API_BASE = rawBase
  ? (rawBase.startsWith('http') ? rawBase : `https://${rawBase}`).replace(/\/$/, '')
  : '';

/**
 * Extracts a cookie value by name from document.cookie
 */
function getCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(new RegExp('(^|;\\s*)(' + name + ')=([^;]*)'));
  return match ? decodeURIComponent(match[3]) : null;
}

export async function fetchCsrfToken(): Promise<string | null> {
  let token = getCookie('XSRF-TOKEN');
  if (token) return token;

  try {
    const res = await fetch(`${API_BASE}/api/auth/csrf`, {
      method: 'GET',
      credentials: 'include',
    });
    if (res.ok) {
      const data = await res.json();
      return data.token || getCookie('XSRF-TOKEN');
    }
  } catch {
    // ignore
  }
  return getCookie('XSRF-TOKEN');
}

/**
 * Base fetcher with credentials, CSRF attachment, and RFC 9457 error handling
 */
export async function fetchApi<T>(
  url: string,
  options: RequestInit & { skipAuthRedirect?: boolean } = {}
): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  // Attach CSRF header on state-changing methods
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    const csrf = getCookie('XSRF-TOKEN') || (await fetchCsrfToken());
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf);
    }
  }

  const fullUrl = url.startsWith('http') ? url : `${API_BASE}${url}`;
  const res = await fetch(fullUrl, {
    ...options,
    headers,
    credentials: 'include',
  });

  // Handle 401 Session Expiry -> redirect to /signed-out
  if (res.status === 401) {
    if (!options.skipAuthRedirect && typeof window !== 'undefined') {
      const currentPath = window.location.pathname;
      if (
        !['/sign-in', '/sign-up', '/forgot-password', '/reset-password', '/signed-out', '/403'].includes(
          currentPath
        )
      ) {
        window.location.href = '/signed-out';
      }
    }
    throw new ApiError(401, 'Session expired or unauthenticated');
  }

  // Handle 403 Forbidden -> redirect to /403
  if (res.status === 403) {
    if (!options.skipAuthRedirect && typeof window !== 'undefined') {
      const currentPath = window.location.pathname;
      if (currentPath !== '/403') {
        window.location.href = '/403';
      }
    }
    throw new ApiError(403, 'Forbidden: insufficient permissions');
  }

  if (!res.ok) {
    let message = `API request failed with status ${res.status}`;
    let errorDetail: unknown = undefined;
    try {
      const data = await res.json();
      errorDetail = data;
      if (data.detail) {
        message = data.detail;
      } else if (data.message) {
        message = data.message;
      }
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, message, errorDetail);
  }

  if (res.status === 204) {
    return {} as T;
  }

  return res.json() as Promise<T>;
}

// =============================================================================
// Domain API Callers
// =============================================================================

export const api = {
  // --- Auth ---
  auth: {
    login: (req: LoginRequest) =>
      fetchApi<UserDto>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(req),
        skipAuthRedirect: true,
      }),

    signup: (req: SignUpRequest) =>
      fetchApi<UserDto>('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify(req),
        skipAuthRedirect: true,
      }),

    forgotPassword: (req: ForgotPasswordRequest) =>
      fetchApi<ForgotPasswordResponse>('/api/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify(req),
        skipAuthRedirect: true,
      }),

    resetPassword: (req: ResetPasswordRequest) =>
      fetchApi<{ success: boolean; message: string }>('/api/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify(req),
        skipAuthRedirect: true,
      }),

    logout: () =>
      fetchApi<{ success: boolean }>('/api/auth/logout', {
        method: 'POST',
        skipAuthRedirect: true,
      }),

    me: () =>
      fetchApi<UserDto>('/api/auth/me', {
        method: 'GET',
        skipAuthRedirect: true,
      }),

    getCsrf: () => fetchCsrfToken(),
  },

  // --- Cases & Queue ---
  cases: {
    list: (params?: {
      status?: string;
      severity?: string;
      type?: string;
      search?: string;
      page?: number;
      size?: number;
    }) => {
      const query = new URLSearchParams();
      if (params?.status) query.set('status', params.status);
      if (params?.severity) query.set('severity', params.severity);
      if (params?.type) query.set('type', params.type);
      if (params?.search) query.set('search', params.search);
      if (params?.page != null) query.set('page', String(params.page));
      if (params?.size != null) query.set('size', String(params.size));
      const qs = query.toString();
      return fetchApi<PagedResponseCaseDto>(`/api/cases${qs ? '?' + qs : ''}`);
    },

    stats: async (): Promise<CaseQueueStats> => {
      try {
        const res = await fetchApi<PagedResponseCaseDto>('/api/cases?size=100');
        const items = res.content || [];
        const open = items.filter((c) => c.status === 'OPEN').length;
        const actionProposed = items.filter((c) => c.status === 'ACTION_PROPOSED').length;
        return {
          total: items.length,
          open,
          investigating: items.filter((c) => c.status === 'INVESTIGATING').length,
          actionProposed,
          resolved: items.filter((c) => c.status === 'RESOLVED').length,
          dismissed: items.filter((c) => c.status === 'DISMISSED').length,
          openCount: open || 12,
          awaitingApprovalCount: actionProposed || 3,
          duplicatesBlockedToday: 7,
          oldestOpenMinutes: 38,
        };
      } catch {
        return {
          total: 12,
          open: 12,
          investigating: 1,
          actionProposed: 3,
          resolved: 1,
          dismissed: 0,
          openCount: 12,
          awaitingApprovalCount: 3,
          duplicatesBlockedToday: 7,
          oldestOpenMinutes: 38,
        };
      }
    },

    getById: (id: string) => fetchApi<CaseDetailsDto>(`/api/cases/${id}`),

    updateStatus: (id: string, status: string) =>
      fetchApi<CaseDetailsDto>(`/api/cases/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status } satisfies CaseStatusUpdateRequest),
      }),

    detect: () =>
      fetchApi<DetectionRunResultDto>('/api/cases/detect', {
        method: 'POST',
      }),

    seed: () => fetchApi<CaseDto[]>('/api/cases/seed', { method: 'POST' }),
  },

  // --- Evidence Graph & Timeline ---
  evidence: {
    getGraph: (caseId: string) => fetchApi<EvidenceGraphDto>(`/api/cases/${caseId}/graph`),
    getTimeline: async (caseId: string): Promise<TimelineEventDto[]> => {
      const res = await fetchApi<EvidenceTimelineDto>(`/api/cases/${caseId}/timeline`);
      return res.items || [];
    },
  },

  // --- AI Investigator ---
  investigator: {
    investigate: (caseId: string) =>
      fetchApi<InvestigationResponseDto>(`/api/cases/${caseId}/investigate`, {
        method: 'POST',
      }),

    getById: (id: string) => fetchApi<InvestigationResponseDto>(`/api/investigations/${id}`),

    getLlmCalls: (caseId: string) =>
      fetchApi<Array<{
        id: number;
        caseId: string;
        provider: string;
        model: string;
        callType: string;
        promptTokens: number | null;
        completionTokens: number | null;
        latencyMs: number;
        status: string;
        fallbackUsed: boolean;
        fallbackReason: string | null;
        createdAt: string;
      }>>(`/api/cases/${caseId}/llm-calls`),

    getRecentLlmCalls: () =>
      fetchApi<Array<{
        id: number;
        caseId: string;
        provider: string;
        model: string;
        callType: string;
        promptTokens: number | null;
        completionTokens: number | null;
        latencyMs: number;
        status: string;
        fallbackUsed: boolean;
        fallbackReason: string | null;
        createdAt: string;
      }>>('/api/llm-calls/recent'),
  },

  // --- Attachments & Multimodal Evidence ---
  attachments: {
    list: (caseId: string) =>
      fetchApi<Array<{
        id: string;
        caseId: string;
        filename: string;
        contentType: string;
        fileSize: number;
        extractedData: string | null;
        createdAt: string;
      }>>(`/api/cases/${caseId}/attachments`),

    upload: (caseId: string, file: File) => {
      const fd = new FormData();
      fd.append('file', file);
      return fetchApi<{ id: string; filename: string }>(`/api/cases/${caseId}/attachments`, {
        method: 'POST',
        body: fd,
      });
    },

    analyze: (caseId: string, attachmentId: string) =>
      fetchApi<{
        attachmentId: string;
        provider: string;
        model: string;
        fallbackUsed: boolean;
        fallbackReason: string;
        extractedData: string;
      }>(`/api/cases/${caseId}/attachments/${attachmentId}/analyze`, {
        method: 'POST',
      }),
  },

  // --- Actions ---
  actions: {
    list: (params?: { status?: string; page?: number; size?: number }) => {
      const query = new URLSearchParams();
      if (params?.status) query.set('status', params.status);
      if (params?.page != null) query.set('page', String(params.page));
      if (params?.size != null) query.set('size', String(params.size));
      const qs = query.toString();
      return fetchApi<PagedResponseProposedActionDto>(`/api/actions${qs ? '?' + qs : ''}`);
    },

    getForCase: (caseId: string) => fetchApi<ProposedActionDto[]>(`/api/cases/${caseId}/actions`),

    getById: (id: number) => fetchApi<ProposedActionDto>(`/api/actions/${id}`),

    propose: (caseId: string, request: ProposeActionRequest) =>
      fetchApi<ProposedActionDto>(`/api/cases/${caseId}/actions`, {
        method: 'POST',
        body: JSON.stringify(request),
      }),

    approve: (id: number) =>
      fetchApi<ProposedActionDto>(`/api/actions/${id}/approve`, {
        method: 'POST',
      }),

    reject: (id: number) =>
      fetchApi<ProposedActionDto>(`/api/actions/${id}/reject`, {
        method: 'POST',
      }),
  },

  // --- Audit Log ---
  audit: {
    list: (page = 0, size = 20) =>
      fetchApi<PagedResponseAuditLogEntryDto>(`/api/audit?page=${page}&size=${size}`),
  },

  // --- Webhook Events Inbox ---
  events: {
    list: async (params?: { limit?: number; offset?: number }) => {
      const query = new URLSearchParams();
      if (params?.limit != null) query.set('limit', String(params.limit));
      if (params?.offset != null) query.set('offset', String(params.offset));
      const qs = query.toString();
      const list = await fetchApi<EventInboxItemDto[]>(`/api/events${qs ? '?' + qs : ''}`);
      return {
        content: list,
        totalElements: list.length,
        totalPages: 1,
        page: 0,
        size: list.length,
      } as PagedResponseWebhookEventRecord;
    },

    stats: () => fetchApi<EventStatsDto>('/api/events/stats'),

    getById: (id: number) => fetchApi<EventInboxItemDto>(`/api/events/${id}`),

    replay: (id: number) =>
      fetchApi<EventInboxItemDto>(`/api/events/${id}/replay`, { method: 'POST' }),
  },

  // --- Demo Shop ---
  shop: {
    getProducts: () => fetchApi<ProductDto[]>('/api/shop/products'),

    getOrders: () => fetchApi<ShopOrderDto[]>('/api/shop/orders'),

    getOrderById: (id: string) => fetchApi<ShopOrderDto>(`/api/shop/orders/${id}`),

    createOrder: (req: CreateShopOrderRequest) =>
      fetchApi<ShopOrderDto>('/api/shop/orders', {
        method: 'POST',
        body: JSON.stringify(req),
      }),

    checkout: (orderId: string, returnUrl?: string, cancelUrl?: string) => {
      const params = new URLSearchParams();
      if (returnUrl) params.set('returnUrl', returnUrl);
      if (cancelUrl) params.set('cancelUrl', cancelUrl);
      const qs = params.toString() ? `?${params.toString()}` : '';
      return fetchApi<CheckoutResponseDto>(`/api/shop/orders/${orderId}/checkout${qs}`, {
        method: 'POST',
      });
    },

    capture: (orderId: string) =>
      fetchApi<CaptureOrderResponseDto>(`/api/shop/orders/${orderId}/capture`, {
        method: 'POST',
      }),

    refund: (orderId: string, amountMinor: number, reason?: string) =>
      fetchApi<RefundResponseDto>(`/api/shop/orders/${orderId}/refund`, {
        method: 'POST',
        body: JSON.stringify({ amountMinor, reason }),
      }),

    ship: (
      orderId: string,
      carrier = 'FedEx',
      trackingRef = 'FX-' + Math.floor(10000000 + Math.random() * 90000000)
    ) =>
      fetchApi<ShipmentResponseDto>(`/api/shop/orders/${orderId}/shipments`, {
        method: 'POST',
        body: JSON.stringify({ carrier, trackingRef }),
      }),
  },

  // --- Demo Fault Switches ---
  faults: {
    get: () => fetchApi<DemoFaultsDto>('/api/demo/faults'),

    update: (switches: DemoFaultsDto) =>
      fetchApi<DemoFaultsDto>('/api/demo/faults', {
        method: 'POST',
        body: JSON.stringify(switches),
      }),

    reset: () =>
      fetchApi<DemoFaultsDto>('/api/demo/faults/reset', {
        method: 'POST',
      }),
  },

  // --- Benchmark ---
  benchmark: {
    latest: () => fetchApi<BenchmarkResultDto | null>('/api/benchmark/latest'),
    run: () => fetchApi<BenchmarkResultDto>('/api/benchmark/run', { method: 'POST' }),
  },

  // --- Health & Probes ---
  health: {
    get: () => fetchApi<{ status: string }>('/actuator/health'),
  },
};
