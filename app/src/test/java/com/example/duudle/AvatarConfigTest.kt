package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AvatarConfigTest {
    private val default = AvatarConfig.DEFAULT

    /** One entry per stored field: its key and one setter per enum value. */
    private class Field(val key: String, val setters: List<(AvatarConfig) -> AvatarConfig>)

    private val fields = listOf(
        Field("hair", AvatarHair.entries.map { v -> { c: AvatarConfig -> c.copy(hair = v) } }),
        Field("skin", AvatarSkin.entries.map { v -> { c: AvatarConfig -> c.copy(skin = v) } }),
        Field("outfit", AvatarOutfit.entries.map { v -> { c: AvatarConfig -> c.copy(outfit = v) } }),
        Field("color", AvatarOutfitColor.entries.map { v -> { c: AvatarConfig -> c.copy(outfitColor = v) } }),
        Field("item", AvatarItem.entries.map { v -> { c: AvatarConfig -> c.copy(item = v) } }),
        Field("glasses", AvatarGlasses.entries.map { v -> { c: AvatarConfig -> c.copy(glasses = v) } }),
        Field("bg", AvatarBackdrop.entries.map { v -> { c: AvatarConfig -> c.copy(backdrop = v) } }),
        Field("bottom", AvatarBottom.entries.map { v -> { c: AvatarConfig -> c.copy(bottom = v) } }),
        Field("bottomcolor", AvatarBottomColor.entries.map { v -> { c: AvatarConfig -> c.copy(bottomColor = v) } }),
        Field("shoes", AvatarShoes.entries.map { v -> { c: AvatarConfig -> c.copy(shoes = v) } }),
        Field("shoecolor", AvatarShoeColor.entries.map { v -> { c: AvatarConfig -> c.copy(shoeColor = v) } }),
        Field("eyes", AvatarEyes.entries.map { v -> { c: AvatarConfig -> c.copy(eyes = v) } }),
        Field("cheeks", AvatarCheeks.entries.map { v -> { c: AvatarConfig -> c.copy(cheeks = v) } }),
    )

    private val allKeys = fields.map { it.key }

    @Test
    fun defaultEncodesToTheDocumentedString() {
        assertEquals(
            "v1;hair=curly;skin=peach;outfit=hoodie;color=purple;item=cat;glasses=none;bg=sunny;" +
                "bottom=jeans;bottomcolor=denim;shoes=sneakers;shoecolor=white;eyes=dots;cheeks=none",
            default.encode(),
        )
    }

    @Test
    fun encodeWritesEveryKeyOnceInOrder() {
        val keys = default.encode().split(';').drop(1).map { it.substringBefore('=') }
        assertEquals(allKeys, keys)
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
    fun everyBottomRoundTrips() {
        for (v in AvatarBottom.entries) assertRoundTrip(default.copy(bottom = v))
    }

    @Test
    fun everyBottomColorRoundTrips() {
        for (v in AvatarBottomColor.entries) assertRoundTrip(default.copy(bottomColor = v))
    }

    @Test
    fun everyShoesRoundTrips() {
        for (v in AvatarShoes.entries) assertRoundTrip(default.copy(shoes = v))
    }

    @Test
    fun everyShoeColorRoundTrips() {
        for (v in AvatarShoeColor.entries) assertRoundTrip(default.copy(shoeColor = v))
    }

    @Test
    fun everyEyesRoundTrips() {
        for (v in AvatarEyes.entries) assertRoundTrip(default.copy(eyes = v))
    }

    @Test
    fun everyCheeksRoundTrips() {
        for (v in AvatarCheeks.entries) assertRoundTrip(default.copy(cheeks = v))
    }

    /** Every value of every field paired with every value of every other field (rest default). */
    @Test
    fun everyPairOfFieldValuesRoundTrips() {
        var count = 0
        for (i in fields.indices) for (j in i + 1 until fields.size) {
            for (a in fields[i].setters) for (b in fields[j].setters) {
                assertRoundTrip(b(a(default)))
                count++
            }
        }
        val expected = fields.indices.sumOf { i ->
            (i + 1 until fields.size).sumOf { j -> fields[i].setters.size * fields[j].setters.size }
        }
        assertEquals(expected, count)
    }

    /** Full combinations are too many to enumerate, so check a fixed random sample of them. */
    @Test
    fun sampledFullCombinationsRoundTrip() {
        val random = Random(20261023L)
        repeat(5000) {
            var config = default
            for (field in fields) config = field.setters[random.nextInt(field.setters.size)](config)
            assertRoundTrip(config)
        }
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
    fun oldSaveWithoutTheNewKeysKeepsItsValuesAndGetsNewDefaults() {
        // Written by the first release, before bottoms, shoes and faces existed.
        val raw = "v1;hair=bob;skin=deep;outfit=tee;color=green;item=duck;glasses=round;bg=mint"
        assertEquals(
            AvatarConfig(
                hair = AvatarHair.BOB,
                skin = AvatarSkin.DEEP,
                outfit = AvatarOutfit.TEE,
                outfitColor = AvatarOutfitColor.GREEN,
                item = AvatarItem.DUCK,
                glasses = AvatarGlasses.ROUND,
                backdrop = AvatarBackdrop.MINT,
                bottom = AvatarBottom.JEANS,
                bottomColor = AvatarBottomColor.DENIM,
                shoes = AvatarShoes.SNEAKERS,
                shoeColor = AvatarShoeColor.WHITE,
                eyes = AvatarEyes.DOTS,
                cheeks = AvatarCheeks.NONE,
            ),
            decodeAvatarConfig(raw),
        )
    }

    @Test
    fun oneUnknownIdFallsBackForThatKeyOnly() {
        val raw = "v1;hair=mohawk;skin=deep;outfit=tee;color=white;item=calico;glasses=square;bg=white;" +
            "bottom=skirt;bottomcolor=olive;shoes=boots;shoecolor=red;eyes=wink;cheeks=blush"
        assertEquals(
            AvatarConfig(
                hair = AvatarHair.CURLY, // unknown -> default
                skin = AvatarSkin.DEEP,
                outfit = AvatarOutfit.TEE,
                outfitColor = AvatarOutfitColor.WHITE,
                item = AvatarItem.CALICO_CAT,
                glasses = AvatarGlasses.SQUARE,
                backdrop = AvatarBackdrop.WHITE,
                bottom = AvatarBottom.SKIRT,
                bottomColor = AvatarBottomColor.OLIVE,
                shoes = AvatarShoes.BOOTS,
                shoeColor = AvatarShoeColor.RED,
                eyes = AvatarEyes.WINK,
                cheeks = AvatarCheeks.BLUSH,
            ),
            decodeAvatarConfig(raw),
        )
    }

    @Test
    fun unknownIdForEachKeyFallsBackIndependently() {
        val custom = AvatarConfig(
            AvatarHair.BUN, AvatarSkin.TAN, AvatarOutfit.SWEATER, AvatarOutfitColor.CORAL,
            AvatarItem.DUCK, AvatarGlasses.SUNGLASSES, AvatarBackdrop.MINT,
            AvatarBottom.CARGO, AvatarBottomColor.KHAKI, AvatarShoes.LOAFERS, AvatarShoeColor.BROWN,
            AvatarEyes.SLEEPY, AvatarCheeks.FRECKLES,
        )
        for (key in allKeys) {
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
                "bg" -> custom.copy(backdrop = default.backdrop)
                "bottom" -> custom.copy(bottom = default.bottom)
                "bottomcolor" -> custom.copy(bottomColor = default.bottomColor)
                "shoes" -> custom.copy(shoes = default.shoes)
                "shoecolor" -> custom.copy(shoeColor = default.shoeColor)
                "eyes" -> custom.copy(eyes = default.eyes)
                "cheeks" -> custom.copy(cheeks = default.cheeks)
                else -> error("untested key $key")
            }
            assertEquals("unknown $key", expected, decodeAvatarConfig(raw))
        }
    }

    @Test
    fun missingKeysFallBackAndPresentKeysAreKept() {
        assertEquals(
            default.copy(hair = AvatarHair.BOB, glasses = AvatarGlasses.ROUND, shoes = AvatarShoes.BOOTS),
            decodeAvatarConfig("v1;glasses=round;shoes=boots;hair=bob"),
        )
    }

    @Test
    fun unknownExtraKeyIsIgnored() {
        val custom = default.copy(hair = AvatarHair.LONG_WAVY, item = AvatarItem.CALICO_CAT)
        assertEquals(custom, decodeAvatarConfig(custom.encode() + ";gloves=wool;hat=top"))
        assertEquals(custom, decodeAvatarConfig("v1;mood=happy;" + custom.encode().removePrefix("v1;")))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        val custom = default.copy(skin = AvatarSkin.BROWN, bottom = AvatarBottom.SHORTS)
        assertEquals(custom, decodeAvatarConfig("  " + custom.encode() + "\n"))
    }

    @Test
    fun keysAreUnique() {
        assertUnique("key", allKeys)
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
        assertUnique("bottom", AvatarBottom.entries.map { it.id })
        assertUnique("bottomcolor", AvatarBottomColor.entries.map { it.id })
        assertUnique("shoes", AvatarShoes.entries.map { it.id })
        assertUnique("shoecolor", AvatarShoeColor.entries.map { it.id })
        assertUnique("eyes", AvatarEyes.entries.map { it.id })
        assertUnique("cheeks", AvatarCheeks.entries.map { it.id })
    }

    @Test
    fun idsContainNoSeparators() {
        val all = AvatarHair.entries.map { it.id } + AvatarSkin.entries.map { it.id } +
            AvatarOutfit.entries.map { it.id } + AvatarOutfitColor.entries.map { it.id } +
            AvatarItem.entries.map { it.id } + AvatarGlasses.entries.map { it.id } +
            AvatarBackdrop.entries.map { it.id } + AvatarBottom.entries.map { it.id } +
            AvatarBottomColor.entries.map { it.id } + AvatarShoes.entries.map { it.id } +
            AvatarShoeColor.entries.map { it.id } + AvatarEyes.entries.map { it.id } +
            AvatarCheeks.entries.map { it.id }
        for (id in all + allKeys) {
            // '|' is the editor state's separator (CharacterEditor.kt), so ids can't use it either.
            assertEquals("id '$id' must be non-blank and free of ';' '=' '|' and spaces", true,
                id.isNotBlank() && ';' !in id && '=' !in id && '|' !in id && id.trim() == id)
        }
    }

    @Test
    fun withOutfitChangesOnlyTheSixClothingFields() {
        val base = default.copy(
            hair = AvatarHair.BUN, skin = AvatarSkin.DEEP, item = AvatarItem.FLOWER,
            glasses = AvatarGlasses.ROUND, backdrop = AvatarBackdrop.MINT,
            eyes = AvatarEyes.HAPPY, cheeks = AvatarCheeks.FRECKLES,
        )
        for (preset in AvatarOutfitPresets) {
            val dressed = base.withOutfit(preset)
            assertEquals(
                base.copy(
                    outfit = preset.top, outfitColor = preset.topColor,
                    bottom = preset.bottom, bottomColor = preset.bottomColor,
                    shoes = preset.shoes, shoeColor = preset.shoeColor,
                ),
                dressed,
            )
            assertTrue("matches ${preset.id}", dressed.matches(preset))
        }
    }

    @Test
    fun presetsAreDistinctAndMatchOnlyThemselves() {
        assertUnique("preset", AvatarOutfitPresets.map { it.id })
        for (a in AvatarOutfitPresets) for (b in AvatarOutfitPresets) {
            assertEquals("${a.id} vs ${b.id}", a == b, default.withOutfit(a).matches(b))
        }
    }

    @Test
    fun describeReadsNaturally() {
        assertEquals(
            "curly hair, peach skin, purple hoodie, denim jeans, white sneakers, cat, no glasses, sunny yellow background",
            default.describe(),
        )
    }

    @Test
    fun describeMentionsBottomsShoesAndANonDefaultFace() {
        val look = default.copy(
            bottom = AvatarBottom.SKIRT, bottomColor = AvatarBottomColor.LIGHT_DENIM,
            shoes = AvatarShoes.HIGH_TOPS, shoeColor = AvatarShoeColor.RED,
            eyes = AvatarEyes.HAPPY, cheeks = AvatarCheeks.BLUSH,
        )
        val text = look.describe()
        assertTrue(text, "light denim skirt" in text)
        assertTrue(text, "red high-tops" in text)
        assertTrue(text, "happy eyes" in text)
        assertTrue(text, "blush" in text)
    }

    private fun assertRoundTrip(config: AvatarConfig) {
        val s = config.encode()
        assertEquals(s, config, decodeAvatarConfig(s))
    }

    private fun assertUnique(name: String, ids: List<String>) {
        assertEquals("duplicate $name ids: $ids", ids.size, ids.toSet().size)
    }
}
