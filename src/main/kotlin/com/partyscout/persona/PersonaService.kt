package com.partyscout.persona

import org.springframework.stereotype.Service

enum class Persona(val key: String, val label: String) {
    BABY("baby", "Baby & Toddler"),
    PRESCHOOL("preschool", "Preschool"),
    EARLY_CHILDHOOD("earlyChildhood", "Kids"),
    TWEEN("tween", "Tweens"),
    EARLY_TEEN("earlyTeen", "Early Teens"),
    OLDER_TEEN("olderTeen", "Teens"),
    YOUNG_ADULT("youngAdult", "Young Adults"),
    ADULT("adult", "Adults"),
}

@Service
class PersonaService {

    fun getPersona(age: Int): Persona = when {
        age <= 2  -> Persona.BABY
        age <= 5  -> Persona.PRESCHOOL
        age <= 8  -> Persona.EARLY_CHILDHOOD
        age <= 12 -> Persona.TWEEN
        age <= 15 -> Persona.EARLY_TEEN
        age <= 17 -> Persona.OLDER_TEEN
        age <= 22 -> Persona.YOUNG_ADULT
        else      -> Persona.ADULT
    }

    fun getSearchQueries(age: Int): List<String> = when (getPersona(age)) {

        Persona.BABY -> listOf(
            "soft play centre toddler",
            "indoor play centre babies",
            "children's farm toddler",
            "petting zoo toddler",
            "toddler adventure playground",
            "children's garden",
            "baby sensory event",
            "toddler messy play session",
            "baby cinema screening",
            "toddler theatre show",
            "children's puppet show",
            "toddler music event",
            "toddler storytime event",
            "family friendly cafe",
            "toddler friendly brunch",
            "family pop-up event",
        )

        Persona.PRESCHOOL -> listOf(
            "soft play centre kids",
            "indoor play centre children",
            "children's farm",
            "petting zoo",
            "kids adventure playground",
            "children's theme park",
            "mini golf kids",
            "kids bowling",
            "kids art workshop",
            "children's pottery class",
            "kids cooking workshop",
            "children's baking class",
            "children's theatre show",
            "kids cinema event",
            "children's comedy show",
            "kids magic show",
            "kids friendly food market",
            "family festival",
            "family outdoor cinema",
        )

        Persona.EARLY_CHILDHOOD -> listOf(
            "kids escape room",
            "laser tag kids",
            "trampoline park kids",
            "go karting kids",
            "kids bowling alley",
            "mini golf children",
            "kids climbing wall taster",
            "children's theme park",
            "indoor adventure centre kids",
            "kids art workshop",
            "children's pottery class",
            "kids cooking class",
            "children's baking workshop",
            "children's theatre show",
            "kids live music event",
            "kids magic show",
            "outdoor cinema kids",
            "family food market",
            "kids kayaking taster",
            "children's surfing lesson",
        )

        Persona.TWEEN -> listOf(
            "escape room kids",
            "laser tag",
            "trampoline park",
            "go karting kids",
            "bowling alley",
            "mini golf",
            "climbing wall drop-in",
            "indoor skydiving kids",
            "VR experience kids",
            "kids pottery workshop",
            "children's cooking class",
            "kids street art workshop",
            "children's photography workshop",
            "kids comedy show",
            "children's theatre",
            "live music kids event",
            "outdoor cinema family",
            "teen kayaking",
            "junior archery experience",
            "street food market family",
        )

        Persona.EARLY_TEEN -> listOf(
            "escape room teen",
            "laser tag",
            "trampoline park",
            "go karting",
            "bowling",
            "axe throwing teen",
            "indoor climbing drop-in",
            "VR experience",
            "rage room teen",
            "teen pottery workshop",
            "teen cooking class",
            "teen street art workshop",
            "teen photography workshop",
            "teen comedy night",
            "live music under 18",
            "teen open mic night",
            "outdoor cinema",
            "teen kayaking",
            "surfing lesson teen",
            "street food market",
            "food festival",
        )

        Persona.OLDER_TEEN -> listOf(
            "escape room",
            "axe throwing",
            "go karting",
            "trampoline park",
            "bowling",
            "laser tag",
            "indoor climbing drop-in",
            "VR experience",
            "rage room",
            "teen pottery workshop",
            "ceramics class drop-in",
            "teen cooking class",
            "teen street art workshop",
            "teen comedy night",
            "under 18 live music",
            "all ages open mic",
            "outdoor cinema",
            "under 18 club night",
            "surfing lesson",
            "kayaking experience",
            "street food market",
            "food festival",
            "night market",
        )

        Persona.YOUNG_ADULT -> listOf(
            "escape room",
            "axe throwing",
            "go karting",
            "bowling alley",
            "mini golf adult",
            "rage room",
            "VR experience",
            "pottery class drop-in",
            "ceramics workshop",
            "cocktail making class",
            "cooking class one-off",
            "life drawing class",
            "comedy club",
            "live music venue",
            "open mic night",
            "jazz bar",
            "rooftop bar",
            "karaoke bar",
            "outdoor cinema",
            "art exhibition",
            "theatre show",
            "surfing lesson adult",
            "kayaking experience",
            "coasteering",
            "street food market",
            "supper club",
            "craft beer festival",
            "night market",
        )

        Persona.ADULT -> listOf(
            "escape room",
            "axe throwing",
            "go karting adult",
            "clay pigeon shooting",
            "archery experience",
            "rage room",
            "pottery class",
            "ceramics workshop",
            "cocktail masterclass",
            "cooking masterclass",
            "life drawing class",
            "candle making workshop",
            "flower arranging workshop",
            "comedy club",
            "live music",
            "jazz club",
            "rooftop bar",
            "wine bar",
            "karaoke bar",
            "improv show",
            "theatre show",
            "art exhibition",
            "street food market",
            "supper club",
            "wine tasting event",
            "whisky tasting",
            "craft beer festival",
            "food festival",
            "gin distillery tour",
            "night market",
        )
    }
}
