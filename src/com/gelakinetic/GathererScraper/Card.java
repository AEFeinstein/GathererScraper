package com.gelakinetic.GathererScraper;

import java.io.Serializable;

/**
 * This class contains all information about a scraped card
 *
 * @author AEFeinstein
 *
 */
public class Card implements Serializable, Comparable<Card> {
	private static final long	serialVersionUID	= 7961150645687367029L;
	/** The card's name */
	public String				mName				= "";
	/** The card's mana cost */
	public String				mManaCost			= "";
	/** The card's converted mana cost */
	public int					mCmc				= 0;
	/** The card's type, includes super and sub */
	public String				mType				= "";
	/** The card's ability text */
	public String				mText				= "";
	/** The card's flavor text */
	public String				mFlavor				= "";
	/** The card's expansion */
	public String				mExpansion			= "";
	/** The card's rarity */
	public String				mRarity				= "";
	/** The card's collector's number. Not an integer (i.e. 181a, 181b) */
	public String				mNumber				= "";
	/** The card's artist */
	public String				mArtist				= "";
	/** The card's colors */
	public String				mColor				= "";
	/** The card's multiverse id */
	public int					mMultiverseId		= 0;
	/** The card's power. Not an integer (i.e. *+1, X) */
	public String				mPower				= "";
	/** The card's toughness, see mPower */
	public String				mToughness			= "";
	/** The card's loyalty. An integer in practice */
	public String				mLoyalty			= "";

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
	public Card(String name, String expansion, int multiverseId) {
		this.mName = name;
		this.mMultiverseId = multiverseId;
		this.mExpansion = expansion;
	}

	/**
	 * Returns a string URL for this card's gatherer page
	 *
	 * @return A string of the URL for this card's gatherer page
	 */
	public String getUrl() {
		return "http://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=" + mMultiverseId;
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
		if (null == mRarity) {
			mRarity = "";
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
		if (null == mPower) {
			mPower = "";
		}
		if (null == mToughness) {
			mToughness = "";
		}
		if (null == mLoyalty) {
			mLoyalty = "";
		}
	}

	/**
	 * Two cards are equal if their multiverseId is the same, and their name is
	 * the same. Halves of split cards do not satisfy this
	 *
	 * @return true if the cards are the same, false if they are different
	 */
	@Override
	public boolean equals(Object arg0) {
		if (!(arg0 instanceof Card)) {
			return false;
		}
		return (((Card) arg0).mMultiverseId == this.mMultiverseId) && ((Card) arg0).mName.equals(this.mName);
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
			return this.mNumber.compareTo(other.mNumber);
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
}