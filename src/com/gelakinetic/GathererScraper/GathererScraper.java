package com.gelakinetic.GathererScraper;

import com.gelakinetic.GathererScraper.JsonTypes.Card;
import com.gelakinetic.GathererScraper.JsonTypes.Card.ForeignPrinting;
import com.gelakinetic.GathererScraper.JsonTypes.Patch;
import com.gelakinetic.GathererScraper.JsonTypesGS.CardGS;
import com.gelakinetic.GathererScraper.JsonTypesGS.ExpansionGS;
import com.gelakinetic.mtgfam.helpers.database.CardDbAdapter;
import com.google.common.net.PercentEscaper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class is filled with static functions which do the actual scraping from
 * Gatherer. It first gets a list of expansions, then gets lists of cards for
 * the expansions, and finally scrapes the individual cards.
 *
 * @author AEFeinstein
 */
public class GathererScraper {

    public static final String PATCH_DIR = "patches-v2";
    private static final Pattern BATTLEBOND_PATTERN = Pattern.compile("Partner with ([^\\(<]+)\\s*[\\(<]");
    
    /**
     * This function scrapes a list of all expansions from Gatherer
     *
     * @return An ArrayList of Expansion objects for all potential expansions to
     * scrape
     * @throws IOException Thrown if the Internet breaks
     */
    public static ArrayList<ExpansionGS> scrapeExpansionList() {
        ArrayList<ExpansionGS> expansions = new ArrayList<>();
        Document gathererMain = ConnectWithRetries("http://gatherer.wizards.com/Pages/Default.aspx");
        Elements expansionElements = gathererMain.getElementsByAttributeValueContaining("name", "setAddText");

        for (Element expansionElement : expansionElements) {
            for (Element e : expansionElement.getAllElements()) {
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
     * @param exp               The expansion to scrape
     * @param mAllMultiverseIds
     * @param gathererScraperUi The UI to post updates to
     * @return An ArrayList of CardGS objects for all cards scraped
     * @throws IOException Thrown if the Internet breaks
     */
    public static ArrayList<CardGS> scrapeExpansion(ExpansionGS exp, GathererScraperUi ui, HashSet<Integer> mAllMultiverseIds) {

        MessageDigest messageDigest;
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
            InputStreamReader oldInputStreamReader = new InputStreamReader(oldGZIPInputStream, StandardCharsets.UTF_8);

            Gson gson = GathererScraper.getGson();

            Patch patch = gson.fromJson(oldInputStreamReader, Patch.class);

            cachedCollectorsNumbers = new HashMap<>();
            for (Card card : patch.mCards) {
                /* Name is the key, collectors number is the value */
                cachedCollectorsNumbers.put(card.mMultiverseId + card.mName, card.mNumber);
            }
        } catch (Exception e) {
            System.err.println("Couldn't open old patch for " + exp.mName_gatherer);
        }

        ArrayList<CardGS> cardsArray = new ArrayList<>();

        HashMap<String, Integer> multiverseMap = new HashMap<>();

        /* Look for normal cards */
        int pageNum = 0;
        boolean loop = true;
        while (loop) {

            String tmpName = exp.mName_gatherer;
            /* Un-ascii Conspiracy */
            if (tmpName.equals("Magic: The Gathering-Conspiracy")) {
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
            for (Element e : cards) {
                CardGS card = new CardGS(e.ownText(), exp.mCode_gatherer, Integer.parseInt(e.attr("href").split("=")[1]));
                multiverseMap.put(card.mName, card.mMultiverseId);

                if (cardsArray.contains(card)) {
                    loop = false;
                } else {
                    cardsArray.add(card);
                }
            }
            pageNum++;
        }

        ArrayList<CardGS> scrapedCards = new ArrayList<>(cardsArray.size());
        for (CardGS c : cardsArray) {

            ArrayList<CardGS> tmpScrapedCards = scrapePage(CardGS.getUrl(c.mMultiverseId), exp, multiverseMap, cachedCollectorsNumbers);

            if (tmpScrapedCards != null) {
                for (CardGS tmpCard : tmpScrapedCards) {
                    if (!scrapedCards.contains(tmpCard)) {
                        scrapedCards.add(tmpCard);
                        mAllMultiverseIds.add(tmpCard.mMultiverseId);
                    }
                }
                ui.setLastCardScraped(c.mExpansion + ": " + c.mName);
            }
        }

        // Now that all the cards and multiverse IDs are known, linkify text
        for(CardGS card : scrapedCards) {
            card.mText = linkifyText(card.mText, exp.mCode_gatherer, scrapedCards);
        }
        
        if (scrapedCards.isEmpty()) {
            System.err.print("Scrape failed " + exp.mName_gatherer);
        } else if (scrapedCards.get(0).mNumber.length() < 1) {
            Collections.sort(scrapedCards);
            for (int i = 0; i < scrapedCards.size(); i++) {
                scrapedCards.get(i).mNumber = "" + (i + 1);
            }
        }

        /* Attempt to renumber consecutive cards with alt-art, but the same artist */
        try {
            Collections.sort(scrapedCards);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println(exp.mName_gatherer + " SORT FAILURE");
        }
        for (int i = 0; i < scrapedCards.size() - 1; i++) {
            if (scrapedCards.get(i).mNumber.equals(scrapedCards.get(i + 1).mNumber) &&
                    scrapedCards.get(i).mName.equals(scrapedCards.get(i + 1).mName)) {
                try {
                    /* Adjust the number, resort the collection, step back the array
                     * This should properly number more than two of the same number
                     */
                    boolean changed = false;
                    try {
                        switch (scrapedCards.get(i + 1).mExpansion) {
                            case "ZEN":
                                /* Increment the number in the string. MTGI's numbers for ZEN basics are weird */
                                scrapedCards.get(i + 1).mNumber = (Integer.parseInt(scrapedCards.get(i + 1).mNumber) + 20) + "";
                                changed = true;
                                break;
                            case "SVT":
                                /* Increment the number in the string. MTGI's numbers for SVT basics are weird */
                                scrapedCards.get(i + 1).mNumber = (Integer.parseInt(scrapedCards.get(i + 1).mNumber) + 43) + "";
                                changed = true;
                                break;
                            default:
                                /* Do nothing. Allow other cards to have the same number */
                                break;
                        }
                    } catch (NumberFormatException e) {
                        /* Guess it has a letter in there, increment that instead */
                        char letter = scrapedCards.get(i + 1).mNumber.charAt(
                                scrapedCards.get(i + 1).mNumber.length() - 1);
                        scrapedCards.get(i + 1).mNumber = scrapedCards.get(i + 1).mNumber
                                .substring(0, scrapedCards.get(i + 1).mNumber.length() - 1) + (char) (letter + 1);
                        changed = true;
                    }

                    if(changed) {
                        Collections.sort(scrapedCards);
                        i--;
                    }
                } catch (Exception e) {
                    System.out.println(String.format("Muy Problemo [%3s] %s: %s",
                            scrapedCards.get(i).mExpansion,
                            scrapedCards.get(i).mName,
                            e.toString()));
                }
            }
        }

        /* Calculate color identities. This is done here because both halves of split cards must be known */
        calculateColorIdentities(scrapedCards);

        /* Debug check for cards with the same number */
        Collections.sort(scrapedCards);
        for (int i = 0; i < scrapedCards.size() - 1; i++) {
            if (scrapedCards.get(i).mNumber.equals(scrapedCards.get(i + 1).mNumber)) {
                System.out.println(String.format("[%3s]\t%s & %s\t%s",
                        scrapedCards.get(i).mExpansion,
                        scrapedCards.get(i).mName,
                        scrapedCards.get(i + 1).mName,
                        scrapedCards.get(i).mNumber));
            }
        }

        Gson gson = new Gson();
        for (CardGS c : scrapedCards) {
            messageDigest.update(gson.toJson(c).getBytes());
        }
        exp.mDigest = null;
        messageDigest.update(gson.toJson(exp).getBytes());

        byte byteDigest[] = messageDigest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : byteDigest) {
            sb.append(String.format("%02x", b));
        }
        exp.mDigest = sb.toString();

        return scrapedCards;
    }

    /**
     * TODO document
     *
     * @param tmpScrapedCards
     */
    private static void calculateColorIdentities(ArrayList<CardGS> tmpScrapedCards) {
        for (CardGS card : tmpScrapedCards) {
            card.calculateColorIdentity(tmpScrapedCards);
        }
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
                // Note to self. If this stops working, wireshark a regular request from chrome and copy the cookie (and other fields)
                return Jsoup
                        .connect(urlStr)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                        .header("Pragma", "no-cache")
                        .header("Cache-Control", "no-cache")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("DNT", "1")
                        .header("Accept-Language", "en-US,en;q=0.8")
                        .header("Cookie", "f5_cspm=1234; f5_cspm=1234; BIGipServerWWWNetPool02=4111468810.20480.0000; CardDatabaseSettings=1=en-US; _ga=GA1.2.1294897467.1509075187; _gid=GA1.2.838335687.1510109719; ASP.NET_SessionId=; __utmt=1; __utma=28542179.1294897467.1509075187.1510152850.1510184901.4; __utmb=28542179.1.10.1510184901; __utmc=28542179; __utmz=28542179.1510109911.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); f5avr1559183795aaaaaaaaaaaaaaaa=CHILKMHBENHPFFICIBHJKDGFPJAMDMHJJPPNJCEEANLNJMLMJNBKKFELMNEKNKFDHDICANOFDFDHNLJHINLABDKABADNIKGENJNFPFEMGGJPCENBGKLPAFOIBCDONJFM")
                        .timeout(0)
                        .get();
            } catch (Exception e) {
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
     *
     * @param cardUrl                 The page to scrape
     * @param exp                     The expansion of the cards on this page
     * @param multiverseMap           A map of card names to multiverse IDs
     * @param cachedCollectorsNumbers A map of card names + multiverseID to collector's numbers
     * @return An array list of scraped cards
     * @throws IOException Thrown if the Internet breaks
     */
    private static ArrayList<CardGS> scrapePage(String cardUrl, ExpansionGS exp,
                                                HashMap<String, Integer> multiverseMap,
                                                HashMap<String, String> cachedCollectorsNumbers) {
    	/* Keep track of a letter for multiple printings with the same name */
    	char ustLetter = 'a';
    	
        /* Put all cards from all pages into this ArrayList */
        ArrayList<CardGS> scrapedCardsAllPages = new ArrayList<>();

        /* Download this page, add it to the collection */
        ArrayList<Document> cardPages = new ArrayList<>();
        cardPages.add(ConnectWithRetries(cardUrl));

        /* Get all the multiverse IDs of all printings */
        ArrayList<Integer> mIds = getPrintingMultiverseIds(cardPages.get(0));
        /* If there are alternate printings */
        if (mIds != null) {
            /* For all printings */
        	Collections.sort(mIds);
            for (Integer mId : mIds) {
                /* If we haven't downloaded this page yet */
                String newUrl = CardGS.getUrl(mId);
                if (!newUrl.equals(cardUrl)) {
                    /* Download it */
                    cardPages.add(ConnectWithRetries(CardGS.getUrl(mId)));
                }
            }
        }

        for (Document cardPage : cardPages) {

            int mId = Integer.parseInt(cardPage.baseUri().substring(cardPage.baseUri().lastIndexOf("=") + 1));

            /* Put all cards from this page into this ArrayList */
            ArrayList<CardGS> scrapedCards = new ArrayList<>();

            /* Get all cards on this page */
            HashMap<String, String> ids = getCardIds(cardPage, "[" + exp.mCode_gatherer + "] ");
            
            /* For all cards on this page, grab their information */
            for (String name : ids.keySet()) {

                String errLabel = "[" + exp.mCode_gatherer + "] " + name;

                /* Sea Eagle was never printed in 9E, but Gatherer returns it... */
                if("Sea Eagle".equals(name) && "9E".equals(exp.mCode_gatherer)) {
                    /* Return the empty set */
                    return scrapedCardsAllPages;
                }

                CardGS card;
                if (cardPages.size() > 1) {
                    /* Pick the multiverseID out of the URL */
                    card = new CardGS(name, exp.mCode_gatherer, mId);
                } else {
                    /* Pick the multiverseID out of the hashmap built from the card list */
                    card = new CardGS(name, exp.mCode_gatherer, multiverseMap.get(name));
                }
                
                // Special handling for Battlebond Alt-Art planeswalkers
                if(card.mName.equals("Rowan Kenrith")) {
                	if(card.mMultiverseId != 445970 && card.mMultiverseId != 446224) {
                		continue;
                	}
                } else if(card.mName.equals("Will Kenrith")) {
                	if(card.mMultiverseId != 445969 && card.mMultiverseId != 446223) {
                		continue;
                	}
                }
                
                /* Get the ID for this card's information */
                String id = ids.get(name);
    
                /* Mana Cost */
                card.mManaCost = getTextFromAttribute(cardPage, id + "manaRow", "value", true, errLabel);
    
                /* Converted Mana Cost */
                try {
                    card.mCmc = Integer.parseInt(getTextFromAttribute(cardPage, id + "cmcRow", "value", true, errLabel));
                } catch (NumberFormatException e) {
                    card.mCmc = 0;
                }
    
                /* Type */
                card.mType = getTextFromAttribute(cardPage, id + "typeRow", "value", true, errLabel);
    
                /* Ability Text */
                card.mText = getTextFromAttribute(cardPage, id + "textRow", "cardtextbox", false, errLabel);
    
                /* For unglued, fix some symbols */
                if ((card.mExpansion.equals("UG") || card.mExpansion.equals("UNH")) &&
                        (null != card.mText)) {
                    card.mText = card.mText
                            .replace("oW", "{W}")
                            .replace("oU", "{U}")
                            .replace("oB", "{B}")
                            .replace("oR", "{R}")
                            .replace("oG", "{G}")
                            .replace("ocT", "{T}")
                            .replace("oX", "{X}")
                            .replace("o1", "{1}")
                            .replace("o2", "{2}")
                            .replace("o3", "{3}")
                            .replace("o4", "{4}")
                            .replace("o7", "{7}");
                }
                
                /* Flavor */
                card.mFlavor = getTextFromAttribute(cardPage, id + "FlavorText", "flavortextbox", false, errLabel);
                if (card.mFlavor == null || card.mFlavor.equals("")) {
                    card.mFlavor = getTextFromAttribute(cardPage, id + "FlavorText", "cardtextbox", false, errLabel);
                }
    
                /* PT */
                String pt = getTextFromAttribute(cardPage, id + "ptRow", "value", true, errLabel);

                if (card.mExpansion.equals("VNG")) {
                    /* this row is the life & hand modifier for vanguard */
                    card.mText += "<br><br><br>" + pt;
                    card.mPower = CardDbAdapter.NO_ONE_CARES;
                    card.mToughness = CardDbAdapter.NO_ONE_CARES;
                    card.mLoyalty = CardDbAdapter.NO_ONE_CARES;
                } else {

                    if (pt != null) {
                        if (pt.contains("/")) {
                        	if(card.mName.equals("Rhino-")) {
                        		pt = "+1 / +4";
                        	}
                        	else if(card.mName.equals("Half-Shark, Half-")) {
                        		pt = "+3 / +3";                        		
                        	}
                            String power = pt.replace("{1/2}", ".5").replace("½", ".5").split("/")[0].trim();
                            card.mPower = PTLstringToFloat(power, errLabel);
     
                            String toughness = pt.replace("{1/2}", ".5").replace("½", ".5").split("/")[1].trim();
                            card.mToughness = PTLstringToFloat(toughness, errLabel);
                        } else if ("Urza, Academy Headmaster".equals(card.mName)){
                            card.mLoyalty = 4;
                        } else {
                            card.mLoyalty = (int) PTLstringToFloat(pt.trim(), errLabel);
                        }
                    }
                }
    
                /* Rarity */
                String rarity = getTextFromAttribute(cardPage, id + "rarityRow", "value", true, errLabel);
                if (rarity.isEmpty()) {
                    /* Edge case for promotional cards */
                    card.mRarity = 'R';
                } else if (card.mExpansion.equals("TSB")) {
                    /* They say Special, I say Timeshifted */
                    card.mRarity = 'T';
                } else if (rarity.toLowerCase().contains("land")) {
                    /* Basic lands aren't technically common, but the app doesn't
                     * understand "Land"
                     */
                    card.mRarity = 'C';
                } else if (rarity.equalsIgnoreCase("Special")) {
                    /* Planechase, Promos, Vanguards */
                    card.mRarity = 'R';
                } else if (rarity.equalsIgnoreCase("Bonus")) {
                    /* Vintage Masters P9 cards */
                    card.mRarity = 'M';
                } else {
                    card.mRarity = rarity.charAt(0);
                }
                
                switch (card.mRarity) {
                    case 'C':
                    case 'U':
                    case 'R':
                    case 'M':
                    case 'T': {
                        break;
                    }
                    default: {
                        System.err.println(errLabel +  " Unknown Rarity: " + card.mRarity);
                    }
                }
    
                /* artist */
                card.mArtist = getTextFromAttribute(cardPage, id + "ArtistCredit", "value", true, errLabel);

                /* artist */
                card.mWatermark = getTextFromAttribute(cardPage, id + "markRow", "value", true, errLabel);

                /* Number */
                /* Try pulling the card number out of the cache first */
                if (cachedCollectorsNumbers != null) {
                    card.mNumber = cachedCollectorsNumbers.get(card.mMultiverseId + card.mName);
                    if (card.mNumber == null) {
                        card.mNumber = cachedCollectorsNumbers.get(card.mMultiverseId + card.mName.replace("Ae", "Æ").replace("ae", "æ"));
                    }
                }
                
                /* If that didn't work, try getting it from Gatherer */
                if (card.mNumber == null || card.mNumber.equals("")) {
                    card.mNumber = getTextFromAttribute(cardPage, id + "numberRow", "value", true, errLabel);
                    
                    /* Clean up Unstable numbers. Thanks Wizards */
                    if(card.mExpansion.equals("UST")) {
                    	if(null != mIds && !mIds.isEmpty()) {
                    		/* This is for the five UST commons with alternate art */
                    		card.mNumber += ustLetter;
                    		ustLetter++;
                    	} else if (card.mName.contains("(")) {
                    		/* This is for the UST cards which have alternate text */
                    		char letter = card.mName.charAt(card.mName.indexOf("(") + 1);
                    		if('a' <= letter && letter <= 'f') {
                    			card.mNumber += letter;
                    		}
                    	} else if (card.mName.contains("Killbot")) {
                    		/* And this is for the Killbots */
                    		if(card.mName.contains("Curious")) {
                    			card.mNumber += 'a';
                    		}
                    		else if(card.mName.contains("Delighted")) {
                    			card.mNumber += 'b';                    			
                    		}
                    		else if(card.mName.contains("Despondent")) {
                    			card.mNumber += 'c';
                    		}
                    		else if(card.mName.contains("Enraged")) {
                    			card.mNumber += 'd';
                    		}
                    	}
                    }
                }
    
                /* If that didn't work, print a warning */
                if (card.mNumber == null || card.mNumber.equals("")) {
                    System.err.println(errLabel + " No Number Found");
                } else if (exp.mCode_gatherer.equals("BBD")) {
                	// Battlebond cards are paired on Gatherer, but don't really have numbers
                	card.mNumber = card.mNumber.replaceAll("a", "").replaceAll("b", "");
                }
                
                /* Manually override some numbers because Gatherer is trash */
                if (card.mExpansion.equals("EMN")) {
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
                } else if (card.mExpansion.equals("V17")) {
                    switch (card.mName) {
                        case "Bruna, the Fading Light":
                            card.mNumber = "5a";
                            break;
                        case "Gisela, the Broken Blade":
                            card.mNumber = "10a";
                            break;
                        case "Brisela, Voice of Nightmares":
                            card.mNumber = "5b";
                            break;
                    }
                }
                
                /* color, calculated */
                String color = getTextFromAttribute(cardPage, id + "colorIndicatorRow", "value", true, errLabel);
                StringBuilder colorBuilder = new StringBuilder();
                if (card.mType.contains("Artifact")) {
                    colorBuilder.append("A");
                }
                if (card.mType.contains("Land")) {
                    colorBuilder.append("L");
                }
                if (color != null) {
                    if (color.contains("White")) {
                        colorBuilder.append("W");
                    }
                    if (color.contains("Blue")) {
                        colorBuilder.append("U");
                    }
                    if (color.contains("Black")) {
                        colorBuilder.append("B");
                    }
                    if (color.contains("Red")) {
                        colorBuilder.append("R");
                    }
                    if (color.contains("Green")) {
                        colorBuilder.append("G");
                    }
                } else if (card.mManaCost != null) {
                    if (card.mManaCost.contains("W")) {
                        colorBuilder.append("W");
                    }
                    if (card.mManaCost.contains("U")) {
                        colorBuilder.append("U");
                    }
                    if (card.mManaCost.contains("B")) {
                        colorBuilder.append("B");
                    }
                    if (card.mManaCost.contains("R")) {
                        colorBuilder.append("R");
                    }
                    if (card.mManaCost.contains("G")) {
                        colorBuilder.append("G");
                    }
                }
                card.mColor = colorBuilder.toString();
                
                /* If the card has no color, or it's Ghostfire, or it has Devoid */
                if (card.mColor.isEmpty() || card.mName.equals("Ghostfire") ||
                        (card.mText != null && card.mText.contains("(This card has no color.)"))) {
                    card.mColor = "C";
                }

                //Scrape foreign language page, scrapping the name and the multiverse id of the card in foreign languages.
                scrapeLanguage(card.mMultiverseId, card.mForeignPrintings, errLabel);
                Collections.sort(card.mForeignPrintings);

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
            
            /* If this is a multicard */
            if (scrapedCards.size() == 2) {
                /* Check to see if one CMC is 0 and the other is greater than 0 */
                int cmc0 = scrapedCards.get(0).mCmc;
                int cmc1 = scrapedCards.get(1).mCmc;
                /* Also make sure this is a transform card */
                if (cmc0 == 0 && cmc1 > 0 && scrapedCards.get(1).mText.toLowerCase().contains("transform")) {
                    /* Give the back face the same cmc as the front face */
                    scrapedCards.get(0).mCmc = scrapedCards.get(1).mCmc;
                } else if (cmc0 > 0 && cmc1 == 0 && scrapedCards.get(0).mText.toLowerCase().contains("transform")) {
                    /* Give the back face the same cmc as the front face */
                    scrapedCards.get(1).mCmc = scrapedCards.get(0).mCmc;
                }

                for (CardGS card : scrapedCards) {
                    switch (card.mName) {
                        case "Brisela, Voice of Nightmares":
                            card.mCmc = 11;
                            break;
                        case "Chittering Host":
                            card.mCmc = 7;
                            break;
                        case "Hanweir, the Writhing Township":
                            card.mCmc = 3;
                            break;
                    }
                }
            }

            
            scrapedCardsAllPages.addAll(scrapedCards);
        }
        return scrapedCardsAllPages;
    }

    /**
     * Given a string power, toughness, or loyalty, convert it into a float
     * 
     * @param value The string value to convert
     * @param errLabel A label to print in case of error
     * @return the converted float value
     */
	private static float PTLstringToFloat(String value, String errLabel) {
		switch (value) {
			case "*": {
				return CardDbAdapter.STAR;
			}
			case "1+*": {
				return CardDbAdapter.ONE_PLUS_STAR;
			}
			case "7-*": {
				return CardDbAdapter.SEVEN_MINUS_STAR;
			}
			case "2+*": {
				return CardDbAdapter.TWO_PLUS_STAR;
			}
			case "*{^2}": {
				return CardDbAdapter.STAR_SQUARED;
			}
			case "X": {
				return CardDbAdapter.X;
			}
			case "∞": {
				return CardDbAdapter.INFINITY;
			}
			case "?": {
				return CardDbAdapter.QUESTION_MARK;
			}
			default: {
				try {
					return Float.parseFloat(value);
				} catch (NumberFormatException e) {
					System.err.println(errLabel + " PTL Fail, " + e.getMessage());
					return 0;
				}
			}
		}
	}

	/**
     * Scrape the Language Gatherer page of the card with the english multiverse id given in the params.
     *
     * @param englishMultiverseId  The english multiverse ID of the card for which we will scrape the foreign language infos.
     * @param foreignNames         A HashMap where will be added the foreign names of the card, indexed by their language code.
     * @param foreignMultiverseIds A HashMap where will be added the foreign multiverse ID of the card, indexed by their language code.
     * @param errLabel a label to print in case of error
     */
    private static void scrapeLanguage(
            int englishMultiverseId, ArrayList<Card.ForeignPrinting> foreignPrintings, String errLabel) {
        if (englishMultiverseId == 0 || foreignPrintings == null) {
            return;
        }

        /* Start the loop at page 0 */
        int pageNum = 0;
        boolean foreignPrintingAdded = true;
        boolean hasMultiplePages = true;

        while (foreignPrintingAdded && hasMultiplePages) {
            Document page = ConnectWithRetries(CardGS.getLanguageUrl(englishMultiverseId, pageNum));
            Elements languageElements = page.getElementsByAttributeValueContaining("class", "cardItem");
            
            /* If there are multiple pages, we'll need to loop again */
            hasMultiplePages = !page.getElementsByAttributeValueContaining("id", "pagingControlsParent").isEmpty();
            
            /* No need to loop again, there's nothing on this page */
            if (languageElements.isEmpty()) {
                break;
            }
    
            /* Try to add each element */
            for (Element elt : languageElements) {
                ForeignPrinting fp = (new Card()).new ForeignPrinting();

                String language = elt.child(1).html();
                switch (language) {
                    case "English":
                        fp.mLanguageCode = Language.English;
                        break;
                    case "German":
                        fp.mLanguageCode = Language.German;
                        break;
                    case "French":
                        fp.mLanguageCode = Language.French;
                        break;
                    case "Japanese":
                        fp.mLanguageCode = Language.Japanese;
                        break;
                    case "Portuguese (Brazil)":
                        fp.mLanguageCode = Language.Portuguese_Brazil;
                        break;
                    case "Russian":
                        fp.mLanguageCode = Language.Russian;
                        break;
                    case "Chinese Traditional":
                        fp.mLanguageCode = Language.Chinese_Traditional;
                        break;
                    case "Chinese Simplified":
                        fp.mLanguageCode = Language.Chinese_Simplified;
                        break;
                    case "Korean":
                        fp.mLanguageCode = Language.Korean;
                        break;
                    case "Italian":
                        fp.mLanguageCode = Language.Italian;
                        break;
                    case "Spanish":
                        fp.mLanguageCode = Language.Spanish;
                        break;
                    default:
                        System.err.println(errLabel + " Unknown language: " + language);
                        continue;
                }

                fp.mName = elt.child(0).text();
                fp.mMultiverseId = Integer.parseInt(elt.child(0).child(0).attr("href").split("=")[1]);
                if (!foreignPrintings.contains(fp)) {
                    foreignPrintings.add(fp);
                } else {
                    /* Duplicate, which means WotC served the same page twice and we're done */
                    foreignPrintingAdded = false;
                    break;
                }
            }
            pageNum++;
        }
    }


    /**
     * Add links to the text to handle meld cards
     *
     * @param mText The card text without links
     * @param scrapedCards 
     * @param code_gatherer 
     * @return The card text with links
     */
    private static String linkifyText(String mText, String code_gatherer, ArrayList<CardGS> scrapedCards) {
        if (mText == null) {
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
        
        mText = mText.replace("AskUrza.com", "<a href=\"http://www.AskUrza.com\">AskUrza.com</a>");
        
        // Try to linkify Battlebond cards
        if(code_gatherer.equals("BBD")) {
        	// Use a regex to find the Partner name
            Matcher matcher = BATTLEBOND_PATTERN.matcher(mText);
            if (matcher.find()) {
            	String otherName = matcher.group(1).trim();
            	int otherMultiverseId = -1;
            	// Look through the cards scraped on this page to find the other card's multiverse ID
            	for(CardGS card : scrapedCards) {
            		if(card.mName.equals(otherName)) {
            			otherMultiverseId = card.mMultiverseId;
            			break;
            		}
            	}
            	// If the multiverse ID was found, linkify!
            	if(otherMultiverseId > 0) {
            		 mText = mText.replaceFirst(otherName, uriLink(otherName, otherMultiverseId));
            	}
            }
        }
        
        return mText;
    }

    private static String uriLink(String string, int i) {
        return "<a href=\"card://multiverseid/internal/" + i + "\">" + string + "</a>";
    }

    /**
     * Get all IDs for all cards on a given page. This usually returns one ID
     * in the HashMap, but will return two for split, double faced, or flip cards
     *
     * @param cardPage The Document to extract an ID from
     * @param errLabel A label to print in case of error
     * @return All the IDs on this page
     */
    private static HashMap<String, String> getCardIds(Document cardPage, String errLabel) {

        HashMap<String, String> ids = new HashMap<>(2);

        /* Get all names on this page */
        Elements names = cardPage.getElementsByAttributeValueContaining("id", "nameRow");

        /* For each name, get the ID */
        for (Element name : names) {
            /* Get the actual ID */
            String id = name.getElementsByAttribute("id").first().attr("id");
            id = id.substring(id.length() - 14, id.length() - 7);

            /* Get the actual card name */
            Elements e2 = name.getElementsByAttributeValueContaining("class", "value");
            String stringName = cleanHtml(e2.outerHtml(), true, errLabel);

            /* Store the name & ID combo */
            ids.put(stringName, id);
        }
        return ids;
    }

    /**
     * This function scrapes one field of a card at a time. Fields are denoted
     * by attributeVal and subAttributeVal
     *
     * @param cardPage        The Document to scrape part of the card from
     * @param attributeVal    The attribute to scrape
     * @param subAttributeVal A sub-attribute to scrape, usually something boring like
     *                        "value" or "cardtextbox"
     * @param removeNewlines  Should newlines be removed
     * @param errLabel A label to print in case of error
     * @return A String with the requested field, or null if it doesn't exist
     */
    private static String getTextFromAttribute(Document cardPage, String attributeVal, String subAttributeVal,
                                               boolean removeNewlines, String errLabel) {
        try {
            Element ele = cardPage.getElementsByAttributeValueContaining("id", attributeVal).first();// get(position);
            Elements ele2 = ele.getElementsByAttributeValueContaining("class", subAttributeVal);

            return cleanHtml(ele2.outerHtml(), removeNewlines, errLabel);
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * TODO
     *
     * @param cardPage
     * @return
     */
    private static ArrayList<Integer> getPrintingMultiverseIds(Document cardPage) {
        ArrayList<Integer> multiverseIds = new ArrayList<>();
        try {
            Element ele = cardPage.getElementsByAttributeValueContaining("id", "VariationLinks").first();// get(position);
            Elements ele2 = ele.getElementsByAttributeValueContaining("class", "VariationLink");

            for (Element e : ele2) {
                multiverseIds.add(Integer.parseInt(e.attr("id")));
            }
            return multiverseIds;
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * This function processes HTML tags and cleans the HTML so that it is ready
     * for a patch file. This includes maybe removing newlines, definitely
     * removing whitespace, changing embedded images into {mana symbols}, and
     * normalizing dashes
     *
     * @param html           A String of HTML to clean
     * @param removeNewlines Should newlines be removed?
     * @param errLabel A label to print in case of error
     * @return The cleaned string
     */
    private static String cleanHtml(String html, boolean removeNewlines, String errLabel) {
        boolean inTag = false;
        StringBuilder output = new StringBuilder();

        StringBuilder tag = new StringBuilder();

        for (char c : html.toCharArray()) {
            switch (c) {
                case '<': {
                    inTag = true;
                    tag.append(c);
                    break;
                }
                case '>': {
                    /* Process the tag */
                    tag.append(c);

                    /* replace <div> tags with newlines */
                    if (tag.toString().matches(".*[\\s<]+div[\\s>]+.*")) {
                        if (!removeNewlines && output.length() > 0) {
                            output.append("<br>");
                        }
                    } else if (tag.toString().contains("img src")) {
                        String substr = tag.substring(tag.indexOf("name=") + 5);
                        String symbol = substr.split("&")[0];
                        switch (symbol.toLowerCase()) {
                            case "tap":
                                symbol = "T";
                                break;
                            case "untap":
                                symbol = "Q";
                                break;
                            case "snow":
                                symbol = "S";
                                break;
                            case "halfr":
                                symbol = "HR";
                                break;
                            case "halfw":
                            case "500":
                                symbol = "HW";
                                break;
                            case "infinity":
                                symbol = "+oo";
                            case "w":
                            case "u":
                            case "b":
                            case "r":
                            case "g":
                            case "wu":
                            case "uw":
                            case "ub":
                            case "bu":
                            case "br":
                            case "rb":
                            case "rg":
                            case "gr":
                            case "gw":
                            case "wg":
                            case "wb":
                            case "bw":
                            case "bg":
                            case "gb":
                            case "gu":
                            case "ug":
                            case "ur":
                            case "ru":
                            case "rw":
                            case "wr":
                            case "2w":
                            case "w2":
                            case "2u":
                            case "u2":
                            case "2b":
                            case "b2":
                            case "2r":
                            case "r2":
                            case "2g":
                            case "g2":
                            case "pw":
                            case "wp":
                            case "pu":
                            case "up":
                            case "pb":
                            case "bp":
                            case "pr":
                            case "rp":
                            case "pg":
                            case "gp":
                            case "p":
                            case "c":
                            case "chaos":
                            case "z":
                            case "y":
                            case "x":
                            case "h":
                            case "pwk":
                            case "e":
                                // Known symbols which don't need tweaking
                                break;
                            default:
                                if (!StringUtils.isNumeric(symbol)) {
                                    System.err.println(errLabel + " Unknown symbol: " + symbol);
                                }
                                break;
                        }
                        output.append("{").append(symbol).append("}");
                    }
                    /* clear the tag */
                    inTag = false;
                    tag = new StringBuilder();
                    break;
                }
                default: {
                    if (c != '\r' && c != '\n') {
                        if (inTag) {
                            tag.append(c);
                        } else {
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
     * @param rulesFile The file to clean
     * @throws IOException Thrown if something goes wrong
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
            if (line.matches(".*[^\\x00-\\x7F].*")) {
                problematicLines.append(line).append("\r\n");
            }
        }
        /* Clean up */
        fw.close();
        br.close();
        /* Return any lines with non-ascii chars */
        return problematicLines.toString();
    }

    /**
     * Replaces known non-ascii chars in a string with ascii equivalents
     *
     * @param line The string to clean up
     * @return The cleaned up string
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
                        {"©", "(C)"},
                        {"•", "*"},
                        {"…", "..."}};
             /* Loop through all the known replacements and perform them */
        for (String[] replaceSet : replacements) {
            line = line.replaceAll(replaceSet[0], replaceSet[1]);
        }
        return line;
    }

    /**
     * Write the json object to a json file, UTF-8, Unix line endings
     *
     * @param json    The JSON object to write
     * @param outFile The file to write to
     * @throws IOException Thrown if the write fails
     */
    static void writeFile(Object object, File outFile, boolean shouldZip) throws IOException {
        System.setProperty("line.separator", "\n");
        OutputStream fos;

        if (shouldZip) {
            fos = new GZIPOutputStream(new FileOutputStream(outFile));
        } else {
            fos = new FileOutputStream(outFile);
        }

        OutputStreamWriter osw = new OutputStreamWriter(fos, Charset.forName("UTF-8"));

        if (object instanceof String) {
            osw.write(((String) object).replace("\r", ""));
        } else {
            osw.write(GathererScraper.getGson().toJson(object).replace("\r", ""));
        }

        osw.flush();
        osw.close();
    }
}
