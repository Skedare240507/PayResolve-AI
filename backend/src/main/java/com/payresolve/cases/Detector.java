package com.payresolve.cases;

import java.util.List;

/**
 * Pure functional contract for detecting payment discrepancies over an immutable {@link DetectionSnapshot}.
 * Implementations must be side-effect free, deterministic, and perform no database or network I/O.
 */
public interface Detector {

    /**
     * The specific discrepancy type this detector is responsible for identifying.
     */
    CaseType supportedType();

    /**
     * Evaluates the given snapshot and returns zero or more detected findings.
     *
     * @param snapshot immutable snapshot of PayPal state, merchant shop state, and webhook events
     * @return list of findings, or an empty list if no discrepancy exists
     */
    List<DetectionFinding> detect(DetectionSnapshot snapshot);
}
