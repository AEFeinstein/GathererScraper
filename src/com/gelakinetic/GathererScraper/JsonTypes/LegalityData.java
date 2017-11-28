package com.gelakinetic.GathererScraper.JsonTypes;

import java.util.ArrayList;

public class LegalityData {

    public Format mFormats[];
    public long mTimestamp;

    public class Format {
        public String mName;
        public final ArrayList<String> mSets = new ArrayList<>();
        public final ArrayList<String> mRestrictedlist = new ArrayList<>();
        public final ArrayList<String> mBanlist = new ArrayList<>();
    }
}

