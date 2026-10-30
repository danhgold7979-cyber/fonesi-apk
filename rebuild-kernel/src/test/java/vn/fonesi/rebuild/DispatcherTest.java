package vn.fonesi.rebuild;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static vn.fonesi.rebuild.OperationProtocol.*;
import static vn.fonesi.rebuild.CapabilityPolicy.*;
import static vn.fonesi.rebuild.SharedUpdateDispatcher.*;

public final class DispatcherTest {
    private static int checks;
    private static final Provider PROVIDER = new Provider("test-provider", "revision1");
    private static SharedUpdateDispatcher.Request request(String packageName) {
        UUID clone = UUID.randomUUID();
        return new SharedUpdateDispatcher.Request(packageName,
            new Identity(UUID.randomUUID(), PROVIDER, packageName, "api34-arm64-4k", Scope.SYNTHETIC, clone, "a".repeat(64), Set.of(clone)), "api34-arm64-4k", Scope.SYNTHETIC);
    }
    private static Preflight valid(SharedUpdateDispatcher.Request request) {
        Map<UUID, PeerState> peers = new HashMap<>();
        for (UUID peer : request.identity().affectedClones()) peers.put(peer,
            new PeerState(request.identity().provider(), request.packageName(), "b".repeat(64), true, true));
        Evidence evidence = new Evidence(request.identity().provider(), request.environment(), request.identity().clusterSha256(),
            Scope.SYNTHETIC, Isolation.HOST_UID_NAMESPACE,
            Set.of(Claim.SIGNED_CLUSTER_IMPORT, Claim.PRIVATE_CLUSTER_READBACK,
                   Claim.SPLIT_EXECUTION, Claim.ATOMIC_UPDATE, Claim.GUEST_LIFECYCLE));
        return new Preflight(request.identity(), true, true, true, peers, evidence);
    }
    private static Readback readback(SharedUpdateDispatcher.Request request, long revision) {
        Map<UUID, PeerObservation> peers = new HashMap<>();
        for (UUID peer : request.identity().affectedClones()) peers.put(peer,
            new PeerObservation(request.identity().provider(), request.identity().clusterSha256(), true));
        return new Readback(request.identity().operation(), revision, peers);
    }
    private static SharedUpdateDispatcher fresh() throws IOException {
        return new SharedUpdateDispatcher(Files.createTempDirectory("fonesi-rebuild-dispatch-"));
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("hold-scope")) {
            SharedUpdateDispatcher held = new SharedUpdateDispatcher(Path.of(args[1]));
            held.execute(request("example.cross"), DispatcherTest::valid, r -> {
                System.out.println("DISPATCHED"); System.out.flush(); Thread.sleep(60_000);
            }, DispatcherTest::readback);
            return;
        }
        SharedUpdateDispatcher dispatcher = fresh();
        SharedUpdateDispatcher.Request request = request("example.app");
        AtomicInteger probes = new AtomicInteger(), calls = new AtomicInteger(), inspections = new AtomicInteger();
        check(dispatcher.execute(request, r -> { probes.incrementAndGet(); return valid(r); }, r -> calls.incrementAndGet(),
            (r, revision) -> { inspections.incrementAndGet(); return readback(r, revision); }) == Result.VERIFIED);
        check(probes.get() == 3 && calls.get() == 1 && inspections.get() == 1);
        check(dispatcher.execute(request, DispatcherTest::valid, r -> calls.incrementAndGet(), DispatcherTest::readback) == Result.DUPLICATE);
        check(calls.get() == 1);
        for (int kind = 0; kind < 8; kind++) {
            final int failure = kind;
            SharedUpdateDispatcher test = fresh(); SharedUpdateDispatcher.Request target = request("example.reject");
            AtomicInteger mutations = new AtomicInteger();
            Result result = test.execute(target, r -> {
                Preflight good = valid(r);
                return switch (failure) {
                    case 0 -> new Preflight(good.consent(), false, true, true, good.peers(), good.evidence());
                    case 1 -> new Preflight(good.consent(), true, false, true, good.peers(), good.evidence());
                    case 2 -> new Preflight(good.consent(), true, true, true, Map.of(), good.evidence());
                    case 3 -> new Preflight(good.consent(), true, true, true, good.peers(), null);
                    case 4 -> new Preflight(request("example.reject").identity(), true, true, true, good.peers(), good.evidence());
                    case 5 -> new Preflight(good.consent(), true, true, true, Map.of(r.identity().cloneId(),
                        new PeerState(PROVIDER, r.packageName(), "b".repeat(64), false, true)), good.evidence());
                    case 6 -> new Preflight(good.consent(), true, true, true, Map.of(r.identity().cloneId(),
                        new PeerState(PROVIDER, r.packageName(), "b".repeat(64), true, false)), good.evidence());
                    default -> new Preflight(good.consent(), true, true, true, Map.of(r.identity().cloneId(),
                        new PeerState(PROVIDER, "other.app", "b".repeat(64), true, true)), good.evidence());
                };
            }, r -> mutations.incrementAndGet(), DispatcherTest::readback);
            check(result == Result.PREFLIGHT_REJECTED); check(mutations.get() == 0);
        }
        for (int phase = 2; phase <= 3; phase++) {
            final int rejectAt = phase; AtomicInteger seen = new AtomicInteger(), mutations = new AtomicInteger();
            SharedUpdateDispatcher test = fresh(); SharedUpdateDispatcher.Request target = request("example.relock");
            Result result = test.execute(target, r -> {
                Preflight good = valid(r); boolean allowed = seen.incrementAndGet() != rejectAt;
                return new Preflight(good.consent(), allowed, true, true, good.peers(), good.evidence());
            }, r -> mutations.incrementAndGet(), DispatcherTest::readback);
            check(result == (phase == 2 ? Result.PREFLIGHT_REJECTED : Result.REVIEW_REQUIRED)); check(mutations.get() == 0);
            if (phase == 3) check(test.execute(request("example.relock"), DispatcherTest::valid,
                r -> mutations.incrementAndGet(), DispatcherTest::readback) == Result.SCOPE_REVIEW_REQUIRED);
            check(mutations.get() == 0);
        }
        SharedUpdateDispatcher drift = fresh(); AtomicInteger driftProbe = new AtomicInteger(), driftCalls = new AtomicInteger();
        check(drift.execute(request("example.drift"), r -> {
            Preflight good = valid(r); String cluster = driftProbe.incrementAndGet() == 1 ? "b".repeat(64) : "c".repeat(64);
            return new Preflight(good.consent(), true, true, true, Map.of(r.identity().cloneId(),
                new PeerState(PROVIDER, r.packageName(), cluster, true, true)), good.evidence());
        }, r -> driftCalls.incrementAndGet(), DispatcherTest::readback) == Result.PREFLIGHT_REJECTED);
        check(driftCalls.get() == 0);
        SharedUpdateDispatcher uncertain = fresh(); AtomicInteger uncertainCalls = new AtomicInteger();
        SharedUpdateDispatcher.Request failed = request("example.uncertain");
        check(uncertain.execute(failed, DispatcherTest::valid, r -> {
            uncertainCalls.incrementAndGet(); throw new IOException("synthetic mutation outcome unknown");
        }, DispatcherTest::readback) == Result.REVIEW_REQUIRED);
        check(uncertainCalls.get() == 1);
        check(uncertain.execute(request("example.uncertain"), DispatcherTest::valid, r -> uncertainCalls.incrementAndGet(),
            DispatcherTest::readback) == Result.SCOPE_REVIEW_REQUIRED);
        SharedUpdateDispatcher.Request newer = request("example.uncertain");
        Identity newerIdentity = new Identity(newer.identity().operation(), new Provider(PROVIDER.id(), "revision2"), newer.packageName(), newer.environment(), newer.scope(), newer.identity().cloneId(),
            newer.identity().clusterSha256(), newer.identity().affectedClones());
        check(uncertain.execute(new SharedUpdateDispatcher.Request(newer.packageName(), newerIdentity, newer.environment(), newer.scope()),
            DispatcherTest::valid, r -> uncertainCalls.incrementAndGet(), DispatcherTest::readback) == Result.SCOPE_REVIEW_REQUIRED);
        check(uncertain.execute(request("example.unrelated"), DispatcherTest::valid, r -> uncertainCalls.incrementAndGet(),
            DispatcherTest::readback) == Result.VERIFIED); check(uncertainCalls.get() == 2);
        SharedUpdateDispatcher lease = fresh(); AtomicInteger overlapping = new AtomicInteger();
        check(lease.execute(request("example.lease"), DispatcherTest::valid, r -> {
            check(lease.execute(request("example.lease"), DispatcherTest::valid, nested -> overlapping.incrementAndGet(),
                DispatcherTest::readback) == Result.BUSY);
            check(lease.execute(request("example.different"), DispatcherTest::valid, nested -> overlapping.incrementAndGet(),
                DispatcherTest::readback) == Result.VERIFIED);
        }, (r, revision) -> {
            check(lease.execute(request("example.lease"), DispatcherTest::valid, nested -> overlapping.incrementAndGet(),
                DispatcherTest::readback) == Result.BUSY);
            return readback(r, revision);
        }) == Result.VERIFIED); check(overlapping.get() == 1);
        SharedUpdateDispatcher mismatch = fresh();
        check(mismatch.execute(request("example.readback"), DispatcherTest::valid, r -> {}, (r, revision) -> null) == Result.REVIEW_REQUIRED);
        check(mismatch.execute(request("example.readback"), DispatcherTest::valid, r -> { throw new AssertionError("Must not mutate"); },
            DispatcherTest::readback) == Result.SCOPE_REVIEW_REQUIRED);
        SharedUpdateDispatcher deniedProduction = fresh(); SharedUpdateDispatcher.Request synthetic = request("example.production");
        check(deniedProduction.execute(new SharedUpdateDispatcher.Request(synthetic.packageName(), new Identity(synthetic.identity().operation(), PROVIDER, synthetic.packageName(), synthetic.environment(), Scope.THIRD_PARTY, synthetic.identity().cloneId(), synthetic.identity().clusterSha256(), synthetic.identity().affectedClones()), synthetic.environment(),
            Scope.THIRD_PARTY), DispatcherTest::valid, r -> { throw new AssertionError("Synthetic proof cannot grant production"); },
            DispatcherTest::readback) == Result.PREFLIGHT_REJECTED);
        Path corruptRoot = Files.createTempDirectory("fonesi-dispatch-corrupt-"); SharedUpdateDispatcher corrupt = new SharedUpdateDispatcher(corruptRoot);
        check(corrupt.execute(request("example.corrupt"), DispatcherTest::valid, r -> {}, DispatcherTest::readback) == Result.VERIFIED);
        Path scopeDir = corruptRoot;
        Files.writeString(scopeDir.resolve("not-a-uuid.journal"), "bad");
        try { corrupt.execute(request("example.corrupt"), DispatcherTest::valid, r -> { throw new AssertionError("Corrupt journal mutated"); },
            DispatcherTest::readback); throw new AssertionError("Corruption accepted"); } catch (IOException expected) { checks++; }
        Files.delete(scopeDir.resolve("not-a-uuid.journal")); // Only this test-created malformed file.
        check(corrupt.execute(request("example.clean"), DispatcherTest::valid, r -> {}, DispatcherTest::readback) == Result.VERIFIED);
        // Same UUID cannot be rebound into another package/environment/evidence scope.
        for (int altered = 0; altered < 3; altered++) {
            Identity old = request.identity();
            Identity rebound = new Identity(old.operation(), old.provider(), altered == 0 ? "another.app" : old.packageName(),
                altered == 1 ? "different-environment" : old.environment(), altered == 2 ? Scope.THIRD_PARTY : old.scope(),
                old.cloneId(), old.clusterSha256(), old.affectedClones());
            check(dispatcher.execute(new SharedUpdateDispatcher.Request(rebound.packageName(), rebound, rebound.environment(), rebound.scope()),
                DispatcherTest::valid, r -> { throw new AssertionError("Rebound operation replayed"); }, DispatcherTest::readback) == Result.DUPLICATE);
        }
        SharedUpdateDispatcher incomplete = fresh();
        check(incomplete.execute(request("example.incomplete"), r -> {
            Preflight good = valid(r); return new Preflight(good.consent(), true, true, false, good.peers(), good.evidence());
        }, r -> { throw new AssertionError("Incomplete enumeration mutated"); }, DispatcherTest::readback) == Result.PREFLIGHT_REJECTED);
        SharedUpdateDispatcher.Request single = request("example.multipeer"); UUID extra = UUID.randomUUID();
        Identity multiIdentity = new Identity(single.identity().operation(), PROVIDER, single.packageName(), single.environment(), single.scope(),
            single.identity().cloneId(), single.identity().clusterSha256(), Set.of(single.identity().cloneId(), extra));
        SharedUpdateDispatcher.Request multi = new SharedUpdateDispatcher.Request(single.packageName(), multiIdentity, single.environment(), single.scope());
        check(fresh().execute(multi, DispatcherTest::valid, r -> {}, DispatcherTest::readback) == Result.VERIFIED);
        check(fresh().execute(multi, r -> {
            Preflight good = valid(r); Map<UUID, PeerState> peers = new HashMap<>(good.peers());
            peers.put(extra, new PeerState(PROVIDER, r.packageName(), "c".repeat(64), true, true));
            return new Preflight(good.consent(), true, true, true, peers, good.evidence());
        }, r -> { throw new AssertionError("Mixed shared generation mutated"); }, DispatcherTest::readback) == Result.PREFLIGHT_REJECTED);
        check(fresh().execute(multi, DispatcherTest::valid, r -> {}, (r, revision) -> new Readback(r.identity().operation(), revision,
            Map.of(r.identity().cloneId(), new PeerObservation(PROVIDER, r.identity().clusterSha256(), true)))) == Result.REVIEW_REQUIRED);
        AtomicInteger memberProbe = new AtomicInteger();
        check(fresh().execute(request("example.newpeer"), r -> {
            Preflight good = valid(r); Map<UUID, PeerState> peers = new HashMap<>(good.peers());
            if (memberProbe.incrementAndGet() == 3) peers.put(UUID.randomUUID(), new PeerState(PROVIDER, r.packageName(), "b".repeat(64), true, true));
            return new Preflight(good.consent(), true, true, true, peers, good.evidence());
        }, r -> { throw new AssertionError("New unselected peer ignored"); }, DispatcherTest::readback) == Result.REVIEW_REQUIRED);
        for (int call = 1; call <= 3; call++) {
            final int failAt = call; AtomicInteger seen = new AtomicInteger();
            Result rejected = fresh().execute(request("example.probeerror"), r -> {
                if (seen.incrementAndGet() == failAt) throw new IOException("probe unavailable"); return valid(r);
            }, r -> { throw new AssertionError("Probe error mutated"); }, DispatcherTest::readback);
            check(rejected == (call < 3 ? Result.PREFLIGHT_REJECTED : Result.REVIEW_REQUIRED));
        }
        check(fresh().execute(request("example.inspecterror"), DispatcherTest::valid, r -> {},
            (r, revision) -> { throw new IOException("inspection unavailable"); }) == Result.REVIEW_REQUIRED);
        for (OperationJournal.PublishPoint point : OperationJournal.PublishPoint.values()) {
            for (int write = 1; write <= 3; write++) {
                final int failAt = write; AtomicInteger writes = new AtomicInteger(), mutations = new AtomicInteger();
                Path faultRoot = Files.createTempDirectory("fonesi-dispatch-publish-fault-");
                SharedUpdateDispatcher fault = new SharedUpdateDispatcher(faultRoot, at -> {
                    if (at == point && writes.incrementAndGet() == failAt) throw new IOException("injected dispatcher publish failure");
                });
                SharedUpdateDispatcher.Request target = request("example.publishfault");
                Result result = fault.execute(target, DispatcherTest::valid, r -> mutations.incrementAndGet(), DispatcherTest::readback);
                check(mutations.get() == (write < 3 ? 0 : 1));
                check(result == (write == 3 && point == OperationJournal.PublishPoint.BEFORE_RENAME ? Result.REVIEW_REQUIRED : Result.STORAGE_UNCERTAIN));
            }
            AtomicInteger writes = new AtomicInteger(); Path failureRoot = Files.createTempDirectory("fonesi-dispatch-review-fault-");
            SharedUpdateDispatcher fault = new SharedUpdateDispatcher(failureRoot, at -> {
                if (at == point && writes.incrementAndGet() == 3) throw new IOException("review commit failure");
            });
            check(fault.execute(request("example.reviewfault"), DispatcherTest::valid, r -> { throw new IOException("mutation uncertain"); },
                DispatcherTest::readback) == Result.STORAGE_UNCERTAIN);
            check(new SharedUpdateDispatcher(failureRoot).execute(request("example.reviewfault"), DispatcherTest::valid,
                r -> { throw new AssertionError("Uncertain state replayed"); }, DispatcherTest::readback) == Result.SCOPE_REVIEW_REQUIRED);
        }
        Path crossRoot = Files.createTempDirectory("fonesi-dispatch-cross-process-");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), DispatcherTest.class.getName(),
            "hold-scope", crossRoot.toString()).redirectErrorStream(true).start();
        try (BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!output.ready() && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            check(output.ready()); check("DISPATCHED".equals(output.readLine()));
            check(new SharedUpdateDispatcher(crossRoot).execute(request("example.cross"), DispatcherTest::valid,
                r -> { throw new AssertionError("Overlapping process mutated"); }, DispatcherTest::readback) == Result.BUSY);
        } finally { child.destroyForcibly(); check(child.waitFor(10, TimeUnit.SECONDS)); }
        check(new SharedUpdateDispatcher(crossRoot).execute(request("example.cross"), DispatcherTest::valid,
            r -> { throw new AssertionError("Crashed mutation replayed"); }, DispatcherTest::readback) == Result.SCOPE_REVIEW_REQUIRED);
        Path leaseRoot = Files.createTempDirectory("fonesi-dispatch-lock-safety-");
        SharedUpdateDispatcher lockSafety = new SharedUpdateDispatcher(leaseRoot);
        check(lockSafety.execute(request("example.locktest"), DispatcherTest::valid, r -> {}, DispatcherTest::readback) == Result.VERIFIED);
        Path leaseDir; try (var children = Files.list(leaseRoot)) {
            leaseDir = children.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).findFirst().orElseThrow();
        }
        Path leaseFile = leaseDir.resolve(".dispatch.lock"); Files.delete(leaseFile); Files.createDirectory(leaseFile);
        try { lockSafety.execute(request("example.locktest"), DispatcherTest::valid, r -> { throw new AssertionError("Unsafe lock mutated"); },
            DispatcherTest::readback); throw new AssertionError("Directory lock accepted"); } catch (IOException expected) { checks++; }
        Files.delete(leaseFile); Path outside = Files.createTempFile("fonesi-dispatch-marker-", ".txt"); Files.writeString(outside, "untouched");
        Files.createSymbolicLink(leaseFile, outside);
        try { lockSafety.execute(request("example.locktest"), DispatcherTest::valid, r -> { throw new AssertionError("Symlink lock mutated"); },
            DispatcherTest::readback); throw new AssertionError("Symlink lock accepted"); } catch (IOException expected) { checks++; }
        check(Files.readString(outside).equals("untouched"));
        if (checks < 45) throw new AssertionError("Incomplete dispatcher suite");
        System.out.println("PASS " + checks + " synthetic dispatcher checks; trusted callbacks only; no provider");
    }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
}
