package com.payresolve.investigator;

/**
 * Thread-local context tracking the current investigation ID for auditable tool logging.
 */
public final class InvestigationContext {

    private static final ThreadLocal<String> CURRENT_INVESTIGATION_ID = new ThreadLocal<>();

    private InvestigationContext() {}

    public static void setCurrentInvestigationId(String investigationId) {
        CURRENT_INVESTIGATION_ID.set(investigationId);
    }

    public static String getCurrentInvestigationId() {
        return CURRENT_INVESTIGATION_ID.get();
    }

    public static void clear() {
        CURRENT_INVESTIGATION_ID.remove();
    }
}

