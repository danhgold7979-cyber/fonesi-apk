package vn.fonesi.app.storage;

import vn.fonesi.apk.foundation.CatalogReservation;

/** Sole process reservation for participating manager-catalog openers; not an auth grant. */
final class CatalogProcessGate {
    private static final CatalogReservation RESERVATION = new CatalogReservation();
    private CatalogProcessGate() { }
    static CatalogReservation.Token tryAcquire() { return RESERVATION.tryAcquire(); }
}
