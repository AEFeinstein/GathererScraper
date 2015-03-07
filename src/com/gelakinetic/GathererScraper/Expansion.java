package com.gelakinetic.GathererScraper;

import java.util.ArrayList;
import java.util.Calendar;

import org.json.simple.JSONObject;

import com.gelakinetic.GathererChecker.RssEntry;

/**
 * This class contains all information about an expansion to be parsed
 * 
 * @author AEFeinstein
 *
 */
public class Expansion implements Comparable<Expansion>{

	private static final String SUBSET_DIVIDER	= "##";
	private static final String	KEY_NAME		= "name";
	private static final String	KEY_CODE		= "code";
	private static final String	KEY_MTGI		= "mtgi";
	private static final String	KEY_TCGP		= "tcgp";
	private static final String	KEY_MKM			= "mkm";
	private static final String	KEY_DATE		= "date";
	private static final String	KEY_SUBSETS		= "subsets";

	/** Name used by Gatherer */
	public String				mName_gatherer	= "";
	/** expansion code used by Gatherer */
	protected String			mCode_gatherer	= "";
	/** expansion code used by magiccards.info */
	public String				mCode_mtgi		= "";
	/** expansion name used by TCGPlayer.com */
	public String				mName_tcgp		= "";
	/** expansion name used by MagicCardMarket.eu */
	public String				mName_mkm		= "";
	/** Date the expansion was released */
	public String				mDate			= "";

	/** To scrape, or not to scrape ? */
	public Boolean				mChecked		= false;

	/** Subsets for duel decks anthology */
	public ArrayList<String> mSubSets			= new ArrayList<String>();
	
	/**
	 * The most basic constructor for an expansion. Only sets the gatherer name
	 * 
	 * @param name_gatherer
	 *            The name of this expansion on Gatherer
	 */
	public Expansion(String name_gatherer) {
		mName_gatherer = name_gatherer;
		this.mSubSets.add(name_gatherer);
	}

	/**
	 * Create an expansion from a JSONObject. This is used when reading the
	 * expansions back from the expansion file
	 * 
	 * @param jo
	 *            A JSONObject containing the information about the expansion
	 */
	public Expansion(JSONObject jo) {
		mName_gatherer = (String) jo.get(KEY_NAME);
		mCode_gatherer = (String) jo.get(KEY_CODE);
		mCode_mtgi = (String) jo.get(KEY_MTGI);
		mName_tcgp = (String) jo.get(KEY_TCGP);
		mName_mkm = (String) jo.get(KEY_MKM);

		try {
			Calendar c = Calendar.getInstance();
			c.clear();
			c.setTimeInMillis((Long) jo.get(KEY_DATE));
			mDate = (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.YEAR);
		}
		catch (Exception e) {
			mDate = (String) jo.get(KEY_DATE);
		}
		
		try {
			String subsets[] = ((String) jo.get(KEY_SUBSETS)).split(SUBSET_DIVIDER);
			for (String subset : subsets) {
				mSubSets.add(subset);
			}
		}
		catch(Exception e) {
			/* info DNE */
		}
	}

	/**
	 * TODO
	 * @param string
	 * @param ownText
	 */
	public Expansion(String string, String ownText) {
		this.mName_gatherer = string;
		this.mSubSets.add(ownText);
	}

	/**
	 * Create a JSON representation of this expansion, to be used to save
	 * expansion information between sessions
	 * 
	 * @return a JSONObject containing this expansion's information
	 */
	public JSONObject toJsonObject() {
		JSONObject obj = new JSONObject();

		obj.put(KEY_NAME, mName_gatherer);
		obj.put(KEY_CODE, mCode_gatherer);
		obj.put(KEY_MTGI, mCode_mtgi);
		obj.put(KEY_TCGP, mName_tcgp);
		obj.put(KEY_MKM, mName_mkm);

		try {
			obj.put(KEY_DATE, getDateMs());
		}
		catch (Exception e) {
			obj.put(KEY_DATE, "");

		}
		
		String allSubsets = "";
		for(String subset : mSubSets) {
			allSubsets += subset + SUBSET_DIVIDER;
		}
		obj.put(KEY_SUBSETS, allSubsets);
		
		return obj;
	}

	/**
	 * Returns the date of this expansion, in ms since the epoch, as parsed from
	 * the string date
	 * 
	 * @return the date of this expansion, in ms since the epoch
	 * @throws NumberFormatException
	 *             If the date can't be parsed, this is thrown
	 */
	public long getDateMs() throws NumberFormatException {
		int month = Integer.parseInt(mDate.split("/")[0]);
		int year = Integer.parseInt(mDate.split("/")[1]);
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(year, month - 1, 1, 0, 0, 0);
		return c.getTimeInMillis();
	}

	@Override
	public int compareTo(Expansion o) {
		return this.mName_gatherer.compareTo(o.mName_gatherer);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Expansion) {
			return this.mName_gatherer.equals(((Expansion)obj).mName_gatherer);
		}
		else if(obj instanceof RssEntry) {
			return this.mName_gatherer.equals(((RssEntry)obj).getTitle());			
		}
		return false;
	}

	public void addSubSet(String ownText) {
		mSubSets.add(ownText);
	}
	
	
}
