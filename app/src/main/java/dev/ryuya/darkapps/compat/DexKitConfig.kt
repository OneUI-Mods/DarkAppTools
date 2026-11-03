package dev.ryuya.darkapps.compat

internal object DexKitConfig {
    const val SCHEMA = 2
    const val MODULE_ID = "dev.ryuya.darkapps"
    val MODULE_VERSION
        get() = dev.ryuya.darkapps.BuildConfig.VERSION_CODE

    const val PROFILE_HASH = "8695436b780376b9f4909fb882c5ce12c9321645095d2b28d7ccc1fa96b16c6f"
    val FEATURE_PREFIXES = setOf<String>()
    val TARGETS = setOf("co.candyhouse.sesame2", "com.kddi.kdla.jp")
    val HOSTS = setOf("com.kddi.kdla.jp", "co.candyhouse.sesame2")
    val CLASSES_BY_TARGET =
        mapOf(
            "co.candyhouse.sesame2" to listOf("androidx.cardview.widget.CardView"),
            "com.kddi.kdla.jp" to
                listOf(
                    "Md.d",
                    "Md.g",
                    "Md.g\$b",
                    "Md.i",
                    "Md.k",
                    "androidx.cardview.widget.CardView",
                    "androidx.recyclerview.widget.RecyclerView\$Adapter",
                ),
        )
    val KNOWN_VERSIONS =
        mapOf("co.candyhouse.sesame2" to setOf(906L), "com.kddi.kdla.jp" to setOf(864L))
    val PROFILE_PACKAGES =
        mapOf(
            36 to setOf("co.candyhouse.sesame2", "com.kddi.kdla.jp"),
            37 to setOf("co.candyhouse.sesame2", "com.kddi.kdla.jp"),
        )
}
