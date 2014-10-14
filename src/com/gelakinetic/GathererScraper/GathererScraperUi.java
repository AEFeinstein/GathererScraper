package com.gelakinetic.GathererScraper;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.camick.TableColumnAdjuster;

/**
 * This class handles the UI for the application, as well as some file I/O
 * 
 * @author AEFeinstein
 *
 */
public class GathererScraperUi {

	private static final String	PATCH_FILE_NAME		= "patches.json";
	private static final String	TCG_FILE_NAME		= "TCGnames.json";
	private static final String	MKM_FILE_NAME		= "mkmnames.json";
	private static final String	EXPANSION_FILE_NAME	= "expansions.json";
	public static final String	LEGAL_FILE_NAME		= "legality.json";

	private JProgressBar		mExpansionProgressBar;
	private JLabel				mLastCardScraped;
	private JRadioButton		mRdbtnOverwriteManifests;
	private JTable				mTable;

	private ExpansionTableModel	mExpansionTableModel;
	private LegalityListModel	mLegalityListModel;
	private String				mFilesPath;

	File						mTcgNamesFile		= null;
	File						mMkmNamesFile		= null;
	File						mPatchesFile		= null;
	File						mLegalityFile		= null;
	File						mExpansionsFile		= null;

	private JSONArray			mPatchesArray		= new JSONArray();
	private JSONArray			mTcgNamesArray		= new JSONArray();
	private JSONArray			mMkmNamesArray		= new JSONArray();

	private int					mNumExpansions;
	private int					mExpansionsProcessed;

	/**
	 * Launch the application.
	 * 
	 * @param args
	 *            Command line arguments, unused
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					(new GathererScraperUi()).initialize();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Set the look and feel, get the directory for the data files, and create
	 * some File objects
	 */
	public GathererScraperUi() {

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			; /* don't worry about it */
		}

		/* Ask for an expansions file, the directory will be figured out */
		File inputDirectory = askForFile("Manifest Files?", new JsonFilter());

		mFilesPath = null;
		if (inputDirectory != null) {
			if (inputDirectory.isDirectory()) {
				mFilesPath = inputDirectory.getAbsolutePath();
			}
			else {
				mFilesPath = inputDirectory.getParent();
			}
		}

		/* Make some files to be opened later */
		mExpansionsFile = new File(mFilesPath, EXPANSION_FILE_NAME);
		mPatchesFile = new File(mFilesPath, PATCH_FILE_NAME);
		mTcgNamesFile = new File(mFilesPath, TCG_FILE_NAME);
		mMkmNamesFile = new File(mFilesPath, MKM_FILE_NAME);
		mLegalityFile = new File(mFilesPath, LEGAL_FILE_NAME);

		/*
		 * If the expansion file isn't found, don't bother running the
		 * application
		 */
		if (mFilesPath == null || !mExpansionsFile.exists()) {
			JOptionPane.showMessageDialog(null, "Expansion info not found.", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {

		if (mFilesPath == null || !mExpansionsFile.exists()) {
			return;
		}

		JFrame frame = new JFrame();
		frame.setVisible(false);
		frame.setBounds(100, 100, 930, 512);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0, 278, 80, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0, 0, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		frame.getContentPane().setLayout(gridBagLayout);

		frame.addWindowListener(new java.awt.event.WindowAdapter() {

			/**
			 * When the program closes, make a JSONArray of all the expansions,
			 * and then write it to a file.
			 * 
			 * @param arg0
			 *            An event that indicates this window has changed it's
			 *            status
			 */
			@Override
			public void windowClosing(java.awt.event.WindowEvent arg0) {
				JSONArray expansions = new JSONArray();
				for (Expansion e : mExpansionTableModel.mExpansions) {
					expansions.add(e.toJsonObject());
				}
				try {
					FileWriter fw = new FileWriter(new File(EXPANSION_FILE_NAME));
					fw.write(expansions.toJSONString());
					fw.close();
				}
				catch (IOException e1) {
					e1.printStackTrace();
				}

				super.windowClosing(arg0);
			}

		});

		/* Create the models and scraper objects */
		mExpansionTableModel = new ExpansionTableModel();
		mLegalityListModel = new LegalityListModel();
		try {
			/* Get a list of expansions from the internet */
			mExpansionTableModel.mExpansions = GathererScraper.scrapeExpansionList();
			mLegalityListModel.setExpansions(mExpansionTableModel.mExpansions);
			try {
				/* Then add the extra data from the expansions file */
				mExpansionTableModel.readInfo(mExpansionsFile);

				JSONParser parser = new JSONParser();
				if (mPatchesFile.exists()) {
					mPatchesArray = (JSONArray) ((JSONObject) parser.parse(new FileReader(mPatchesFile)))
							.get("Patches");
				}
				if (mTcgNamesFile.exists()) {
					mTcgNamesArray = (JSONArray) ((JSONObject) parser.parse(new FileReader(mTcgNamesFile))).get("Sets");
				}
				if (mMkmNamesFile.exists()) {
					mMkmNamesArray = (JSONArray) ((JSONObject) parser.parse(new FileReader(mMkmNamesFile))).get("Sets");
				}
				if (mLegalityFile.exists()) {
					mLegalityListModel.loadLegalities(mLegalityFile);
				}
			}
			catch (ParseException e) {
				e.printStackTrace();
			}
		}
		catch (IOException e) {
			mTable = new JTable();
		}

		mExpansionProgressBar = new JProgressBar();
		GridBagConstraints gbc_progressBar_1 = new GridBagConstraints();
		gbc_progressBar_1.fill = GridBagConstraints.BOTH;
		gbc_progressBar_1.gridwidth = 3;
		gbc_progressBar_1.insets = new Insets(0, 0, 5, 5);
		gbc_progressBar_1.gridx = 0;
		gbc_progressBar_1.gridy = 0;
		frame.getContentPane().add(mExpansionProgressBar, gbc_progressBar_1);

		mLastCardScraped = new JLabel("");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel.gridwidth = 3;
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 0;
		gbc_lblNewLabel.gridy = 1;
		frame.getContentPane().add(mLastCardScraped, gbc_lblNewLabel);

		Component lblLegalities = new JLabel("Legalities");
		GridBagConstraints gbc_lblLegalities = new GridBagConstraints();
		gbc_lblLegalities.insets = new Insets(0, 0, 5, 0);
		gbc_lblLegalities.gridx = 3;
		gbc_lblLegalities.gridy = 1;
		frame.getContentPane().add(lblLegalities, gbc_lblLegalities);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.gridwidth = 3;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 0;
		gbc_scrollPane.gridy = 2;
		frame.getContentPane().add(scrollPane, gbc_scrollPane);
		mTable = new JTable(mExpansionTableModel);
		mTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		TableColumnAdjuster tca = new TableColumnAdjuster(mTable);
		tca.adjustColumns();

		scrollPane.setViewportView(mTable);

		JButton btnScrape = new JButton("Scrape!");
		btnScrape.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {

				(new SwingWorker<Void, Void>() {

					/**
					 * Once the user presses the button, do the actual scraping
					 * in a SwingWorker
					 */
					@Override
					protected Void doInBackground() {

						long startTime = System.currentTimeMillis();

						/* Make sure the legality dialogs are closed */
						if (mLegalityListModel.mWindowsOpen > 0) {
							JOptionPane.showMessageDialog(frame, "Please close all format windows.", "Error",
									JOptionPane.ERROR_MESSAGE);
							return null;
						}

						/* Prevent clicks */
						frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
						frame.setEnabled(false);

						/* Should we append or overwrite the manifest files? */
						if (mRdbtnOverwriteManifests.isSelected()) {
							mPatchesArray.clear();
							mTcgNamesArray.clear();
							mMkmNamesArray.clear();
						}

						/* Get today's date */
						DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
						Calendar cal = Calendar.getInstance();
						String date = dateFormat.format(cal.getTime());

						/* Write the legality information first */
						try {
							if (!mLegalityListModel.writeJsonToFile(frame, mFilesPath, date)) {
								frame.setEnabled(true);
								frame.setCursor(Cursor.getDefaultCursor());
								return null;
							}
						}
						catch (IOException e1) {
							e1.printStackTrace();
						}

						JSONObject patchFile = new JSONObject();
						patchFile.put("Date", date);
						patchFile.put("Patches", mPatchesArray);

						JSONObject TcgFile = new JSONObject();
						TcgFile.put("Date", date);
						TcgFile.put("Sets", mTcgNamesArray);

						JSONObject MkmFile = new JSONObject();
						MkmFile.put("Date", date);
						MkmFile.put("Sets", mMkmNamesArray);

						/*
						 * Make a thread pool to scrape each set in it's own
						 * thread
						 */
						ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime()
								.availableProcessors());
						mNumExpansions = 0;
						mExpansionsProcessed = 0;
						for (final Expansion exp : mExpansionTableModel.mExpansions) {
							if (exp.mChecked) {

								threadPool.submit(new Runnable() {
									/**
									 * This will scrape all the cards in the
									 * final Expansion exp. It also adds data to
									 * the output files for MKM name and TCG
									 * name
									 */
									@Override
									public void run() {
										try {
											JSONObject patchInfo;
											patchInfo = writeJsonPatchFile(exp,
													GathererScraper.scrapeExpansion(exp, GathererScraperUi.this));
											addToArray(mPatchesArray, patchInfo);

											JSONObject tcgname = new JSONObject();
											tcgname.put("Code", exp.mCode_gatherer);
											tcgname.put("TCGName", exp.mName_tcgp);
											addToArray(mTcgNamesArray, tcgname);

											JSONObject mkmname = new JSONObject();
											mkmname.put("Code", exp.mCode_gatherer);
											mkmname.put("MKMName", exp.mName_mkm);
											addToArray(mMkmNamesArray, mkmname);
										}
										catch (Exception e) {
											e.printStackTrace();
										}
										incrementExpansionsProcessed();
									}
								});
								mNumExpansions++;
							}
						}

						mExpansionProgressBar.setValue(0);
						mExpansionProgressBar.setMaximum(mNumExpansions);

						/*
						 * Set the threads a-running and wait for them to stop.
						 * This functionally never times out
						 */
						try {
							threadPool.shutdown();
							threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
						}
						catch (InterruptedException e1) {
							frame.setEnabled(true);
							frame.setCursor(Cursor.getDefaultCursor());
						}

						try {
							/* Write the patches manifest */
							FileWriter file;

							file = new FileWriter(new File(mFilesPath, PATCH_FILE_NAME));
							file.write(patchFile.toJSONString());
							file.flush();
							file.close();

							file = new FileWriter(new File(mFilesPath, TCG_FILE_NAME));
							file.write(TcgFile.toJSONString());
							file.flush();
							file.close();

							file = new FileWriter(new File(mFilesPath, MKM_FILE_NAME));
							file.write(MkmFile.toJSONString());
							file.flush();
							file.close();
						}
						catch (IOException e) {
							e.printStackTrace();
						}

						/* Just a little feedback on how long the operation took */
						long time = System.currentTimeMillis() - startTime;
						JOptionPane.showMessageDialog(frame, "Done in " + time + "ms", "Complete",
								JOptionPane.PLAIN_MESSAGE);

						/* Reenable the cursor */
						frame.setEnabled(true);
						frame.setCursor(Cursor.getDefaultCursor());
						return null;
					}
				}).execute();
			}
		});

		JScrollPane scrollPane_1 = new JScrollPane();
		GridBagConstraints gbc_scrollPane_1 = new GridBagConstraints();
		gbc_scrollPane_1.fill = GridBagConstraints.BOTH;
		gbc_scrollPane_1.insets = new Insets(0, 0, 5, 0);
		gbc_scrollPane_1.gridx = 3;
		gbc_scrollPane_1.gridy = 2;
		frame.getContentPane().add(scrollPane_1, gbc_scrollPane_1);

		JList<String> list = new JList<String>(mLegalityListModel);
		scrollPane_1.setViewportView(list);

		/*
		 * Set a listener to show the legality dialog when a format is double
		 * clicked
		 */
		list.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {

				Rectangle r = list.getCellBounds(0, list.getLastVisibleIndex());
				if (r != null && r.contains(evt.getPoint())) {
					if (evt.getClickCount() == 2) {
						mLegalityListModel.showDialog(list.locationToIndex(evt.getPoint()));
					}
				}
			}
		});

		JRadioButton rdbtnAppendManifests = new JRadioButton("Append Manifests");
		rdbtnAppendManifests.setSelected(true);
		GridBagConstraints gbc_rdbtnAppendManifests = new GridBagConstraints();
		gbc_rdbtnAppendManifests.insets = new Insets(0, 0, 0, 5);
		gbc_rdbtnAppendManifests.gridx = 0;
		gbc_rdbtnAppendManifests.gridy = 3;
		frame.getContentPane().add(rdbtnAppendManifests, gbc_rdbtnAppendManifests);

		mRdbtnOverwriteManifests = new JRadioButton("Overwrite Manifests");
		GridBagConstraints gbc_rdbtnOverwriteManifests = new GridBagConstraints();
		gbc_rdbtnOverwriteManifests.insets = new Insets(0, 0, 0, 5);
		gbc_rdbtnOverwriteManifests.gridx = 1;
		gbc_rdbtnOverwriteManifests.gridy = 3;
		frame.getContentPane().add(mRdbtnOverwriteManifests, gbc_rdbtnOverwriteManifests);

		ButtonGroup group = new ButtonGroup();
		group.add(rdbtnAppendManifests);
		group.add(mRdbtnOverwriteManifests);

		GridBagConstraints gbc_btnScrape = new GridBagConstraints();
		gbc_btnScrape.fill = GridBagConstraints.BOTH;
		gbc_btnScrape.insets = new Insets(0, 0, 0, 5);
		gbc_btnScrape.gridx = 2;
		gbc_btnScrape.gridy = 3;
		frame.getContentPane().add(btnScrape, gbc_btnScrape);

		JCheckBox chckbxSelectAll = new JCheckBox("Select All");
		GridBagConstraints gbc_chckbxSelectAll = new GridBagConstraints();
		gbc_chckbxSelectAll.gridx = 3;
		gbc_chckbxSelectAll.gridy = 3;
		frame.getContentPane().add(chckbxSelectAll, gbc_chckbxSelectAll);
		chckbxSelectAll.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (chckbxSelectAll.isSelected()) {
					for (Expansion exp : mExpansionTableModel.mExpansions) {
						exp.mChecked = true;
					}
				}
				else {
					for (Expansion exp : mExpansionTableModel.mExpansions) {
						exp.mChecked = false;
					}
				}
				mTable.repaint();
			}
		});

		frame.setVisible(true);
	}

	/**
	 * This function builds and writes a patch file which contains all the
	 * scraped information.
	 * 
	 * @param exp
	 *            The expansion to make write this file for
	 * @param allCards
	 *            The scraped cards in the expansion
	 * @return a JSONObject containing metadata about the patch. This is used to
	 *         build a patch manifest
	 */
	public JSONObject writeJsonPatchFile(Expansion exp, ArrayList<Card> allCards) {
		JSONObject topLevel = new JSONObject();
		JSONObject mtg_carddatabase = new JSONObject();
		JSONObject cards = new JSONObject();
		JSONObject expansions = new JSONObject();

		JSONArray jsonAllCards = new JSONArray();
		if (allCards != null) {
			for (Card c : allCards) {
				JSONObject jsonCard = new JSONObject();
				jsonCard.put("a", c.mName);
				jsonCard.put("b", c.mExpansion);
				jsonCard.put("c", c.mType);
				jsonCard.put("d", c.mRarity);
				jsonCard.put("e", c.mManaCost);
				jsonCard.put("f", c.mCmc);
				jsonCard.put("g", c.mPower);
				jsonCard.put("h", c.mToughness);
				jsonCard.put("i", c.mLoyalty);
				jsonCard.put("j", c.mText);
				jsonCard.put("k", c.mFlavor);
				jsonCard.put("l", c.mArtist);
				jsonCard.put("m", c.mNumber);
				jsonCard.put("n", c.mColor);
				jsonCard.put("x", c.mMultiverseId);
				jsonAllCards.add(jsonCard);
			}
		}
		try {

			topLevel.put("t", mtg_carddatabase);

			mtg_carddatabase.put("w", jsonAllCards.size()); /* num_cards */
			mtg_carddatabase.put("u", "1.00"); /* bdd_version */

			mtg_carddatabase.put("s", expansions);

			JSONArray jsonAllExpansions = new JSONArray();

			JSONObject jsonExpansion = new JSONObject();
			jsonExpansion.put("r", exp.mCode_mtgi); /* code_magiccards */
			jsonExpansion.put("a", exp.mName_gatherer); /* name */
			jsonExpansion.put("q", exp.mCode_gatherer); /* code */
			jsonExpansion.put("y", exp.getDateMs()); /* date */
			jsonAllExpansions.add(jsonExpansion);

			expansions.put("b", jsonAllExpansions);

			mtg_carddatabase.put("p", cards);
			cards.put("o", jsonAllCards);

			DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
			Calendar cal = Calendar.getInstance();
			mtg_carddatabase.put("v", dateFormat.format(cal.getTime())); /* bdd_date */

			File gzipout = new File(mFilesPath, exp.mCode_gatherer + ".json.gzip");
			GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzipout));
			gos.write(topLevel.toJSONString().getBytes());
			gos.flush();
			gos.close();

		}
		catch (IOException e) {
			e.printStackTrace();
		}
		JSONObject patchInfo = new JSONObject();
		patchInfo.put("Name", exp.mName_gatherer);
		patchInfo.put("URL", "https://sites.google.com/site/mtgfamiliar/patches/" + exp.mCode_gatherer + ".json.gzip");
		patchInfo.put("Code", exp.mCode_gatherer);
		return patchInfo;
	}

	/**
	 * A helper function display a JFileChooser asking the user to select a file
	 * 
	 * @param title
	 *            The title of the file chooser
	 * @param filter
	 *            A filter to only display certain files
	 * @return A File selected by the user, or null if one was not selected
	 */
	private static File askForFile(String title, FileFilter filter) {
		JFileChooser chooser = new JFileChooser("./");

		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		chooser.setDialogTitle(title);
		chooser.setFileFilter(filter);
		int returnVal = chooser.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		return null;
	}

	/**
	 * A synchronized wrapper to add a JSONObject to a JSONArray. This is used
	 * by threads which scrape cards and save data
	 * 
	 * @param array
	 *            The array to add the object to
	 * @param obj
	 *            The object to add to the array
	 */
	public synchronized void addToArray(JSONArray array, JSONObject obj) {
		array.add(obj);
	}

	/**
	 * A synchronized wrapper to increment the progress bar for expansions
	 * scraped
	 */
	public synchronized void incrementExpansionsProcessed() {
		mExpansionProgressBar.setValue(++mExpansionsProcessed);
	}

	/**
	 * A synchronized wrapper to print to the UI what the last card scraped was
	 * 
	 * @param str
	 *            A string representation of the last card scraped
	 */
	public synchronized void setLastCardScraped(String str) {
		mLastCardScraped.setText(str);
	}
}
