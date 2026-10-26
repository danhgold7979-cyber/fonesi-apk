package vn.fonesi.core

/** Conservative boundaries of the pinned candidate, not a compatibility certification. */
object ProbePolicy {
    fun rejectionReason(apiLevel: Int, abis: List<String>, pageSize: Long): String? = when {
        apiLevel !in 26..34 -> "SDK thử nghiệm chưa được đánh giá cho Android/API này (phạm vi thử: API 26–34)."
        "arm64-v8a" !in abis -> "Bản thử engine này cần máy hỗ trợ ARM64."
        pageSize != 4096L -> "SDK hiện có thư viện native căn trang 4 KB; chưa bật trên cấu hình trang khác."
        else -> null
    }
}
