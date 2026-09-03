/**
 * Bounded, cycle-safe throwable normalization.
 *
 * <p>Values in this package are immutable scalar copies but remain pre-redaction
 * because they contain {@code CandidateText}. They are short-lived pipeline inputs:
 * classify and redact them promptly, never render or persist them directly, and
 * release them after completed diagnostic/debug-record construction. Returned
 * graphs retain no throwable, stack-frame, class, class-loader, path, logger,
 * platform, mutable collection, array, or callback object.
 */
package org.minecraftprot.stackframe.normalization;
