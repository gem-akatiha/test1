import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TdmFeatureGenerator {

    public static void main(String[] args) throws IOException {
        // Configurable root directory for feature files
        Path featureRoot = Paths.get("src/test/resources/features");

        // 1. Scan for all .feature files
        List<Path> featureFiles = findFeatureFiles(featureRoot);

        for (Path featureFile : featureFiles) {
            try {
                // 2. Extract CSV file name from @TDM(tileCode="...") annotation
                Optional<String> csvFileNameOpt = extractCsvFileName(featureFile);

                if (csvFileNameOpt.isEmpty()) {
                    System.out.println("No @TDM annotation found in: " + featureFile);
                    continue;
                }

                String tileCode = csvFileNameOpt.get();

                // 3. Download the CSV file using tileCode
                // Note: downloadCsvFile(tileCode) is assumed to be implemented elsewhere
                Path csvPath = Paths.get(".tdm-tmp").resolve(tileCode + ".csv");

                if (!Files.exists(csvPath)) {
                    System.err.println("CSV file for tileCode not found: " + tileCode + ", expected at: " + csvPath);
                    continue;
                }

                // 4. Inject the TDM data into the feature file
                processFeatureFile(featureFile, csvPath);

            } catch (Exception e) {
                System.err.println("Error processing file " + featureFile + ": " + e.getMessage());
            }
        }
    }

    public static List<Path> findFeatureFiles(Path rootDir) throws IOException {
        try (Stream<Path> stream = Files.walk(rootDir)) {
            return stream
                .filter(path -> path.toString().endsWith(".feature"))
                .collect(Collectors.toList());
        }
    }

    public static Optional<String> extractCsvFileName(Path featureFile) throws IOException {
        try (Stream<String> lines = Files.lines(featureFile)) {
            return lines
                .filter(line -> line.trim().startsWith("@TDM"))
                .map(line -> {
                    int start = line.indexOf("\"") + 1;
                    int end = line.lastIndexOf("\"");
                    return (start > 0 && end > start) ? line.substring(start, end) : null;
                })
                .filter(s -> s != null && !s.isEmpty())
                .findFirst();
        }
    }

    public static void processFeatureFile(Path featureFile, Path csvFile) {
        // You will implement logic to inject rows into the Examples section of featureFile
        // based on columns and values from csvFile.
        System.out.println("Injecting data from " + csvFile + " into " + featureFile);
        // Example: Modify file content, replace placeholder Examples, etc.
    }
}
