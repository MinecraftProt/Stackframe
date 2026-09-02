/**
 * Immutable loader-independent diagnostic schema 1.0.
 *
 * <p>All coordinates are one-based Unicode code-point positions with
 * end-exclusive ranges. Repeated fields retain deterministic producer order and
 * use {@link org.minecraftprot.stackframe.diagnostic.BoundedList} for explicit
 * omission accounting. Completed documents contain only trusted catalog text and
 * post-policy display text; pre-redaction candidate text is intentionally absent
 * from the completed graph.
 */
package org.minecraftprot.stackframe.diagnostic;
