package vn.fonesi.nativeprobe;

/** Disposable in-app synthetic Binder contract; NOT a virtualized Android system service. */
interface ISyntheticGuestChannel {
    String exchange(String nonce);
}
