package com.gelakinetic.GathererScraper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import javax.swing.table.AbstractTableModel;

import com.gelakinetic.GathererScraper.JsonTypes.Expansion;
import com.gelakinetic.GathererScraper.JsonTypes.Manifest;
import com.gelakinetic.GathererScraper.JsonTypes.Manifest.ManifestEntry;
import com.gelakinetic.GathererScraper.JsonTypesGS.ExpansionGS;
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
	ArrayList<ExpansionGS>		mExpansions				= new ArrayList<ExpansionGS>();

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
				return timestampToDateString(mExpansions.get(row).mReleaseTimestamp);
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
				mExpansions.get(row).mReleaseTimestamp = dateStringToTimestamp((String) value);
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
			for (ExpansionGS existing : mExpansions) {
				if (GathererScraper.removeNonAscii(existing.mName_gatherer).equals(GathererScraper.removeNonAscii(e.mName_gatherer))) {
					existing.mName_gatherer = GathererScraper.removeNonAscii(e.mName_gatherer);
					existing.mDigest = e.mDigest;
					existing.mCode_gatherer = e.mCode_gatherer;
					existing.mCode_mtgi = e.mCode_mtgi;
					existing.mName_mkm = e.mName_mkm;
					existing.mName_tcgp = e.mName_tcgp;
					existing.mReleaseTimestamp = e.mReleaseTimestamp;
					existing.mCanBeFoil = e.mCanBeFoil;
				}
			}
		}
	}
	
	/**
	 * @param timestamp A timestamp (in seconds)
	 * @return A String MM/yyyy created from the timestamp
	 */
	private String timestampToDateString(long timestamp)
	{
		DateFormat dateFormat = new SimpleDateFormat("MM/yyyy");
		return dateFormat.format(new Date(timestamp * 1000));
	}
	
	/**
	 * 
	 * @param date A string containing a date like 'MM/yyyy'
	 * @return A timestamp (in seconds) for the 1st day of the MM month of the yyyy year.
	 */
	private long dateStringToTimestamp(String date)
	{
		long retval = 0;
		DateFormat dateFormat = new SimpleDateFormat("MM/yyyy");
		try {
			retval = dateFormat.parse(date).getTime() / 1000;
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return retval;
	}

	/**
	 * @return Today's UNIX timestamp
	 */
	static private long getTodayTimestamp() {
		Calendar cal = Calendar.getInstance();
		return cal.getTime().getTime() / 1000;
	}
	
	/**
	 * Writes the patches manifest out to a file from the ExpansionTableModel
	 * 
	 * @param outFile
	 *            The file to write to
	 * @throws IOException
	 *             If the write failed
	 */
	public void writePatchesManifestFile(File outFile) throws IOException {

		Manifest manifest = new Manifest();
		manifest.mTimestamp = getTodayTimestamp();
		
		/* Build an array of patches */
		for (ExpansionGS exp : mExpansions) {
			/*
			 * Note, new fields cannot be added to this JSON object. It breaks
			 * old updaters
			 */
			if (exp.isScraped()) {
				ManifestEntry entry = manifest.new ManifestEntry();
				entry.mName = exp.mName_gatherer;
				entry.mURL = "https://raw.githubusercontent.com/AEFeinstein/GathererScraper/master/patches-v2/" + exp.mCode_gatherer
						+ ".json.gzip";
				entry.mCode = exp.mCode_gatherer;
				entry.mDigest = exp.mDigest;
				manifest.mPatches.add(entry);
			}
		}

		/* Sort the patches before writing them */
		Collections.sort(manifest.mPatches);
		GathererScraper.writeFile(manifest, outFile, false);
	}
	
}

