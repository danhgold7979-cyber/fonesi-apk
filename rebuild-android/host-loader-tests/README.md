# Isolated loader orchestration test

`../check-loader-host.sh` compiles the actual production CatalogLoader, gate, page and manager classes against host-only minimal Android scheduling/context/cancellation stand-ins and a latch-controlled reader. These fake classes are outside `app/src` and never packaged. The real bounded ThreadPoolExecutor is exercised; tests use reflection only to inspect/release/reset test instances, not to change production behavior.

The deterministic fake clock/main queue covers success/Next, reader failure, one active plus one replaceable queued request, cross-owner cancellation, pause/detach, timeout and late result suppression, rejected timeout/delivery posting, delayed main delivery and executor rejection. Delivery-post rejection cannot render failure on an unavailable main handler: it releases ownership and prevents publication; no callback runs on the worker. A later request can recover.

This proves host orchestration behavior, NOT Android Handler/CancellationSignal/SQLite/CursorWindow/OEM/UI execution. Actual Java17 compilation remains a hosted CI gate; local stripped Java21 fallback is labeled explicitly. No SQLite reader code or device database is exercised here.
