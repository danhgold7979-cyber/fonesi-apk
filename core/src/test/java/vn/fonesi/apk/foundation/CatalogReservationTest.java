package vn.fonesi.apk.foundation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CatalogReservationTest {
    private static final AtomicInteger CHECKS = new AtomicInteger();
    private CatalogReservationTest() { }
    private static void check(boolean value) {
        CHECKS.incrementAndGet(); if (!value) throw new AssertionError("Reservation fixture failed");
    }
    private static void failure(CatalogReservation.Error expected, Runnable action) {
        try { action.run(); throw new AssertionError("Expected typed failure"); }
        catch (CatalogReservation.Failure error) {
            check(error.error() == expected); check(error.getCause() == null);
            check("Catalog ownership unavailable".equals(error.getMessage()));
        }
    }
    private static void await(CountDownLatch latch) {
        try { check(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void join(Thread thread) {
        try { thread.join(10000); check(!thread.isAlive()); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static Thread worker(AtomicReference<Throwable> error, Runnable action) {
        Thread thread = new Thread(() -> {
            try { action.run(); } catch (Throwable failure) { error.compareAndSet(null, failure); }
        });
        thread.setDaemon(true); thread.start(); return thread;
    }
    private static void healthy(AtomicReference<Throwable> error) {
        check(error.get() == null);
        if (error.get() != null) throw new AssertionError(error.get());
    }
    private static void lifecycle() {
        CatalogReservation gate = new CatalogReservation();
        CatalogReservation.Token first = gate.tryAcquire(); check(first != null);
        first.requireActive(); check(gate.tryAcquire() == null);
        first.close(); first.close();
        failure(CatalogReservation.Error.STATE, first::requireActive);
        CatalogReservation.Token second = gate.tryAcquire(); check(second != null && second != first);
        first.close(); second.requireActive(); check(gate.tryAcquire() == null);
        failure(CatalogReservation.Error.STATE, first::quarantine);
        second.requireActive(); second.close();
        CatalogReservation.Token third = gate.tryAcquire(); check(third != null); third.close();
    }
    private static void wrongThread() {
        CatalogReservation gate = new CatalogReservation();
        CatalogReservation.Token token = gate.tryAcquire(); check(token != null);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = worker(error, () -> {
            check(gate.tryAcquire() == null);
            failure(CatalogReservation.Error.WRONG_THREAD, token::requireActive);
            failure(CatalogReservation.Error.WRONG_THREAD, token::quarantine);
            failure(CatalogReservation.Error.WRONG_THREAD, token::close);
        });
        join(thread); healthy(error); token.requireActive(); token.close();
        Thread after = worker(error, () -> failure(CatalogReservation.Error.WRONG_THREAD, token::close));
        join(after); healthy(error);
        CatalogReservation.Token next = gate.tryAcquire(); check(next != null); next.close();
    }
    private static void quarantine() {
        CatalogReservation gate = new CatalogReservation();
        CatalogReservation.Token token = gate.tryAcquire(); check(token != null);
        token.quarantine(); token.quarantine(); check(gate.tryAcquire() == null);
        failure(CatalogReservation.Error.RESOURCE_UNCERTAIN, token::requireActive);
        failure(CatalogReservation.Error.RESOURCE_UNCERTAIN, token::close);
        failure(CatalogReservation.Error.RESOURCE_UNCERTAIN, token::close);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = worker(error, () -> check(gate.tryAcquire() == null));
        join(thread); healthy(error); check(gate.tryAcquire() == null);
    }
    private static void raceAfterRelease() {
        CatalogReservation gate = new CatalogReservation();
        CatalogReservation.Token old = gate.tryAcquire(); check(old != null); old.close();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(2), release = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(); AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable racing = () -> {
            CatalogReservation.Token token = null;
            ready.countDown(); await(start);
            try {
                token = gate.tryAcquire();
                if (token != null) { winners.incrementAndGet(); token.requireActive(); }
            } finally { attempted.countDown(); }
            if (token != null) { await(release); token.requireActive(); token.close(); }
        };
        Thread one = worker(error, racing), two = worker(error, racing);
        try {
            await(ready); start.countDown(); await(attempted);
            check(winners.get() == 1); check(gate.tryAcquire() == null);
            old.close(); failure(CatalogReservation.Error.STATE, old::quarantine);
            check(gate.tryAcquire() == null);
        } finally { start.countDown(); release.countDown(); }
        join(one); join(two); healthy(error);
        CatalogReservation.Token finalToken = gate.tryAcquire(); check(finalToken != null); finalToken.close();
    }
    private static void deadOwnerRetained() {
        CatalogReservation gate = new CatalogReservation(); AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = worker(error, () -> { CatalogReservation.Token token = gate.tryAcquire(); check(token != null); token.requireActive(); });
        join(thread); healthy(error); check(gate.tryAcquire() == null);
    }
    public static void main(String[] args) {
        lifecycle(); wrongThread(); quarantine(); raceAfterRelease(); deadOwnerRetained();
        System.out.println("CatalogReservation HOST assertions: " + CHECKS.get() + " PASS; no auth/file/Android acceptance");
    }
}
