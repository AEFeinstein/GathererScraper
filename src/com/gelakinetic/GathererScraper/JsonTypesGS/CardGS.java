package com.gelakinetic.GathererScraper.JsonTypesGS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SerializationUtils;

import com.gelakinetic.GathererScraper.JsonTypes.Card;
/**
 * This class contains all information about a scraped card
 *
 * @author AEFeinstein
 *
 */
public class CardGS extends Card implements Serializable {
	private static final long	serialVersionUID	= 7961150645687367029L;

	/**
	 * Creates a card object with the basic information. The rest will be
	 * scraped later
	 *
	 * @param name
	 *            The name of the card
	 * @param expansion
	 *            The expansion of the card
	 * @param multiverseId
	 *            The multiverse ID of the card
	 */
	public CardGS(String name, String expansion, int multiverseId) {
		this.mName = name;
		this.mMultiverseId = multiverseId;
		this.mExpansion = expansion;
	}

	/**
	 * Returns a string URL for this card's gatherer page
	 *
	 * @return A string of the URL for this card's gatherer page
	 */
	public static String getUrl(int multiverseId) {
		return "http://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=" + multiverseId;
	}
	
	/**
	 * Returns a string URL for this card's gatherer language page
	 *
	 * @return A string of the URL for this card's gatherer language page
	 */
	public static String getLanguageUrl(int multiverseId) {
		return "http://gatherer.wizards.com/Pages/Card/Languages.aspx?multiverseid=" + multiverseId;
	}

	/**
	 * Turns any null fields into empty strings
	 */
	public void clearNulls() {
		if (null == mName) {
			mName = "";
		}
		if (null == mManaCost) {
			mManaCost = "";
		}
		if (null == mType) {
			mType = "";
		}
		if (null == mText) {
			mText = "";
		}
		if (null == mFlavor) {
			mFlavor = "";
		}
		if (null == mExpansion) {
			mExpansion = "";
		}
		if (null == mNumber) {
			mNumber = "";
		}
		if (null == mArtist) {
			mArtist = "";
		}
		if (null == mColor) {
			mColor = "";
		}
		if (null == mColorIdentity) {
			mColorIdentity = "";
		}
		if (null == mWatermark) {
			mWatermark = "";
		}
		if (null == mForeignPrintings) {
			mForeignPrintings = new ArrayList<>();
		}
		/* Don't worry about mRarity, mPower, mToughness, or mLoyalty */
	}

	/**
	 * Two cards are equal if their multiverseId is the same, and their name is
	 * the same. Halves of split cards do not satisfy this
	 *
	 * @return true if the cards are the same, false if they are different
	 */
	@Override
	public boolean equals(Object arg0) {
		if (!(arg0 instanceof CardGS)) {
			return false;
		}
		return (((CardGS) arg0).mMultiverseId == this.mMultiverseId) && ((CardGS) arg0).mName.equals(this.mName);
	}

	/**
	 * @return The byte array representation of this object
	 */
	public byte[] getBytes() {
		return SerializationUtils.serialize(this);
	}

	/**
	 * @return A comparator to sort cards by name
	 */
	public static Comparator<CardGS> getNameComparator() {
		return new Comparator<CardGS>() {

			@Override
			public int compare(CardGS o1, CardGS o2) {
				return o1.mName.compareTo(o2.mName);
			}
		};
	}
	
	/**
     * Calculates the mColor identity for this card, not counting any parts of a
     * multicard
     *
     * @param card The card to find a mColor identity for, excluding multicard
     * @return A mColor identity string for the given card consisting of "WUBRG"
     */
    private String getColorIdentity() {
        boolean colors[] = {false, false, false, false, false};
        String colorLetters[] = {"W", "U", "B", "R", "G"};
        String basicLandTypes[] = {"Plains", "Island", "Swamp", "Mountain",
                "Forest"};

		/* Search for colors in the cost & mColor */
        for (int i = 0; i < colors.length; i++) {
            if (mColor.contains(colorLetters[i])) {
                colors[i] = true;
            }
            if (mManaCost.contains(colorLetters[i])) {
                colors[i] = true;
            }
        }

		/* Remove reminder text */
        String noReminderText = mText.replaceAll("\\([^\\(\\)]+\\)", "");
        /* Find mana symbols in the rest of the text */
        Pattern manaPattern = Pattern.compile("\\{[^\\{\\}]+\\}");
        Matcher m = manaPattern.matcher(noReminderText);
        while (m.find()) {
            /* Search for colors in the mana symbols in the non-reminder text */
            for (int i = 0; i < colors.length; i++) {
                if (m.group(0).contains(colorLetters[i])) {
                    colors[i] = true;
                }
            }
        }

		/* For typed lands, add mColor identity */
        if (mType.toLowerCase().contains("land")) {
            for (int i = 0; i < colors.length; i++) {
                if (mType.contains(basicLandTypes[i])) {
                    colors[i] = true;
                }
            }
        }

		/* Write the mColor identity */
        String colorIdentity = "";
        for (int i = 0; i < colors.length; i++) {
            if (colors[i]) {
                colorIdentity += colorLetters[i];
            }
        }
        return colorIdentity;
    }
    
    /**
     * Calculates the full mColor identity for this card, and stores it in
     * mColorIdentity
     *
     * @param otherCards A list of other cards, used to find the second part if this is
     *                   a multi-card
     */
    public void calculateColorIdentity(ArrayList<CardGS> otherCards) {
        String colorLetters[] = {"W", "U", "B", "R", "G"};

		/* Get the mColor identity for the first part of the card */
        String firstPartIdentity = getColorIdentity();

		/* Find the mColor identity for multicards */
        String secondPartIdentity = "";
        String newNumber = null;
        if (mNumber.contains("a")) {
            newNumber = mNumber.replace("a", "b");
        } else if (mNumber.contains("b")) {
            newNumber = mNumber.replace("b", "a");
        }
        if (newNumber != null) {
            for (CardGS otherCard : otherCards) {
                if (otherCard.mNumber.equals(newNumber)) {
                    secondPartIdentity = otherCard.getColorIdentity();
                    break;
                }
            }
        }

		/* Combine the two mColor identity parts into one */
        mColorIdentity = "";
        for (String colorLetter : colorLetters) {
            if (firstPartIdentity.contains(colorLetter)
                    || secondPartIdentity.contains(colorLetter)) {
            	mColorIdentity += colorLetter;
            }
        }
    }
}