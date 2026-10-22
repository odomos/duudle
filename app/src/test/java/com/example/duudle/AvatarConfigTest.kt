package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarConfigTest {
    private val default = AvatarConfig.DEFAULT

    @Test
    fun defaultEncodesToTheDocumentedString() {
        assertEquals(
            "v1;hair=curly;skin=peach;outfit=hoodie;color=purple;item=cat;glasses=none;bg=sunny",
            default.encode(),
        )
    }

    @Test
    fun everyHairRoundTrips() {
        for (v in AvatarHair.entries) assertRoundTrip(default.copy(hair = v))
    }

    @Test
    fun everySkinRoundTrips() {
        for (v in AvatarSkin.entries) assertRoundTrip(default.copy(skin = v))
    }

    @Test
    fun everyOutfitRoundTrips() {
        for (v in AvatarOutfit.entries) assertRoundTrip(default.copy(outfit = v))
    }

    @Test
    fun everyOutfitColorRoundTrips() {
        for (v in AvatarOutfitColor.entries) assertRoundTrip(default.copy(outfitColor = v))
    }

    @Test
    fun everyItemRoundTrips() {
        for (v in AvatarItem.entries) assertRoundTrip(default.copy(item = v))
    }

    @Test
    fun everyGlassesRoundTrips() {
        for (v in AvatarGlasses.entries) assertRoundTrip(default.copy(glasses = v))
    }

    @Test
    fun everyBackdropRoundTrips() {
        for (v in AvatarBackdrop.entries) assertRoundTrip(default.copy(backdrop = v))
    }

    @Test
    fun everyCombinationRoundTrips() {
        var count = 0
        for (h in AvatarHair.entries) for (s in AvatarSkin.entries) for (o in AvatarOutfit.entries)
            for (c in AvatarOutfitColor.entries) for (i in AvatarItem.entries)
                for (g in AvatarGlasses.entries) for (b in AvatarBackdrop.entries) {
                    assertRoundTrip(AvatarConfig(h, s, o, c, i, g, b))
                    count++
                }
        assertEquals(
            AvatarHair.entries.size * AvatarSkin.entries.size * AvatarOutfit.entries.size *
                AvatarOutfitColor.entries.size * AvatarItem.entries.size *
                AvatarGlasses.entries.size * AvatarBackdrop.entries.size,
            count,
        )
    }

    @Test
    fun unreadableInputGivesDefaultWithoutThrowing() {
        val bad = listOf(
            null, "", "   ", "\n", "garbage", "v2;hair=bob", "=;==", "v1", "v1;", "v1;=;==",
            ";;;", "V1;hair=bob", "hair=bob;v1", "v1;hair", "v1;hair=;skin=",
        )
        for (raw in bad) assertEquals("input: $raw", default, decodeAvatarConfig(raw))
    }

    @Test
    fun wrongVersionIgnoresOtherwiseValidValues() {
        val v2 = default.copy(hair = AvatarHair.BOB, glasses = AvatarGlasses.ROUND).encode().replaceFirst("v1", "v2")
        assertEquals(default, decodeAvatarConfig(v2))
    }

    @Test
    fun oneUnknownIdFallsBackForThatKeyOnly() {
        val raw = "v1;hair=mohawk;skin=deep;outfit=tee;color=white;item=calico;glasses=square;bg=white"
        assertEquals(
            AvatarConfig(
                hair = AvatarHair.CURLY, // unknown -> default
                skin = AvatarSkin.DEEP,
                outfit = AvatarOutfit.TEE,
                outfitColor = AvatarOutfitColor.WHITE,
                item = AvatarItem.CALICO_CAT,
                glasses = AvatarGlasses.SQUARE,
                backdrop = AvatarBackdrop.WHITE,
            ),
            decodeAvatarConfig(raw),
        )
    }

    @Test
    fun unknownIdForEachKeyFallsBackIndependently() {
        val custom = AvatarConfig(
            AvatarHair.BUN, AvatarSkin.TAN, AvatarOutfit.SWEATER, AvatarOutfitColor.CORAL,
            AvatarItem.DUCK, AvatarGlasses.SUNGLASSES, AvatarBackdrop.MINT,
        )
        val keys = listOf("hair", "skin", "outfit", "color", "item", "glasses", "bg")
        for (key in keys) {
            val raw = custom.encode().split(';').joinToString(";") {
                if (it.startsWith("$key=")) "$key=zzz" else it
            }
            val expected = when (key) {
                "hair" -> custom.copy(hair = default.hair)
                "skin" -> custom.copy(skin = default.skin)
                "outfit" -> custom.copy(outfit = default.outfit)
                "color" -> custom.copy(outfitColor = default.outfitColor)
                "item" -> custom.copy(item = default.item)
                "glasses" -> custom.copy(glasses = default.glasses)
                else -> custom.copy(backdrop = default.backdrop)
            }
            assertEquals("unknown $key", expected, decodeAvatarConfig(raw))
        }
    }

    @Test
    fun missingKeysFallBackAndPresentKeysAreKept() {
        assertEquals(
            default.copy(hair = AvatarHair.BOB, glasses = AvatarGlasses.ROUND),
            decodeAvatarConfig("v1;glasses=round;hair=bob"),
        )
    }

    @Test
    fun unknownExtraKeyIsIgnored() {
        val custom = default.copy(hair = AvatarHair.LONG_WAVY, item = AvatarItem.CALICO_CAT)
        assertEquals(custom, decodeAvatarConfig(custom.encode() + ";shoes=boots;hat=top"))
        assertEquals(custom, decodeAvatarConfig("v1;mood=happy;" + custom.encode().removePrefix("v1;")))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        val custom = default.copy(skin = AvatarSkin.BROWN)
        assertEquals(custom, decodeAvatarConfig("  " + custom.encode() + "\n"))
    }

    @Test
    fun idsAreUniqueWithinEachEnum() {
        assertUnique("hair", AvatarHair.entries.map { it.id })
        assertUnique("skin", AvatarSkin.entries.map { it.id })
        assertUnique("outfit", AvatarOutfit.entries.map { it.id })
        assertUnique("color", AvatarOutfitColor.entries.map { it.id })
        assertUnique("item", AvatarItem.entries.map { it.id })
        assertUnique("glasses", AvatarGlasses.entries.map { it.id })
        assertUnique("bg", AvatarBackdrop.entries.map { it.id })
    }

    @Test
    fun idsContainNoSeparators() {
        val all = AvatarHair.entries.map { it.id } + AvatarSkin.entries.map { it.id } +
            AvatarOutfit.entries.map { it.id } + AvatarOutfitColor.entries.map { it.id } +
            AvatarItem.entries.map { it.id } + AvatarGlasses.entries.map { it.id } +
            AvatarBackdrop.entries.map { it.id }
        for (id in all) {
            assertEquals("id '$id' must be non-blank and free of ';' '=' and spaces", true,
                id.isNotBlank() && ';' !in id && '=' !in id && id.trim() == id)
        }
    }

    @Test
    fun describeReadsNaturally() {
        assertEquals(
            "curly hair, peach skin, purple hoodie, cat, no glasses, sunny yellow background",
            default.describe(),
        )
    }

    private fun assertRoundTrip(config: AvatarConfig) {
        val s = config.encode()
        assertEquals(s, config, decodeAvatarConfig(s))
    }

    private fun assertUnique(name: String, ids: List<String>) {
        assertEquals("duplicate $name ids: $ids", ids.size, ids.toSet().size)
    }
}
