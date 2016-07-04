package com.gelakinetic.GathererScraper;

public class JsonPatch {
	JsonCardDatabase t;
}

class JsonCardDatabase {
	int w; /* num cards */
	JsonExpansions s;
	JsonCards p;
}

class JsonExpansions {
	JsonExpansion[] b;
}

class JsonExpansion {
	String y; /* date */
	String r; /* code_magiccards */
	String q; /* code */
	String a; /* name */
	Boolean z; /* can be foil */
}

class JsonCards {
	JsonCard[] o;
}

class JsonCard {
	String a; /* mName */
	String b; /* mExpansion */
	String c; /* mType */
	String d; /* mRarity */
	String e; /* mManaCost */
	String f; /* mCmc */
	String g; /* mPower */
	String h; /* mToughness */
	String i; /* mLoyalty */
	String j; /* mText */
	String k; /* mFlavor */
	String l; /* mArtist */
	String m; /* mNumber */
	String n; /* mColor */
	int x; /* mMultiverseId */
}