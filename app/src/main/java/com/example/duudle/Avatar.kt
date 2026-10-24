package com.example.duudle

// The profile character: hair, skin, face, top, bottoms, shoes, item, glasses and backdrop.
// Pure Kotlin (no Android or Compose) so it can be unit tested on the JVM.
//
// Every `id` below is the storage contract (see AvatarConfig.encode): it's what lands in
// SharedPreferences. Never rename or reuse an id. To retire an option, keep its id
// reserved; to add one, add an entry with a new id.

/** The "Head" tab. Hair is always Ink black. */
enum class AvatarHair(val id: String, val label: String) {
    CURLY("curly", "Curly"),
    BOB("bob", "Bob"),
    PONYTAIL("ponytail", "Ponytail"),
    LONG_WAVY("long", "Long wavy"),
    SHORT("short", "Short"),
    BUN("bun", "Bun"),
}

enum class AvatarSkin(val id: String, val label: String, val color: Long) {
    PAPER("paper", "Paper white", 0xFFFFFFFF),
    CREAM("cream", "Cream", 0xFFFFF1E0),
    PEACH("peach", "Peach", 0xFFF9DACF),
    TAN("tan", "Tan", 0xFFE2B48C),
    BROWN("brown", "Brown", 0xFFB07A4F),
    DEEP("deep", "Deep", 0xFF6E4630),
}

/** The "Body" tab. */
enum class AvatarOutfit(val id: String, val label: String) {
    HOODIE("hoodie", "Hoodie"),
    COLLAR("collar", "Collared shirt"),
    TEE("tee", "T-shirt"),
    SWEATER("sweater", "Sweater"),
}

enum class AvatarOutfitColor(val id: String, val label: String, val color: Long) {
    PURPLE("purple", "Purple", 0xFF5E2BB8),
    GREEN("green", "Green", 0xFF2FBF5B),
    WHITE("white", "White", 0xFFFFFFFF),
    NAVY("navy", "Navy", 0xFF14215A),
    BLUE("blue", "Blue", 0xFF1F3FE0),
    BUTTER("butter", "Butter", 0xFFFBE0A0),
    SKY("sky", "Sky", 0xFFA9C8FF),
    CORAL("coral", "Coral", 0xFFFF6B66),
    BLACK("black", "Black", 0xFF2A2A2A),
}

/** Something sitting on (or sprouting from) the top of the head. */
enum class AvatarItem(val id: String, val label: String) {
    NONE("none", "No item"),
    CAT("cat", "Cat"),
    CALICO_CAT("calico", "Calico cat"),
    DUCK("duck", "Rubber duck"),
    FLOWER("flower", "Flower"),
    ANTENNAE("antennae", "Antennae"),
    PARTY_HAT("partyhat", "Party hat"),
}

enum class AvatarGlasses(val id: String, val label: String) {
    NONE("none", "No glasses"),
    ROUND("round", "Round glasses"),
    SQUARE("square", "Square glasses"),
    SUNGLASSES("sunglasses", "Sunglasses"),
}

enum class AvatarBackdrop(val id: String, val label: String, val color: Long) {
    SUNNY("sunny", "Sunny yellow", 0xFFFFC800),
    PURPLE("purple", "Purple", 0xFF8A1FD6),
    // Not Cream: that's the page colour, so the character card would vanish into it.
    WHITE("white", "White", 0xFFFFFFFF),
    PEACH("peach", "Peach", 0xFFFCE6D2),
    APRICOT("apricot", "Apricot", 0xFFFFC57A),
    MINT("mint", "Mint", 0xFFD9F8C4),
    SKY("sky", "Sky", 0xFFD3EBFF),
}

/** Trousers, shorts or skirt (storage key "bottom"). */
enum class AvatarBottom(val id: String, val label: String) {
    JEANS("jeans", "Jeans"),
    TROUSERS("trousers", "Trousers"),
    SHORTS("shorts", "Shorts"),
    SKIRT("skirt", "Skirt"),
    CARGO("cargo", "Cargo trousers"),
}

/** Storage key "bottomcolor". */
enum class AvatarBottomColor(val id: String, val label: String, val color: Long) {
    DENIM("denim", "Denim", 0xFF3B5BA5),
    LIGHT_DENIM("lightdenim", "Light denim", 0xFF8FB0DE),
    BLACK("black", "Black", 0xFF2A2A2A),
    KHAKI("khaki", "Khaki", 0xFFD8C08E),
    GREY("grey", "Grey", 0xFF9A9AA0),
    NAVY("navy", "Navy", 0xFF14215A),
    OLIVE("olive", "Olive", 0xFF6B7A3A),
    CORAL("coral", "Coral", 0xFFFF6B66),
}

/** Storage key "shoes". */
enum class AvatarShoes(val id: String, val label: String) {
    SNEAKERS("sneakers", "Sneakers"),
    HIGH_TOPS("hightops", "High-tops"),
    BOOTS("boots", "Boots"),
    LOAFERS("loafers", "Loafers"),
}

/** Storage key "shoecolor". */
enum class AvatarShoeColor(val id: String, val label: String, val color: Long) {
    WHITE("white", "White", 0xFFFFFFFF),
    BLACK("black", "Black", 0xFF2A2A2A),
    RED("red", "Red", 0xFFE53935),
    YELLOW("yellow", "Yellow", 0xFFFFC83A),
    BROWN("brown", "Brown", 0xFF8B5A2B),
    BLUE("blue", "Blue", 0xFF1F3FE0),
}

/** Storage key "eyes". DOTS is the original look. */
enum class AvatarEyes(val id: String, val label: String) {
    DOTS("dots", "Dot eyes"),
    HAPPY("happy", "Happy eyes"),
    SLEEPY("sleepy", "Sleepy eyes"),
    WINK("wink", "Winking"),
    WIDE("wide", "Wide eyes"),
}

/** Storage key "cheeks". */
enum class AvatarCheeks(val id: String, val label: String) {
    NONE("none", "No cheeks"),
    BLUSH("blush", "Blush"),
    FRECKLES("freckles", "Freckles"),
}

/**
 * The look. Fields added after the first release go at the END with defaults, so saves
 * written before them decode to the old values plus these defaults.
 */
data class AvatarConfig(
    val hair: AvatarHair,
    val skin: AvatarSkin,
    val outfit: AvatarOutfit,
    val outfitColor: AvatarOutfitColor,
    val item: AvatarItem,
    val glasses: AvatarGlasses,
    val backdrop: AvatarBackdrop,
    val bottom: AvatarBottom = AvatarBottom.JEANS,
    val bottomColor: AvatarBottomColor = AvatarBottomColor.DENIM,
    val shoes: AvatarShoes = AvatarShoes.SNEAKERS,
    val shoeColor: AvatarShoeColor = AvatarShoeColor.WHITE,
    val eyes: AvatarEyes = AvatarEyes.DOTS,
    val cheeks: AvatarCheeks = AvatarCheeks.NONE,
) {
    companion object {
        /** Curly hair, purple hoodie, denim jeans, white sneakers and a cat on the head, on sunny yellow. */
        val DEFAULT = AvatarConfig(
            hair = AvatarHair.CURLY,
            skin = AvatarSkin.PEACH,
            outfit = AvatarOutfit.HOODIE,
            outfitColor = AvatarOutfitColor.PURPLE,
            item = AvatarItem.CAT,
            glasses = AvatarGlasses.NONE,
            backdrop = AvatarBackdrop.SUNNY,
        )
    }
}

/**
 * A named set of the six clothing fields. Presets are code, never stored: applying one
 * just writes those fields into the config.
 */
data class AvatarOutfitPreset(
    val id: String,
    val label: String,
    val top: AvatarOutfit,
    val topColor: AvatarOutfitColor,
    val bottom: AvatarBottom,
    val bottomColor: AvatarBottomColor,
    val shoes: AvatarShoes,
    val shoeColor: AvatarShoeColor,
)

val AvatarOutfitPresets: List<AvatarOutfitPreset> = listOf(
    AvatarOutfitPreset(
        "cosy", "Cosy", AvatarOutfit.HOODIE, AvatarOutfitColor.PURPLE,
        AvatarBottom.JEANS, AvatarBottomColor.DENIM, AvatarShoes.SNEAKERS, AvatarShoeColor.WHITE
    ),
    AvatarOutfitPreset(
        "smart", "Smart", AvatarOutfit.COLLAR, AvatarOutfitColor.WHITE,
        AvatarBottom.TROUSERS, AvatarBottomColor.NAVY, AvatarShoes.LOAFERS, AvatarShoeColor.BROWN
    ),
    AvatarOutfitPreset(
        "weekend", "Weekend", AvatarOutfit.TEE, AvatarOutfitColor.GREEN,
        AvatarBottom.SHORTS, AvatarBottomColor.KHAKI, AvatarShoes.SNEAKERS, AvatarShoeColor.RED
    ),
    AvatarOutfitPreset(
        "bookish", "Bookish", AvatarOutfit.SWEATER, AvatarOutfitColor.BUTTER,
        AvatarBottom.TROUSERS, AvatarBottomColor.KHAKI, AvatarShoes.BOOTS, AvatarShoeColor.BROWN
    ),
    AvatarOutfitPreset(
        "street", "Street", AvatarOutfit.HOODIE, AvatarOutfitColor.BLACK,
        AvatarBottom.CARGO, AvatarBottomColor.OLIVE, AvatarShoes.HIGH_TOPS, AvatarShoeColor.WHITE
    ),
    AvatarOutfitPreset(
        "sunny", "Sunny", AvatarOutfit.TEE, AvatarOutfitColor.CORAL,
        AvatarBottom.SKIRT, AvatarBottomColor.LIGHT_DENIM, AvatarShoes.SNEAKERS, AvatarShoeColor.YELLOW
    ),
    AvatarOutfitPreset(
        "chill", "Chill", AvatarOutfit.SWEATER, AvatarOutfitColor.SKY,
        AvatarBottom.JEANS, AvatarBottomColor.LIGHT_DENIM, AvatarShoes.HIGH_TOPS, AvatarShoeColor.BLUE
    ),
)

/** The same look wearing [preset]: only the six clothing fields change. */
fun AvatarConfig.withOutfit(preset: AvatarOutfitPreset): AvatarConfig = copy(
    outfit = preset.top,
    outfitColor = preset.topColor,
    bottom = preset.bottom,
    bottomColor = preset.bottomColor,
    shoes = preset.shoes,
    shoeColor = preset.shoeColor,
)

/** True when all six clothing fields are exactly [preset]'s. */
fun AvatarConfig.matches(preset: AvatarOutfitPreset): Boolean =
    outfit == preset.top && outfitColor == preset.topColor &&
        bottom == preset.bottom && bottomColor == preset.bottomColor &&
        shoes == preset.shoes && shoeColor == preset.shoeColor

/**
 * Payload version, the first field of [encode]'s string. Additive changes (new ids, new
 * keys) keep v1; bump only for a breaking change. The prefs key prefix (avatar_v1_) never
 * changes; the payload prefix is the version.
 */
private const val AVATAR_FORMAT_VERSION = "v1"

/**
 * e.g. "v1;hair=curly;skin=peach;outfit=hoodie;color=purple;item=cat;glasses=none;bg=sunny;
 * bottom=jeans;bottomcolor=denim;shoes=sneakers;shoecolor=white;eyes=dots;cheeks=none".
 * Keys added later go at the end.
 */
fun AvatarConfig.encode(): String =
    "$AVATAR_FORMAT_VERSION;hair=${hair.id};skin=${skin.id};outfit=${outfit.id};color=${outfitColor.id};" +
        "item=${item.id};glasses=${glasses.id};bg=${backdrop.id};" +
        "bottom=${bottom.id};bottomcolor=${bottomColor.id};shoes=${shoes.id};shoecolor=${shoeColor.id};" +
        "eyes=${eyes.id};cheeks=${cheeks.id}"

/**
 * Reads [encode]'s format. Never throws: null, blank or unreadable input gives
 * [AvatarConfig.DEFAULT]; an unknown id for one key falls back to that key's default and
 * keeps the rest; unknown keys are ignored. So a save made by a newer build with an added
 * key or id still loads here, with only the unknown parts reset to their defaults.
 */
fun decodeAvatarConfig(raw: String?): AvatarConfig {
    val default = AvatarConfig.DEFAULT
    if (raw.isNullOrBlank()) return default
    val parts = raw.trim().split(';')
    if (parts.first().trim() != AVATAR_FORMAT_VERSION) return default
    val values = HashMap<String, String>()
    for (part in parts.drop(1)) {
        val eq = part.indexOf('=')
        if (eq <= 0) continue
        values[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
    }
    return AvatarConfig(
        hair = AvatarHair.entries.firstOrNull { it.id == values["hair"] } ?: default.hair,
        skin = AvatarSkin.entries.firstOrNull { it.id == values["skin"] } ?: default.skin,
        outfit = AvatarOutfit.entries.firstOrNull { it.id == values["outfit"] } ?: default.outfit,
        outfitColor = AvatarOutfitColor.entries.firstOrNull { it.id == values["color"] } ?: default.outfitColor,
        item = AvatarItem.entries.firstOrNull { it.id == values["item"] } ?: default.item,
        glasses = AvatarGlasses.entries.firstOrNull { it.id == values["glasses"] } ?: default.glasses,
        backdrop = AvatarBackdrop.entries.firstOrNull { it.id == values["bg"] } ?: default.backdrop,
        bottom = AvatarBottom.entries.firstOrNull { it.id == values["bottom"] } ?: default.bottom,
        bottomColor = AvatarBottomColor.entries.firstOrNull { it.id == values["bottomcolor"] } ?: default.bottomColor,
        shoes = AvatarShoes.entries.firstOrNull { it.id == values["shoes"] } ?: default.shoes,
        shoeColor = AvatarShoeColor.entries.firstOrNull { it.id == values["shoecolor"] } ?: default.shoeColor,
        eyes = AvatarEyes.entries.firstOrNull { it.id == values["eyes"] } ?: default.eyes,
        cheeks = AvatarCheeks.entries.firstOrNull { it.id == values["cheeks"] } ?: default.cheeks,
    )
}

/**
 * For TalkBack, e.g. "curly hair, peach skin, purple hoodie, denim jeans, white sneakers,
 * cat, no glasses, sunny yellow background". Eyes and cheeks are mentioned only when they
 * aren't the plain default ("..., peach skin, happy eyes, blush, ...").
 */
fun AvatarConfig.describe(): String = buildString {
    append(hair.label.lowercase()).append(" hair, ")
    append(skin.label.lowercase()).append(" skin, ")
    if (eyes != AvatarEyes.DOTS) append(eyes.label.lowercase()).append(", ")
    if (cheeks != AvatarCheeks.NONE) append(cheeks.label.lowercase()).append(", ")
    append(outfitColor.label.lowercase()).append(' ').append(outfit.label.lowercase()).append(", ")
    append(bottomColor.label.lowercase()).append(' ').append(bottom.label.lowercase()).append(", ")
    append(shoeColor.label.lowercase()).append(' ').append(shoes.label.lowercase()).append(", ")
    append(item.label.lowercase()).append(", ")
    append(glasses.label.lowercase()).append(", ")
    append(backdrop.label.lowercase()).append(" background")
}
