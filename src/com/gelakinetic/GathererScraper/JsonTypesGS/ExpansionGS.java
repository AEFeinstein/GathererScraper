package com.gelakinetic.GathererScraper.JsonTypesGS;

import com.gelakinetic.GathererChecker.RssEntry;
import com.gelakinetic.GathererScraper.JsonTypes.Expansion;

/**
 * This class contains all information about an expansion to be parsed
 *
 * @author AEFeinstein
 */
public class ExpansionGS extends Expansion implements Comparable<ExpansionGS> {

    /**
     * To scrape, or not to scrape ?
     */
    public transient Boolean mChecked = false;

    /**
     * The most basic constructor for an expansion. Only sets the gatherer name
     *
     * @param name_gatherer The name of this expansion on Gatherer
     */
    public ExpansionGS(String name_gatherer) {
        mName_gatherer = name_gatherer;
    }


    @Override
    public int compareTo(ExpansionGS o) {
        return this.mName_gatherer.compareTo(o.mName_gatherer);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExpansionGS) {
            return this.mName_gatherer.equals(((ExpansionGS) obj).mName_gatherer);
        } else if (obj instanceof RssEntry) {
            return this.mName_gatherer.equals(((RssEntry) obj).mTitle);
        }
        return false;
    }

    /**
     * Use the Gatherer code as a proxy if this expansion was scraped or not
     *
     * @return true if the gatherer code exists, false if it does not
     */
    public boolean isScraped() {
        return mCode_gatherer != null && !mCode_gatherer.isEmpty();
    }


}
