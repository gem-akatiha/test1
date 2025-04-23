public static List<Map<String, String>> parseCsv(Path csvFilePath) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(csvFilePath)) {
        CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        List<Map<String, String>> rows = new ArrayList<>();
        for (CSVRecord record : parser) {
            Map<String, String> row = new HashMap<>();
            parser.getHeaderMap().keySet().forEach(header -> row.put(header, record.get(header)));
            rows.add(row);
        }
        return rows;
    }
}
