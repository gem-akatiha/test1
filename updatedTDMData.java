
public class TDMHooks {
    private static final Logger logger = LoggerFactory.getLogger(TDMHooks.class);
    public static final String TDM_AUTO_CLEANUP = "tdm.auto.cleanup";

    public static final Path CSV_ROOT_FOLDER = Paths.get("src/test/resources");
    private static final Path GENERATED_FOLDER = Paths.get("target/generated-features/");
    public static final Path FEATURE_ROOT_DIR = Paths.get("src/test/resources/features");

    public static void main(String[] args) throws IOException {
        // Ensure output folder exists
        Files.createDirectories(GENERATED_FOLDER);

        tdmCleanup(); // Optional: run cleanup before processing

        List<Path> featureFiles = findFeatureFiles();

        for (Path featureFile : featureFiles) {
            try {
                Optional<String> csvFileNameOpt = extractCsvFileName(featureFile);
                if (csvFileNameOpt.isEmpty()) {
                    logger.info("No @TDM annotation found in: {}", featureFile);
                    continue;
                }

                String tileCode = csvFileNameOpt.get();

                // Call pre-hook (e.g. download or prepare CSV)
                tdmBeforeHook(tileCode);

                Path csvPath = CsvReader.findCSVFile(CSV_ROOT_FOLDER, tileCode + ".csv");

                // Inject data and write modified feature to target
                processFeatureFile(featureFile, csvPath);

            } catch (Exception e) {
                logger.error("Error processing file {}: {}", featureFile, e.getMessage(), e);
            }
        }
    }

    private static List<Path> findFeatureFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(FEATURE_ROOT_DIR)) {
            return stream
                    .filter(path -> path.toString().endsWith(".feature"))
                    .collect(Collectors.toList());
        }
    }

    private static Optional<String> extractCsvFileName(Path featureFile) throws IOException {
        Pattern pattern = Pattern.compile("@TDM\\(\\s*tileCode\\s*=\\s*\"(.*?)\"");

        try (Stream<String> lines = Files.lines(featureFile)) {
            return lines
                    .map(pattern::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .findFirst();
        }
    }

    private static void processFeatureFile(Path featurePath, Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(featurePath);
        List<String> updatedLines = new ArrayList<>();
        boolean injected = false;

        for (String line : lines) {
            updatedLines.add(line);

            // Inject data just after "Examples:"
            if (!injected && line.trim().startsWith("Examples:")) {
                injectCsvData(updatedLines, csvPath);
                injected = true;
            }
        }

        Path outputPath = GENERATED_FOLDER.resolve(featurePath.getFileName());
        Files.write(outputPath, updatedLines);
        logger.info("Injected CSV data into: {}", outputPath);
    }

    private static void injectCsvData(List<String> updatedLines, Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) {
            throw new RuntimeException("CSV file not found: " + csvPath);
        }

        List<Map<String, String>> rows = CsvReader.parseCsv(csvPath);

        if (rows.isEmpty()) return;

        // Add headers from first row keys
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        String headerLine = headers.stream()
                .map(h -> h == null ? "" : h)
                .collect(Collectors.joining("|", "|", "|"));
        updatedLines.add(headerLine);

        // Add data rows
        for (Map<String, String> row : rows) {
            String rowData = headers.stream()
                    .map(h -> row.getOrDefault(h, ""))
                    .collect(Collectors.joining("|", "|", "|"));
            updatedLines.add(rowData);
        }
    }

    // Assume tdmCleanup() and tdmBeforeHook(tileCode) are defined elsewhere
}
