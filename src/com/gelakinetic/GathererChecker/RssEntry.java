package com.gelakinetic.GathererChecker;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;

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
	
	/**
	 * Creates a new RSS entry
	 * 
	 * @param title		The expansion's name
	 * @param guid		The entry's GUID, or null if a new one should be created
	 * @param pubDate	The entry's publication date, or null if it is today
	 */
	public RssEntry(String title, String guid, String pubDate) {
		mTitle = title;
		mDescription = "Time to scrape " + title;
		if(guid == null) {
			mGuid = UUID.randomUUID().toString();
		}
		else {
			mGuid = guid;
		}
		if(mPubDate == null) {
			Calendar rightNow = Calendar.getInstance();
			mPubDate = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(rightNow.getTime());
		}
		else {
			mPubDate = pubDate;
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
				"\t\t<"+LINK+">"+ 
				StringEscapeUtils.escapeHtml4("http://gatherer.wizards.com/Pages/Search/Default.aspx?page=0"
						+ "&output=compact&action=advanced&special=true&set=+%5b%22"
						+ (new PercentEscaper("", true)).escape(getTitle()) + "%22%5d")
				+"</"+LINK+">\n"+
				"\t\t<"+GUID+">"+mGuid+"</"+GUID+">\n"+
				"\t\t<"+PUBDATE+">"+ mPubDate +"</"+PUBDATE+">\n"+
				"\t</item>\n");
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
