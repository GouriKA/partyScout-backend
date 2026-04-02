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
            "kids birthday party venue",
            "toddler birthday party place",
            "indoor play area toddler birthday",
            "children's museum toddler birthday",
            "petting zoo toddler birthday",
            "kids party room rental",
            "family fun center toddler",
            "soft play toddler party",
            "toddler adventure playground",
            "baby sensory play class",
            "toddler music class birthday",
            "family friendly restaurant kids party",
        )

        Persona.PRESCHOOL -> listOf(
            "kids birthday party venue",
            "children's party place",
            "indoor play center kids birthday",
            "kids entertainment center party",
            "children's museum birthday party",
            "petting zoo birthday party",
            "kids bowling birthday party",
            "mini golf kids birthday",
            "kids art studio birthday party",
            "children's cooking class birthday",
            "kids magic show birthday",
            "pizza party kids birthday",
            "kids party room rental",
            "family fun center birthday",
            "kids gymnastics birthday party",
        )

        Persona.EARLY_CHILDHOOD -> listOf(
            "kids birthday party venue",
            "children's entertainment center birthday",
            "family fun center party",
            "kids pizza party birthday",
            "trampoline park kids birthday",
            "laser tag kids birthday",
            "kids bowling birthday party",
            "mini golf birthday party kids",
            "kids escape room birthday",
            "arcade birthday party kids",
            "kids art studio party",
            "children's cooking class birthday",
            "kids climbing gym birthday",
            "indoor adventure park kids birthday",
            "kids gymnastics birthday party",
            "skating rink birthday party kids",
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
