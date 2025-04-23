import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public static List<Map<String, String>> parseCsv(Path csvFile) {
    try (Reader reader = Files.newBufferedReader(csvFile);
         CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

        List<CSVRecord> records = parser.getRecords();
        List<Map<String, String>> rows = new ArrayList<>();

        for (CSVRecord record : records) {
            Map<String, String> row = new HashMap<>();
            for (String header : parser.getHeaderMap().keySet()) {
                row.put(header, record.get(header));
            }
            rows.add(row);
        }
        return rows;
    } catch (IOException e) {
        throw new RuntimeException("Failed to read CSV", e);
    }
}
