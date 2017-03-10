package com.gelakinetic.GathererScraper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import javax.swing.table.AbstractTableModel;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;

/**
 * This class contains the table model to display information about expansions
 * ripe for the scraping
 *
 * @author AEFeinstein
 *
 */
public class ExpansionTableModel extends AbstractTableModel {

	private static final long	serialVersionUID		= -292863957248684813L;

	private static final int	COLUMN_NAME				= 0;
	private static final int	COLUMN_CODE				= 1;
	private static final int	COLUMN_MTGINFO_NAME		= 2;
	private static final int	COLUMN_TCGPLAYER_NAME	= 3;
	private static final int	COLUMN_MKM_NAME			= 4;
	private static final int	COLUMN_DATE				= 5;
	private static final int	COLUMN_FOIL				= 6;
	private static final int	COLUMN_CHECKED			= 7;

	private static final int[]	COLUMNS					= {
		COLUMN_NAME,
		COLUMN_MTGINFO_NAME,
		COLUMN_TCGPLAYER_NAME,
		COLUMN_MKM_NAME,
		COLUMN_DATE,
		COLUMN_CHECKED,
		COLUMN_CODE,
		COLUMN_FOIL
	};

	/** A list of expansions prime for the scraping */
	ArrayList<Expansion>		mExpansions				= new ArrayList<Expansion>();

	/**
	 * @return The number of columns in this model
	 */
	@Override
	public int getColumnCount() {
		return COLUMNS.length;
	}

	/**
	 * @return The number of rows in this model
	 */
	@Override
	public int getRowCount() {
		return mExpansions.size();
	}

	/**
	 * Returns the value of a particular cell
	 *
	 * @param row
	 *            The index of the cell's row
	 * @param col
	 *            The index of the cell's column
	 * @return The value of the cell
	 */
	@Override
	public Object getValueAt(int row, int col) {
		switch (col) {
			case COLUMN_NAME: {
				return mExpansions.get(row).mName_gatherer;
			}
			case COLUMN_CODE: {
				return mExpansions.get(row).mCode_gatherer;
			}
			case COLUMN_MTGINFO_NAME: {
				return mExpansions.get(row).mCode_mtgi;
			}
			case COLUMN_TCGPLAYER_NAME: {
				return mExpansions.get(row).mName_tcgp;
			}
			case COLUMN_MKM_NAME: {
				return mExpansions.get(row).mName_mkm;
			}
			case COLUMN_DATE: {
				return mExpansions.get(row).mDate;
			}
			case COLUMN_CHECKED: {
				return mExpansions.get(row).mChecked;
			}
			case COLUMN_FOIL: {
				return mExpansions.get(row).mCanBeFoil;
			}
		}
		return null;
	}

	/**
	 * JTable uses this method to determine the default renderer/ editor for
	 * each cell. If we didn't implement this method, then the last column would
	 * contain text ("true"/"false"), rather than a check box.
	 *
	 * @param col
	 *            The index of the cell's column
	 * @return The class of the value in that cell
	 */
	public Class<? extends Object> getColumnClass(int col) {
		try {
			return getValueAt(0, col).getClass();
		}
		catch (NullPointerException e) {
			return null;
		}
	}

	/**
	 * Returns if a cell is editable or not
	 *
	 * @param row
	 *            The row of the cell to check
	 * @param col
	 *            The column of the cell to check
	 * @return true if the cell is editable, false otherwise
	 */
	public boolean isCellEditable(int row, int col) {
		/*
		 * Note that the data/cell address is constant, no matter where the cell
		 * appears onscreen.
		 */
		if (col > COLUMN_NAME) {
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Sets the value of a given cell to the given value
	 *
	 * @param value
	 *            The value to assign to the cell
	 * @param row
	 *            The row of the cell to modify
	 * @param col
	 *            The column of the cell to modify
	 */
	public void setValueAt(Object value, int row, int col) {
		switch (col) {
			case COLUMN_NAME: {
				mExpansions.get(row).mName_gatherer = (String) value;
				break;
			}
			case COLUMN_CODE: {
				mExpansions.get(row).mCode_gatherer = (String) value;
				break;
			}
			case COLUMN_MTGINFO_NAME: {
				mExpansions.get(row).mCode_mtgi = (String) value;
				break;
			}
			case COLUMN_TCGPLAYER_NAME: {
				mExpansions.get(row).mName_tcgp = (String) value;
				break;
			}
			case COLUMN_MKM_NAME: {
				mExpansions.get(row).mName_mkm = (String) value;
				break;
			}
			case COLUMN_DATE: {
				mExpansions.get(row).mDate = (String) value;
				break;
			}
			case COLUMN_CHECKED: {
				mExpansions.get(row).mChecked = (Boolean) value;
				break;
			}
			case COLUMN_FOIL: {
				mExpansions.get(row).mCanBeFoil = (Boolean) value;
				break;
			}
		}
	}

	/**
	 * Returns column names for display
	 *
	 * @param col
	 *            the index of the column
	 * @return the name of the column
	 */
	@Override
	public String getColumnName(int col) {
		switch (col) {
			case COLUMN_NAME: {
				return "Name";
			}
			case COLUMN_CODE: {
				return "Code";
			}
			case COLUMN_MTGINFO_NAME: {
				return "Code MTGI";
			}
			case COLUMN_TCGPLAYER_NAME: {
				return "Name TCGP";
			}
			case COLUMN_MKM_NAME: {
				return "Name MKM";
			}
			case COLUMN_DATE: {
				return "Date";
			}
			case COLUMN_CHECKED: {
				return "Scrape?";
			}
			case COLUMN_FOIL: {
				return "Can be Foil?";
			}
			default: {
				return "";
			}
		}
	}

	/**
	 * Reads expansion information from a json file of expansions. If the
	 * expansion names match, update the entry we already have.
	 *
	 * @param JsonExpansions
	 *            The file to read expansion information from
	 * @throws FileNotFoundException
	 *             If the file doesn't exist
	 * @throws IOException
	 *             If the file can't be read
	 * @throws ParseException
	 *             If the data in the file is corrupted
	 */
	public void readInfo(File JsonExpansions) throws FileNotFoundException, IOException {

		Gson gson = GathererScraper.getGson();
		
		String jsonContent = new String(Files.readAllBytes(Paths.get(JsonExpansions.getPath())));
		Expansion[] expansions = gson.fromJson(jsonContent, Expansion[].class);
		
		for (Expansion e : expansions) {
			for (Expansion existing : mExpansions) {
				if (existing.mName_gatherer.equals(e.mName_gatherer)) {
					existing.mDigest = e.mDigest;
					existing.mCode_gatherer = e.mCode_gatherer;
					existing.mCode_mtgi = e.mCode_mtgi;
					existing.mName_mkm = e.mName_mkm;
					existing.mName_tcgp = e.mName_tcgp;
					existing.mDate = e.mDate;
					existing.mCanBeFoil = e.mCanBeFoil;
				}
			}
		}
	}

	/**
	 * @return Today's date, in String form
	 */
	private String getDateString() {
		/* Get today's date */
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
		Calendar cal = Calendar.getInstance();
		return dateFormat.format(cal.getTime());
	}
	
	/**
	 * Writes the patches manifest out to a file from the ExpansionTableModel
	 * 
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             If the write failed
	 */
	public void writePatchesFile(File outFile) throws IOException {

		/* Build an array of patches */
		JSONArray patchesArray = new JSONArray();
		for (Expansion exp : mExpansions) {
			/*
			 * Note, new fields cannot be added to this JSON object. It breaks
			 * old updaters
			 */
			if (exp.isScraped()) {
				JSONObject patchInfo = new JSONObject();
				patchInfo.put("Name", exp.mName_gatherer);
				patchInfo.put("URL", "https://sites.google.com/site/mtgfamiliar/patches/" + exp.mCode_gatherer
						+ ".json.gzip");
				patchInfo.put("Code", exp.mCode_gatherer);
				patchesArray.add(patchInfo);
			}
		}

		JSONObject patchFile = new JSONObject();
		patchFile.put("Date", getDateString());
		patchFile.put("Patches", patchesArray);

		writeFile(patchFile, outFile);
	}
	
	/**
	 * Writes the TCGPlayer.com names out to a file from the ExpansionTableModel
	 * 
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             If the write failed
	 */
	public void writeTcgNamesFile(File outFile) throws IOException {

		JSONArray tcgNamesArray = new JSONArray();

		for (Expansion exp : mExpansions) {
			if (exp.isScraped()) {
				JSONObject tcgname = new JSONObject();
				tcgname.put("Code", exp.mCode_gatherer);
				tcgname.put("TCGName", exp.mName_tcgp);
				tcgNamesArray.add(tcgname);
			}
		}

		JSONObject TcgFile = new JSONObject();
		TcgFile.put("Date", getDateString());
		TcgFile.put("Sets", tcgNamesArray);

		writeFile(TcgFile, outFile);
	}

	/**
	 * Writes the magiccardmarket.eu names out to a file from the
	 * ExpansionTableModel
	 * 
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             If the write failed
	 */
	public void writeMkmNamesFile(File outFile) throws IOException {

		JSONArray mkmNamesArray = new JSONArray();

		for (Expansion exp : mExpansions) {
			if (exp.isScraped()) {
				JSONObject mkmname = new JSONObject();
				mkmname.put("Code", exp.mCode_gatherer);
				mkmname.put("MKMName", exp.mName_mkm);
				mkmNamesArray.add(mkmname);
			}
		}

		JSONObject MkmFile = new JSONObject();
		MkmFile.put("Date", getDateString());
		MkmFile.put("Sets", mkmNamesArray);

		writeFile(MkmFile, outFile);
	}

	/**
	 * Writes the expansion digests out to a file from the ExpansionTableModel
	 * 
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             If the write failed
	 */
	public void writeDigestsFile(File outFile) throws IOException {
		JSONArray digestsArray = new JSONArray();

		for (Expansion exp : mExpansions) {
			if (exp.isScraped()) {
				JSONObject digest = new JSONObject();
				digest.put("Code", exp.mCode_gatherer);
				digest.put("Digest", exp.mDigest);
				digestsArray.add(digest);
			}
		}

		JSONObject DigestsFile = new JSONObject();
		DigestsFile.put("Date", getDateString());
		DigestsFile.put("Digests", digestsArray);

		writeFile(DigestsFile, outFile);
	}

	/**
	 * Writes the "can be foil"s out to a file from the ExpansionTableModel
	 * 
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             If the write failed
	 */
	public void writeCanBeFoilFile(File outFile) throws IOException {
		JSONArray canBeFoilArray = new JSONArray();

		for (Expansion exp : mExpansions) {
			if (exp.isScraped()) {
				JSONObject canBeFoil = new JSONObject();
				canBeFoil.put("Code", exp.mCode_gatherer);
				canBeFoil.put("canBeFoil", exp.mCanBeFoil);
				canBeFoilArray.add(canBeFoil);
			}
		}

		JSONObject CanBeFoilFile = new JSONObject();
		CanBeFoilFile.put("Date", getDateString());
		CanBeFoilFile.put("CanBeFoil", canBeFoilArray);

		writeFile(CanBeFoilFile, outFile);
	}
	
	/**
	 * Write the json object to a json file, UTF-8, Unix line endings
	 * 
	 * @param json
	 *            The JSON object to write
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             Thrown if the write fails
	 */
	private void writeFile(JSONObject json, File outFile) throws IOException {
		System.setProperty("line.separator", "\n");
		OutputStreamWriter osw = new OutputStreamWriter(
				new FileOutputStream(outFile), Charset.forName("UTF-8"));
		osw.write(json.toJSONString().replace("\r", ""));
		osw.flush();
		osw.close();
	}
	
}

