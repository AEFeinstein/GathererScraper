package com.gelakinetic.GathererScraper.JsonTypes;

import java.util.ArrayList;

public class Manifest {

    public long mTimestamp;
    public final ArrayList<ManifestEntry> mPatches = new ArrayList<>();

    public class ManifestEntry implements Comparable<ManifestEntry>{
        public String mName;
        public String mURL;
        public String mCode;
        public String mDigest;
        
		@Override
		public int compareTo(ManifestEntry o) {
			return mName.compareTo(o.mName);
		}
    }

}
