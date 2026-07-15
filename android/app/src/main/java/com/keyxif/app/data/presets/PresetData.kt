package com.keyxif.app.data.presets

import com.keyxif.app.R
import com.keyxif.app.domain.model.HousingPreset
import com.keyxif.app.domain.model.KeycapPreset
import com.keyxif.app.domain.model.LogoPreset
import com.keyxif.app.domain.model.LogoColorPolicy
import com.keyxif.app.domain.model.SwitchPreset
import com.keyxif.app.domain.model.VendorPreset

object PresetData {
    object LogoIds {
        const val KEYXIF = "keyxif"
        const val QWERTYKEYS = "qwertykeys"
        const val GEON = "geon"
        const val MODE = "mode"
        const val OWLAB = "owlab"
        const val TGR = "tgr"
        const val KEYCULT = "keycult"
        const val SINGAKBD = "singakbd"
        const val TKD = "tkd"
        const val MATRIX = "matrix"
        const val SWAGKEY = "swagkey"
        const val KLC = "klc"
        const val SYRYAN = "syryan"
        const val NEWONE = "newone"
        const val LIN = "lin"
        const val MACHINA = "machina"
        const val NUXROS = "nuxros"
        const val KBDFANS = "kbdfans"
        const val BAIONLENJA = "baionlenja"
        const val JJW = "jjw"
        const val MERISI = "merisi"
        const val ORION = "orion"
        const val KALAM = "kalam"
        const val REALFORCE = "realforce"
        const val BOWL = "bowl"
        const val CANNONKEYS = "cannonkeys"
        const val GOK = "gok"
        const val GRAYSTUDIO = "graystudio"
        const val HHKB = "hhkb"
        const val MELETRIX = "meletrix"
        const val NOVELKEYS = "novelkeys"
        const val OMNITYPE = "omnitype"
        const val PLEX = "plex"
        const val SENSY = "sensy"
        const val TYPFACE = "typface"
        const val WUQUE = "wuque"
        const val FOX = "fox"
        const val NEO = "neo"
        const val IV_WORKS = "iv-works"
        const val NIUNIU = "niuniu"
        const val GRIT = "grit"
    }

    object VendorIds {
        const val QWERTYKEYS = "qwertykeys"
        const val GEON = "geonworks"
        const val MODE = "mode"
        const val OWLAB = "owlab"
        const val TGR = "tgr"
        const val KEYCULT = "keycult"
        const val SINGAKBD = "singakbd"
        const val TKD = "tkd"
        const val MATRIX = "matrix-lab"
        const val SWAGKEY = "swagkey"
        const val KLC = "klc"
        const val SYRYAN = "syryan"
        const val NEWONE = "newone"
        const val LIN = "lin"
        const val MACHINA = "machina"
        const val NUXROS = "nuxros"
        const val KBDFANS = "kbdfans"
        const val BAIONLENJA = "baionlenja"
        const val JJW = "jjw"
        const val MERISI = "merisi"
        const val ORION = "orion"
        const val KALAM = "kalam"
    }

    val logos = listOf(
        logo(LogoIds.KEYXIF, "Keyxif", default = R.drawable.ic_keyxif, aliases = arrayOf("Keyxif")),
        logo(LogoIds.QWERTYKEYS, "Qwertykeys", default = R.drawable.logo_qwertykeys, aliases = arrayOf("QK", "Neo Studio", "Neo"), colorPolicy = LogoColorPolicy.AUTO_MONO_TINT),
        logo(LogoIds.GEON, "Geonworks", white = R.drawable.logo_geon_w, black = R.drawable.logo_geon_b, aliases = arrayOf("Geon", "Geonworks")),
        logo(LogoIds.MODE, "Mode", white = R.drawable.logo_mode_w, black = R.drawable.logo_mode_b, aliases = arrayOf("Mode Designs", "Mode")),
//        logo(LogoIds.OWLAB, "Owlab", R.drawable.logo_owlab, "OWL", "Owlab"),
        logo(LogoIds.TGR, "TGR", white = R.drawable.logo_tgr_w, black = R.drawable.logo_tgr_b, aliases = arrayOf("TGR")),
        logo(LogoIds.KEYCULT, "Keycult", white = R.drawable.logo_keycult_w, black = R.drawable.logo_keycult_b, aliases = arrayOf("Keycult")),
        logo(LogoIds.SINGAKBD, "SingaKBD", white = R.drawable.logo_singakbd_w, black = R.drawable.logo_singakbd_b, aliases = arrayOf("Singa", "SingaKBD")),
//        logo(LogoIds.TKD, "TKD", R.drawable.logo_tkd, "TheKeyDotCo", "TKD"),
        logo(LogoIds.MATRIX, "Matrix Lab", white = R.drawable.logo_matrix_w, black = R.drawable.logo_matrix_b, aliases = arrayOf("Matrix", "Matrix Lab", "MatrixLab")),
        logo(LogoIds.SWAGKEY, "Swagkey", white = R.drawable.logo_swagkey_w, black = R.drawable.logo_swagkey_b, aliases = arrayOf("Swagkey", "Swagkey")),
        logo(LogoIds.KLC, "KLC", white = R.drawable.logo_klc_w, black = R.drawable.logo_klc_b, aliases = arrayOf("Klc", "Klc")),
        logo(LogoIds.SYRYAN, "Syryan", white = R.drawable.logo_syryan_w, black = R.drawable.logo_syryan_b, aliases = arrayOf("Syryan", "Syryan")),
        logo(LogoIds.NEWONE, "Newone", default = R.drawable.logo_newone, aliases = arrayOf("Newone", "Newone")),
        logo(LogoIds.LIN, "Linworks", white = R.drawable.logo_lin_w, black = R.drawable.logo_lin_b, aliases = arrayOf("Lin", "Lin")),
        logo(LogoIds.MACHINA, "Machina", default = R.drawable.logo_machina, aliases = arrayOf("Machina", "Machina")),
        logo(LogoIds.NUXROS, "Nuxros", white = R.drawable.logo_nuxros_w, black = R.drawable.logo_nuxros_b, aliases = arrayOf("Nuxros", "Nuxros")),
        logo(LogoIds.KBDFANS, "KBDfans", white = R.drawable.logo_kbdfans_w, black = R.drawable.logo_kbdfans_b, aliases = arrayOf("Kbdfans", "Kbdfans")),
        logo(LogoIds.BAIONLENJA, "Baionlenja", white = R.drawable.logo_baionlenja_w, black = R.drawable.logo_baionlenja_b, aliases = arrayOf("Baionlenja")),
        logo(LogoIds.OWLAB, "Owlab", white = R.drawable.logo_owlab_w, black = R.drawable.logo_owlab_b, aliases = arrayOf("Owlab", "Owlab")),
        logo(LogoIds.JJW, "JJW", white = R.drawable.logo_jjw_w, black = R.drawable.logo_jjw_b, aliases = arrayOf("Jjw", "Jjw")),
        logo(LogoIds.MERISI, "Merisi", white = R.drawable.logo_merisi_w, black = R.drawable.logo_merisi_b, aliases = arrayOf("Merisi")),
        logo(LogoIds.ORION, "Orion", white = R.drawable.logo_orion_w, black = R.drawable.logo_orion_b, aliases = arrayOf("Orion")),
        logo(LogoIds.KALAM, "Kalam", default = R.drawable.logo_kalam, overlay = R.drawable.logo_kalam, aliases = arrayOf("Kalam")),
        logo(LogoIds.REALFORCE, "REALFORCE", white = R.drawable.logo_realforce_w, black = R.drawable.logo_realforce_b, aliases = arrayOf("Realforce", "Topre", "리얼포스", "토프레")),
        logo(LogoIds.BOWL, "Bowl Keyboards", white = R.drawable.logo_bowl_w, black = R.drawable.logo_bowl_b, aliases = arrayOf("Bowl", "볼키보드")),
        logo(LogoIds.CANNONKEYS, "CannonKeys", default = R.drawable.logo_cannonkeys, aliases = arrayOf("Cannon", "CK", "캐논키")),
        logo(LogoIds.GOK, "GOK Designs", white = R.drawable.logo_gok_w, black = R.drawable.logo_gok_b, aliases = arrayOf("GOK", "곡디자인")),
        logo(LogoIds.GRAYSTUDIO, "Graystudio", white = R.drawable.logo_graystudio_w, black = R.drawable.logo_graystudio_b, aliases = arrayOf("Gray", "그레이스튜디오")),
        logo(LogoIds.HHKB, "HHKB", default = R.drawable.logo_hhkb, aliases = arrayOf("Happy Hacking", "Topre", "토프레")),
        logo(LogoIds.MELETRIX, "Meletrix", white = R.drawable.logo_meletrix_w, black = R.drawable.logo_meletrix_b, aliases = arrayOf("멜레트릭스", "Zoom")),
        logo(LogoIds.NOVELKEYS, "NovelKeys", default = R.drawable.logo_novelkeys, aliases = arrayOf("NK", "노벨키")),
        logo(LogoIds.OMNITYPE, "Omnitype", default = R.drawable.logo_omnitype, aliases = arrayOf("옴니타입")),
        logo(LogoIds.PLEX, "Plexkbd", white = R.drawable.logo_plex_w, black = R.drawable.logo_plex_b, aliases = arrayOf("Plex", "플렉스")),
        logo(LogoIds.SENSY, "SENSY", white = R.drawable.logo_sensy_w, black = R.drawable.logo_sensy_b, aliases = arrayOf("센시")),
        logo(LogoIds.TYPFACE, "Typface", white = R.drawable.logo_typface_w, black = R.drawable.logo_typface_b, aliases = arrayOf("타입페이스")),
        logo(LogoIds.WUQUE, "Wuque Studio", white = R.drawable.logo_wuque_w, black = R.drawable.logo_wuque_b, aliases = arrayOf("WS", "Wuque", "우케", "우크")),
        logo(LogoIds.TKD, "TKD", white = R.drawable.logo_tkd_w, black = R.drawable.logo_tkd_b, aliases = arrayOf("TheKeyDotCo", "티케이디")),
        logo(LogoIds.FOX, "FOX", default = R.drawable.logo_fox, aliases = arrayOf("Fox")),
        logo(LogoIds.NEO, "Neo", white = R.drawable.logo_neo_w, black = R.drawable.logo_neo_b, aliases = arrayOf("Neo Studio", "Neo")),
        logo(LogoIds.IV_WORKS, "IV Works", white = R.drawable.logo_iv_works_w, black = R.drawable.logo_iv_works_b, aliases = arrayOf("IVWorks", "IV Works")),
        logo(LogoIds.NIUNIU, "NiuNiu", white = R.drawable.logo_niuniu_w, black = R.drawable.logo_niuniu_b, aliases = arrayOf("Niu Niu", "Niuniu")),
        logo(LogoIds.GRIT, "Grit", white = R.drawable.logo_grit_w, black = R.drawable.logo_grit_b, aliases = arrayOf("Grit")),
    ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    val vendors = (
        listOf(
        vendor(VendorIds.QWERTYKEYS, "Qwertykeys", LogoIds.QWERTYKEYS, "QK", "Neo Studio"),
        vendor(VendorIds.GEON, "Geonworks", LogoIds.GEON, "Geon"),
        vendor(VendorIds.MODE, "Mode", LogoIds.MODE, "Mode Designs"),
//        vendor(VendorIds.OWLAB, "Owlab", LogoIds.OWLAB, "OWL"),
        vendor(VendorIds.TGR, "TGR", LogoIds.TGR),
        vendor(VendorIds.KEYCULT, "Keycult", LogoIds.KEYCULT),
        vendor(VendorIds.SINGAKBD, "SingaKBD", LogoIds.SINGAKBD, "Singa"),
//        vendor(VendorIds.TKD, "TKD", LogoIds.TKD, "TheKeyDotCo"),
        vendor(VendorIds.MATRIX, "Matrix Lab", LogoIds.MATRIX, "Matrix"),
        vendor(VendorIds.SWAGKEY, "Swagkey", LogoIds.SWAGKEY, "Swagkey"),
        vendor(VendorIds.KLC, "Klc", LogoIds.KLC, "Klc"),
        vendor(VendorIds.SYRYAN, "Syryan", LogoIds.SYRYAN, "Syryan"),
        vendor(VendorIds.NEWONE, "Newone", LogoIds.NEWONE, "Newone"),
        vendor(VendorIds.LIN, "Lin", LogoIds.LIN, "Lin"),
        vendor(VendorIds.MACHINA, "Machina", LogoIds.MACHINA, "Machina"),
        vendor(VendorIds.NUXROS, "Nuxros", LogoIds.NUXROS, "Nuxros"),
        vendor(VendorIds.KBDFANS, "KBDfans", LogoIds.KBDFANS, "Kbdfans"),
        vendor(VendorIds.BAIONLENJA, "Baionlenja", LogoIds.BAIONLENJA, "Baionlenja"),
        vendor(VendorIds.OWLAB, "Owlab", LogoIds.OWLAB, "Owlab"),
        vendor(VendorIds.JJW, "JJW", LogoIds.JJW, "Jjw", "JJW Concepts"),
        vendor(VendorIds.MERISI, "Merisi", LogoIds.MERISI),
        vendor(VendorIds.ORION, "Orion", LogoIds.ORION),
        vendor(VendorIds.KALAM, "Kalam", LogoIds.KALAM),
        ) + GeneratedPresetData.vendors
        ).distinctBy { it.id }

    val plates = listOf(
        "Alu",
        "PP",
        "POM",
        "PC",
        "FR4",
        "Brass",
        "SUS",
        "Copper",
        "PEI",
        "CF",
        "Plateless",
    )

    val mounts = listOf(
        "Top-mount",
        "Bottom-mount",
        "Sandwich-mount",
        "O-ring-mount",
        "Gasket-mount",
        "Leaf Spring-mount",
        "Tadpole-mount",
    )

    val switches = (
        listOf(
        switch("hmx-hyacinth", "HMX Hyacinth", "HMX", "Hyacinth", "Hyacinth V2"),
        switch("hmx-xinhai", "HMX Xinhai", "HMX", "Xinhai"),
        switch("mx-black", "Cherry MX Black", "Cherry", "MX Black", "Hyperglide Black"),
        switch("mx-brown", "Cherry MX Brown", "Cherry", "MX Brown"),
        switch("oil-king", "Gateron Oil King", "Gateron", "Oil King"),
        switch("ink-black", "Gateron Ink Black", "Gateron", "Ink Black"),
        switch("ws-morandi", "WS Morandi", "Wuque Studio", "Morandi"),
        switch("akko-cream-yellow", "Akko V3 Cream Yellow", "Akko", "Cream Yellow"),
        switch("ttc-bluish-white", "TTC Bluish White", "TTC", "Bluish White"),
        switch("kailh-box-white", "Kailh Box White", "Kailh", "Box White"),
        switch("cream", "NovelKeys Cream", "NovelKeys", "NK Cream"),
        switch("bsun-raw", "BSUN Raw", "BSUN", "Raw"),
        ) + GeneratedPresetData.switches
        ).distinctBy { it.switchKey() }

    val housings = (
        listOf(
        housing(
            id = "neo65",
            name = "Neo65",
            vendorId = VendorIds.QWERTYKEYS,
            designer = "Neo Studio",
            aliases = listOf("Neo 65", "Neo"),
        ),
        housing(
            id = "mode-sonnet",
            name = "Mode Sonnet",
            vendorId = VendorIds.MODE,
            aliases = listOf("Sonnet"),
        ),
        housing(
            id = "f1-8x",
            name = "F1-8X",
            vendorId = VendorIds.GEON,
            aliases = listOf("F1 8X", "F1"),
        ),
        housing(
            id = "spring",
            name = "Spring",
            vendorId = VendorIds.OWLAB,
            aliases = listOf("Owlab Spring"),
        ),
        housing(
            id = "jane-v2-me",
            name = "Jane V2 ME",
            vendorId = VendorIds.TGR,
            aliases = listOf("Jane", "TGR Jane"),
        ),
        housing(
            id = "kohaku",
            name = "Kohaku",
            vendorId = VendorIds.SINGAKBD,
            aliases = listOf("Singa Kohaku"),
        ),
        housing(
            id = "keycult-no1",
            name = "No. 1",
            vendorId = VendorIds.KEYCULT,
            aliases = listOf("Keycult No. 1", "No1"),
        ),
        housing(
            id = "cycle7",
            name = "Cycle7",
            vendorId = VendorIds.TKD,
            aliases = listOf("Cycle 7"),
        ),
        housing(
            id = "matrix-corsa",
            name = "Matrix Corsa",
            vendorId = VendorIds.MATRIX,
            aliases = listOf("Corsa"),
        ),
        ) + GeneratedPresetData.housings
        ).map { housing ->
            if (housing.vendorId == VendorIds.QWERTYKEYS && housing.name.startsWith("Neo")) {
                housing.copy(logoId = LogoIds.NEO)
            } else {
                housing
            }
        }.distinctBy { it.housingKey() }

    val keycaps = (
        listOf(
        keycap("gmk-honor", "GMK Honor", "GMK", "Honor"),
        keycap("gmk-hammerhead", "GMK Hammerhead", "GMK", "Hammerhead"),
        keycap("gmk-botanical", "GMK Botanical", "GMK", "Botanical"),
        keycap("epbt-be-the-one", "ePBT Be The One", "ePBT", "Be The One"),
        keycap("crp-jipink", "CRP JiPink", "CRP", "JiPink"),
        keycap("mw-heresy", "MW Heresy", "Milkyway", "Heresy"),
        ) + GeneratedPresetData.keycaps
        ).distinctBy { it.keycapKey() }

    private fun SwitchPreset.switchKey(): String = "${manufacturer.orEmpty().presetKey()}|${name.presetKey()}"

    private fun HousingPreset.housingKey(): String = "${vendorId.orEmpty().presetKey()}|${name.presetKey()}"

    private fun KeycapPreset.keycapKey(): String = "${manufacturer.orEmpty().presetKey()}|${name.presetKey()}"

    private fun String.presetKey(): String = trim().lowercase().replace(Regex("[^a-z0-9가-힣]+"), "")

    private fun logo(
        id: String,
        name: String,
        default: Int? = null,
        white: Int? = null,
        black: Int? = null,
        overlay: Int? = null,
        aliases: Array<String> = emptyArray(),
        colorPolicy: LogoColorPolicy = LogoColorPolicy.MANUAL_LIGHT_DARK,
    ) = LogoPreset(
        id = id,
        name = name,
        drawableResId = default,
        whiteDrawableResId = white,
        blackDrawableResId = black,
        photoOverlayDrawableResId = overlay,
        aliases = aliases.toList(),
        colorPolicy = colorPolicy,
    )

    private fun vendor(
        id: String,
        name: String,
        logoId: String,
        vararg aliases: String,
    ) = VendorPreset(
        id = id,
        name = name,
        logoId = logoId,
        aliases = aliases.toList(),
    )

    private fun housing(
        id: String,
        name: String,
        vendorId: String,
        designer: String? = null,
        logoId: String? = null,
        aliases: List<String> = emptyList(),
    ): HousingPreset {
        val vendor = vendors.firstOrNull { it.id == vendorId }
        return HousingPreset(
            id = id,
            name = name,
            vendorId = vendorId,
            vendor = vendor?.name,
            designer = designer,
            logoId = logoId ?: vendor?.logoId,
            aliases = aliases,
        )
    }

    private fun switch(
        id: String,
        name: String,
        manufacturer: String,
        vararg aliases: String,
    ) = SwitchPreset(
        id = id,
        name = name,
        manufacturer = manufacturer,
        aliases = aliases.toList(),
    )

    private fun keycap(
        id: String,
        name: String,
        manufacturer: String,
        vararg aliases: String,
    ) = KeycapPreset(
        id = id,
        name = name,
        manufacturer = manufacturer,
        aliases = aliases.toList(),
    )
}
