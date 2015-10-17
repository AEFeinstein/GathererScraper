package com.gelakinetic.GathererScraper;

import java.io.File;

import javax.swing.filechooser.FileFilter;

/**
 * This class is used to only display json files in the Filechooser
 *
 * @author AEFeinstein
 *
 */
public class JsonFilter extends FileFilter {

	/**
	 * Checks a file's extension to see if it should be displayed or not.
	 *
	 * @param f
	 *            The file to be displayed, or not
	 * @return true if the file's extension is "json". False otherwise
	 */
	@Override
	public boolean accept(File f) {
		if (f.isDirectory()) {
			return true;
		}

		String extension = getExtension(f);
		if (extension != null) {
			extension = extension.toLowerCase();
			if (extension.equalsIgnoreCase("json")) {
				return true;
			}
			else {
				return false;
			}
		}
		return false;
	}

	/**
	 * Returns a brief description of what is filtered
	 *
	 * @return "JSON Files"
	 */
	@Override
	public String getDescription() {
		return "JSON Files";
	}

	/**
	 * Get the extension of a file.
	 *
	 * @param f
	 *            The file to have the extension read
	 * @return The file extension (anything after the last '.')
	 */
	public static String getExtension(File f) {
		try {
			String ext = null;
			String s = f.getName();
			int i = s.lastIndexOf('.');

			if (i > 0 && i < s.length() - 1) {
				ext = s.substring(i + 1).toLowerCase();
			}
			return ext;
		}
		catch (Exception e) {
			System.out.println(e.toString());
			return " ";
		}
	}
}
