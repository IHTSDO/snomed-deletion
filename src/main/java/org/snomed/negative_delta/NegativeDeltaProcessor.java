package org.snomed.negative_delta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.snomed.ApplicationException;
import org.snomed.util.GlobalUtils;

import com.google.common.io.Files;

public class NegativeDeltaProcessor implements SnomedConstants {

	Connection conn;
	Statement stmt;
	Statement readOnlyStmt;
	File negativeDeltaArchive;
	String targetEffectiveTime;
	String edition = "INT";
	Timestamp lastMsg = new Timestamp(System.currentTimeMillis());
	
	public static final String DELETION_PREFIX = "d";
	public static final String MODIFIER_PREFIX = "modified_";
	public static final String FIELD_DELIMITER = "\t";
	
	public static final int IDX_ID = 0; 
	public static final int IDX_EFFECTIVETIME = 1; 
	public static final int IDX_DELETIONEFFECTIVETIME = 2; 
	public static final int IDX_ACTIVE = 3; 
	public static final int IDX_DELETIONACTIVE = 4; 
	public static final int IDX_MODULEID = 5; 
	
	public static final String ACTIVE = "1";
	
	public static void main(String args[]) throws Exception{
		NegativeDeltaProcessor app = new NegativeDeltaProcessor();
		Timestamp startTime = new Timestamp(System.currentTimeMillis());
		System.out.println ("Started at " +  startTime);
		try{
			app.init(args);
			app.cloneTables();  //Create a d,s,f set of tables with modified_ prepended to them.
			app.processNegativeDelta();
			app.calculateSnapshot();
			app.calculateDelta();
			app.outputArchive();
			//app.outputCurrentStateOfDeletedComponents();
		} finally {
			Timestamp now = new Timestamp(System.currentTimeMillis());
			System.out.println ("\nTime now " + now);
			System.out.println ("Time taken: " + timeDiff (now, startTime));
		}
	}

	private void init(String[] args) throws SQLException, ClassNotFoundException {
		if (args.length < 6) {
			print ("Usage NegativeDeltaProcessor <dbHost:port> <dbName> <dbUsername> <dbPassword> <negativeDeltaArchive> <targetEffectiveTime>");
			System.exit(-1);
		}
		String connStr = "jdbc:mysql://" + args[0] + "/" + args[1];
		print ("Connecting to database: " + connStr + " as " + args[2]);
		Class.forName("com.mysql.jdbc.Driver");
		String username = args[2];
		String password = args[3].length() > 2 ? args[3] : null;
		conn = DriverManager.getConnection(connStr, username, password);
		conn.setAutoCommit(false);
		stmt = conn.createStatement();
		readOnlyStmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		readOnlyStmt.setFetchSize(Integer.MIN_VALUE);
		if (!tableExists("concept_s", true)) {
			print ("Did not find concept table - is database ready?");
			System.exit(-1);
		}
		
		negativeDeltaArchive = new File(args[4]);
		if (!negativeDeltaArchive.canRead()) {
			print ("Did not find valid negative delta archive - " + args[4]);
			System.exit(-1);
		}
		
		targetEffectiveTime = args[5];
		long targetEffectiveTimeLong = Long.parseLong(targetEffectiveTime);
		if (targetEffectiveTime.length() != 8 || targetEffectiveTimeLong < 20010101 || targetEffectiveTimeLong > 20600731) {
			print ("Invalid target effective time: " +targetEffectiveTime);
			System.exit(-1);
		}
	}
	
	private boolean tableExists (String tableName, boolean verbose) throws SQLException {
		boolean tableExists = true;
		try {
			rowCount(tableName, verbose);
		} catch (SQLException e) {
			tableExists = false;
		}
		return tableExists;
	}
	
	private int rowCount (String tableName, boolean verbose) throws SQLException {
		int rows = 0;
		ResultSet rs=stmt.executeQuery("select count(*) from " + tableName);
		while(rs.next()) {
			rows = rs.getInt(1);
		}
		if (verbose) {
			print ( rows + " rows available in table " + tableName);
		}
		return rows;
	}
	
	private void cloneTables() throws SQLException {
		for (SnomedTable table : SnomedTable.SnomedTables) {
			print ("Preparing modifiable copy of " + table.getTableName() + " tables");
			for (String tableType : TABLE_TYPES) {
				String origName = table.getTableName() + tableType;
				String cloneName = MODIFIER_PREFIX  + table.getTableName() + tableType;
				if (tableExists(origName, false)) {
					String sql = "drop table if exists " + cloneName;
					stmt.execute(sql);
					sql = "CREATE TABLE " + cloneName + " LIKE " + table.getTableName() + tableType;
					stmt.execute(sql);
					//Only want data copied in the case of the FULL table
					if (tableType.equals(FULL_SUFFIX)) {
						sql = "INSERT INTO " + cloneName + " SELECT * FROM  " + table.getTableName() + tableType;
						stmt.execute(sql);
					}
				} else {
					print ("Warning, skipping " + origName + ", does not exist");
				}
			}
		}
	}
	
	private void processNegativeDelta() throws ApplicationException {
		//Work through the negative delta archive and remove those rows from the cloned tables
		print ("Processing negative delta archive " + negativeDeltaArchive.getAbsolutePath());
		String fileName = "";
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(negativeDeltaArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						fileName = p.getFileName().toString();
						if (fileName.startsWith(DELETION_PREFIX)) {
							SnomedTable table = identifyTable(fileName, DELTA_SUFFIX);
							if (table != null) {
								processNegativeDeltaFile(zis, table);
							}
						}
					}
					ze = zis.getNextEntry();
				}
			} catch (SQLException | IOException e) {
				throw new ApplicationException ("Exception while processing " + fileName, e);
			} finally {
				try{
					zis.closeEntry();
					zis.close();
				} catch (Exception e){}
			}
		} catch (IOException e) {
			throw new ApplicationException("Failed to read negative delta archive", e);
		}
	}

	private SnomedTable identifyTable(String fileName, String tableType) {
		for (SnomedTable table : SnomedTable.SnomedTables) {
			String fileNamePart = table.getFilenamePart().replace("TYPE", SnomedTable.getFileType(tableType));
			if (fileName.contains(fileNamePart)) {
				return table;
			}
		}
		print ("Unable to determine table for file " + fileName + " skipping...");
		return null;
	}

	private void processNegativeDeltaFile(InputStream is, SnomedTable table) throws IOException, SQLException {
		//Read each row from the file and remove it from the cloned full table
		print ("Processing negative delta for " + table.getTableName());
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				processNegativeDeltaLine (lineItems, table);
			}
			isHeaderLine = false;
		}
	}

	private void processNegativeDeltaLine(String[] lineItems, SnomedTable table) throws SQLException {
		//If the deletion line is active, delete that row from the database
		String sql = "DELETE FROM " + MODIFIER_PREFIX + table.getTableName() + FULL_SUFFIX + 
				" WHERE id = ?" +
				" AND effectiveTime =  ?" + 
				" AND active = ?" +
				" AND moduleId = ?";
		PreparedStatement delStmt = conn.prepareStatement(sql);
		if (lineItems[IDX_DELETIONACTIVE].equals(ACTIVE)) {
			delStmt.setString(1, lineItems[IDX_ID]);
			delStmt.setString(2, lineItems[IDX_EFFECTIVETIME]);
			delStmt.setString(3, lineItems[IDX_ACTIVE]);
			delStmt.setString(4, lineItems[IDX_MODULEID]);
			int rowsDeleted = delStmt.executeUpdate();
			String line =  StringUtils.join(lineItems, "\t");
			print (((rowsDeleted == 1)?"Deleted ":"Failed to delete ") + table.getTableName() + " row: " + line);
		}
	}

	private void calculateSnapshot() throws ApplicationException {
		try{
			//First strip off any rows that are greater than our target effectiveTime
			deleteFutureRows();
		} catch (SQLException e) {
			throw new ApplicationException("Failed to remove future rows prior to calculating SNAPSHOT",e);
		}
		
		String lastTableProcessed="";
		try {
			for (SnomedTable table : SnomedTable.SnomedTables) {
				lastTableProcessed =  table.getTableName();
				print ("Calculating Snapshot for " + table.getTableName());
				String fullTable = MODIFIER_PREFIX  + table.getTableName() + FULL_SUFFIX;
				String snapshotTable = MODIFIER_PREFIX  + table.getTableName() + SNAPSHOT_SUFFIX;
				String sql = "INSERT INTO " + snapshotTable +
				" SELECT a.* FROM " + fullTable + " a " +
				" WHERE a.effectiveTime = " +
					"(SELECT max(effectiveTime) FROM " + fullTable + " b " +
					" WHERE a.id = b.id )";
				stmt.execute(sql);
			}
		} catch (SQLException e) {
			throw new ApplicationException("Failed to calculate SNAPSHOT for " + lastTableProcessed,e);
		}
	}

	private void deleteFutureRows() throws SQLException {
		for (SnomedTable table : SnomedTable.SnomedTables) {
			print ("Removing rows after " + targetEffectiveTime + " from " + table.getTableName());
			String cloneName = MODIFIER_PREFIX  + table.getTableName() + FULL_SUFFIX;
			String sql = "DELETE FROM  " + cloneName + 
					" WHERE effectiveTime > " + targetEffectiveTime;
			stmt.execute(sql);
		}
	}
	

	private void calculateDelta() throws ApplicationException {
		String lastTableProcessed="";
		try {
			for (SnomedTable table : SnomedTable.SnomedTables) {
				lastTableProcessed =  table.getTableName();
				print ("Calculating Delta for " + table.getTableName());
				String deltaTable = MODIFIER_PREFIX  + table.getTableName() + DELTA_SUFFIX;
				String snapshotTable = MODIFIER_PREFIX  + table.getTableName() + SNAPSHOT_SUFFIX;
				String sql = "INSERT INTO " + deltaTable +
				" SELECT a.* FROM " + snapshotTable + " a " +
				" WHERE a.effectiveTime = " + targetEffectiveTime;
				stmt.execute(sql);
			}
		} catch (SQLException e) {
			throw new ApplicationException("Failed to calculate DELTA for " + lastTableProcessed,e);
		}
	}
	

	private void outputArchive() throws ApplicationException {
		File exportLocationRoot = Files.createTempDir();
		File exportLocation = new File(exportLocationRoot, "SnomedCT_" + targetEffectiveTime);
		print ("Export location: " + exportLocation.getAbsolutePath(), false);
		writeFilesToDisk(exportLocation);
		createArchive(exportLocation);
	}
	
	private void writeFilesToDisk(File exportLocation) throws ApplicationException {
		String lastTableProcessed="";
		try {
			for (SnomedTable table : SnomedTable.SnomedTables) {
				for (String tableType : TABLE_TYPES) {
					lastTableProcessed =  table.getTableName() + tableType;
					print ("Exporting " + lastTableProcessed, false);
					export (exportLocation, table, tableType);
				}
			}
		} catch (IOException|SQLException e) {
			throw new ApplicationException("Failed to export " + lastTableProcessed,e);
		}
	}

	private void export(File exportLocation, SnomedTable table, String tableType) throws SQLException, IOException {
		String fileName = table.getFilename(edition, targetEffectiveTime, tableType);
		File outputFile = new File (exportLocation, fileName);
		GlobalUtils.ensureFileExists(outputFile.getAbsolutePath());
		//Write the header line
		GlobalUtils.writeToFile(outputFile, table.getFileHeader());
		String tableName = MODIFIER_PREFIX + table.getTableName() + tableType;
		int rowTarget = rowCount(tableName, false);
		String sql = "SELECT * FROM " + tableName;
		ResultSet rs = readOnlyStmt.executeQuery(sql);
		StringBuffer sb = new StringBuffer();
		int rowsProcessed = 0;
		while(rs.next()) {
			sb.setLength(0);
			for (int col=1 ; col <= rs.getMetaData().getColumnCount(); col++) {
				if (col > 1) {
					sb.append(FIELD_DELIMITER);
				}
				sb.append(rs.getString(col));
			}
			rowsProcessed++;
			int perc = (int)(((double)rowsProcessed /(double)rowTarget) * 100);
			tempPrint(perc + "% complete");
			GlobalUtils.writeToFile(outputFile, sb.toString());
		}
		print("");  //Clear print line
	}

	public void createArchive(File exportLocation) throws ApplicationException {
		try {
			// The zip filename will be the name of the first thing in the zip location
			// ie in this case the directory SnomedCT_RF1Release_INT_20150731
			String zipFileName = exportLocation.listFiles()[0].getName() + ".zip";
			int fileNameModifier = 1;
			while (new File(zipFileName).exists()) {
				zipFileName = exportLocation.listFiles()[0].getName() + "_" + fileNameModifier++ + ".zip";
			}
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));
			String rootLocation = exportLocation.getAbsolutePath() + File.separator;
			print("Creating archive : " + zipFileName + " from files found in " + rootLocation);
			addDir(rootLocation, exportLocation, out);
			out.close();
		} catch (IOException e) {
			throw new ApplicationException("Failed to create revised archive from " + exportLocation, e);
		}
	}

	public void addDir(String rootLocation, File dirObj, ZipOutputStream out) throws IOException {
		File[] files = dirObj.listFiles();
		byte[] tmpBuf = new byte[1024];

		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				addDir(rootLocation, files[i], out);
				continue;
			}
			FileInputStream in = new FileInputStream(files[i].getAbsolutePath());
			String relativePath = files[i].getAbsolutePath().substring(rootLocation.length());
			print(" Adding: " + relativePath);
			out.putNextEntry(new ZipEntry(relativePath));
			int len;
			while ((len = in.read(tmpBuf)) > 0) {
				out.write(tmpBuf, 0, len);
			}
			out.closeEntry();
			in.close();
		}
	}

	private void print (String msg)  {
		print (msg, true);
	}
	
	private void print (String msg, boolean includeTiming)  {
		if (includeTiming) {
			//Add time taken onto the end of the last message
			Timestamp now = new Timestamp(System.currentTimeMillis());
			System.out.println (" (" + timeDiff(lastMsg, now) + ")");
			System.out.print(msg);
			lastMsg = now;
		} else {
			System.out.println(msg);
		}
	}
	
	private void tempPrint (String msg) {
		System.out.print(msg + "\r");
	}
	
	private static String timeDiff (Timestamp earlier, Timestamp later) {
		int totalSecs = (int)(later.getTime() - earlier.getTime()) / 1000;
		int minutes = totalSecs / 60;
		int seconds = totalSecs % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}
}
