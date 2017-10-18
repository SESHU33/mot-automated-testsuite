package uk.gov.dvsa.mot.server.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import uk.gov.dvsa.mot.server.model.DatasetMetrics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Optional;

/**
 * Generates a report on data usage by the test suite run.
 */
public class DataUsageReportGenerator {

    /** The logger to use. */
    private static final Logger logger = LoggerFactory.getLogger(DataUsageReportGenerator.class);

    /** The report filename to use. */
    private final String reportFilename;

    /**
     * Creates a new instance.
     * @param env       The environment settings
     */
    public DataUsageReportGenerator(Environment env) {
        this.reportFilename = env.getRequiredProperty("dataUsageReport");
    }

    /**
     * Generate the data usage report. Must be triggered after the test suite has completed.
     * @param datasetMetrics    The dataset metrics used in the report
     */
    public void generateReport(Map<String, DatasetMetrics> datasetMetrics) {
        logger.info("Generating data usage report as {}", reportFilename);
        writeReport(generateHtml(datasetMetrics));
    }

    /**
     * Generates the report content, in HTML format.
     * @param datasetMetrics    The dataset metrics used in the report
     * @return The report content
     */
    private String generateHtml(Map<String, DatasetMetrics> datasetMetrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><head><title>Data Usage Report</title><style>")
                .append("h1, th, td { font-family: arial, sans-serif; }")
                .append("table { border-collapse: collapse; }")
                .append("table, th, td { border: 1px solid black; }")
                .append("th, td { padding: 10px; }")
                .append("th { background-color: #2571EA; color: #FFFFFF; }")
                .append("tr:nth-child(even) { background-color: #f2f2f2; }")
                .append(".red { color: #FF0000; }")
                .append("</style></head><body><h1>Data Usage Report</h1>");

        builder.append("<table><thead><tr>")
                    .append("<th colspan=\"2\"/>")
                    .append("<th colspan=\"2\">Cached</th>")
                    .append("<th colspan=\"2\">Loaded Immediately</th>")
                .append("</tr><tr>")
                    .append("<th>Dataset Name</th>")
                    .append("<th>Timing (secs)</th>")
                    .append("<th>Dataset Size</th>")
                    .append("<th>Amount Requested</th>")
                    .append("<th>Dataset Size</th>")
                    .append("<th>Amount Requested</th>")
                .append("</tr></thead><tbody>");

        datasetMetrics.keySet().stream().sorted().forEach(name -> {
            DatasetMetrics metrics = datasetMetrics.get(name);

            String formattedTiming = "";
            if (metrics.getTimingMilliseconds().isPresent()) {
                formattedTiming = String.format("%.3f", metrics.getTimingMilliseconds().get() / 1000.0D);
            }

            // highlight any slow queries (beyond 10 seconds)
            boolean isSlowQuery = metrics.getTimingMilliseconds().orElse(0L) > 10000;

            // highlight if queries to cache had no data
            boolean cacheEmpty = metrics.getCacheSize().orElse(1) == 0;

            // highlight if more data requested than available in cache
            boolean cacheInsufficient =
                    metrics.getCacheRequested().orElse(0) > metrics.getCacheSize().orElse(0);

            // highlight if queries loaded immediately had no data
            boolean loadImmediatelyEmpty = metrics.getLoadedImmediatelySize().orElse(1) == 0;

            // highlight if more data requested than available when loaded immediately
            // Note: in practice this depends upon whether the amount of changed passwords in the environment tested
            boolean loadImmediatelyInsufficient =
                    metrics.getLoadedImmediatelyRequested().orElse(0)
                            > metrics.getLoadedImmediatelySize().orElse(0);

            builder.append("<tr>")
                        // dataset name
                        .append(formatDataCell(false))
                            .append(name).append("</td>")

                        // timing
                        .append(formatDataCell(isSlowQuery))
                            .append(formattedTiming).append("</td>")

                        // cached - dataset size
                        .append(formatDataCell(cacheEmpty))
                            .append(formatOptional(metrics.getCacheSize())).append("</td>")

                        // cached - amount requested
                        .append(formatDataCell(cacheInsufficient))
                            .append(formatOptional(metrics.getCacheRequested())).append("</td>")

                        // loaded immediately - dataset size
                        .append(formatDataCell(loadImmediatelyEmpty))
                            .append(formatOptional(metrics.getLoadedImmediatelySize())).append("</td>")

                        // loaded immediately - amount requested
                        .append(formatDataCell(loadImmediatelyInsufficient))
                            .append(formatOptional(metrics.getLoadedImmediatelyRequested())).append("</td>")

                    .append("</tr>");
        });
        builder.append("</tbody></table></body></html>");
        return builder.toString();
    }

    /**
     * Formats an optional value, either as the number or a blank string.
     * @param value     The optional value to format
     * @return The formatted value
     */
    private String formatOptional(Optional<Integer> value) {
        return value.map(v -> String.valueOf(v)).orElse("");
    }

    /**
     * Formats a datacell with the appropriate CSS class, depending upon errors.
     * @param error     Whether there is an error
     * @return The HTML to use
     */
    private String formatDataCell(boolean error) {
        return error ? "<td class=\"red\">" : "<td>";
    }

    /**
     * Write the report, as a HTML file.
     * @param content   The report content
     */
    private void writeReport(String content) {
        File file;
        try {
            file = new File(reportFilename);
            file.createNewFile();

        } catch (IOException ex) {
            String message = "Error creating data usage report: " + ex.getMessage();
            logger.error(message, ex);
            // swallow error, abort the write
            return;
        }

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8"))) {
            out.write(content);

        } catch (IOException ex) {
            String message = "Error writing data usage report: " + ex.getMessage();
            logger.error(message, ex);
            // swallow error and continue
        }
    }
}
