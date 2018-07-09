package com.gelakinetic.GathererScraper;

import java.awt.EventQueue;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class JudgeDocScraperUi {

    private JFrame mFrame;
    private JTextPane mTextPane;
    
    public static void main(String args[]) {
        new JudgeDocScraperUi();
    }

    /**
     * Create the application.
     *
     */
    public JudgeDocScraperUi() {
        EventQueue.invokeLater(() -> {
            try {
                initialize();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Initialize the contents of the frame.
     * @wbp.parser.entryPoint
     *
     */
    private void initialize() {
        mFrame = new JFrame();
        mFrame.setBounds(100, 100, 450, 300);
        mFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        mFrame.getContentPane().setLayout(new BoxLayout(mFrame.getContentPane(), BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane();
        mFrame.getContentPane().add(scrollPane);

        mTextPane = new JTextPane();
        scrollPane.setViewportView(mTextPane);
        mTextPane.setEditable(false);
        mTextPane.setText("");
        mFrame.setVisible(true);
        
        new Thread(new Runnable() {
            
            @Override
            public void run() {
                JudgeDocScraper scraper = new JudgeDocScraper(JudgeDocScraperUi.this);
                scraper.ScrapeAll();
            }
        }).start();
    }

    /**
     * Add text to the server text output window
     * 
     * @param text
     *            The text to display
     */
    void appendText(String text) {
        SwingUtilities.invokeLater(() -> mTextPane.setText(mTextPane.getText() + '\n' + text));
    }
}
