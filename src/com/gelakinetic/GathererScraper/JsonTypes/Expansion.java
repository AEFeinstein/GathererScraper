package com.gelakinetic.GathererScraper.JsonTypes;

import java.util.ArrayList;

/*
 * This class contains all information about an expansion to be parsed
 *
 * @author AEFeinstein
 *
 */
public class Expansion {

    // Name used by Gatherer
    public String mName_gatherer = "";

    // expansion code used by Gatherer
    public String mCode_gatherer = "";

    // expansion code used by magiccards.info
    public String mCode_mtgi = "";

    // expansion mName used by TCGPlayer.com
    public String mName_tcgp = "";

    // expansion name used by MagicCardMarket.eu
    public String mName_mkm = "";

    // Date the expansion was released
    public long mReleaseTimestamp = 0;

    // Whether or not this expansion has foil cards
    public boolean mCanBeFoil = false;
    
    // Whether this expansion is online-only or has paper printings
    public boolean mIsOnlineOnly = false;
    
    // The color of the border, either Black, White, or Silver
    public String mBorderColor = "";

    // MD5 digest for scraped cards, to see when things change
    public String mDigest = "";
    
    // List of image URLs
    public ArrayList<String> mExpansionImageURLs = new ArrayList<>();
}
