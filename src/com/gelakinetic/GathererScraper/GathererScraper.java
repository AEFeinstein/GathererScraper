package com.gelakinetic.GathererScraper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.net.PercentEscaper;

/**
 * This class is filled with static functions which do the actual scraping from
 * Gatherer. It first gets a list of expansions, then gets lists of cards for
 * the expansions, and finally scrapes the individual cards.
 * 
 * @author AEFeinstein
 *
 */
public class GathererScraper {

	/**
	 * This function scrapes a list of all expansions from Gatherer
	 * 
	 * @return An ArrayList of Expansion objects for all potential expansions to
	 *         scrape
	 * @throws IOException
	 *             Thrown if the Internet breaks
	 */
	public static ArrayList<Expansion> scrapeExpansionList() throws IOException {
		ArrayList<Expansion> expansions = new ArrayList<Expansion>();
		Document gathererMain = Jsoup.connect("http://gatherer.wizards.com/Pages/Default.aspx").get();
		Elements expansionElements = gathererMain.getElementsByAttributeValueContaining("name", "setAddText");

		for (int i = 0; i < expansionElements.size(); i++) {
			for (Element e : expansionElements.get(i).getAllElements()) {
				if (e.ownText().length() > 0) {
					if(e.ownText().contains("Duel Decks Anthology")) {
						boolean exists = false;
						for(Expansion exp : expansions) {
							if(exp.equals(new Expansion("Duel Decks Anthology"))) {
								exists = true;
								exp.addSubSet(e.ownText());
							}
						}
						
						if(!exists) {
							expansions.add(new Expansion("Duel Decks Anthology", e.ownText()));							
						}
					}
					else {
						expansions.add(new Expansion(e.ownText()));
					}
				}
			}
		}
		return expansions;
	}

	/**
	 * This function scrapes all cards from a given expansion and posts updated
	 * to UI
	 * 
	 * @param exp
	 *            The expansion to scrape
	 * @param gathererScraperUi
	 *            The UI to post updates to
	 * @return An ArrayList of Card objects for all cards scraped
	 * @throws IOException
	 *             Thrown if the Internet breaks
	 */
	public static ArrayList<Card> scrapeExpansion(Expansion exp, GathererScraperUi ui) throws IOException {

		ArrayList<Card> cardsArray = new ArrayList<Card>();

		/* Look for normal cards */
		for(int subSetNum = 0; subSetNum < exp.mSubSets.size(); subSetNum++) {
			int pageNum = 0;
			boolean loop = true;
			while (loop) {
	
				String urlStr = "http://gatherer.wizards.com/Pages/Search/Default.aspx?page=" + pageNum
						+ "&output=compact&action=advanced&special=true&set=+%5b%22"
						+ (new PercentEscaper("", true)).escape(exp.mSubSets.get(subSetNum)) + "%22%5d";
	
				System.out.println(urlStr);
				
				Document individualExpansion = Jsoup.connect(urlStr).get();
	
				Elements cards = individualExpansion.getElementsByAttributeValueContaining("id", "cardTitle");
				if (cards.size() == 0) {
					loop = false;
				}
				for (int i = 0; i < cards.size(); i++) {
					Element e = cards.get(i);
					Card card = new Card(e.ownText(), exp.mCode_gatherer, Integer.parseInt(e.attr("href").split("=")[1]));
	
					if (cardsArray.contains(card)) {
						loop = false;
					}
					else {
						cardsArray.add(card);
					}
				}
				pageNum++;
			}
		}

		for (Card c : cardsArray) {
			scrapeCard(c);
			ui.setLastCardScraped(c.mExpansion + ": " + c.mName);
		}

		if (cardsArray.get(0).mNumber.length() < 1) {
			Collections.sort(cardsArray);
			for (int i = 0; i < cardsArray.size(); i++) {
				cardsArray.get(i).mNumber = "" + (i + 1);
			}
		}

		return cardsArray;
	}

	/**
	 * Scrapes an individual card
	 * 
	 * @param card
	 *            The card to scrape and populate
	 * @return The card that was scraped, same as the input
	 * @throws IOException
	 *             Thrown if the Internet breaks
	 */
	private static Card scrapeCard(Card card) throws IOException {
		Document cardPage = Jsoup.connect(card.getUrl()).get();

		/* Get the list of cards and ids for split cards */
		String id = getCardId(card.mName, cardPage);

		/* Mana Cost */
		card.mManaCost = getTextFromAttribute(cardPage, id + "manaRow", "value", true);

		/* Converted Mana Cost */
		try {
			card.mCmc = Integer.parseInt(getTextFromAttribute(cardPage, id + "cmcRow", "value", true));
		}
		catch (NumberFormatException e) {
			card.mCmc = 0;
		}

		/* Type */
		card.mType = getTextFromAttribute(cardPage, id + "typeRow", "value", true);

		/* Ability Text */
		card.mText = getTextFromAttribute(cardPage, id + "textRow", "cardtextbox", false);

		/* Flavor */
		card.mFlavor = getTextFromAttribute(cardPage, id + "FlavorText", "cardtextbox", false);

		/* PT */
		String pt = getTextFromAttribute(cardPage, id + "ptRow", "value", true);

		if (card.mExpansion.equals("VNG")) {
			/* this row is the life & hand modifier for vanguard */
			card.mText += "<br><br><br>" + pt;
			card.mPower = null;
			card.mToughness = null;
			card.mLoyalty = null;
		}
		else {
			if (pt != null) {
				if (pt.contains("/")) {
					card.mPower = pt.split("/")[0].trim();
					card.mToughness = pt.split("/")[1].trim();
				}
				else {
					card.mLoyalty = pt.trim();
				}
			}
		}

		/* Rarity */
		card.mRarity = getTextFromAttribute(cardPage, id + "rarityRow", "value", true);
		if (card.mExpansion.equals("TSB")) {
			/* They say Special, I say Timeshifted */
			card.mRarity = "Timeshifted";
		}
		else if (card.mRarity.isEmpty()) {
			/* Edge case for promotional cards */
			card.mRarity = "Rare";
		}
		else if (card.mRarity.equalsIgnoreCase("Land")) {
			/*
			 * Basic lands aren't technically common, but the app doesn't
			 * understand "Land"
			 */
			card.mRarity = "Common";
		}
		else if (card.mRarity.equalsIgnoreCase("Special")) {
			/* Planechase, Promos, Vanguards */
			card.mRarity = "Rare";
		}

		/* Number */
		card.mNumber = getTextFromAttribute(cardPage, id + "numberRow", "value", true);

		/* artist */
		card.mArtist = getTextFromAttribute(cardPage, id + "ArtistCredit", "value", true);

		/* color, calculated */
		String color = getTextFromAttribute(cardPage, id + "colorIndicatorRow", "value", true);
		card.mColor = "";
		if (card.mType.contains("Artifact")) {
			card.mColor += "A";
		}
		if (card.mType.contains("Land")) {
			card.mColor += "L";
		}
		if (color != null) {
			if (color.contains("White")) {
				card.mColor += "W";
			}
			if (color.contains("Blue")) {
				card.mColor += "U";
			}
			if (color.contains("Black")) {
				card.mColor += "B";
			}
			if (color.contains("Red")) {
				card.mColor += "R";
			}
			if (color.contains("Green")) {
				card.mColor += "G";
			}
		}
		else if (card.mManaCost != null) {
			if (card.mManaCost.contains("W")) {
				card.mColor += "W";
			}
			if (card.mManaCost.contains("U")) {
				card.mColor += "U";
			}
			if (card.mManaCost.contains("B")) {
				card.mColor += "B";
			}
			if (card.mManaCost.contains("R")) {
				card.mColor += "R";
			}
			if (card.mManaCost.contains("G")) {
				card.mColor += "G";
			}
		}
		if (card.mColor.isEmpty() || card.mName.equals("Ghostfire")) {
			card.mColor = "C";
		}

		card.clearNulls();
		return card;
	}

	/**
	 * Get's the given card's ID from a Document. This is relevant when there
	 * are two halves of a split card on the same page, and you don't want to
	 * mix up what you scrape
	 * 
	 * @param cardname
	 *            The name of the card to extract an ID for
	 * @param cardPage
	 *            The Document to extract an ID from
	 * @return The string ID
	 */
	private static String getCardId(String cardname, Document cardPage) {

		Elements names = cardPage.getElementsByAttributeValueContaining("id", "nameRow");

		if (names.size() == 1) {
			return "";
		}

		for (int i = 0; i < names.size(); i++) {
			String id = names.get(i).getElementsByAttribute("id").first().attr("id");
			id = id.substring(id.length() - 14, id.length() - 7);

			Elements e2 = names.get(i).getElementsByAttributeValueContaining("class", "value");
			String text = cleanHtml(e2.outerHtml(), true);
			if (cardname.equalsIgnoreCase(text)) {
				return id;
			}
		}
		return "";
	}

	/**
	 * This function scrapes one field of a card at a time. Fields are denoted
	 * by attributeVal and subAttributeVal
	 * 
	 * @param cardPage
	 *            The Document to scrape part of the card from
	 * @param attributeVal
	 *            The attribute to scrape
	 * @param subAttributeVal
	 *            A sub-attribute to scrape, usually something boring like
	 *            "value" or "cardtextbox"
	 * @param removeNewlines
	 *            Should newlines be removed
	 * @return A String with the requested field, or null if it doesn't exist
	 */
	private static String getTextFromAttribute(Document cardPage, String attributeVal, String subAttributeVal,
			boolean removeNewlines) {
		try {
			Element ele = cardPage.getElementsByAttributeValueContaining("id", attributeVal).first();// get(position);
			Elements ele2 = ele.getElementsByAttributeValueContaining("class", subAttributeVal);

			return cleanHtml(ele2.outerHtml(), removeNewlines);
		}
		catch (NullPointerException e) {
			return null;
		}
		catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	/**
	 * This function processes HTML tags and cleans the HTML so that it is ready
	 * for a patch file. This includes maybe removing newlines, definitely
	 * removing whitespace, changing embedded images into {mana symbols}, and
	 * normalizing dashes
	 * 
	 * @param html
	 *            A String of HTML to clean
	 * @param removeNewlines
	 *            Should newlines be removed?
	 * @return The cleaned string
	 */
	private static String cleanHtml(String html, boolean removeNewlines) {
		boolean inTag = false;
		StringBuilder output = new StringBuilder();

		String tag = "";

		for (char c : html.toCharArray()) {
			switch (c) {
				case '<': {
					inTag = true;
					tag += c;
					break;
				}
				case '>': {
					/* Process the tag */
					tag += c;

					/* replace <div> tags with newlines */
					if (tag.matches(".*[\\s<]+div[\\s>]+.*")) {
						if (!removeNewlines && output.length() > 0) {
							output.append("<br>");
						}
					}
					else if (tag.contains("img src")) {
						String substr = tag.substring(tag.indexOf("name=") + 5);
						String symbol = substr.split("&")[0];
						if (symbol.equalsIgnoreCase("tap")) {
							symbol = "T";
						}
						else if (symbol.equalsIgnoreCase("untap")) {
							symbol = "Q";
						}
						else if (symbol.equalsIgnoreCase("snow")) {
							symbol = "S";
						}
						else if (symbol.equalsIgnoreCase("halfr")) {
							symbol = "HR";
						}
						else if (symbol.equalsIgnoreCase("halfw")) {
							symbol = "HW";
						}
						else if (symbol.equalsIgnoreCase("infinity")) {
							symbol = "+oo";
						}
						else if (symbol.toLowerCase().contains("chaos")) {
							symbol = "c";
						}
						output.append("{" + symbol + "}");
					}
					/* clear the tag */
					inTag = false;
					tag = "";
					break;
				}
				default: {
					if (c != '\r' && c != '\n') {
						if (inTag) {
							tag += c;
						}
						else {
							output.append(c);
						}
					}
				}
			}
		}
		return StringEscapeUtils.unescapeHtml4(output.toString()
		/* replace whitespace at the head and tail */
		.trim()
		/* remove whitespace around newlines */
		.replaceAll("[ \\t]*<br>[ \\t]*", "<br>")
		/* Condense spaces and tabs */
		.replaceAll("[ \\t]+", " ")
		/* remove whitespace between symbols */
		.replaceAll("\\}\\s+\\{", "\\}\\{")
		/* replace silly divider, planeswalker minus */
		.replaceAll("—", "-").replaceAll("−", "-"));
	}
}
