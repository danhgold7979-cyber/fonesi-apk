package vn.fonesi.engine

import vn.fonesi.apk.VerifiedPrivateActivitySelection
import java.lang.reflect.Modifier

/**
 * Checks the *type* behind a signed, explicit private Activity selection.
 *
 * This does not instantiate/attach/launch an Android Activity, grant permissions,
 * register a component, allocate a guest process or provide account isolation.
 * A signed manifest declaration by itself never proves executable guest code.
 */
internal object PrivateGuestActivityClassPreflight {
    fun resolve(
        selection: VerifiedPrivateActivitySelection,
        privateLoader: ClassLoader,
        activityBase: Class<*>,
    ): Class<*> = requireMatches(
        selection,
        // Do not initialize a third-party class while checking signed bytecode.
        // No fallback to host or installed-original package class lookup.
        Class.forName(selection.targetActivityName, false, privateLoader),
        privateLoader,
        activityBase,
    )

    fun requireMatches(
        selection: VerifiedPrivateActivitySelection,
        candidate: Class<*>,
        privateLoader: ClassLoader,
        activityBase: Class<*>,
    ): Class<*> {
        require(selection.packageName.isNotBlank() && selection.targetActivityName.isNotBlank()) {
            "Missing signed private Activity target identity"
        }
        require(candidate.name == selection.targetActivityName) {
            "Selected signed Activity name does not match private bytecode"
        }
        // Parent-first classloaders may resolve host/framework classes by name.
        // Only a class DEFINED by the loaded private image can be considered.
        require(candidate.classLoader === privateLoader) {
            "Signed Activity was not defined by the exact private guest classloader"
        }
        require(candidate != activityBase && activityBase.isAssignableFrom(candidate)) {
            "Signed Activity is not an Android Activity class"
        }
        require(Modifier.isPublic(candidate.modifiers) &&
            !Modifier.isAbstract(candidate.modifiers) &&
            !candidate.isInterface && !candidate.isAnonymousClass && !candidate.isLocalClass &&
            (!candidate.isMemberClass || Modifier.isStatic(candidate.modifiers))) {
            "Signed Activity must be a public, concrete standalone or static class"
        }
        // Inspect the constructor shape without invoking guest code. The future framework
        // adapter must not discover a missing public no-arg constructor after dispatch.
        val constructor = try {
            candidate.getConstructor()
        } catch (error: NoSuchMethodException) {
            throw IllegalArgumentException("Signed Activity requires a public zero-argument constructor", error)
        }
        require(Modifier.isPublic(constructor.modifiers) && constructor.parameterCount == 0) {
            "Signed Activity requires a public zero-argument constructor"
        }
        return candidate
    }
}
