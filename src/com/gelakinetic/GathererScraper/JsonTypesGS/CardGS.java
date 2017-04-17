package com.gelakinetic.GathererScraper.JsonTypesGS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.commons.lang3.SerializationUtils;

import com.gelakinetic.GathererScraper.JsonTypes.Card;
/**
 * This class contains all information about a scraped card
 *
 * @author AEFeinstein
 *
 */
public class CardGS extends Card implements Serializable, Comparable<CardGS> {
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
	 * This function usually sorts by collector's number. However, gatherer
	 * doesn't have collector's number for expansions before collector's number
	 * was printed, and magiccards.info uses a strange numbering scheme. This
	 * function does it's best
	 */
	@Override
	public int compareTo(CardGS other) {
		/* Sort by collector's number */
		if (this.mNumber != null && other.mNumber != null && this.mNumber.length() > 0 && other.mNumber.length() > 0) {
			
			int this_num = this.getNumberInteger();
			int other_num = other.getNumberInteger();
			if(this_num > other_num) {
				return 1;
			}
			else if(this_num < other_num) {
				return -1;
			}
			else {
				char thisChar = this.getNumberChar();
				char otherChar = other.getNumberChar();
				if(thisChar > otherChar) {
					return 1;
				}
				else if (thisChar < otherChar) {
					return -1;
				}
				else {
					return 0;
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
		if (this.getNumFromColor() > other.getNumFromColor()) {
			return 1;
		}
		else if (this.getNumFromColor() < other.getNumFromColor()) {
			return -1;
		}

		/* If the color matches, sort by name */
		return this.mName.compareTo(other.mName);
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

	public int getNumberInteger() {
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
	
	public char getNumberChar() {
		char c = this.mNumber.charAt(this.mNumber.length() - 1);
		if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')) {
			return c;
		}
		return 0;
	}
}