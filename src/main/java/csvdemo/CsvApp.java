package csvdemo;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

public class CsvApp {
    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.defaultContentType = "text/html";
        }).start(7070);

        app.get("/", ctx -> {
            ctx.html(getUploadForm(""));
        });

        app.post("/upload", ctx -> {
            UploadedFile file = ctx.uploadedFile("file");
            if (file == null) {
                ctx.html(getUploadForm("<p style='color:red;'>No file uploaded.</p>"));
                return;
            }

            try (Reader reader = new InputStreamReader(file.getContent())) {
                CSVParser parser = CSVFormat.DEFAULT.parse(reader);
                List<CSVRecord> csvRecords = parser.getRecords();

                StringBuilder output = new StringBuilder();
                output.append("<p>Rows: ").append(csvRecords.size()).append("</p>");
                if (!csvRecords.isEmpty()) {
                    output.append("<p>Columns: ").append(csvRecords.get(0).size()).append("</p>");
                    output.append("<table border='1'><thead><tr>");
                    for (int i = 0; i < csvRecords.get(0).size(); i++) {
                        output.append("<th>Column ").append(i + 1).append("</th>");
                    }
                    output.append("</tr></thead><tbody>");
                    for (CSVRecord record : csvRecords) {
                        output.append("<tr>");
                        record.forEach(cell -> output.append("<td>").append(cell).append("</td>"));
                        output.append("</tr>");
                    }
                    output.append("</tbody></table>");
                } else {
                    output.append("<p>No records found.</p>");
                }

                ctx.html(getUploadForm(output.toString()));

            } catch (Exception e) {
                ctx.html(getUploadForm("<p style='color:red;'>Failed to read CSV: " + e.getMessage() + "</p>"));
            }
        });
    }

    private static String getUploadForm(String content) {
        return "<!DOCTYPE html><html><head><title>CSV Upload</title></head><body>" +
                "<h2>Upload a CSV File</h2>" +
                "<form action=\"/upload\" method=\"post\" enctype=\"multipart/form-data\">" +
                "<input type=\"file\" name=\"file\" accept=\".csv\">" +
                "<button type=\"submit\">Upload CSV</button>" +
                "</form><hr>" +
                content +
                "</body></html>";
    }
}
