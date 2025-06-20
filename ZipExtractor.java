import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZipExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipExtractor.class);

    public static void extractZIPFile(String zipFilePath, String extractPath, String tileCode) throws IOException {
        Path zipPath = Paths.get(zipFilePath);
        Path outputDir = Paths.get(extractPath);

        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new FileNotFoundException("ZIP file not found: " + zipFilePath);
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry zipEntry;
            boolean extractedSomething = false;

            while ((zipEntry = zis.getNextEntry()) != null) {
                Path extractedFilePath = outputDir.resolve(zipEntry.getName()).normalize();

                // Prevent Zip Slip vulnerability
                if (!extractedFilePath.startsWith(outputDir)) {
                    throw new IOException("Entry is outside of target dir: " + zipEntry.getName());
                }

                // If it's a file, check if it's a CSV and apply tileCode logic
                if (!zipEntry.isDirectory()) {
                    String fileName = extractedFilePath.getFileName().toString();

                    if (fileName.endsWith(".csv") && fileName.contains("-")) {
                        int dashIndex = fileName.lastIndexOf("-");
                        int dotIndex = fileName.lastIndexOf(".");
                        if (dashIndex != -1 && dotIndex > dashIndex) {
                            String partToReplace = fileName.substring(0, dashIndex);
                            fileName = fileName.replace(partToReplace, tileCode);
                            extractedFilePath = extractedFilePath.getParent().resolve(fileName);
                        }
                    }

                    Files.createDirectories(extractedFilePath.getParent());

                    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(extractedFilePath))) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                    extractedSomething = true;
                } else {
                    Files.createDirectories(extractedFilePath);
                }

                zis.closeEntry();
            }

            if (extractedSomething) {
                LOGGER.info("Unzip operation completed successfully: {}", zipFilePath);
            } else {
                LOGGER.warn("ZIP file was empty or contained no extractable files: {}", zipFilePath);
            }

        } catch (IOException e) {
            LOGGER.error("Error occurred while extracting ZIP file: {}", zipFilePath, e);
            throw e; // propagate the error
        }
    }
}
