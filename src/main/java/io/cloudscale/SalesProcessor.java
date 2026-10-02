package io.cloudscale;

import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Component
public class SalesProcessor {
    private static final List<String> HEADERS = List.of("order_id", "customer_id", "product", "category", "quantity", "price", "region", "date");
    public byte[] process(InputStream input) throws IOException {
        long valid = 0, invalid = 0;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, Map<String, BigDecimal>> groups = new LinkedHashMap<>();
        for (String group : List.of("product", "category", "region", "month")) groups.put(group, new TreeMap<>());
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            if (!parser.getHeaderNames().equals(HEADERS)) throw new IOException("Unexpected CSV headers");
            for (var row : parser) {
                try {
                    if (!row.isConsistent()) throw new IllegalArgumentException();
                    for (String header : HEADERS) if (row.get(header).isBlank()) throw new IllegalArgumentException();
                    int quantity = Integer.parseInt(row.get("quantity"));
                    BigDecimal price = new BigDecimal(row.get("price"));
                    if (quantity <= 0 || price.signum() < 0 || price.scale() > 2 || price.precision() > 18) throw new IllegalArgumentException();
                    var month = YearMonth.from(LocalDate.parse(row.get("date"))).toString();
                    var revenue = price.multiply(BigDecimal.valueOf(quantity));
                    for (String key : List.of("product", "category", "region")) groups.get(key).merge(row.get(key), revenue, BigDecimal::add);
                    groups.get("month").merge(month, revenue, BigDecimal::add);
                    total = total.add(revenue);
                    valid++;
                } catch (IllegalArgumentException | java.time.DateTimeException e) { invalid++; }
            }
        }
        var output = new StringWriter();
        try (var csv = new CSVPrinter(output, CSVFormat.DEFAULT)) {
            csv.printRecord("metric", "key", "value");
            csv.printRecord("valid_rows", "all", valid);
            csv.printRecord("invalid_rows", "all", invalid);
            csv.printRecord("revenue", "all", total.toPlainString());
            for (var group : groups.entrySet())
                for (var entry : group.getValue().entrySet()) csv.printRecord("revenue_by_" + group.getKey(), entry.getKey(), entry.getValue().toPlainString());
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }
}
