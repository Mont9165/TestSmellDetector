package testsmell;

import file_mapping.MappingResultsWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.List;

/**
 * This class is utilized to write output to a CSV file
 */
public class ResultsWriter {

    private final String outputFile;
    private FileWriter writer;

    /**
     * Creates the file into which output it to be written into. Results from each file will be stored in a new file
     */
    private ResultsWriter(String repoName) throws IOException {
        String time =  String.valueOf(Calendar.getInstance().getTimeInMillis());
        outputFile = MessageFormat.format("{0}/{1}/{2}.{3}", "results/smells", repoName, "smells_number", "csv");
        writer = new FileWriter(outputFile,false);
    }

    /**
     * Factory method that provides a new instance of the ResultsWriter
     * @return new ResultsWriter instance
     */
    public static ResultsWriter createResultsWriter(String repoName) throws IOException {
        return new ResultsWriter(repoName);
    }

    /**
     * Writes column names into the CSV file
     * @param columnNames the column names
     */
    public void writeColumnName(List<String> columnNames) throws IOException {
        writeOutput(columnNames);
    }

    /**
     * Writes column values into the CSV file
     * @param columnValues the column values
     */
    public void writeLine(List<String> columnValues) throws IOException {
        writeOutput(columnValues);
    }

    /**
     * Appends the input values into the CSV file
     * @param dataValues the data that needs to be written into the file
     */
    private void writeOutput(List<String> dataValues)throws IOException {
        writer = new FileWriter(outputFile,true);

        MappingResultsWriter.addLineSeparator(dataValues, writer);
    }
}
