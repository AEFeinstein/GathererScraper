package com.gelakinetic.GathererScraper;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import com.camick.TableColumnAdjuster;
import com.gelakinetic.GathererScraper.JsonTypes.Expansion;
import com.gelakinetic.GathererScraper.JsonTypesGS.CardGS;
import com.gelakinetic.GathererScraper.JsonTypesGS.ExpansionGS;
import com.gelakinetic.GathererScraper.JsonTypesGS.PatchGS;

/**
 * This class handles the UI for the application, as well as some file I/O
 *
 * @author AEFeinstein
 *
 */
public class GathererScraperUi {

	private static final String	PATCH_FILE_NAME		= "patches.json";
	private static final String	EXPANSION_FILE_NAME	= "expansions.json";
	public static final String	LEGAL_FILE_NAME		= "legality.json";
	private static final String	APPMAP_FILE_NAME	= "appmap-com.gelakinetic.mtgfam.xml";

	private JProgressBar		mExpansionProgressBar;
	private JLabel				mLastCardScraped;
	private JTable				mTable;

	private ExpansionTableModel	mExpansionTableModel;
	private LegalityListModel	mLegalityListModel;
	private String				mFilesPath;

	private File						mLegalityFile		= null;
	private File						mExpansionsFile		= null;
	private File						mAppmapFile			= null;

	private HashSet<Integer>	mAllMultiverseIds;

	private int					mNumExpansions;
	private int					mExpansionsProcessed;

	/**
	 * Launch the application.
	 *
	 * @param args
	 *            Command line arguments, unused
	 */
	public static void main(String[] args) {
		System.setProperty("http.agent", "");
		EventQueue.invokeLater(() -> {
            try {
                if(!(new GathererScraperUi()).initialize()) {
                	System.exit(0);
				}
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
	}

	/**
	 * Set the look and feel, get the directory for the data files, and create
	 * some File objects
	 */
	private GathererScraperUi() {

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			/* don't worry about it */
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
		mLegalityFile = new File(mFilesPath, LEGAL_FILE_NAME);
		mAppmapFile = new File(mFilesPath, APPMAP_FILE_NAME);

		/*
		 * If the expansion file isn't found, don't bother running the
		 * application
		 */
		if (mFilesPath == null || !mExpansionsFile.exists()) {
			JOptionPane.showMessageDialog(null, "Expansion info not found.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private boolean initialize() {

		if (mFilesPath == null || !mExpansionsFile.exists()) {
			return false;
		}

		final JFrame frame = new JFrame();
		frame.setVisible(false);
		frame.setBounds(100, 100, 1160, 512);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
				/* Get today's timestamp */
				Calendar cal = Calendar.getInstance();
				long timestamp = cal.getTime().getTime() / 1000;

				/* Write the legality information first */
				try {
					if (!mLegalityListModel.writeLegalDataFile(frame, mFilesPath, timestamp)) {
						frame.setEnabled(true);
						frame.setCursor(Cursor.getDefaultCursor());
					}
				}
				catch (IOException e1) {
					e1.printStackTrace();
				}
								
				try {
					GathererScraper.writeFile(mExpansionTableModel.mExpansions, new File(EXPANSION_FILE_NAME), false);
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
			if(mExpansionTableModel.mExpansions.isEmpty()) {
				System.err.println("Gatherer 404!");
				return false;
			}
			mLegalityListModel.setExpansions(mExpansionTableModel.mExpansions);
			
			/* Then add the extra data from the expansions file */
			mExpansionTableModel.readInfo(mExpansionsFile);

			if (mLegalityFile.exists()) {
				mLegalityListModel.loadLegalities(mLegalityFile);
			}
			mAllMultiverseIds = new HashSet<>();
			if(mAppmapFile.exists()) {
				loadMultiverseIds(mAppmapFile, mAllMultiverseIds);
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

		JButton btnCleanRules = new JButton("Clean Rules");
		btnCleanRules.addActionListener(arg0 -> {
            File rulesFile = askForFile("Comprehensive Rules to Clean?", null);
            try {
                String problemLines = GathererScraper.cleanRules(rulesFile);
                if(problemLines.length() > 0) {
                    JOptionPane.showMessageDialog(frame, "Rules Cleaned\r\n" + problemLines, "Warning",
                            JOptionPane.PLAIN_MESSAGE);
                }
                else {
                    JOptionPane.showMessageDialog(frame, "Rules Cleaned", "Complete",
                            JOptionPane.PLAIN_MESSAGE);
                }
            } catch (IOException | NullPointerException e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                JOptionPane.showMessageDialog(frame, sw.toString(), "ERROR",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
		GridBagConstraints gbc_btnCleanRules = new GridBagConstraints();
		gbc_btnCleanRules.insets = new Insets(0, 0, 5, 0);
		gbc_btnCleanRules.gridx = 3;
		gbc_btnCleanRules.gridy = 0;
		frame.getContentPane().add(btnCleanRules, gbc_btnCleanRules);

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
		btnScrape.addActionListener(arg0 -> (new SwingWorker<Void, Void>() {

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

                /* Get today's timestamp */
                Calendar cal = Calendar.getInstance();
                long timestamp = cal.getTime().getTime() / 1000;

                /* Write the legality information first */
                try {
                    if (!mLegalityListModel.writeLegalDataFile(frame, mFilesPath, timestamp)) {
                        frame.setEnabled(true);
                        frame.setCursor(Cursor.getDefaultCursor());
                        return null;
                    }
                }
                catch (IOException e1) {
                    e1.printStackTrace();
                }

                /*
                 * Make a thread pool to scrape each set in it's own
                 * thread
                 */
                ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime()
                        .availableProcessors());
                mNumExpansions = 0;
                mExpansionsProcessed = 0;
                for (final ExpansionGS exp : mExpansionTableModel.mExpansions) {
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
                                    ArrayList<CardGS> cards = GathererScraper.scrapeExpansion(exp, GathererScraperUi.this, mAllMultiverseIds);
                                    writeJsonPatchFile(exp, cards);
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

                    Collections.sort(mExpansionTableModel.mExpansions);
                    mExpansionTableModel.writePatchesManifestFile(new File(mFilesPath, PATCH_FILE_NAME));
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    if(mAppmapFile.exists()) {
                        mAppmapFile.delete();
                    }
                    Integer multiverseIdsArray[] = new Integer[mAllMultiverseIds.size()];
                    mAllMultiverseIds.toArray(multiverseIdsArray);
                    Arrays.sort(multiverseIdsArray);

                    StringBuilder appmapBuilder = new StringBuilder();
                    appmapBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    appmapBuilder.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
                    for(int multiverseId : multiverseIdsArray) {
                        appmapBuilder.append("<url><loc>android-app://com.gelakinetic.mtgfam/card/multiverseid/").append(multiverseId).append("</loc></url>\n");
                    }
                    appmapBuilder.append("</urlset>\n");

                    GathererScraper.writeFile(appmapBuilder.toString(), new File(mFilesPath, APPMAP_FILE_NAME), false);
                } catch (IOException e) {
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
        }).execute());

		JScrollPane scrollPane_1 = new JScrollPane();
		GridBagConstraints gbc_scrollPane_1 = new GridBagConstraints();
		gbc_scrollPane_1.fill = GridBagConstraints.BOTH;
		gbc_scrollPane_1.insets = new Insets(0, 0, 5, 0);
		gbc_scrollPane_1.gridx = 3;
		gbc_scrollPane_1.gridy = 2;
		frame.getContentPane().add(scrollPane_1, gbc_scrollPane_1);

		final JList<String> list = new JList<>(mLegalityListModel);
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

		GridBagConstraints gbc_btnScrape = new GridBagConstraints();
		gbc_btnScrape.gridwidth = 3;
		gbc_btnScrape.fill = GridBagConstraints.BOTH;
		gbc_btnScrape.insets = new Insets(0, 0, 0, 5);
		gbc_btnScrape.gridx = 0;
		gbc_btnScrape.gridy = 3;
		frame.getContentPane().add(btnScrape, gbc_btnScrape);

		final JCheckBox chckbxSelectAll = new JCheckBox("Select All");
		GridBagConstraints gbc_chckbxSelectAll = new GridBagConstraints();
		gbc_chckbxSelectAll.gridx = 3;
		gbc_chckbxSelectAll.gridy = 3;
		frame.getContentPane().add(chckbxSelectAll, gbc_chckbxSelectAll);
		chckbxSelectAll.addActionListener(e -> {
            if (chckbxSelectAll.isSelected()) {
                for (ExpansionGS exp : mExpansionTableModel.mExpansions) {
                    exp.mChecked = exp.isScraped();
                }
            }
            else {
                for (ExpansionGS exp : mExpansionTableModel.mExpansions) {
                    exp.mChecked = false;
                }
            }
            mTable.repaint();
        });

		frame.setVisible(true);
		return true;
	}

	/**
	 * Reads in all the multiverse IDs from an appmap
	 *
	 * @param appmapFile The file to read from
	 * @param allMultiverseIds A HashSet to store all the IDs
	 */
	private void loadMultiverseIds(File appmapFile,
			HashSet<Integer> allMultiverseIds) {
		try {
			// String to be scanned to find the pattern.
			String pattern = "multiverseid/([0-9]+)";

			BufferedReader br = new BufferedReader(new FileReader(appmapFile));
			String line;
			while ((line = br.readLine()) != null) {

				// Create a Pattern object
				Pattern r = Pattern.compile(pattern);

				// Now create matcher object.
				Matcher m = r.matcher(line);
				if (m.find()) {
					int mId = Integer.parseInt(m.group().split("/")[1]);
					allMultiverseIds.add(mId);
				}
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	private void writeJsonPatchFile(Expansion exp, ArrayList<CardGS> allCards) {
		try {
			/* Only fix this weird character when writing the patch */
			exp.mName_gatherer = GathererScraper.removeNonAscii(exp.mName_gatherer);
			
			PatchGS patch = new PatchGS(exp, allCards);

			File gzipout = new File(new File(mFilesPath, GathererScraper.PATCH_DIR), exp.mCode_gatherer + ".json.gzip");
			GathererScraper.writeFile(patch, gzipout, true);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
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
		if(filter != null) {
			chooser.setFileFilter(filter);
		}
		int returnVal = chooser.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		return null;
	}

	/**
	 * A synchronized wrapper to increment the progress bar for expansions
	 * scraped
	 */
	private synchronized void incrementExpansionsProcessed() {
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
