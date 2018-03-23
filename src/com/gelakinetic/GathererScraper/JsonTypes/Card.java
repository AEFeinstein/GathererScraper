package com.gelakinetic.GathererScraper.JsonTypes;

import com.gelakinetic.mtgfam.helpers.database.CardDbAdapter;

import java.util.ArrayList;

/*
 * This class contains all information about a scraped card
 *
 * @author AEFeinstein
 *
 */
public class Card implements Comparable<Card> {

    // The card's name
    public String mName = "";

    // The card's mana cost
    public String mManaCost = "";

    // The card's converted mana cost
    public int mCmc = 0;

    // The card's type, includes super and sub
    public String mType = "";

    // The card's text text
    public String mText = "";

    // The card's flavor text
    public String mFlavor = "";

    // The card's expansion
    public String mExpansion = "";

    // The card's rarity
    public char mRarity = '\0';

    // The card's collector's number. Not an integer (i.e. 181a, 181b)
    public String mNumber = "";

    // The card's artist
    public String mArtist = "";

    // The card's colors
    public String mColor = "";

    // The card's colors
    protected String mColorIdentity = "";

    // The card's multiverse id
    public int mMultiverseId = 0;

    // The card's power. Not an integer (i.e. *+1, X)
    public float mPower = CardDbAdapter.NO_ONE_CARES;

    // The card's toughness, see mPower
    public float mToughness = CardDbAdapter.NO_ONE_CARES;

    // The card's loyalty. An integer in practice
    public int mLoyalty = CardDbAdapter.NO_ONE_CARES;

    // All the card's foreign printings
    public ArrayList<ForeignPrinting> mForeignPrintings = new ArrayList<>();

    // The card's loyalty. An integer in practice
    public String mWatermark = "";

    // Private class for encapsulating foreign printing information
    public class ForeignPrinting implements Comparable<ForeignPrinting> {
        public int mMultiverseId;
        public String mName;
        public String mLanguageCode;

        @Override
        public int compareTo(ForeignPrinting o) {
            return Integer.compare(this.mMultiverseId, o.mMultiverseId);
        }

        @Override
        public boolean equals(Object arg0) {
            if (arg0 instanceof ForeignPrinting) {
                return this.mMultiverseId == ((ForeignPrinting) arg0).mMultiverseId;
            }
            return false;
        }
    }

    /**
     * This function usually sorts by collector's number. However, gatherer
     * doesn't have collector's number for expansions before collector's number
     * was printed, and magiccards.info uses a strange numbering scheme. This
     * function does it's best
     */
    @Override
    public int compareTo(Card other) {

        /* Sort by collector's number */
        if (this.mNumber != null && other.mNumber != null && this.mNumber.length() > 0 && other.mNumber.length() > 0) {

            // Try comparing by integer number
            int switchOn = Integer.compare(this.getNumberInteger(), other.getNumberInteger());
            switch(switchOn) {
                case 0: {
                    // If they match, try comparing by letter after the number
                    switchOn = Character.compare(this.getNumberChar(), other.getNumberChar());
                    switch(switchOn) {
                        case 0: {
                            // If they match, try comparing by name
                            return this.mName.compareTo(other.mName);
                        }
                        default: {
                            return switchOn;
                        }
                    }
                }
                default: {
                    return switchOn;
                }
            }
        }

        /* Battle Royale is pure alphabetical, except for basics, why not */
        if (this.mExpansion.equals("BR")) {
            if (this.mType.contains("Basic Land") && !other.mType.contains("Basic Land")) {
                return 1;
            }
            if (!this.mType.contains("Basic Land") && other.mType.contains("Basic Land")) {
                return -1;
            }
            return this.mName.compareTo(other.mName);
        }

        /*
         * Or if that doesn't exist, sort by color order. Weird for
         * magiccards.info
         */
        int switchOn = Integer.compare(this.getNumFromColor(), other.getNumFromColor());
        switch (switchOn) {
            case 0: {
                // They match, try comparing by name
                return this.mName.compareTo(other.mName);
            }
            default: {
                // Num from color doesn't match, return it
                return switchOn;
            }
        }
    }

    /**
     * Returns a number used for sorting by color. This is different for
     * Beatdown because magiccards.info is weird
     *
     * @return A number indicating how the card's color is sorted
     */
    private int getNumFromColor() {
        /* Because Beatdown properly sorts color */
        if (this.mExpansion.equals("BD")) {
            if (this.mColor.length() > 1) {
                return 7;
            }
            switch (this.mColor.charAt(0)) {
                case 'W': {
                    return 0;
                }
                case 'U': {
                    return 1;
                }
                case 'B': {
                    return 2;
                }
                case 'R': {
                    return 3;
                }
                case 'G': {
                    return 4;
                }
                case 'A': {
                    return 5;
                }
                case 'L': {
                    return 6;
                }
            }
        }
        /* And magiccards.info has weird numbering for everything else */
        else {
            if (this.mColor.length() > 1) {
                return 7;
            }
            switch (this.mColor.charAt(0)) {
                case 'B': {
                    return 0;
                }
                case 'U': {
                    return 1;
                }
                case 'G': {
                    return 2;
                }
                case 'R': {
                    return 3;
                }
                case 'W': {
                    return 4;
                }
                case 'A': {
                    return 5;
                }
                case 'L': {
                    return 6;
                }
            }
        }
        return 8;
    }

    private int getNumberInteger() {
        try {
            char c = this.mNumber.charAt(this.mNumber.length() - 1);
            if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')) {
                return Integer.parseInt(this.mNumber.substring(0, this.mNumber.length() - 1));
            }
            return Integer.parseInt(this.mNumber);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private char getNumberChar() {
        char c = this.mNumber.charAt(this.mNumber.length() - 1);
        if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')) {
            return c;
        }
        return 0;
    }
}