package Scrapper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class papersPDFScrapper {

    private static final int THREAD_COUNT = 50; // Number of concurrent threads
    private static final int MAX_RETRIES = 3;   // Maximum retries for failed connections
    private static final int TIMEOUT = 60000;   // Connection timeout in milliseconds
    private static String outputDir = "E:/programing/PDFs/Scrappped/"; // Output directory for PDFs
    private static String baseUrl = "https://papers.nips.cc";
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("What do you want to scrape?");
        System.out.println("1. Metadata");
        System.out.println("2. PDFs");
        System.out.println("3. Both");
        int choice = scanner.nextInt();
        scanner.nextLine();

        try {
            Files.createDirectories(Paths.get(outputDir));
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            Document mainPage = Jsoup.connect(baseUrl).timeout(TIMEOUT).get();
            System.out.println("Connected to main page: " + baseUrl);
            processYearLinks(mainPage, baseUrl, executor, choice);
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        } catch (IOException | InterruptedException e) {
            System.err.println("An error occurred during the scraping process.");
            e.printStackTrace();
        }
    }
    private static void processYearLinks(Document mainPage, String baseUrl, ExecutorService executor, int choice) {
        Elements yearLinks = mainPage.select("a[href^=/paper_files/paper/]"); // Select links for years
        for (Element yearLink : yearLinks) {
            String yearUrl = baseUrl + yearLink.attr("href");
            System.out.println("Processing year: " + yearUrl);

            try {
                Document yearPage = Jsoup.connect(yearUrl).timeout(TIMEOUT).get();
                processPaperLinks(yearPage, baseUrl, executor, choice, yearUrl);
            } catch (IOException e) {
                System.err.println("Failed to process year: " + yearUrl);
                e.printStackTrace();
            }
        }
    }
    private static void processPaperLinks(Document yearPage, String baseUrl, ExecutorService executor, int choice, String yearUrl) {
        Elements paperLinks = yearPage.select("body > div.container-fluid > div > ul li a"); // Adjust selector as needed
        for (Element paperLink : paperLinks) {
            String paperUrl = baseUrl + paperLink.attr("href");
            executor.submit(() -> processPaper(baseUrl, paperUrl, choice, yearUrl));
        }
    }
    private static void processPaper(String baseUrl, String paperUrl, int choice, String yearUrl) {
        String threadId = Thread.currentThread().getName();
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            try {
                System.out.println(threadId + " - Processing paper: " + paperUrl + " (Attempt " + (attempts + 1) + ")");

                Document paperPage = Jsoup.connect(paperUrl).timeout(TIMEOUT).get();
                String paperTitle = sanitizeFilename(paperPage.select("title").text());
                Element pdfLink = paperPage.selectFirst("body > div.container-fluid > div > div a:contains(Paper)");
                if (pdfLink != null) {
                    String pdfUrl = baseUrl + pdfLink.attr("href");

                    if (choice == 2 || choice == 3) {
                        System.out.println(threadId + " - Found PDF: " + pdfUrl);
                        downloadPDF(pdfUrl, paperTitle, yearUrl);
                    }
                    if (choice == 1 || choice == 3) {
                        extractMetadata(paperPage, paperTitle, yearUrl);
                    }
                }

                break;
            } catch (IOException e) {
                System.err.println(threadId + " - Failed to process paper: " + paperUrl);
                e.printStackTrace();
                attempts++;
            }
        }

        if (attempts >= MAX_RETRIES) {
            System.err.println(threadId + " - Giving up on paper: " + paperUrl);
        }
    }
    private static void downloadPDF(String pdfUrl, String paperTitle, String yearUrl) {
        try {
            String year = sanitizeFilename(yearUrl.split("/")[yearUrl.split("/").length - 1]);
            Path yearDir = Paths.get(outputDir, year);
            Files.createDirectories(yearDir);
            Path pdfDir = Paths.get(yearDir.toString(), "pdfs");
            Files.createDirectories(pdfDir);

            Path filePath = Paths.get(pdfDir.toString(), paperTitle + ".pdf");

            try (InputStream inputStream = Jsoup.connect(pdfUrl).timeout(TIMEOUT).ignoreContentType(true).execute().bodyStream();
                 FileOutputStream outputStream = new FileOutputStream(filePath.toString())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                System.out.println(Thread.currentThread().getName() + " - Saved PDF: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to download PDF: " + pdfUrl);
            e.printStackTrace();
        }
    }
    private static void extractMetadata(Document paperPage, String paperTitle, String yearUrl) {
        String abstractText = "No abstract available";
        String authors = "Unknown";
        Element authorsElement = paperPage.selectFirst("h4:contains(Authors)");
        if (authorsElement != null) {
            authors = authorsElement.nextElementSibling().text();
        }

        Element abstractElement = paperPage.selectFirst("h4:contains(Abstract)");
        if (abstractElement != null) {
            abstractText = abstractElement.nextElementSibling().text();
        }

        String year = sanitizeFilename(yearUrl.split("/")[yearUrl.split("/").length - 1]);
        Path yearDir = Paths.get(outputDir, year);
        try {
            Files.createDirectories(yearDir);

            Path csvFile = Paths.get(yearDir.toString(), year + ".csv");
            boolean isNewFile = !Files.exists(csvFile);

            try (BufferedWriter writer = Files.newBufferedWriter(csvFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (isNewFile) {
                    writer.write("Title, Authors, Abstract\n");
                }
                writer.write(String.format("\"%s\",\"%s\",\"%s\"\n", paperTitle, authors, abstractText));
                System.out.println("Saved metadata for: " + paperTitle);
            }
        } catch (IOException e) {
            System.err.println("Failed to save metadata for: " + paperTitle);
            e.printStackTrace();
        }
    }
    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "").replaceAll("\\s+", "_");
    }
}
