package org.snomed.negative_delta;

import static org.snomed.util.GlobalUtils.print;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.snomed.ApplicationException;

public class NegativeDeltaProcessor {

	Connection conn;
	Statement stmt;
	File negativeDeltaArchive;
	String targetEffectiveTime;
	
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
		app.init(args);
		app.cloneTables();  //Create a tables with modified_ prepended to them.
		app.processNegativeDelta();
		app.calculateSnapshot();
		app.calculateDelta();
		app.outputArchive();
		app.outputCurrentStateOfDeletedComponents();
	}

	private void init(String[] args) throws SQLException, ClassNotFoundException {
		if (args.length < 5) {
			print ("Usage NegativeDeltaProcessor <dbHost:port> <dbName> <dbUsername> <dbPassword> <negativeDeltaArchive>");
			System.exit(-1);
		}
		
		negativeDeltaArchive = new File(args[4]);
		if (!negativeDeltaArchive.canRead()) {
			print ("Did not find valid negative delta archive - " + args[4]);
			System.exit(-1);	
		}
		
		Class.forName("com.mysql.jdbc.Driver");
		conn=DriverManager.getConnection(
		"jdbc:mysql://" + args[0] + "/" + args[1], args[2], args[3]);
		conn.setAutoCommit(false);
		stmt=conn.createStatement();
		checkConnection();
	}
	
	private void checkConnection() throws SQLException {
		int rows = 0;
		ResultSet rs=stmt.executeQuery("select count(*) from concepts_s");
		while(rs.next()) {
			rows = rs.getInt(1);
		}
		print ( rows + " concepts available.");
	}
	
	private void cloneTables() throws SQLException {
		for (SnomedTable table : SnomedTable.SnomedTables) {
			print ("Preparing a modified copy of " + table.getTableName());
			String cloneName = MODIFIER_PREFIX  + table.getTableName();
			String sql = "drop table if exists " + cloneName;
			stmt.execute(sql);
			sql = "CREATE TABLE " + cloneName + " LIKE " + table.getTableName();
			stmt.execute(sql);
			sql = "INSERT INTO " + cloneName + " SELECT * FROM  " + table.getTableName();
			stmt.execute(sql);
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
							SnomedTable table = identifyTable(fileName);
							processNegativeDeltaFile(zis, table);
						}
					}
				}
			}catch (SQLException | IOException e) {
				print ("Exception while processing " + fileName + ": " + e);
				e.printStackTrace();
			}
		} catch (IOException e) {
			throw new ApplicationException("Failed to read negative delta archive", e);
		}
	
	}

	private SnomedTable identifyTable(String fileName) {
		for (SnomedTable table : SnomedTable.SnomedTables) {
			if (fileName.contains(table.getFilenamePart())) {
				return table;
			}
		}
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
		String sql = "DELETE FROM " + MODIFIER_PREFIX + table.getTableName() +
				"WHERE id = ? " +
				"AND effectiveTime =  ? " + 
				"AND active = ? " +
				"AND moduleId = ?";
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
}
