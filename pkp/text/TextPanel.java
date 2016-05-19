/**
 * Copyright 2015 Pushkar Piggott
 *
 *  TextPanel.java
 */
 
package pkp.text;

import java.awt.Font;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.FontMetrics;
import javax.swing.JPanel;
import java.awt.FontMetrics;
import java.io.File;
import java.util.StringTokenizer;
import pkp.source.KeyPressListSource;
import pkp.source.ChordSource;
import pkp.source.KeyPressSource;
import pkp.twiddle.Assignment;
import pkp.twiddle.KeyMap;
import pkp.twiddle.KeyPressList;
import pkp.twiddle.KeyPress.Format;
import pkp.twiddle.Twiddle;
import pkp.twiddle.Chord;
import pkp.twiddle.ThumbKeys;
import pkp.string.*;
import pkp.util.Pref;
import pkp.util.Log;
import pkp.io.Io;

///////////////////////////////////////////////////////////////////////////////
// A panel that displays the text to be chorded.
// Incorporates a source for the text. 
public class TextPanel 
   extends JPanel 
   implements KeyPressListSource.Message {

   ////////////////////////////////////////////////////////////////////////////
   public TextPanel(KeyMap km, String chordsPrompt) {
      // badtags.sh supports only one tag per line
      int size = Pref.getInt("#.text.size");
      m_TextFont = new Font(Pref.get("#.text.font"), Font.BOLD, size);
      setFont(m_TextFont);
      m_PromptFont = new Font(Pref.get("#.prompt.font"), Font.PLAIN, size);
      m_CHORDS_PROMPT = chordsPrompt;
      m_KeyMap = km;
		m_Text = "";
	   m_SPACE = (char)Pref.getInt("#.text.visible.space", 0x87);
      m_Past = "";
      m_KplSource = null;
   }

   ////////////////////////////////////////////////////////////////////////////
   public void setChords(int[] timeCounts) {
      m_KplSource = new ChordSource(m_KeyMap, timeCounts);
   }

   ////////////////////////////////////////////////////////////////////////////
   public void setKeystrokes(File f) {
      m_KplSource = new KeyPressSource(f);
   }

   ////////////////////////////////////////////////////////////////////////////
   public boolean isChords() {
      return m_KplSource != null && m_KplSource instanceof ChordSource;
   }

   ////////////////////////////////////////////////////////////////////////////
   public boolean isKeystrokes() {
      return m_KplSource != null && m_KplSource instanceof KeyPressSource;
   }

   ////////////////////////////////////////////////////////////////////////////
   public void setHideText(boolean set) {
      m_HideText = set;
   }

   ////////////////////////////////////////////////////////////////////////////
   public boolean getHideText() {
      return m_HideText;
   }

   ////////////////////////////////////////////////////////////////////////////
   public KeyMap getKeyMap() {
      return m_KeyMap;
   }

   ////////////////////////////////////////////////////////////////////////////
   public void setKeyMap(KeyMap km) {
      m_KeyMap = km;
      if (isChords()) {
         m_KplSource = ((ChordSource)m_KplSource).newKeyMap(m_KeyMap);
      }
   }

   ////////////////////////////////////////////////////////////////////////////
   public Twiddle getFirstTwiddle() {
      Assignment asg = null;
      for (int i = 0; i < 1000 && asg == null; ++i) {
         m_Text = getNextString();
         asg = KeyPressList.parseTextAndTags(m_Text).findLongestPrefix(m_KeyMap);
      }
      if (asg == null) {
         Log.warn("No key found that maps to a chord.");
         return null;
      }
      highlight(asg.getKeyPressList().toString(Format.DISPLAY).length());
      repaint();
      return asg.getTwiddle();
   }

   ////////////////////////////////////////////////////////////////////////////
   public Twiddle getNextTwiddle(KeyPressList pressed) {
      String str = pressed.toString(Format.DISPLAY);
      if (!m_Text.startsWith(str)) {
         return null;
      }
		m_Past += str;
      m_Text = m_Text.substring(str.length());
      Assignment asg = null;
      while (!"".equals(m_Text) 
          && (asg = KeyPressList.parseTextAndTags(m_Text).findLongestPrefix(m_KeyMap)) == null) {
         // skip unmapped characters
         m_Past += m_Text.substring(0, 1);
         m_Text = m_Text.substring(1);
      }
		if ("".equals(m_Text)) {
	      //Lesson.progress();
			return getFirstTwiddle();
		}
//System.out.printf("'%s' %d%n", asg.getKeyPressList().toString(Format.DISPLAY), asg.getKeyPressList().toString(Format.DISPLAY).length());
      highlight(asg.getKeyPressList().toString(Format.DISPLAY).length());
      return asg.getTwiddle();
   }

   ////////////////////////////////////////////////////////////////////////////
   public void next(boolean accepted) {
      if (m_KplSource != null) {
         m_KplSource.send(accepted ? this : null);
//System.out.printf("m_Text.length() %d%n", m_Text.length());
      }
   }

   ////////////////////////////////////////////////////////////////////////////
   public void clear() {
      super.repaint();
   }

   ////////////////////////////////////////////////////////////////////////////
   @Override
   public void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (m_Past.length() + m_Text.length() == 0 || !(g instanceof Graphics2D)) {
			return;
		}
		int y = (int)(getHeight() * 0.75);
      if (isChords()) {
         // show prompt, not chords' source keys
         int offset = setPromptFont(g);
			g.setColor(Pref.getColor("#.text.color"));
			g.drawString(m_CHORDS_PROMPT, offset, y);
         g.setFont(m_TextFont);
         return;
      }
		FontMetrics fm = g.getFontMetrics(getFont());
		String hlight = m_Text.substring(0, m_Length).replace(' ', m_SPACE);
		int start = (getWidth() - fm.stringWidth(hlight.substring(0, 0))) / 3;
		int startPast = start - fm.stringWidth(m_Past);
		if (startPast < 0) {
			int i = 0;
			while (startPast < 0) {
				++i;
				startPast = start - fm.stringWidth(m_Past.substring(i));
			}
			m_Past = m_Past.substring(i);
		}
		int startFuture = start + fm.stringWidth(hlight);
		String extend;
		while (startFuture + fm.stringWidth(m_Text.substring(Math.min(m_Length, m_Text.length()))) < getWidth()
			 && (!"".equals(extend = getNextString()))) {
			m_Text += extend;
		}
		if (!m_HideText) {
			g.setColor(Pref.getColor("#.text.color"));
			g.drawString(m_Past, startPast, y);
			g.setColor(Pref.getColor("#.text.highlight.color"));
			g.drawString(hlight, start, y);
			g.setColor(Pref.getColor("#.text.color"));
			g.drawString(m_Text.substring(m_Length), startFuture, y);
		}
   }

   // Private /////////////////////////////////////////////////////////////////

   ////////////////////////////////////////////////////////////////////////////
   private void highlight(int length) {
      m_Length = Math.min(length, m_Text.length());
      repaint();
   }

   ////////////////////////////////////////////////////////////////////////////
   // returns characters representing a random twiddle
   private String getNextString() {
      KeyPressList kpl = m_KplSource.getNext();
      if (kpl == null) {
         Log.warn("Source has no keys");
         return null;
      }
      return kpl.toString(Format.DISPLAY);
   }

   ////////////////////////////////////////////////////////////////////////////
   private int setPromptFont(Graphics g) {
      final int WIDTH = (int)(getWidth() * 0.9);
      FontMetrics fm;
      int size = m_PromptFont.getSize();
      for (;;) {
         g.setFont(m_PromptFont);
         fm = g.getFontMetrics(g.getFont());
         if (fm.stringWidth(m_CHORDS_PROMPT) > WIDTH
          || fm.getHeight() > getHeight()) {
            break;
         }
//System.out.printf("fm.stringWidth(m_CHORDS_PROMPT) %d getWidth() %d%n", fm.stringWidth(m_CHORDS_PROMPT), getWidth());
         ++size;
         m_PromptFont = new Font(m_PromptFont.getName(), m_PromptFont.getStyle(), size);
      }
      for (;;) {
         if (fm.stringWidth(m_CHORDS_PROMPT) < WIDTH
          && fm.getHeight() < getHeight()) {
            break;
         }
//System.out.printf("fm.stringWidth(m_CHORDS_PROMPT) %d getWidth() %d%n", fm.stringWidth(m_CHORDS_PROMPT), getWidth());
         --size;
         m_PromptFont = new Font(m_PromptFont.getName(), m_PromptFont.getStyle(), size);
         g.setFont(m_PromptFont);
         fm = g.getFontMetrics(g.getFont());
      }
      return (getWidth() - fm.stringWidth(m_CHORDS_PROMPT)) / 2;
   }
   
   // Data ////////////////////////////////////////////////////////////////////
   private final char m_SPACE;
   private final String m_CHORDS_PROMPT;
   private KeyMap m_KeyMap;
   private KeyPressListSource m_KplSource;
   private Assignment m_Assignment;
   private boolean m_HideText;
   private String m_Past;
   private int m_Start;
   private int m_Length;
   private String m_Text;
   private boolean m_Hit;
   private Font m_PromptFont;
   private Font m_TextFont;
}
