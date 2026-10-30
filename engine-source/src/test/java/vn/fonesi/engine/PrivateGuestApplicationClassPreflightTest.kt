package vn.fonesi.engine

import android.app.Application
import org.junit.Assert.*
import org.junit.Test

/** JVM bytecode checks use the real Android Application base type; no constructor is invoked. */
class PrivateGuestApplicationClassPreflightTest {
    private val loader get() = GoodPrivateApplicationForTest::class.java.classLoader
    private val base get() = Application::class.java

    @Test fun exactPrivateConcreteApplicationPassesWithoutRunningConstructor() {
        ApplicationConstructorCounter.count = 0
        val clazz = PrivateGuestApplicationClassPreflight.resolve(
            GoodPrivateApplicationForTest::class.java.name, loader, base)
        assertSame(GoodPrivateApplicationForTest::class.java, clazz)
        assertSame(loader, clazz.classLoader)
        assertEquals(0, ApplicationConstructorCounter.count)
    }

    @Test fun delegatedParentClassAndMissingSignedNameFailClosed() {
        val delegated = object : ClassLoader(loader) {}
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestApplicationClassPreflight.resolve(
                GoodPrivateApplicationForTest::class.java.name, delegated, base)
        }
        assertThrows(ClassNotFoundException::class.java) {
            PrivateGuestApplicationClassPreflight.resolve(
                "vn.fonesi.missing.SignedApplication", loader, base)
        }
    }

    @Test fun wrongBaseAbstractNonPublicAndInnerTypesAreRejected() {
        for (clazz in listOf(
            NotAnApplicationForTest::class.java,
            AbstractApplicationForTest::class.java,
            PrivateApplicationForTest::class.java,
            ApplicationTestOuter.InnerApplication::class.java
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                PrivateGuestApplicationClassPreflight.requireMatches(clazz.name, clazz, loader, base)
            }
        }
    }

    @Test fun parameterizedPrivateAndProtectedConstructorsAreRejected() {
        for (clazz in listOf(
            ParameterOnlyApplicationForTest::class.java,
            PrivateCtorApplicationForTest::class.java,
            ProtectedCtorApplicationForTest::class.java
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                PrivateGuestApplicationClassPreflight.requireMatches(clazz.name, clazz, loader, base)
            }
        }
    }

    @Test fun staticInitializerIsNotRunOnSuccessfulOrFailedPreflight() {
        ApplicationStaticSentinel.count = 0
        val ok = Class.forName(StaticApplicationForTest::class.java.name, false, loader)
        PrivateGuestApplicationClassPreflight.requireMatches(ok.name, ok, loader, base)
        assertEquals(0, ApplicationStaticSentinel.count)
        val invalid = Class.forName(StaticPrivateCtorApplicationForTest::class.java.name, false, loader)
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestApplicationClassPreflight.requireMatches(invalid.name, invalid, loader, base)
        }
        assertEquals(0, ApplicationStaticSentinel.count)
    }
}

object ApplicationConstructorCounter { var count = 0 }
class GoodPrivateApplicationForTest : Application() {
    init { ApplicationConstructorCounter.count++ }
}
class NotAnApplicationForTest
abstract class AbstractApplicationForTest : Application()
private class PrivateApplicationForTest : Application()
class ParameterOnlyApplicationForTest(value: String) : Application()
class PrivateCtorApplicationForTest private constructor() : Application()
class ProtectedCtorApplicationForTest protected constructor() : Application()
class ApplicationTestOuter { inner class InnerApplication : Application() }
object ApplicationStaticSentinel { var count = 0 }
class StaticApplicationForTest : Application() {
    companion object { init { ApplicationStaticSentinel.count++ } }
}
class StaticPrivateCtorApplicationForTest private constructor() : Application() {
    companion object { init { ApplicationStaticSentinel.count++ } }
}
