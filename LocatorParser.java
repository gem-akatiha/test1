import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class LocatorParser {

    private static final Set<String> CUSTOM_TYPES = Set.of("Text", "Link", "Button");
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "(?m)^(@FindBy\\s*\\(.*?\\))\\s*\\n\\s*(public|protected|private|)?\\s*(\\w+)\\s+(\\w+)\\s*;"
    );

    private static final String BACKUP_SUFFIX = "-backup";
    private static final Map<String, Path> backupDirectories = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java LocatorRewriterWithBackup <source-folder>");
            System.exit(1);
        }

        Path sourceFolder = Paths.get(args[0]);
        if (!Files.isDirectory(sourceFolder)) {
            System.err.println("Invalid directory: " + args[0]);
            System.exit(1);
        }

        Files.walk(sourceFolder)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(file -> processFile(file, sourceFolder));
    }

    private static void processFile(Path filePath, Path root) {
        try {
            String originalContent = Files.readString(filePath);
            Matcher matcher = FIELD_PATTERN.matcher(originalContent);
            StringBuffer updatedContent = new StringBuffer();

            boolean changed = false;

            while (matcher.find()) {
                String annotation = matcher.group(1);
                String modifier = Optional.ofNullable(matcher.group(2)).orElse("").trim();
                String type = matcher.group(3);
                String name = matcher.group(4);

                if (CUSTOM_TYPES.contains(type)) {
                    String replacement = String.format(
                        "%s\n%s WebElementFacade %s;",
                        annotation,
                        modifier.isEmpty() ? "" : modifier,
                        name
                    ).trim();

                    matcher.appendReplacement(updatedContent, Matcher.quoteReplacement(replacement));
                    changed = true;
                }
            }

            matcher.appendTail(updatedContent);

            if (changed) {
                // Backup original file before writing
                Path relativePath = root.relativize(filePath);
                String topPackage = relativePath.iterator().next().toString();
                Path backupFolder = backupDirectories.computeIfAbsent(topPackage, pkg -> {
                    Path backupDir = root.resolve(pkg + BACKUP_SUFFIX);
                    try {
                        Files.createDirectories(backupDir);
                    } catch (IOException e) {
                        System.err.println("Failed to create backup folder: " + backupDir);
                    }
                    return backupDir;
                });

                // Recreate folder structure inside backup
                Path backupFilePath = backupFolder.resolve(relativePath.subpath(1, relativePath.getNameCount()));
                Files.createDirectories(backupFilePath.getParent());
                Files.copy(filePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Backed up: " + filePath + " -> " + backupFilePath);

                // Write updated content
                Files.writeString(filePath, updatedContent.toString());
                System.out.println("Updated: " + filePath);
            } else {
                System.out.println("No change: " + filePath);
            }

        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
