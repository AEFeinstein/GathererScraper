package com.gelakinetic.GathererScraper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;

public class JudgeDocScraper {

    private final static String DOC_DIR = "rules";
    
    final JudgeDocScraperUi mUi;
    
    /**
     * TODO doc
     * 
     * @param judgeDocScraperUi
     */
    public JudgeDocScraper(JudgeDocScraperUi judgeDocScraperUi) {
        mUi = judgeDocScraperUi;
    }

    /**
     * TODO doc
     */
    public void ScrapeAll() {

        mUi.appendText("Processing documents");

        ScrapeDocument("mtr", "MagicTournamentRules-light.html", true);
        ScrapeDocument("ipg", "InfractionProcedureGuide-light.html", true);
        ScrapeDocument("jar", "JudgingAtRegular-light.html", false);

        mUi.appendText("All done");

    }

    /**
     * TODO doc
     *
     * @param docType
     * @param string 
     */
    private void ScrapeDocument(String docType, String ouputName, boolean removeLinks) {

        mUi.appendText("Processing " + docType);
        
        HashSet<String> pagesToScrape = new HashSet<>();
        Document mainPage = GathererScraper.ConnectWithRetries("https://blogs.magicjudges.org/rules/" + docType + "/");

        if (null == mainPage) {
            return;
        }
        for (Element link : mainPage.getElementsByAttributeValue("class", "entry-content").first().getElementsByTag("a")) {
            String linkHref = link.attr("href");
            if (linkHref.contains("/" + docType)) {
				linkHref = linkHref.replaceAll("https", "http");
				if(!linkHref.endsWith("/")) {
					linkHref += "/";
				}
                pagesToScrape.add(linkHref);
            }
        }

        ArrayList<String> pagesAl = new ArrayList<>(pagesToScrape.size());
        pagesAl.addAll(pagesToScrape);
        Pattern pattern = Pattern.compile("http://blogs\\.magicjudges\\.org/rules/" + docType + "([0-9]+)-*([0-9]*)/");
        pagesAl.sort((str, oth) -> {

            Matcher strMatcher = pattern.matcher(str);
            Matcher otherMatcher = pattern.matcher(oth);
            if (strMatcher.matches() && otherMatcher.matches()) {

                int strSection = Integer.parseInt(strMatcher.group(1));
                int strSubsection;
                try {
                    strSubsection = Integer.parseInt(strMatcher.group(2));
                } catch (NumberFormatException | NullPointerException e) {
                    strSubsection = 0;
                }

                int othSection = Integer.parseInt(otherMatcher.group(1));
                int othSubsection;
                try {
                    othSubsection = Integer.parseInt(otherMatcher.group(2));
                } catch (NumberFormatException | NullPointerException e) {
                    othSubsection = 0;
                }

                if (0 == Integer.compare(strSection, othSection)) {
                    return Integer.compare(strSubsection, othSubsection);
                } else {
                    return Integer.compare(strSection, othSection);
                }
            } else if (strMatcher.matches() && !otherMatcher.matches()) {
                return -1;
            } else if (!strMatcher.matches() && otherMatcher.matches()) {
                return 1;
            } else {
                return str.compareTo(oth);
            }
        });

        pagesAl.add(0, "https://blogs.magicjudges.org/rules/" + docType + "/");

        ArrayList<String> linkIds = new ArrayList<>();

        Document doc = new Document(docType + ".html");

        doc.appendChild(new DocumentType("html", "", ""));
        Element html = new Element("html");
        doc.appendChild(html);
        html.appendChild(new Element("head"));
        doc.head().appendElement("meta").attr("http-equiv", "Content-Type").attr("content", "text/html; charset=utf-8");

        for (String page : pagesAl) {
            addPageToFile(page, html, linkIds);
        }

		// Now that all sections have been written and all link IDs are known,
		// replace links with internal ones
		for (Element link : html.getElementsByTag("a")) {
			try {
				String linkDestination = link.attr("href");
				if (linkIds.contains(getLastPathSegment(linkDestination))) {
					link.attr("href", "#" + getLastPathSegment(linkDestination));
				}
				else if (linkDestination.contains("cardfinder")) {
					for (NameValuePair param : URLEncodedUtils.parse(new URI(linkDestination),
							Charset.forName("UTF-8"))) {
						if (param.getName().equals("find")) {
							link.attr("href", "http://gatherer.wizards.com/Pages/Card/Details.aspx?name=" +
									URLEncoder.encode(GathererScraper.removeNonAscii(StringEscapeUtils.unescapeHtml4(param.getValue())), "UTF-8"));
						}
					}
				}
				else if (removeLinks && linkDestination.contains("magicjudges")) {
					mUi.appendText("Link removed: " + linkDestination);
					link.unwrap();
				}
			}
			catch (URISyntaxException e) {
				e.printStackTrace();
			}
			catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

        // Write the HTML file
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File(DOC_DIR, ouputName)), StandardCharsets.UTF_8))) {
            // Write the current date
            LocalDateTime now = LocalDateTime.now();
            bw.write(String.format("%d-%02d-%02d\n", now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth()));

            // Write the HTML
            bw.write(GathererScraper.removeNonAscii(doc.toString()));
        } catch (IOException e) {
            mUi.appendText("EXCEPTION!!! " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * TODO doc
     *
     * @param page
     * @param rootElement
     * @param linkIds
     */
    private void addPageToFile(String page, Element rootElement, ArrayList<String> linkIds) {

        mUi.appendText("Processing " + page);

        // Download the page
        Document mainPage = GathererScraper.ConnectWithRetries(page);
        if (null == mainPage) {
            return;
        }

        // Get the main element
        Element content = mainPage.body();

        // Get the header content for this page
        Element entry_header = content.getElementsByAttributeValue("class", "entry-header").first();
        // Add a link ID, and save the link ID
        entry_header.attr("id", getLastPathSegment(page));
        linkIds.add(entry_header.attr("id"));
        // Append it to the root
        rootElement.appendChild(entry_header);

        // Get the body content for this page
        Element entry_content = content.getElementsByAttributeValue("class", "entry-content").first();

        // Remove all annotations
        entry_content.getElementsByAttributeValue("class", "alert alert-grey").remove();
        entry_content.getElementsByAttributeValue("class", "alert alert-info").remove();

        // Remove all styles
        for (Element element : entry_content.getElementsByAttribute("style")) {
            element.removeAttr("style");
        }

        // Remove all background colors
        for (Element element : entry_content.getElementsByAttribute("bgcolor")) {
            element.removeAttr("bgcolor");
        }

        // Remove all mentions of "Annotated" and credits for the annotated documents
        for (Element header : entry_content.getElementsByTag("h2")) {
            if (header.text().toLowerCase().contains("annotated")) {
                mUi.appendText("Removed:\n" + header.text() + '\n');
                header.remove();
            }
            else if (header.text().toLowerCase().equals("credit")) {
                mUi.appendText("Removed:\n" + header.text() + '\n');
                header.remove();
            }
        }
        for (Element paragraph : entry_content.getElementsByTag("p")) {
            if (paragraph.text().toLowerCase().contains("annotated")) {
                mUi.appendText("Removed:\n" + paragraph.text() + '\n');
                paragraph.remove();
            }
            else if (paragraph.text().toLowerCase().contains("aipg")) {
                mUi.appendText("Removed:\n" + paragraph.text() + '\n');
                paragraph.remove();
            }
        }

        // Replace all linked images with embedded base64 ones
        for (Element image : entry_content.getElementsByTag("img")) {
            try {
                // Get the image source, ensuring it's using https (normal http gets a redirect)
                String imgSrc = image.attr("src").replace("http:", "https:");
                // Download the image
                byte[] imageBytes = IOUtils.toByteArray(new URL(imgSrc));
                // Convert the image to base64
                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                // Write the base64 image into the html
                image.attr("src", "data:image/" + getFileExtension(imgSrc) + "; base64, " + base64);

                mUi.appendText("Embedded image: " + imgSrc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Append the cleaned HTML to the root
        rootElement.appendChild(entry_content);
    }

    /**
     * TODO doc
     *
     * @param name
     * @return
     */
    private static String getFileExtension(String name) {
        try {
            return name.substring(name.lastIndexOf(".") + 1);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * TODO doc
     *
     * @param str
     * @return
     */
    private static String getLastPathSegment(String str) {
        String parts[] = str.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].trim().isEmpty()) {
                return parts[i].trim();
            }
        }
        return "";
    }
}
