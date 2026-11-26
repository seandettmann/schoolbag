package io.github.seandettmann.schoolbag.core;

/** The game's small card fronts (ui_cardfronts.anm2) for the pocket. */
final class Cards {
    private Cards() {}

    // the tarot cards, IDs 1-22, in the game's order
    private static final String[] TAROT = {"00_TheFool", "01_TheMagician", "02_TheHighPriestess", "03_TheEmpress",
            "04_TheEmperor", "05_TheHierophant", "06_TheLovers", "07_TheChariot", "08_TheJustice", "09_TheHermit",
            "10_WheelOfFortune", "11_Strength", "12_TheHangedMan", "13_Death", "14_Temperance", "15_TheDevil",
            "16_TheTower", "17_TheStars", "18_TheMoon", "19_TheSun", "20_Judgement", "21_TheWorld"};

    static String region(int id) {
        if (id >= 1 && id <= TAROT.length) return "cardfront-" + TAROT[id - 1];
        return "cardfront-Outline";
    }
}
