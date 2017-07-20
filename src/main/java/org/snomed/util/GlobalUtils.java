package org.snomed.util;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class GlobalUtils {
	
	public static final String LINE_DELIMITER = "\r\n";
	public static final String QUOTE = "\"";
	
	public static void print(String msg) {
		print(msg, true);
	}
	
	public static void print(String msg, boolean newLine) {
		if (newLine) {
			System.out.println(msg);
		} else {
			System.out.print(msg);
		}
	}
	
	public static void writeToFile(File outputFile, String line) {
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			out.print(line + LINE_DELIMITER);
		} catch (Exception e) {
			print ("Unable to output report line: " + line + " due to " + e.getMessage());
		}
	}
	
	public static void outputToFile(String fileName, String[] columns, String delimiter, boolean quoteFields) throws IOException {
		File file = ensureFileExists(fileName);
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			StringBuffer line = new StringBuffer();
			for (int x=0; x<columns.length; x++) {
				if (x > 0) {
					line.append(delimiter);
				}
				line.append(quoteFields?QUOTE:"");
				line.append(columns[x]==null?"":columns[x]);
				line.append(quoteFields?QUOTE:"");
			}
			out.print(line.toString() + LINE_DELIMITER);
		} catch (Exception e) {
			print ("Unable to output to " + file.getAbsolutePath() + " due to " + e.getMessage());
		}
	}

	public static File ensureFileExists(String fileName) throws IOException {
		File file = new File(fileName);
		if (!file.exists()) {
			if (file.getParentFile() != null) {
				file.getParentFile().mkdirs();
			} 
			file.createNewFile();
		}
		return file;
	}
	
	/**
	 * @return an array of 3 elements containing:  The path, the filename, the file extension (if it exists) or empty strings
	 */
	public static String[] deconstructFilename(File file) {
		String[] parts = new String[] {"","",""};
		
		if (file== null) {
			return parts;
		}
		parts[0] = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(File.separator));
		if (file.getName().lastIndexOf(".") > 0) {
			parts[1] = file.getName().substring(0, file.getName().lastIndexOf("."));
			parts[2] = file.getName().substring(file.getName().lastIndexOf(".") + 1);
		} else {
			parts[1] = file.getName();
		}
		
		return parts;
	}
	
	/**
	 * Creates a new file, appending a incrementing integer if a file of the same
	 * name already exists
	 */
	public static File createUniqueFile(File file) {
		int increment = 0;
		while (file.exists()) {
			String[] fileNameParts = deconstructFilename(file);
			String proposedName = fileNameParts[0] + File.separator + fileNameParts[1] + "_" + (++increment) + fileNameParts[2] ;
			file = new File(proposedName);
		}
		return file;
	}
}
