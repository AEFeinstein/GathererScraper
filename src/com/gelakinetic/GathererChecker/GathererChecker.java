package com.gelakinetic.GathererChecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gelakinetic.GathererScraper.Expansion;
import com.gelakinetic.GathererScraper.GathererScraper;

public class GathererChecker {
	/**
	 * Main function. It reads in an RSS file, scrapes a list of sets from Gatherer,
	 * and writes a new RSS file if anything changed
	 * 
	 * @param args unused
	 */
	public static void main(String[] args) {
		try {
			File rssFile = new File(args[0]);
			
			boolean changes = false;
			/* Read in the current RSS file */
			ArrayList<RssEntry>		entries		= readRssFile(rssFile);
			/* Scrape a list of expansions */
			ArrayList<Expansion>	expansions	= GathererScraper.scrapeExpansionList();

			/* For each expansion */
			for(Expansion exp : expansions) {
				/* See if it's already in the RSS file */
				if(!entries.contains(exp)) {
					/* If it isn't, add it to the file. Null GUID and date will be populated */
					entries.add(new RssEntry(exp.mName_gatherer, null, null));
					changes = true;
				}
			}

			/* If there are changes, write the new RSS file */
			if(changes) {
				/* First delete the file */
				if(rssFile.exists()) {
					rssFile.delete();
				}
				
				FileOutputStream fos = new FileOutputStream(rssFile, false); 
				FileChannel channel = fos.getChannel();

				/* Writes a sequence of bytes to this channel from the given buffer. */
				channel.write(GetRssHeader());
				for(RssEntry entry : entries) {
					channel.write(entry.getRssEntry());
				}
				channel.write(GetRssFooter());

				/* close the channel */
				channel.close();
				fos.close();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return a UTF-8 encoded header for an RSS file
	 */
	public static ByteBuffer GetRssHeader() {
		return Charset.forName("UTF-8").encode(
				"<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
				+ "<rss version=\"2.0\">\n"
				+ "<channel>\n"
				+ "\t<title>Gatherer's Available Sets</title>\n"
				+ "\t<link>http://gatherer.wizards.com/Pages/Default.aspx</link>\n"
				+ "\t<description>A dinky little feed to notify me when new sets are posted to Gatherer</description>\n");
	}

	/**
	 * @return a UTF-8 encoded footer for an RSS file
	 */
	public static ByteBuffer GetRssFooter() {
		return Charset.forName("UTF-8").encode(
				"</channel>\n" +
				"</rss>\n");
	}

	/**
	 * Reads the given RSS file and returns an ArrayList of it's contents
	 * 
	 * @param rssFile	The RSS file to read in
	 * @return			An ArrayList<RssEntry> with the given file's information
	 */
	public static ArrayList<RssEntry> readRssFile(File rssFile) {
		ArrayList<RssEntry> rssEntries = new ArrayList<>();
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(rssFile);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("item");

			for (int temp = 0; temp < nList.getLength(); temp++) {

				Node nNode = nList.item(temp);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;
					rssEntries.add(new RssEntry(
							eElement.getElementsByTagName(RssEntry.TITLE).item(0).getTextContent(),
							eElement.getElementsByTagName(RssEntry.GUID).item(0).getTextContent(),
							eElement.getElementsByTagName(RssEntry.PUBDATE).item(0).getTextContent()));
				}
			}
		}
		catch (Exception e) {
			/* eat it */
		}
		return rssEntries;
	}
}
