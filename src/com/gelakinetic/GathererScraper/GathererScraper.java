package com.gelakinetic.GathererScraper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

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
		Document gathererMain = ConnectWithRetries("http://gatherer.wizards.com/Pages/Default.aspx");
		Elements expansionElements = gathererMain.getElementsByAttributeValueContaining("name", "setAddText");

		for (int i = 0; i < expansionElements.size(); i++) {
			for (Element e : expansionElements.get(i).getAllElements()) {
				if (e.ownText().length() > 0) {
					expansions.add(new Expansion(e.ownText()));
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
	 * @param mAllMultiverseIds
	 * @param gathererScraperUi
	 *            The UI to post updates to
	 * @return An ArrayList of Card objects for all cards scraped
	 * @throws IOException
	 *             Thrown if the Internet breaks
	 */
	public static ArrayList<Card> scrapeExpansion(Expansion exp, GathererScraperUi ui, HashSet<Integer> mAllMultiverseIds) throws IOException {

		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			/* This should never happen */
			return null;
		}
		
		ArrayList<Card> cardsArray = new ArrayList<Card>();

		HashMap<String, Integer> multiverseMap = new HashMap<String, Integer>();
		
		/* Look for normal cards */
		for(int subSetNum = 0; subSetNum < exp.mSubSets.size(); subSetNum++) {
			int pageNum = 0;
			boolean loop = true;
			while (loop) {

				String urlStr = "http://gatherer.wizards.com/Pages/Search/Default.aspx?page=" + pageNum
						+ "&output=compact&action=advanced&special=true&set=+%5b%22"
						+ (new PercentEscaper("", true)).escape(exp.mSubSets.get(subSetNum)) + "%22%5d";

				Document individualExpansion = ConnectWithRetries(urlStr);

				Elements cards = individualExpansion.getElementsByAttributeValueContaining("id", "cardTitle");
				if (cards.size() == 0) {
					loop = false;
				}
				for (int i = 0; i < cards.size(); i++) {
					Element e = cards.get(i);
					Card card = new Card(e.ownText(), exp.mCode_gatherer, Integer.parseInt(e.attr("href").split("=")[1]));
					multiverseMap.put(card.mName, card.mMultiverseId);
					
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

		ArrayList<Card> scrapedCards = new ArrayList<Card>(cardsArray.size());
		for (Card c : cardsArray) {

			ArrayList<Card> tmpScrapedCards = scrapePage(Card.getUrl(c.mMultiverseId), exp, multiverseMap);
			
			if(tmpScrapedCards != null) {
				for(Card tmpCard : tmpScrapedCards) {
					if(!scrapedCards.contains(tmpCard)) {
						scrapedCards.add(tmpCard);
						mAllMultiverseIds.add(tmpCard.mMultiverseId);
					}
				}
				ui.setLastCardScraped(c.mExpansion + ": " + c.mName);
			}
		}

		if (scrapedCards.get(0).mNumber.length() < 1) {
			Collections.sort(scrapedCards);
			for (int i = 0; i < scrapedCards.size(); i++) {
				scrapedCards.get(i).mNumber = "" + (i + 1);
			}
		}
		
		/* Attempt to renumber consecutive cards with alt-art, but the same artist */
		Collections.sort(scrapedCards);
		for (int i = 0; i < scrapedCards.size() - 1; i++) {
			if( scrapedCards.get(i).mNumber.equals(scrapedCards.get(i+1).mNumber) &&
				scrapedCards.get(i).mName.equals(scrapedCards.get(i+1).mName)) {
				try {
					/* Adjust the number, resort the collection, step back the array
					 * This should properly number more than two of the same number
					 */
					try {
						if(scrapedCards.get(i+1).mExpansion.equals("ZEN")) {
							/* Increment the number in the string. MTGI's numbers for ZEN basics are weird */
							scrapedCards.get(i+1).mNumber = (Integer.parseInt(scrapedCards.get(i+1).mNumber) + 20) + "";														
						}
						else if(scrapedCards.get(i+1).mExpansion.equals("SVT")) {
							/* Increment the number in the string. MTGI's numbers for SVT basics are weird */
							scrapedCards.get(i+1).mNumber = (Integer.parseInt(scrapedCards.get(i+1).mNumber) + 43) + "";														
						}
						else {
							/* Increment the number in the string */
							scrapedCards.get(i+1).mNumber = (Integer.parseInt(scrapedCards.get(i+1).mNumber) + 1) + "";							
						}
					} catch(NumberFormatException e) {
						/* Guess it has a letter in there, increment that instead */
						char letter = scrapedCards.get(i+1).mNumber.charAt(
								scrapedCards.get(i+1).mNumber.length() - 1);
						scrapedCards.get(i+1).mNumber = scrapedCards.get(i+1).mNumber
								.substring(0, scrapedCards.get(i+1).mNumber.length() - 1) + (char)(letter+1);
					}
					
					Collections.sort(scrapedCards);
					i--;
				} catch (Exception e) {
					System.out.println(String.format("Muy Problemo [%3s] %s: %s",
							scrapedCards.get(i).mExpansion,
							scrapedCards.get(i).mName,
							e.toString()));
				}
			}
		}
		
		/* Debug check for cards with the same number */
		Collections.sort(scrapedCards);
		for (int i = 0; i < scrapedCards.size() - 1; i++) {
			if(scrapedCards.get(i).mNumber.equals(scrapedCards.get(i+1).mNumber)) {
				System.out.println(String.format("[%3s]\t%s & %s\t%s",
						scrapedCards.get(i).mExpansion,
						scrapedCards.get(i).mName,
						scrapedCards.get(i+1).mName,
						scrapedCards.get(i).mNumber));
			}
		}
		
		for(Card c : scrapedCards) {
			messageDigest.update(c.getBytes());
		}
		exp.mDigest = messageDigest.digest();
		return scrapedCards;
	}

	/**
	 * A little wrapper function to overcome any network hiccups
	 *
	 * @param urlStr The URL to get a Document from
	 * @return A Document, or null
	 */
	public static Document ConnectWithRetries(String urlStr) {
		int retries = 0;
		while (retries < 20) {
			try {
				return Jsoup.connect(urlStr).get();
			}
			catch(Exception e) {
				retries++;
			}
		}
		return null;
	}

	/**
	 * Scrape all cards off a given page
	 * @param cardUrl	The page to scrape
	 * @param exp		The expansion of the cards on this page
	 * @param multiverseMap	A map of card names to multiverse IDs
	 * @return	An array list of scraped cards
	 * @throws IOException Thrown if the Internet breaks
	 */
	private static ArrayList<Card> scrapePage(String cardUrl, Expansion exp, HashMap<String, Integer> multiverseMap) throws IOException {

		boolean usingGathererNumbers = false;

		/* Put all cards from all pages into this ArrayList */
		ArrayList<Card> scrapedCardsAllPages = new ArrayList<Card>();

		/* Download this page, add it to the collection */
		ArrayList<Document> cardPages = new ArrayList<Document>();
		cardPages.add(ConnectWithRetries(cardUrl));

		/* Get all the multiverse IDs of all printings */
		ArrayList<Integer> mIds = getPrintingMultiverseIds(cardPages.get(0));
		/* If there are alternate printings */
		if(mIds != null) {
			/* For all printings */
			for(Integer mId : mIds) {
				/* If we haven't downloaded this page yet */
				String newUrl = Card.getUrl(mId);
				if(!newUrl.equals(cardUrl)) {
					/* Download it */
					cardPages.add(ConnectWithRetries(Card.getUrl(mId)));
				}
			}
		}

//		System.out.println(cardUrl);
		
		for(Document cardPage : cardPages) {
			
//			System.out.println("\t" + cardPage.baseUri());
			int mId = Integer.parseInt(cardPage.baseUri().substring(cardPage.baseUri().lastIndexOf("=") + 1));

			/* Put all cards from this page into this ArrayList */
			ArrayList<Card> scrapedCards = new ArrayList<Card>();

			/* Get all cards on this page */
			HashMap<String, String> ids = getCardIds(cardPage);
	
			/* For all cards on this page, grab their information */
			for(String name : ids.keySet()) {
				
//				System.out.println("\t\t" + name);

				Card card;
				if(cardPages.size() > 1) {
					/* Pick the multiverseID out of the URL */
					card = new Card(name, exp.mCode_gatherer, mId);
				}
				else {
					/* Pick the multiverseID out of the hashmap built from the card list */
					card = new Card(name, exp.mCode_gatherer, multiverseMap.get(name));
				}
				
				/* Get the ID for this card's information */
				String id = ids.get(name);
	
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
				card.mFlavor = getTextFromAttribute(cardPage, id + "FlavorText", "flavortextbox", false);
				if(card.mFlavor == null || card.mFlavor.equals("")) {
					card.mFlavor = getTextFromAttribute(cardPage, id + "FlavorText", "cardtextbox", false);
				}
	
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
					/* Basic lands aren't technically common, but the app doesn't
					 * understand "Land"
					 */
					card.mRarity = "Common";
				}
				else if (card.mRarity.equalsIgnoreCase("Special")) {
					/* Planechase, Promos, Vanguards */
					card.mRarity = "Rare";
				}
	
				/* artist */
				card.mArtist = getTextFromAttribute(cardPage, id + "ArtistCredit", "value", true);
	
				/* Number */
				/* Try grabbing the number from magiccards.info first. It's more accurate than Gatherer */
				if(card.mNumber == null || card.mNumber.equals("")) {
					try {
						/* This line gets the image URL from a name, set code, and artist */
						String url = ConnectWithRetries("http://magiccards.info/query?q=\"" + card.mName.replace(" ", "+") +
								"\"+e%3A"+exp.mCode_mtgi+"%2Fen+a%3A\"" + card.mArtist.replace(" ", "+") + "\"")
								.getElementsByAttributeValue("alt", card.mName).get(0).attr("src");
	
						/* This picks the number out of the URL */
						card.mNumber = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("."));
					}
					catch(Exception e) {
						/* Eat it */
					}
				}
				
				/* If that fails, try again, but without the artist this time */
				if(card.mNumber == null || card.mNumber.equals("")) {
					try {
						/* This line gets the image URL from a name, set code */
						String url = ConnectWithRetries("http://magiccards.info/query?q=\"" + card.mName.replace(" ", "+") +
								"\"+e%3A"+exp.mCode_mtgi+"%2Fen")
								.getElementsByAttributeValue("alt", card.mName).get(0).attr("src");
	
						/* This picks the number out of the URL */
						card.mNumber = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("."));
					}
					catch(Exception e) {
						/* Eat it */
					}
				}
	
				/* If the mtgi lookup failed, try gatherer second */
				if(card.mNumber == null || card.mNumber.equals("")) {
					card.mNumber = getTextFromAttribute(cardPage, id + "numberRow", "value", true);
					usingGathererNumbers = true;
				}
	
				if(card.mNumber == null || card.mNumber.equals("")) {
					System.out.println(String.format("[%3s]\t%s\tNo Number Found",
							card.mExpansion,
							card.mName));
				}
				
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
				
				/* If the card has no color, or it's Ghostfire, or it has Devoid */
				if (card.mColor.isEmpty() || card.mName.equals("Ghostfire") ||
						(card.mText != null && card.mText.contains("(This card has no color.)"))) {
					card.mColor = "C";
				}
	
				/* Because the ORI walkers don't have color listed... */
				if(card.mExpansion.equals("ORI")) {
					if(card.mName.contains("Gideon, Battle-Forged")) {
						card.mColor = "W";
					}
					else if(card.mName.contains("Jace, Telepath Unbound")) {
						card.mColor = "U";
					}
					else if(card.mName.contains("Liliana, Defiant Necromancer")) {
						card.mColor = "B";
					}
					else if(card.mName.contains("Chandra, Roaring Flame")) {
						card.mColor = "R";
					}
					else if(card.mName.contains("Nissa, Sage Animist")) {
						card.mColor = "G";
					}
				}
	
				card.clearNulls();
				scrapedCards.add(card);
			}
			
			/*
			 * Since Gatherer seems to be non-deterministic, if we are using their
			 * numbers, and this is a multicard, sort the card parts and renumber
			 * the letters alphabetically 
			 */
			if(scrapedCards.size() > 1 && usingGathererNumbers) {
				Collections.sort(scrapedCards, Card.getNameComparator());
				for(int i = 0; i < scrapedCards.size(); i++) {
					scrapedCards.get(i).mNumber = scrapedCards.get(i).mNumber.replaceAll("[A-Za-z]", ((char)('a' + i)) + "");
				}
			}
			
			scrapedCardsAllPages.addAll(scrapedCards);
		}
		return scrapedCardsAllPages;
	}

	/**
	 * Get all IDs for all cards on a given page. This usually returns one ID
	 * in the HashMap, but will return two for split, double faced, or flip cards
	 *
	 * @param cardPage
	 *            The Document to extract an ID from
	 * @return All the IDs on this page
	 */
	private static HashMap<String, String> getCardIds(Document cardPage) {

		HashMap<String, String> ids = new HashMap<String, String>(2);

		/* Get all names on this page */
		Elements names = cardPage.getElementsByAttributeValueContaining("id", "nameRow");

		/* For each name, get the ID */
		for (int i = 0; i < names.size(); i++) {
			/* Get the actual ID */
			String id = names.get(i).getElementsByAttribute("id").first().attr("id");
			id = id.substring(id.length() - 14, id.length() - 7);

			/* Get the actual card name */
			Elements e2 = names.get(i).getElementsByAttributeValueContaining("class", "value");
			String name = cleanHtml(e2.outerHtml(), true);

			/* Store the name & ID combo */
			ids.put(name, id);
		}
		return ids;
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
	 * TODO
	 * @param cardPage
	 * @return
	 */
	private static ArrayList<Integer> getPrintingMultiverseIds(Document cardPage) {
		ArrayList<Integer> multiverseIds = new ArrayList<Integer>();
		try {
			Element ele = cardPage.getElementsByAttributeValueContaining("id", "VariationLinks").first();// get(position);
			Elements ele2 = ele.getElementsByAttributeValueContaining("class", "VariationLink");

			for(Element e : ele2) {
				multiverseIds.add(Integer.parseInt(e.attr("id")));
			}
			return multiverseIds;
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

	/**
	 * Clean up a comprehensive rules file. Wizards likes non-ascii chars
	 *
	 * @param rulesFile		The file to clean
	 * @throws IOException 	Thrown if something goes wrong
	 */
	public static String cleanRules(File rulesFile) throws IOException, NullPointerException {
		/* Save any post-formatting lines with non-ascii chars here */
		StringBuilder problematicLines = new StringBuilder();
		/* Open up file in & out */
		BufferedReader br = new BufferedReader(new FileReader(rulesFile));
		FileWriter fw = new FileWriter(rulesFile.getAbsolutePath() + ".clean");
		/* Read the file, one line at a time */
		String line;
		while ((line = br.readLine()) != null) {
			/* Clean and write the line */
			line = removeNonAscii(line);
			fw.write(line + "\r\n");
			/* If the line still has any non-ascii chars, note it */
			if(line.matches(".*[^\\x00-\\x7F].*")) {
				problematicLines.append(line + "\r\n");
			};
		}
		/* Clean up */
		fw.close();
		br.close();
		/* Return any lines with non-ascii chars */
		return problematicLines.toString();
	}

	/**
	 * Replaces known non-ascii chars in a string with ascii equivalents
	 * @param line	The string to clean up
	 * @return		The cleaned up string
	 */
	private static String removeNonAscii(String line) {
		String replacements[][] =
			{{"’", "'"},
			{"®", "(R)"},
			{"™", "(TM)"},
			{"“", "\""},
			{"”", "\""},
			{"—", "-"},
			{"–", "-"},
			{"‘", "'"},
			{"â", "a"},
			{"á", "a"},
			{"ú", "u"},
			{"û", "u"},
			{"Æ", "Ae"},
			{"©", "(C)"}};
		/* Loop through all the known replacements and perform them */
		for(String[] replaceSet : replacements) {
			line = line.replaceAll(replaceSet[0], replaceSet[1]);
		}
		return line;
	}
}
