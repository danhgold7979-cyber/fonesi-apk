package vn.fonesi.engine

import android.app.Application
import java.lang.reflect.Modifier

/** Signed PRIVATE Application class preflight only. No creation, attach or Android registration. */
internal object PrivateGuestApplicationClassPreflight {
    fun resolve(signedName: String, privateLoader: ClassLoader, applicationBase: Class<*>): Class<*> {
        require(signedName.isNotBlank() && signedName.length <= 255) { "Missing signed Application identity" }
        val candidate = Class.forName(signedName, false, privateLoader)
        return requireMatches(signedName, candidate, privateLoader, applicationBase)
    }

    fun requireMatches(
        signedName: String,
        candidate: Class<*>,
        privateLoader: ClassLoader,
        applicationBase: Class<*>,
    ): Class<*> {
        require(signedName.isNotBlank() && candidate.name == signedName) { "Signed Application name mismatch" }
        require(candidate.classLoader === privateLoader) { "Application not defined by PRIVATE loader" }
        require(candidate != applicationBase && applicationBase.isAssignableFrom(candidate)) { "Not an Application subtype" }
        require(Modifier.isPublic(candidate.modifiers) && !Modifier.isAbstract(candidate.modifiers) &&
            !candidate.isInterface && !candidate.isAnonymousClass && !candidate.isLocalClass &&
            (!candidate.isMemberClass || Modifier.isStatic(candidate.modifiers))) {
            "Signed Application must be a public concrete standalone/static class"
        }
        val constructor = try {
            candidate.getConstructor()
        } catch (error: NoSuchMethodException) {
            throw IllegalArgumentException("Signed Application requires public no-arg constructor", error)
        }
        require(Modifier.isPublic(constructor.modifiers) && constructor.parameterCount == 0) {
            "Signed Application requires public no-arg constructor"
        }
        return candidate
    }
}
