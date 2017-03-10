package com.gelakinetic.GathererChecker;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;

import com.gelakinetic.GathererScraper.JsonTypes.Expansion;
import com.google.common.net.PercentEscaper;

public class RssEntry {

	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String GUID = "guid";
	public static final String PUBDATE = "pubDate";
	public static final String LINK = "link";

	private String mTitle;
	private String mDescription;
	private String mGuid;
	private String mPubDate;
	private String	mUrl;

	/**
	 * Creates a new RSS entry
	 *
	 * @param title		The expansion's name
	 * @param guid		The entry's GUID, or null if a new one should be created
	 * @param pubDate	The entry's publication date, or null if it is today
	 */
	public RssEntry(String title, String guid, String pubDate, String url) {
		mTitle = title;
		mDescription = "Time to scrape " + title;
		if(guid == null) {
			mGuid = UUID.randomUUID().toString();
		}
		else {
			mGuid = guid;
		}
		if(pubDate == null) {
			Calendar rightNow = Calendar.getInstance();
			mPubDate = GathererChecker.GetRfc822Date(rightNow.getTime());
		}
		else {
			mPubDate = pubDate;
		}
		if (url == null) {
			mUrl = StringEscapeUtils.escapeHtml4("http://gatherer.wizards.com/Pages/Search/Default.aspx?page=0"
					+ "&output=compact&action=advanced&special=true&set=+%5b%22"
					+ (new PercentEscaper("", true)).escape(getTitle()) + "%22%5d");
		}
		else {
			mUrl = StringEscapeUtils.escapeHtml4(url);
		}
	}

	/**
	 * Returns a string of XML for this entry
	 * @return A String of XML
	 */
	public ByteBuffer getRssEntry() {

		return	Charset.forName("UTF-8").encode(
				"\t<item>\n"+
				"\t\t<"+TITLE+">"+getTitle()+"</"+TITLE+">\n"+
				"\t\t<"+DESCRIPTION+">"+mDescription+"</"+DESCRIPTION+">\n"+
				"\t\t<"+LINK+">"+ mUrl +"</"+LINK+">\n"+
				"\t\t<"+GUID+">"+mGuid+"</"+GUID+">\n"+
				"\t\t<"+PUBDATE+">"+ mPubDate +"</"+PUBDATE+">\n"+
				"\t</item>\n");
	}

	/**
	 * Compares an RssEntry to another RssEntry or Expansion, based on title / name
	 *
	 * @return true if the objects are equal, false otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Expansion) {
			return this.mTitle.equals(((Expansion)obj).mName_gatherer);
		}
		else if(obj instanceof RssEntry) {
			return this.mTitle.equals(((RssEntry)obj).mTitle);
		}
		return false;
	}

	/**
	 * Returns this entry's title. Used for Expansion's .equals()
	 *
	 * @return This entry's title
	 */
	public String getTitle() {
		return mTitle;
	}
}
