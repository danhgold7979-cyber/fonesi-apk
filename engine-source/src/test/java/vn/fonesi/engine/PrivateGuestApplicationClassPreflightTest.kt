package vn.fonesi.engine

import org.junit.Assert.*
import org.junit.Test

/** JVM-only bytecode safety; never claims Android Application execution. */
class PrivateGuestApplicationClassPreflightTest {
    private val loader get() = GoodPrivateApplicationForTest::class.java.classLoader
    private val base get() = ApplicationBaseForTest::class.java

    @Test fun exactPrivateConcreteClassPassesWithoutRunningConstructor() {
        ApplicationConstructorCounter.count = 0
        val clazz = PrivateGuestApplicationClassPreflight.resolve(
            GoodPrivateApplicationForTest::class.java.name, loader, base)
        assertSame(GoodPrivateApplicationForTest::class.java, clazz)
        assertEquals(0, ApplicationConstructorCounter.count)
    }

    @Test fun delegatedParentClassAndMissingSignedNameFailClosed() {
        val delegated = object : ClassLoader(loader) {}
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestApplicationClassPreflight.resolve(GoodPrivateApplicationForTest::class.java.name, delegated, base)
        }
        assertThrows(ClassNotFoundException::class.java) {
            PrivateGuestApplicationClassPreflight.resolve("vn.fonesi.missing.SignedApplication", loader, base)
        }
    }

    @Test fun invalidTypesAndConstructorsAreRefused() {
        for (clazz in listOf(
            NotAnApplicationForTest::class.java,
            AbstractApplicationForTest::class.java,
            ParameterOnlyApplicationForTest::class.java,
            PrivateCtorApplicationForTest::class.java,
            ApplicationTestOuter.InnerApplication::class.java
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

open class ApplicationBaseForTest
object ApplicationConstructorCounter { var count = 0 }
class GoodPrivateApplicationForTest : ApplicationBaseForTest() {
    init { ApplicationConstructorCounter.count++ }
}
class NotAnApplicationForTest
abstract class AbstractApplicationForTest : ApplicationBaseForTest()
class ParameterOnlyApplicationForTest(value: String) : ApplicationBaseForTest()
class PrivateCtorApplicationForTest private constructor() : ApplicationBaseForTest()
class ApplicationTestOuter { inner class InnerApplication : ApplicationBaseForTest() }
object ApplicationStaticSentinel { var count = 0 }
class StaticApplicationForTest : ApplicationBaseForTest() {
    companion object { init { ApplicationStaticSentinel.count++ } }
}
class StaticPrivateCtorApplicationForTest private constructor() : ApplicationBaseForTest() {
    companion object { init { ApplicationStaticSentinel.count++ } }
}
