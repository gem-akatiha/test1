private static void injectCsvData(List<String> updatedLines, String featureFileName) throws IOException {
        String csvFileName = featureFileName.replace(".feature", ".csv");
        Path csvPath = Paths.get(CSV_FOLDER, csvFileName);

        if (!Files.exists(csvPath)) {
            throw new RuntimeException("CSV file not found for feature: " + featureFileName);
        }

        List<Map<String, String>> rows = parseCsv(csvPath);

        for (Map<String, String> row : rows) {
            String rowData = row.values().stream()
                    .map(value -> value == null ? "" : value)
                    .collect(Collectors.joining("|", "|", "|"));
            updatedLines.add(rowData);
        }
    }

    private static List<Map<String, String>> parseCsv(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        String[] headers = lines.get(0).split(",");

        return lines.stream()
                .skip(1)
                .map(line -> {
                    String[] values = line.split(",");
                    Map<String, String> map = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        map.put(headers[i], i < values.length ? values[i] : "");
                    }
                    return map;
                })
                .collect(Collectors.toList());
    }
