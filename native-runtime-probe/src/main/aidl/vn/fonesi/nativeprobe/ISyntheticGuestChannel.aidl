package vn.fonesi.nativeprobe;

/** Disposable in-app synthetic Binder contract; NOT a virtualized Android system service. */
interface ISyntheticGuestChannel {
    String exchange(String nonce);
    /** Dispatch only the project-owned signed synthetic guest payload; never a third-party app. */
    String exchangeGuest(String nonce);
}
