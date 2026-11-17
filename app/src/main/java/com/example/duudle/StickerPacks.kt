package com.example.duudle

// Every sticker pack this build knows. How to add a pack: stickers/README.md.

/**
 * Every pack this build knows. The sticker ids are a STABLE storage and push-payload contract
 * (they're saved in Firestore doodle docs, FCM pushes and snooze files): never rename or
 * reuse one. The rules accept `card10` to `card99` (two digits), so take the next free number;
 * past 99 the rules must be loosened first (staging, then the owner's OK for live).
 * To retire a sticker, drop it from its pack but keep its id reserved forever
 * ([RetiredFlashCardIds]). Labels, keywords and pack titles are display-only and can change.
 */
val StickerPacks: List<StickerPack> = listOf(
    StickerPack(
        id = "everyday",
        title = "Everyday",
        stickers = listOf(
            FlashCard("card10", R.drawable.sticker_everyday_hi, "Friendly hello face",
                listOf("hi", "hello", "hey", "wave", "friendly"), StickerMotion.BOUNCE),
            FlashCard("card11", R.drawable.sticker_everyday_haha, "Laughing face",
                listOf("haha", "lol", "laugh", "funny", "joke"), StickerMotion.WOBBLE),
            FlashCard("card12", R.drawable.sticker_everyday_wink, "Winking face",
                listOf("wink", "cheeky", "playful", "tease", "joke")),
            FlashCard("card13", R.drawable.sticker_everyday_shocked, "Shocked face",
                listOf("shocked", "wow", "omg", "surprised", "gasp")),
            FlashCard("card14", R.drawable.sticker_everyday_sleepy, "Sleepy face",
                listOf("sleepy", "tired", "sleep", "night", "zzz", "bored")),
            FlashCard("card15", R.drawable.sticker_everyday_love, "Heart-eyes face",
                listOf("love", "heart", "like", "crush", "cute", "aww"), StickerMotion.PULSE),
            FlashCard("card16", R.drawable.sticker_everyday_grr, "Grumpy face",
                listOf("grr", "angry", "mad", "grumpy", "annoyed")),
            FlashCard("card17", R.drawable.sticker_everyday_sad, "Crying face",
                listOf("sad", "cry", "tears", "sorry", "upset"))
        )
    ),
    StickerPack(
        id = "cats",
        title = "Cats",
        stickers = listOf(
            FlashCard("card18", R.drawable.sticker_cats_grin, "Cat with a huge toothy grin",
                listOf("cat", "kitty", "grin", "teeth", "smile", "cheeky", "happy"), StickerMotion.BOUNCE),
            FlashCard("card19", R.drawable.sticker_cats_cackle, "Cat laughing so hard it cries",
                listOf("cat", "kitty", "lol", "laugh", "cackle", "haha", "hilarious", "tears"), StickerMotion.WOBBLE),
            FlashCard("card20", R.drawable.sticker_cats_shook, "Shocked cat with huge eyes",
                listOf("cat", "kitty", "shook", "shocked", "wow", "omg", "scared", "surprised")),
            FlashCard("card21", R.drawable.sticker_cats_smug, "Smug cat with half-closed eyes",
                listOf("cat", "kitty", "smug", "sassy", "judging", "unimpressed", "smirk", "cool")),
            FlashCard("card22", R.drawable.sticker_cats_nap, "Cat napping with a snot bubble",
                listOf("cat", "kitty", "nap", "sleepy", "sleep", "tired", "zzz", "night")),
            FlashCard("card23", R.drawable.sticker_cats_love, "Cat with heart eyes",
                listOf("cat", "kitty", "love", "heart", "cute", "aww", "purr"), StickerMotion.PULSE),
            FlashCard("card24", R.drawable.sticker_cats_yum, "Hungry cat licking its lips",
                listOf("cat", "kitty", "yum", "hungry", "tasty", "lick", "food", "treat")),
            FlashCard("card25", R.drawable.sticker_cats_hiss, "Angry cat hissing with fangs",
                listOf("cat", "kitty", "hiss", "angry", "mad", "grumpy", "fangs"))
        )
    ),
    StickerPack(
        id = "dogs",
        title = "Dogs",
        stickers = listOf(
            FlashCard("card26", R.drawable.sticker_dogs_pant, "Happy dog panting with its tongue out",
                listOf("dog", "puppy", "doggo", "happy", "pant", "wag", "excited", "good boy"), StickerMotion.BOUNCE),
            FlashCard("card27", R.drawable.sticker_dogs_derp, "Goofy dog with crossed eyes",
                listOf("dog", "puppy", "doggo", "derp", "silly", "goofy", "blep", "tongue"), StickerMotion.WOBBLE),
            FlashCard("card28", R.drawable.sticker_dogs_tilt, "Confused dog tilting its head",
                listOf("dog", "puppy", "doggo", "confused", "what", "huh", "curious", "question")),
            FlashCard("card29", R.drawable.sticker_dogs_puppy, "Puppy with huge begging eyes",
                listOf("dog", "puppy", "doggo", "please", "beg", "sorry", "sad", "puppy eyes", "cute"), StickerMotion.PULSE),
            FlashCard("card30", R.drawable.sticker_dogs_love, "Dog with heart eyes",
                listOf("dog", "puppy", "doggo", "love", "heart", "cute", "aww")),
            FlashCard("card31", R.drawable.sticker_dogs_grr, "Dog growling with bared fangs",
                listOf("dog", "puppy", "doggo", "grr", "growl", "angry", "mad", "snarl")),
            FlashCard("card32", R.drawable.sticker_dogs_ball, "Excited dog with a tennis ball",
                listOf("dog", "puppy", "doggo", "ball", "play", "fetch", "excited", "game")),
            FlashCard("card33", R.drawable.sticker_dogs_sleep, "Dog sleeping with a Zzz",
                listOf("dog", "puppy", "doggo", "sleep", "sleepy", "nap", "tired", "zzz"))
        )
    ),
    StickerPack(
        id = "greetings",
        title = "Greetings",
        stickers = listOf(
            FlashCard("card34", R.drawable.sticker_greetings_hi, "Ginger cat saying \"Hi!\"",
                listOf("hi", "hey", "hello", "greeting", "cat", "kitty"), StickerMotion.BOUNCE),
            FlashCard("card35", R.drawable.sticker_greetings_hello, "Golden dog saying \"Hello!\"",
                listOf("hello", "hi", "hey", "greeting", "dog", "doggo", "puppy"), StickerMotion.BOUNCE),
            FlashCard("card36", R.drawable.sticker_greetings_heyyou, "Fox saying \"Hey you\"",
                listOf("hey", "hey you", "hello", "hi", "fox", "wink")),
            FlashCard("card37", R.drawable.sticker_greetings_morning, "Tan bear saying \"Morning!\"",
                listOf("morning", "good morning", "gm", "wake up", "bear", "yawn")),
            FlashCard("card38", R.drawable.sticker_greetings_goodnight, "Panda saying \"Good night\"",
                listOf("good night", "night", "gn", "sleep", "bedtime", "panda")),
            FlashCard("card39", R.drawable.sticker_greetings_sweetdreams, "Lilac cat saying \"Sweet dreams\"",
                listOf("sweet dreams", "dreams", "night", "sleep", "nap", "cat", "kitty")),
            FlashCard("card40", R.drawable.sticker_greetings_bye, "Gray bunny saying \"Bye!\"",
                listOf("bye", "goodbye", "see ya", "later", "cya", "bunny"), StickerMotion.WOBBLE),
            FlashCard("card41", R.drawable.sticker_greetings_seeyou, "Penguin saying \"See you!\"",
                listOf("see you", "later", "cya", "bye", "penguin", "soon")),
            FlashCard("card42", R.drawable.sticker_greetings_welcome, "Pig saying \"Welcome!\"",
                listOf("welcome", "hello", "hi", "come in", "pig", "piggy")),
            FlashCard("card43", R.drawable.sticker_greetings_missyou, "Cream puppy saying \"Miss you\"",
                listOf("miss you", "miss", "missing you", "sad", "love", "dog", "puppy"), StickerMotion.PULSE),
            FlashCard("card44", R.drawable.sticker_greetings_howru, "Gray cat saying \"How r u?\"",
                listOf("how are you", "how r u", "hru", "wassup", "cat", "kitty")),
            FlashCard("card45", R.drawable.sticker_greetings_hugs, "Brown bear saying \"Hugs!\"",
                listOf("hug", "hugs", "cuddle", "love", "comfort", "bear"), StickerMotion.PULSE)
        )
    ),
    StickerPack(
        id = "replies",
        title = "Replies",
        stickers = listOf(
            FlashCard("card46", R.drawable.sticker_replies_yes, "Yellow dog saying \"Yes!\"",
                listOf("yes", "yeah", "yep", "yay", "sure", "agree", "dog"), StickerMotion.BOUNCE),
            FlashCard("card47", R.drawable.sticker_replies_no, "Black cat saying \"No\"",
                listOf("no", "nope", "nah", "never", "refuse", "cat", "kitty")),
            FlashCard("card48", R.drawable.sticker_replies_ok, "Tan bunny saying \"OK!\"",
                listOf("ok", "okay", "fine", "alright", "sounds good", "bunny")),
            FlashCard("card49", R.drawable.sticker_replies_maybe, "Fox saying \"Maybe\"",
                listOf("maybe", "perhaps", "not sure", "hmm", "fox", "think")),
            FlashCard("card50", R.drawable.sticker_replies_thanks, "Panda saying \"Thanks!\"",
                listOf("thanks", "thank you", "thx", "ty", "grateful", "panda"), StickerMotion.PULSE),
            FlashCard("card51", R.drawable.sticker_replies_please, "Cream puppy saying \"Please\"",
                listOf("please", "pls", "plz", "beg", "puppy eyes", "dog", "puppy"), StickerMotion.PULSE),
            FlashCard("card52", R.drawable.sticker_replies_sorry, "Tan bear saying \"Sorry\"",
                listOf("sorry", "my bad", "apologies", "oops", "forgive", "bear")),
            FlashCard("card53", R.drawable.sticker_replies_noworries, "Chick with sunglasses saying \"No worries\"",
                listOf("no worries", "np", "chill", "relax", "cool", "its ok", "chick")),
            FlashCard("card54", R.drawable.sticker_replies_sure, "Pig saying \"Sure!\"",
                listOf("sure", "okay", "of course", "yep", "pig", "piggy")),
            FlashCard("card55", R.drawable.sticker_replies_same, "Ginger cat saying \"Same!\"",
                listOf("same", "me too", "relatable", "agree", "cat", "kitty")),
            FlashCard("card56", R.drawable.sticker_replies_really, "Gray dog saying \"Really?\"",
                listOf("really", "seriously", "are you sure", "doubt", "judging", "dog")),
            FlashCard("card57", R.drawable.sticker_replies_idk, "Cream cat saying \"Idk\"",
                listOf("idk", "dont know", "no idea", "unsure", "shrug", "cat", "kitty"))
        )
    ),
    StickerPack(
        id = "feelings",
        title = "Feelings",
        stickers = listOf(
            FlashCard("card58", R.drawable.sticker_feelings_lol, "Pink cat saying \"LOL\"",
                listOf("lol", "haha", "laugh", "funny", "lmao", "hilarious", "cat"), StickerMotion.WOBBLE),
            FlashCard("card59", R.drawable.sticker_feelings_loveyou, "Red-brown dog saying \"Love you\"",
                listOf("love", "love you", "ily", "heart", "cute", "dog", "puppy"), StickerMotion.PULSE),
            FlashCard("card60", R.drawable.sticker_feelings_wow, "White bunny saying \"Wow!\"",
                listOf("wow", "amazing", "awesome", "omg", "impressive", "bunny")),
            FlashCard("card61", R.drawable.sticker_feelings_omg, "Penguin saying \"OMG!\"",
                listOf("omg", "oh my god", "shocked", "surprised", "wow", "penguin")),
            FlashCard("card62", R.drawable.sticker_feelings_hmm, "Brown dog saying \"Hmm...\"",
                listOf("hmm", "thinking", "wondering", "think", "curious", "dog")),
            FlashCard("card63", R.drawable.sticker_feelings_oops, "Pig saying \"Oops!\"",
                listOf("oops", "whoops", "mistake", "my bad", "pig")),
            FlashCard("card64", R.drawable.sticker_feelings_sosad, "Panda saying \"So sad\"",
                listOf("sad", "so sad", "cry", "crying", "upset", "tears", "panda")),
            FlashCard("card65", R.drawable.sticker_feelings_grr, "Dark dog saying \"Grr!\"",
                listOf("grr", "angry", "mad", "growl", "annoyed", "dog")),
            FlashCard("card66", R.drawable.sticker_feelings_sleepy, "Penguin saying \"Sleepy\"",
                listOf("sleepy", "tired", "yawn", "sleep", "exhausted", "penguin")),
            FlashCard("card67", R.drawable.sticker_feelings_bored, "Gray cat saying \"Bored\"",
                listOf("bored", "boring", "meh", "nothing to do", "cat", "kitty")),
            FlashCard("card68", R.drawable.sticker_feelings_yay, "Chick saying \"Yay!\"",
                listOf("yay", "yippee", "hooray", "happy", "celebrate", "chick"), StickerMotion.BOUNCE),
            FlashCard("card69", R.drawable.sticker_feelings_hungry, "Golden dog saying \"Hungry\"",
                listOf("hungry", "food", "eat", "starving", "snack", "dog"))
        )
    ),
    StickerPack(
        id = "plans",
        title = "Plans",
        stickers = listOf(
            FlashCard("card70", R.drawable.sticker_plans_omw, "Yellow dog saying \"On my way\"",
                listOf("on my way", "omw", "coming", "leaving now", "running", "dog"), StickerMotion.BOUNCE),
            FlashCard("card71", R.drawable.sticker_plans_brb, "Ginger cat saying \"BRB\"",
                listOf("brb", "be right back", "wait", "back soon", "cat", "kitty")),
            FlashCard("card72", R.drawable.sticker_plans_callme, "Tan bunny saying \"Call me\"",
                listOf("call me", "call", "phone", "ring", "bunny")),
            FlashCard("card73", R.drawable.sticker_plans_busy, "Fox saying \"Busy\"",
                listOf("busy", "working", "studying", "later", "meeting", "fox")),
            FlashCard("card74", R.drawable.sticker_plans_where, "Pig saying \"Where r u?\"",
                listOf("where are you", "where r u", "location", "wru", "pig")),
            FlashCard("card75", R.drawable.sticker_plans_letsgo, "Golden dog saying \"Let's go!\"",
                listOf("lets go", "go", "hurry", "ready", "excited", "dog"), StickerMotion.BOUNCE),
            FlashCard("card76", R.drawable.sticker_plans_lunch, "Brown bear saying \"Lunch?\"",
                listOf("lunch", "food", "eat", "dinner", "hungry", "bear")),
            FlashCard("card77", R.drawable.sticker_plans_congrats, "Panda saying \"Congrats!\"",
                listOf("congrats", "congratulations", "well done", "party", "celebrate", "panda"), StickerMotion.BOUNCE),
            FlashCard("card78", R.drawable.sticker_plans_bday, "Pink cat saying \"Happy bday!\"",
                listOf("happy birthday", "bday", "birthday", "party", "cake", "cat"), StickerMotion.WOBBLE),
            FlashCard("card79", R.drawable.sticker_plans_goodluck, "Penguin saying \"Good luck!\"",
                listOf("good luck", "luck", "all the best", "you got this", "exam", "penguin"), StickerMotion.PULSE),
            FlashCard("card80", R.drawable.sticker_plans_getwell, "Tan bear saying \"Get well!\"",
                listOf("get well", "get well soon", "feel better", "sick", "ill", "bear")),
            FlashCard("card81", R.drawable.sticker_plans_cheers, "Gray bunny saying \"Cheers!\"",
                listOf("cheers", "celebrate", "toast", "party", "congrats", "bunny"), StickerMotion.BOUNCE)
        )
    )
)
