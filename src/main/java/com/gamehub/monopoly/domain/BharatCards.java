package com.gamehub.monopoly.domain;

/**
 * India-edition Chance / Community Chest decks (subset aligned with frontend monopolyCards.ts).
 * Drawn when a player lands on the matching tile; resolved immediately by MonopolyEngine.
 */
public final class BharatCards {

    public enum Deck {
        CHANCE,
        CHEST
    }

    public enum Kind {
        MONEY,
        MONEY_FROM_EACH,
        MOVE,
        MOVE_REL,
        JAIL,
        GET_OUT,
        NEAREST_RAILROAD,
        NEAREST_UTILITY,
        REPAIRS
    }

    public record Card(String text, Kind kind, int amount, int to, boolean collectGoIfPass, int perHouse, int perHotel) {
        public static Card money(String text, int amount) {
            return new Card(text, Kind.MONEY, amount, 0, false, 0, 0);
        }

        public static Card moneyFromEach(String text, int amount) {
            return new Card(text, Kind.MONEY_FROM_EACH, amount, 0, false, 0, 0);
        }

        public static Card move(String text, int to, boolean collectGo) {
            return new Card(text, Kind.MOVE, 0, to, collectGo, 0, 0);
        }

        public static Card moveRel(String text, int steps) {
            return new Card(text, Kind.MOVE_REL, steps, 0, false, 0, 0);
        }

        public static Card jail(String text) {
            return new Card(text, Kind.JAIL, 0, 0, false, 0, 0);
        }

        public static Card getOut(String text) {
            return new Card(text, Kind.GET_OUT, 0, 0, false, 0, 0);
        }

        public static Card nearestRailroad(String text) {
            return new Card(text, Kind.NEAREST_RAILROAD, 0, 0, false, 0, 0);
        }

        public static Card nearestUtility(String text) {
            return new Card(text, Kind.NEAREST_UTILITY, 0, 0, false, 0, 0);
        }

        public static Card repairs(String text, int perHouse, int perHotel) {
            return new Card(text, Kind.REPAIRS, 0, 0, false, perHouse, perHotel);
        }
    }

    public static final Card[] CHANCE = {
        Card.move("Advance to GO. Collect ₹2,000.", 0, true),
        Card.move("Advance to Bengaluru.", 24, true),
        Card.move("Advance to Jaipur.", 11, true),
        Card.move("Advance to Nariman Point.", 39, false),
        Card.nearestRailroad("Advance to nearest Railway."),
        Card.nearestUtility("Advance to nearest Utility."),
        Card.money("Bank pays you dividend of ₹500.", 500),
        Card.getOut("Get Out of Jail Free."),
        Card.moveRel("Go back 3 spaces.", -3),
        Card.jail("Go directly to Jail."),
        Card.repairs("Festival repairs: ₹250 per Village–Metro, ₹1,000 per Smart City.", 250, 1000),
        Card.money("Traffic fine ₹150.", -150),
        Card.move("Take a trip to Indian Railways.", 5, true),
        Card.moneyFromEach("Elected city chair — pay each player ₹500.", -500),
        Card.money("Startup grant matures. Collect ₹1,500.", 1500),
        Card.money("Festival contest prize. Collect ₹1,000.", 1000)
    };

    public static final Card[] CHEST = {
        Card.move("Advance to GO. Collect ₹2,000.", 0, true),
        Card.money("Bank error in your favor. Collect ₹2,000.", 2000),
        Card.money("Doctor's fees. Pay ₹500.", -500),
        Card.money("From sale of stock you get ₹500.", 500),
        Card.getOut("Get Out of Jail Free."),
        Card.jail("Go directly to Jail."),
        Card.money("Holiday fund matures. Collect ₹1,000.", 1000),
        Card.money("Tax refund. Collect ₹200.", 200),
        Card.moneyFromEach("It's your birthday — collect ₹100 from each player.", 100),
        Card.money("Insurance payout. Collect ₹1,000.", 1000),
        Card.money("Pay hospital fees of ₹1,000.", -1000),
        Card.money("Pay school fees of ₹500.", -500),
        Card.money("Receive ₹250 consultancy fee.", 250),
        Card.repairs("Street repairs: ₹400 per Village–Metro, ₹1,150 per Smart City.", 400, 1150),
        Card.money("You inherit ₹1,000.", 1000),
        Card.money("You won a cultural fest prize. Collect ₹100.", 100)
    };

    private BharatCards() {}
}
