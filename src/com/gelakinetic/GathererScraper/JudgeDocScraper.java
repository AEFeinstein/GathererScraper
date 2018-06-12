package com.gelakinetic.GathererScraper;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JudgeDocScraper {

    /**
     * TODO doc
     *
     * @param args
     */
    public static void main(String args[]) {

        System.out.println("Processing documents");

        ScrapeDocument("mtr", true);
        ScrapeDocument("ipg", true);
        ScrapeDocument("jar", false);

    }

    /**
     * TODO doc
     *
     * @param docType
     */
    private static void ScrapeDocument(String docType, boolean removeLinks) {

        System.out.println("Processing " + docType);

        HashSet<String> pagesToScrape = new HashSet<>();
        Document mainPage = ConnectWithRetries("https://blogs.magicjudges.org/rules/" + docType + "/");

        if (null == mainPage) {
            return;
        }
        for (Element link : mainPage.getElementsByAttributeValue("class", "entry-content").first().getElementsByTag("a")) {
            String linkHref = link.attr("href");
            if (linkHref.contains("/" + docType)) {
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

        // Now that all sections have been written and all link IDs are known, replace links with internal ones
        for (Element link : html.getElementsByTag("a")) {
            String linkDestination = link.attr("href");
            if (linkIds.contains(getLastPathSegment(linkDestination))) {
                link.attr("href", "#" + getLastPathSegment(linkDestination));
            } else if (linkDestination.contains("cardfinder")) {
                try {
                    for (NameValuePair param : URLEncodedUtils.parse(new URI(linkDestination), Charset.forName("UTF-8"))) {
                        if (param.getName().equals("find")) {
                            link.attr("href", "http://gatherer.wizards.com/Pages/Card/Details.aspx?name=" + param.getValue());
                        }
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            } else if (removeLinks && linkDestination.contains("magicjudges")) {
                System.out.println("Link removed: " + linkDestination);
                link.unwrap();
            }
        }

        // Write the HTML file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File("Documents", docType + ".html")))) {
            // Write the current date
            LocalDateTime now = LocalDateTime.now();
            bw.write(String.format("%d-%02d-%02d\n", now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth()));

            // Write the HTML
            bw.write(doc.toString());
        } catch (IOException e) {
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
    private static void addPageToFile(String page, Element rootElement, ArrayList<String> linkIds) {

        System.out.println("Processing " + page);

        // Download the page
        Document mainPage = ConnectWithRetries(page);
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

        // Remove all mentions of "Annotated"
        for (Element header : entry_content.getElementsByTag("h2")) {
            if (header.text().toLowerCase().contains("annotated")) {
                System.out.println("Removed: " + header.text());
                header.remove();
            }
        }
        for (Element paragraph : entry_content.getElementsByTag("p")) {
            if (paragraph.text().toLowerCase().contains("annotated")) {
                System.out.println("Removed: " + paragraph.text());
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Append the cleaned HTML to the root
        rootElement.appendChild(entry_content);
    }

    /**
     * A little wrapper function to overcome any network hiccups
     *
     * @param urlStr The URL to get a Document from
     * @return A Document, or null
     */
    private static Document ConnectWithRetries(String urlStr) {
        int retries = 0;
        while (retries < Integer.MAX_VALUE - 1) {
            try {
                // Note to self. If this stops working, WireShark a regular request from chrome and copy the cookie (and other fields)
                //noinspection SpellCheckingInspection
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
