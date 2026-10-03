package com.gamehub.monopoly.domain;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Catalog of Free Parking Indian Events (BHARAT EMPIRE rulebook §15). */
public final class IndianEvents {

    public record Definition(String id, String title, String description) {}

    public static final List<Definition> ALL = List.of(
            new Definition(
                    "BUDGET_ANNOUNCEMENT",
                    "Budget Announcement",
                    "GO salary becomes ₹3,000. Income Tax is ₹3,000 flat."),
            new Definition(
                    "ELECTION_RESULTS",
                    "Election Results",
                    "Politics dominate the table — trades stay open; watch your rivals."),
            new Definition(
                    "ECONOMIC_BOOM",
                    "Economic Boom",
                    "All colour-property rents +50%."),
            new Definition(
                    "ECONOMIC_RECESSION",
                    "Economic Recession",
                    "All colour-property rents −50%. Upgrade costs +25%."),
            new Definition(
                    "FESTIVAL_SEASON",
                    "Festival Season",
                    "Railway rents double. Collect ₹500 extra when you pass GO."),
            new Definition(
                    "TOURISM_SEASON",
                    "Tourism Season",
                    "Light Blue & Yellow rents double."),
            new Definition(
                    "FLOODS",
                    "Floods",
                    "Yellow coastal properties charge no rent. Owners get ₹200 insurance per Yellow owned."),
            new Definition(
                    "CYCLONE",
                    "Cyclone",
                    "Yellow properties charge no rent. Utilities use ×2 only."),
            new Definition(
                    "METRO_EXPANSION",
                    "Metro Expansion",
                    "Red & Green upgrade costs −50%."),
            new Definition(
                    "REAL_ESTATE_BOOM",
                    "Real Estate Boom",
                    "Unmortgage with 0% interest."),
            new Definition(
                    "STARTUP_WAVE",
                    "Startup Wave",
                    "Bengaluru & Whitefield charge +100% rent."),
            new Definition(
                    "IPL_SEASON",
                    "IPL Season",
                    "Jail fee drops to ₹250."));

    public static IndianEvent draw(int expiresOnTurn) {
        Definition def = ALL.get(ThreadLocalRandom.current().nextInt(ALL.size()));
        return new IndianEvent(def.id(), def.title(), def.description(), expiresOnTurn);
    }

    public static IndianEvent drawExcluding(String excludeId, int expiresOnTurn) {
        List<Definition> pool = ALL.stream().filter(d -> !d.id().equals(excludeId)).toList();
        if (pool.isEmpty()) pool = ALL;
        Definition def = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return new IndianEvent(def.id(), def.title(), def.description(), expiresOnTurn);
    }

    private IndianEvents() {}
}
