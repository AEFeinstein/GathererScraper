package com.gelakinetic.GathererScraper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.gelakinetic.GathererScraper.JsonTypes.Card;
import com.gelakinetic.GathererScraper.JsonTypes.Patch;
import com.gelakinetic.GathererScraper.JsonTypesGS.CardGS;
import com.gelakinetic.GathererScraper.JsonTypesGS.ExpansionGS;
import com.gelakinetic.mtgfam.helpers.database.CardDbAdapter;
import com.google.common.net.PercentEscaper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This class is filled with static functions which do the actual scraping from
 * Gatherer. It first gets a list of expansions, then gets lists of cards for
 * the expansions, and finally scrapes the individual cards.
 *
 * @author AEFeinstein
 *
 */
public class GathererScraper {

	public static final String PATCH_DIR = "patches-v2";
	
	/**
	 * This function scrapes a list of all expansions from Gatherer
	 *
	 * @return An ArrayList of Expansion objects for all potential expansions to
	 *         scrape
	 * @throws IOException
	 *             Thrown if the Internet breaks
	 */
	public static ArrayList<ExpansionGS> scrapeExpansionList() throws IOException {
		ArrayList<ExpansionGS> expansions = new ArrayList<ExpansionGS>();
		Document gathererMain = ConnectWithRetries("http://gatherer.wizards.com/Pages/Default.aspx");
		Elements expansionElements = gathererMain.getElementsByAttributeValueContaining("name", "setAddText");

		for (int i = 0; i < expansionElements.size(); i++) {
			for (Element e : expansionElements.get(i).getAllElements()) {
				if (e.ownText().length() > 0) {
					expansions.add(new ExpansionGS(e.ownText()));
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
	 * @return An ArrayList of CardGS objects for all cards scraped
	 * @throws IOException
	 *             Thrown if the Internet breaks
	 */
	public static ArrayList<CardGS> scrapeExpansion(ExpansionGS exp, GathererScraperUi ui, HashSet<Integer> mAllMultiverseIds) throws IOException {

		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			/* This should never happen */
			return null;
		}
		
		
		/* Get the card numbers from the old patch, just in case */
		HashMap<String, String> cachedCollectorsNumbers = null;
		try {
			File oldPatchFile = new File(PATCH_DIR, exp.mCode_gatherer + ".json.gzip");
			FileInputStream oldFileInputStream = new FileInputStream(oldPatchFile);
			GZIPInputStream oldGZIPInputStream = new GZIPInputStream(oldFileInputStream);
			InputStreamReader oldInputStreamReader = new InputStreamReader(oldGZIPInputStream);
			
			Gson gson = GathererScraper.getGson();
			
			Patch patch = gson.fromJson(oldInputStreamReader, Patch.class);

			cachedCollectorsNumbers = new HashMap<String, String>();
			for(Card card : patch.mCards) {
				/* Name is the key, collectors number is the value */
				cachedCollectorsNumbers.put(card.mMultiverseId + card.mName, card.mNumber);
			}
		}
		catch(Exception e) {
			System.err.println("Couldn't open old patch for " + exp.mName_gatherer);
		}
		
		ArrayList<CardGS> cardsArray = new ArrayList<CardGS>();

		HashMap<String, Integer> multiverseMap = new HashMap<String, Integer>();
		
		/* Look for normal cards */
		int pageNum = 0;
		boolean loop = true;
		while (loop) {

			String tmpName = exp.mName_gatherer;
			/* Un-ascii Conspiracy */
			if(tmpName.equals("Magic: The Gathering-Conspiracy")) {
				tmpName = "Magic: The Gathering—Conspiracy";
			}
			String urlStr = "http://gatherer.wizards.com/Pages/Search/Default.aspx?page=" + pageNum
					+ "&output=compact&action=advanced&set=%5b%22"
					+ (new PercentEscaper("", true)).escape(tmpName) + "%22%5d&special=true";

			Document individualExpansion = ConnectWithRetries(urlStr);

			Elements cards = individualExpansion.getElementsByAttributeValueContaining("id", "cardTitle");
			if (cards.size() == 0) {
				loop = false;
			}
			for (int i = 0; i < cards.size(); i++) {
				Element e = cards.get(i);
				CardGS card = new CardGS(e.ownText(), exp.mCode_gatherer, Integer.parseInt(e.attr("href").split("=")[1]));
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

		ArrayList<CardGS> scrapedCards = new ArrayList<CardGS>(cardsArray.size());
		for (CardGS c : cardsArray) {

			ArrayList<CardGS> tmpScrapedCards = scrapePage(CardGS.getUrl(c.mMultiverseId), exp, multiverseMap, cachedCollectorsNumbers);
			
			if(tmpScrapedCards != null) {
				for(CardGS tmpCard : tmpScrapedCards) {
					if(!scrapedCards.contains(tmpCard)) {
						scrapedCards.add(tmpCard);
						mAllMultiverseIds.add(tmpCard.mMultiverseId);
					}
				}
				ui.setLastCardScraped(c.mExpansion + ": " + c.mName);
			}
		}

		if (scrapedCards.isEmpty()) {
			System.out.print("Scrape failed " + exp.mName_gatherer);
		}
		else if (scrapedCards.get(0).mNumber.length() < 1) {
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
		
		for(CardGS c : scrapedCards) {
			messageDigest.update(c.getBytes());
		}
		
		byte byteDigest[] = messageDigest.digest();
		StringBuilder sb = new StringBuilder();
		for(byte b : byteDigest) {
			sb.append(String.format("%02x", b));
		}
		exp.mDigest = sb.toString();
		
		return scrapedCards;
	}

	public static Gson getGson() {
		GsonBuilder reader = new GsonBuilder();
		reader.setFieldNamingStrategy((new PrefixedFieldNamingStrategy("m")));
		reader.disableHtmlEscaping();
		reader.setPrettyPrinting();
		return reader.create();
	}

	/**
	 * A little wrapper function to overcome any network hiccups
	 *
	 * @param urlStr The URL to get a Document from
	 * @return A Document, or null
	 */
	public static Document ConnectWithRetries(String urlStr) {
		int retries = 0;
		while (retries < Integer.MAX_VALUE - 1) {
			try {
				return Jsoup.connect(urlStr).timeout(0).get();
			}
			catch(Exception e) {
				retries++;
				try {
					Thread.sleep(1000 * retries);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
		return null;
	}

	/**
	 * Scrape all cards off a given page
	 * @param cardUrl	The page to scrape
	 * @param exp		The expansion of the cards on this page
	 * @param multiverseMap	A map of card names to multiverse IDs
	 * @param cachedCollectorsNumbers  A map of card names + multiverseID to collector's numbers
	 * @return	An array list of scraped cards
	 * @throws IOException Thrown if the Internet breaks
	 */
	private static ArrayList<CardGS> scrapePage(String cardUrl, ExpansionGS exp,
			HashMap<String, Integer> multiverseMap,
			HashMap<String, String> cachedCollectorsNumbers) throws IOException {

		/* Put all cards from all pages into this ArrayList */
		ArrayList<CardGS> scrapedCardsAllPages = new ArrayList<CardGS>();

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
				String newUrl = CardGS.getUrl(mId);
				if(!newUrl.equals(cardUrl)) {
					/* Download it */
					cardPages.add(ConnectWithRetries(CardGS.getUrl(mId)));
				}
			}
		}

//		System.out.println(cardUrl);
		
		for(Document cardPage : cardPages) {
			
//			System.out.println("\t" + cardPage.baseUri());
			int mId = Integer.parseInt(cardPage.baseUri().substring(cardPage.baseUri().lastIndexOf("=") + 1));

			/* Put all cards from this page into this ArrayList */
			ArrayList<CardGS> scrapedCards = new ArrayList<CardGS>();

			/* Get all cards on this page */
			HashMap<String, String> ids = getCardIds(cardPage);
	
			/* For all cards on this page, grab their information */
			for(String name : ids.keySet()) {
				
//				System.out.println("\t\t" + name);

				CardGS card;
				if(cardPages.size() > 1) {
					/* Pick the multiverseID out of the URL */
					card = new CardGS(name, exp.mCode_gatherer, mId);
				}
				else {
					/* Pick the multiverseID out of the hashmap built from the card list */
					card = new CardGS(name, exp.mCode_gatherer, multiverseMap.get(name));
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
				card.mText = linkifyText(card.mText);
	
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
					card.mPower = CardDbAdapter.NO_ONE_CARES;
					card.mToughness = CardDbAdapter.NO_ONE_CARES;
					card.mLoyalty = CardDbAdapter.NO_ONE_CARES;
				}
				else {
					if (pt != null) {
						if (pt.contains("/")) {
							String power = pt.replace("{1/2}", ".5").split("/")[0].trim();
							switch(power) {
								case "*": {
									card.mPower = CardDbAdapter.STAR;
									break;
								}
								case "1+*": {
									card.mPower = CardDbAdapter.ONE_PLUS_STAR;
									break;
								}
								case "7-*": {
									card.mPower = CardDbAdapter.SEVEN_MINUS_STAR;
									break;
								}
								case "2+*": {
									card.mPower = CardDbAdapter.TWO_PLUS_STAR;
									break;
								}
								case "*{^2}": {
									card.mPower = CardDbAdapter.STAR_SQUARED;
									break;
								}
								default: {
									card.mPower = Float.parseFloat(power);
									
								}
							}
							String toughness = pt.replace("{1/2}", ".5").split("/")[1].trim();
							switch(toughness) {
								case "*": {
									card.mToughness = CardDbAdapter.STAR;
									break;
								}
								case "1+*": {
									card.mToughness = CardDbAdapter.ONE_PLUS_STAR;
									break;
								}
								case "7-*": {
									card.mToughness = CardDbAdapter.SEVEN_MINUS_STAR;
									break;
								}
								case "2+*": {
									card.mToughness = CardDbAdapter.TWO_PLUS_STAR;
									break;
								}
								case "*{^2}": {
									card.mToughness = CardDbAdapter.STAR_SQUARED;
									break;
								}
								default: {
									card.mToughness = Float.parseFloat(toughness);
									
								}
							}
						}
						else {
							card.mLoyalty = Integer.parseInt(pt.trim());
						}
					}
				}
	
				/* Rarity */
				String rarity = getTextFromAttribute(cardPage, id + "rarityRow", "value", true);
				if(rarity.isEmpty()) {
					/* Edge case for promotional cards */
					card.mRarity = 'R';
				}
				else if (card.mExpansion.equals("TSB")) {
					/* They say Special, I say Timeshifted */
					card.mRarity = 'T';
				}
				else if (rarity.equalsIgnoreCase("Land")) {
					/* Basic lands aren't technically common, but the app doesn't
					 * understand "Land"
					 */
					card.mRarity = 'C';
				}
				else if (rarity.equalsIgnoreCase("Special")) {
					/* Planechase, Promos, Vanguards */
					card.mRarity = 'R';
				}
				else {
					card.mRarity = rarity.charAt(0);
				}
	
				/* artist */
				card.mArtist = getTextFromAttribute(cardPage, id + "ArtistCredit", "value", true);
	
				/* Number */
				/* Try pulling the card number out of the cache first */
				if(cachedCollectorsNumbers != null) {
					card.mNumber = cachedCollectorsNumbers.get(card.mMultiverseId + card.mName);
					if(card.mNumber == null) {
						card.mNumber = cachedCollectorsNumbers.get(card.mMultiverseId + card.mName.replace("Ae", "Æ").replace("ae", "æ"));						
					}
				}
				
				/* If that didn't work, try getting it from Gatherer */
				if(card.mNumber == null || card.mNumber.equals("")) {
					card.mNumber = getTextFromAttribute(cardPage, id + "numberRow", "value", true);
				}
	
				/* If that didn't work, print a warning */
				if(card.mNumber == null || card.mNumber.equals("")) {
					System.out.println(String.format("[%3s]\t%s\tNo Number Found",
							card.mExpansion,
							card.mName));
				}
				
				/* Manually override some numbers because Gatherer is trash */
				switch (card.mName) {
				case "Bruna, the Fading Light":
					card.mNumber = "15a";
					break;
				case "Gisela, the Broken Blade":
					card.mNumber = "28a";
					break;
				case "Brisela, Voice of Nightmares":
					card.mNumber = "28b";
					break;
				case "Graf Rats":
					card.mNumber = "91a";
					break;
				case "Midnight Scavengers":
					card.mNumber = "96a";
					break;
				case "Chittering Host":
					card.mNumber = "91b";
					break;
				case "Hanweir Battlements":
					card.mNumber = "204a";
					break;
				case "Hanweir Garrison":
					card.mNumber = "130a";
					break;
				case "Hanweir, the Writhing Township":
					card.mNumber = "204b";
					break;
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
	
				/* Because Wizards stopped listing color for backface cards... */
				if(card.mExpansion.equals("ORI")) {
					if(card.mName.equals("Gideon, Battle-Forged")) {
						card.mColor = "W";
					}
					else if(card.mName.equals("Jace, Telepath Unbound")) {
						card.mColor = "U";
					}
					else if(card.mName.equals("Liliana, Defiant Necromancer")) {
						card.mColor = "B";
					}
					else if(card.mName.equals("Chandra, Roaring Flame")) {
						card.mColor = "R";
					}
					else if(card.mName.equals("Nissa, Sage Animist")) {
						card.mColor = "G";
					}
				}
				else if (card.mExpansion.equals("SOI")) {
					if (card.mName.equals("Ancient of the Equinox")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Arlinn, Embraced by the Moon")) {
						card.mColor = "RG";
					}
					else if (card.mName.equals("Avacyn, the Purifier")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Awoken Horror")) {
						card.mColor = "U";
					}
					else if (card.mName.equals("Bearer of Overwhelming Truths")) {
						card.mColor = "U";
					}
					else if (card.mName.equals("Branded Howler")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Demon-Possessed Witch")) {
						card.mColor = "B";
					}
					else if (card.mName.equals("Flameheart Werewolf")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Gatstaf Ravagers")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Heir to the Night")) {
						card.mColor = "B";
					}
					else if (card.mName.equals("Incited Rabble")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Infectious Curse")) {
						card.mColor = "B";
					}
					else if (card.mName.equals("Insidious Mist")) {
						card.mColor = "U";
					}
					else if (card.mName.equals("Krallenhorde Howler")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Lambholt Butcher")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Lone Wolf of the Natterknolls")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Lunarch Inquisitors")) {
						card.mColor = "W";
					}
					else if (card.mName.equals("Moonrise Intruder")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Neck Breaker")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("One of the Pack")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Ormendahl, Profane Prince")) {
						card.mColor = "B";
					}
					else if (card.mName.equals("Perfected Form")) {
						card.mColor = "U";
					}
					else if (card.mName.equals("Persistent Nightmare")) {
						card.mColor = "U";
					}
					else if (card.mName.equals("Skin Shedder")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Timber Shredder")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Unimpeded Trespasser")) {
						card.mColor = "U";
					}
					else if (card.mName.equals("Vildin-Pack Alpha")) {
						card.mColor = "R";
					}
					else if (card.mName.equals("Wayward Disciple")) {
						card.mColor = "B";
					}
					else if (card.mName.equals("Werewolf of Ancient Hunger")) {
						card.mColor = "G";
					}
					else if (card.mName.equals("Westvale Cult Leader")) {
						card.mColor = "W";
					}
				}
				
				//Scrape foreign language page, scrapping the name and the multiverse id of the card in foreign languages.
				HashMap<String, String> foreignNames = new HashMap<>();
				HashMap<String, Integer> foreignMultiverseIds = new HashMap<>();
				scrapeLanguage(card.mMultiverseId, foreignNames, foreignMultiverseIds);
				card.mForeignMultiverseIds.putAll(foreignMultiverseIds);
				card.mForeignNames.putAll(foreignNames);
	
				card.clearNulls();
				scrapedCards.add(card);
			}
			
			/*
			 * Since Gatherer seems to be non-deterministic, if we are using their
			 * numbers, and this is a multicard, sort the card parts and renumber
			 * the letters alphabetically 
			 */
			/* Edit: Seems to be better now, commenting out instead of deleting just in case
			if(scrapedCards.size() > 1 && usingGathererNumbers) {
				Collections.sort(scrapedCards, CardGS.getNameComparator());
				for(int i = 0; i < scrapedCards.size(); i++) {
					scrapedCards.get(i).mNumber = scrapedCards.get(i).mNumber.replaceAll("[A-Za-z]", ((char)('a' + i)) + "");
				}
			}
			*/
			scrapedCardsAllPages.addAll(scrapedCards);
		}
		return scrapedCardsAllPages;
	}
	
	/**
	 * Scrape the Language Gatherer page of the card with the english multiverse id given in the params. 
	 * @param englishMultiverseId The english multiverse ID of the card for which we will scrape the foreign language infos. 
	 * @param foreignNames A HashMap where will be added the foreign names of the card, indexed by their language code.
	 * @param foreignMultiverseIds A HashMap where will be added the foreign multiverse ID of the card, indexed by their language code.
	 */
	private static void scrapeLanguage(
			int englishMultiverseId,
			HashMap<String, String> foreignNames,
			HashMap<String, Integer> foreignMultiverseIds) {	
		if(englishMultiverseId == 0 || foreignNames == null || foreignMultiverseIds == null)
			return;

		Document page = ConnectWithRetries(CardGS.getLanguageUrl(englishMultiverseId));
		Elements elts = page.getElementsByAttributeValueContaining("class", "cardItem");
		
		for(Element elt : elts) {
			String language = elt.child(1).html();
			if(language.equals("English")) language = Language.English;
			else if(language.equals("German")) language = Language.German;
			else if(language.equals("French")) language = Language.French;
			else if(language.equals("Japanese")) language = Language.Japanese;
			else if(language.equals("Portuguese (Brazil)")) language = Language.Portuguese_Brazil;
			else if(language.equals("Russian")) language = Language.Russian;
			else if(language.equals("Chinese Traditional")) language = Language.Chinese_Traditional;
			else if(language.equals("Chinese Simplified")) language = Language.Chinese_Simplified;
			else if(language.equals("Korean")) language = Language.Korean;
			else if(language.equals("Italian")) language = Language.Italian;
			else if(language.equals("Spanish")) language = Language.Spanish;
			else continue;
			
			String name = elt.child(0).text();
			String multiverseId = elt.child(0).child(0).attr("href").split("=")[1];
			
			foreignMultiverseIds.put(language, Integer.parseInt(multiverseId));
			foreignNames.put(language, name);
		}
	}


	/**
	 * Add links to the text to handle meld cards
	 * @param mText The card text without links
	 * @return The card text with links
	 */
	private static String linkifyText(String mText) {
		if(mText == null) {
			return null;
		}
		mText = mText.replace("Bruna, the Fading Light", uriLink("Bruna, the Fading Light", 414304));
		mText = mText.replace("Gisela, the Broken Blade", uriLink("Gisela, the Broken Blade", 414319));
		mText = mText.replace("Brisela, Voice of Nightmares", uriLink("Brisela, Voice of Nightmares", 414305));

		mText = mText.replace("Graf Rats", uriLink("Graf Rats", 414386));
		mText = mText.replace("Midnight Scavengers", uriLink("Midnight Scavengers", 414391));
		mText = mText.replace("Chittering Host", uriLink("Chittering Host", 414392));

		mText = mText.replace("Hanweir Battlements", uriLink("Hanweir Battlements", 414511));
		mText = mText.replace("Hanweir Garrison", uriLink("Hanweir Garrison", 414428));
		mText = mText.replace("Hanweir, the Writhing Township", uriLink("Hanweir, the Writhing Township", 414429));
		return mText;
	}

	private static CharSequence uriLink(String string, int i) {
		return "<a href=\"card://multiverseid/internal/" + i + "\">" + string + "</a>";
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
						} else if (symbol.equalsIgnoreCase("untap")) {
							symbol = "Q";
						} else if (symbol.equalsIgnoreCase("snow")) {
							symbol = "S";
						} else if (symbol.equalsIgnoreCase("halfr")) {
							symbol = "HR";
						} else if (symbol.equalsIgnoreCase("halfw")
								|| symbol.equalsIgnoreCase("500")) {
							symbol = "HW";
						} else if (symbol.equalsIgnoreCase("infinity")) {
							symbol = "+oo";
						} else if (symbol.equalsIgnoreCase("w")) {
						} else if (symbol.equalsIgnoreCase("u")) {
						} else if (symbol.equalsIgnoreCase("b")) {
						} else if (symbol.equalsIgnoreCase("r")) {
						} else if (symbol.equalsIgnoreCase("g")) {
						} else if (symbol.equalsIgnoreCase("wu")
								|| symbol.equalsIgnoreCase("uw")) {
						} else if (symbol.equalsIgnoreCase("ub")
								|| symbol.equalsIgnoreCase("bu")) {
						} else if (symbol.equalsIgnoreCase("br")
								|| symbol.equalsIgnoreCase("rb")) {
						} else if (symbol.equalsIgnoreCase("rg")
								|| symbol.equalsIgnoreCase("gr")) {
						} else if (symbol.equalsIgnoreCase("gw")
								|| symbol.equalsIgnoreCase("wg")) {
						} else if (symbol.equalsIgnoreCase("wb")
								|| symbol.equalsIgnoreCase("bw")) {
						} else if (symbol.equalsIgnoreCase("bg")
								|| symbol.equalsIgnoreCase("gb")) {
						} else if (symbol.equalsIgnoreCase("gu")
								|| symbol.equalsIgnoreCase("ug")) {
						} else if (symbol.equalsIgnoreCase("ur")
								|| symbol.equalsIgnoreCase("ru")) {
						} else if (symbol.equalsIgnoreCase("rw")
								|| symbol.equalsIgnoreCase("wr")) {
						} else if (symbol.equalsIgnoreCase("2w")
								|| symbol.equalsIgnoreCase("w2")) {
						} else if (symbol.equalsIgnoreCase("2u")
								|| symbol.equalsIgnoreCase("u2")) {
						} else if (symbol.equalsIgnoreCase("2b")
								|| symbol.equalsIgnoreCase("b2")) {
						} else if (symbol.equalsIgnoreCase("2r")
								|| symbol.equalsIgnoreCase("r2")) {
						} else if (symbol.equalsIgnoreCase("2g")
								|| symbol.equalsIgnoreCase("g2")) {
						} else if (symbol.equalsIgnoreCase("pw")
								|| symbol.equalsIgnoreCase("wp")) {
						} else if (symbol.equalsIgnoreCase("pu")
								|| symbol.equalsIgnoreCase("up")) {
						} else if (symbol.equalsIgnoreCase("pb")
								|| symbol.equalsIgnoreCase("bp")) {
						} else if (symbol.equalsIgnoreCase("pr")
								|| symbol.equalsIgnoreCase("rp")) {
						} else if (symbol.equalsIgnoreCase("pg")
								|| symbol.equalsIgnoreCase("gp")) {
						} else if (symbol.equalsIgnoreCase("p")) {
						} else if (symbol.equalsIgnoreCase("c")) {
						} else if (symbol.equalsIgnoreCase("chaos")) {
						} else if (symbol.equalsIgnoreCase("z")) {
						} else if (symbol.equalsIgnoreCase("y")) {
						} else if (symbol.equalsIgnoreCase("x")) {
						} else if (symbol.equalsIgnoreCase("h")) {
						} else if (symbol.equalsIgnoreCase("pwk")) {
						} else if (symbol.equalsIgnoreCase("e")) {
						} else if (StringUtils.isNumeric(symbol)) {
						} else {
							System.out.println("Unknown symbol: " + symbol);
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
		.replaceAll("\\}\\s+\\{", "\\}\\{"))
		/* replace silly divider, planeswalker minus */
		.replaceAll("—", "-").replaceAll("−", "-");
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
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(rulesFile), "Cp1252"));
		BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(rulesFile.getAbsolutePath() + ".clean"), "UTF8"));
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
	static String removeNonAscii(String line) {
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
			{"æ", "ae"},
			{"©", "(C)"}};
			 /* Loop through all the known replacements and perform them */
		for(String[] replaceSet : replacements) {
			line = line.replaceAll(replaceSet[0], replaceSet[1]);
		}
		return line;
	}
	
	/**
	 * Write the json object to a json file, UTF-8, Unix line endings
	 * 
	 * @param json
	 *            The JSON object to write
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             Thrown if the write fails
	 */
	static void writeFile(Object object, File outFile) throws IOException {
		System.setProperty("line.separator", "\n");
		OutputStreamWriter osw = new OutputStreamWriter(
				new FileOutputStream(outFile), Charset.forName("UTF-8"));

		if(object instanceof String) {
			osw.write(((String)object).replace("\r", ""));			
		}
		else {
			osw.write(GathererScraper.getGson().toJson(object).replace("\r", ""));
		}
		
		osw.flush();
		osw.close();
	}
}
