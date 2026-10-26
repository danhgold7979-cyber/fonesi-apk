package vn.fonesi.core

import org.junit.Assert.*
import org.junit.Test

class ProbePolicyTest {
    @Test fun `documented boundaries may be attempted without claiming runtime compatibility`() {
        for (api in listOf(26, 30, 34)) {
            assertNull(ProbePolicy.rejectionReason(api, listOf("arm64-v8a", "armeabi-v7a"), 4096))
        }
    }

    @Test fun `new Android is blocked rather than silently lowering target SDK`() {
        for (api in listOf(25, 35, 36)) {
            assertNotNull(ProbePolicy.rejectionReason(api, listOf("arm64-v8a"), 4096))
        }
    }

    @Test fun `native libraries are not loaded on unsupported CPU`() {
        for (abis in listOf(emptyList(), listOf("x86_64"), listOf("armeabi-v7a"))) {
            assertNotNull(ProbePolicy.rejectionReason(34, abis, 4096))
        }
    }

    @Test fun `16 KB and unknown page sizes do not pass a 4 KB binary gate`() {
        for (pageSize in listOf(0L, -1L, 16384L, 65536L)) {
            assertNotNull(ProbePolicy.rejectionReason(34, listOf("arm64-v8a"), pageSize))
        }
    }
}
