/*
 * CrapTerminal
 * Version 3.0, 1 October 2010
 * Copyright 2010 Ian Renton
 * For more information, see this application's web page at:
 * http://www.onlydreaming.net/software/crapterminal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.onlydreaming.crapterminal;

import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Enumeration;
import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;

/**
 * A simple serial console, much like HyperTerminal but infinitely less
 * annoying.
 * @author Ian Renton
 */
public class CrapTerminal extends javax.swing.JFrame {

    SerialClient serialClient = null;
    Thread readerThread = null;
    private BufferedReader reader = null;
    private OutputStream writer = null;
    // We use this bool to pause the reader thread while changing port/baud.
    boolean continueWithReader = true;
    int lastDirection = 0;
    private javax.swing.JComboBox baudRate;
    private javax.swing.JLabel baudRateLabel;
    private javax.swing.JPanel bottomLeft;
    private javax.swing.JButton exitButton;
    private javax.swing.JTextArea incoming;
    private javax.swing.JScrollPane incomingScrollPane;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JPanel bgPanel;
    private javax.swing.JPanel aboutPanel;
    private javax.swing.JLabel aboutText;
    private javax.swing.JTextArea outgoing;
    private javax.swing.JScrollPane outgoingScrollPane;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JButton sendButton;
    private javax.swing.JPanel sendPanel;
    private javax.swing.JComboBox serialPort;
    private javax.swing.JLabel serialPortLabel;
    private javax.swing.JPanel topLeft;
    private javax.swing.JButton sendFile;

    /**
     * Inner class for the serial reader thread.
     */
    private class RemoteReader implements Runnable {

        /**
         * Run method, for great threadage.
         */
        public void run() {

            int nextByte;

            // Reader runs forever
            while (true) {
                try {
                    // This goes false when changing port/baud.
                    while (continueWithReader) {
                        nextByte = reader.read();
                        displayText(String.valueOf((char) nextByte), 1);
                    }
                } catch (IOException ex) {
                    // If we're in a read operation when we change port/baud,
                    // we'll end up here.  Nothing to worry about.
                }
            }
        }
    }

    /**
     * Inner class to set up a serial connection.  This requires JavaComm 2.
     */
    private class SerialClient {

        SerialPort port = null;

        /**
         * Construct a serial comms client
         * @param wantedPortName Desired serial port name, e.g. "COM1"
         * @param baudRate Desired baud rate.
         */
        public SerialClient(String wantedPortName, int baudRate) {

            // Get an enumeration of all ports known to JavaComm
            Enumeration portIdentifiers =
                    CommPortIdentifier.getPortIdentifiers();

            CommPortIdentifier portId = null;

            // If there's a serial port with the correct name, grab it.
            while (portIdentifiers.hasMoreElements()) {
                CommPortIdentifier pid =
                        (CommPortIdentifier) portIdentifiers.nextElement();
                if (pid.getPortType() == CommPortIdentifier.PORT_SERIAL &&
                        pid.getName().equals(wantedPortName)) {
                    portId = pid;
                    break;
                }
            }
            if (portId == null) {
                JOptionPane.showMessageDialog(null,
                        "Could not find serial port " + wantedPortName,
                        "Error!", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            try {
                port = (SerialPort) portId.open("CrapTerminal", 10000);
            } catch (PortInUseException e) {
                JOptionPane.showMessageDialog(null, "Port " + wantedPortName +
                        " is in use by another application.", "Error!",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            try {
                port.setSerialPortParams(baudRate, SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            } catch (UnsupportedCommOperationException ex) {
                JOptionPane.showMessageDialog(null,
                        "Could not configure port to required parameters (e.g. baud" + " rate)", "Error!", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        // Done setting up, now we wait for something to grab the IOStreams.
        }

        /**
         * Get the OutputStream attached to the serial port.
         * @return The OutputStream.
         */
        public OutputStream getOutputStream() {
            OutputStream os = null;
            try {
                os = port.getOutputStream();
            } catch (IOException e) {
            }
            return os;
        }

        /**
         * Get the InputStream attached to the serial port.
         * @return The InputStream.
         */
        public InputStream getInputStream() {
            InputStream is = null;
            try {
                is = port.getInputStream();
            } catch (IOException e) {
            }
            return is;
        }

        /**
         * Close the port, so other things can use it.
         */
        public void close() {
            port.close();
        }
    }

    /**
     * Creates a single instance of CrapTerminal.
     * @param args
     */
    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                CrapTerminal app = new CrapTerminal();
            }
        });
    }

    /**
     * Creates a new CrapTerminal GUI.
     */
    public CrapTerminal() {
        super("CrapTerminal");
        setLAF();

        initComponents();

        getSerialPorts();

        this.setVisible(true);

        // Launch network reader thread.
        readerThread = new Thread(new RemoteReader());
        readerThread.start();
    }

    /**
     * Get the list of available serial ports, and fill up the combobox with
     * them.
     */
    private void getSerialPorts() {

        Enumeration portIdentifiers = CommPortIdentifier.getPortIdentifiers();
        serialPort.removeAllItems();
        // First item is blank, so we don't *have* to connect to COM1/ttsy0/etc
        serialPort.addItem("");

        while (portIdentifiers.hasMoreElements()) {
            CommPortIdentifier pid =
                    (CommPortIdentifier) portIdentifiers.nextElement();
            if (pid.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                serialPort.addItem(pid.getName());
            }
        }
        // If you have only one serial port, use it.
        if (serialPort.getItemCount() == 2) {
            serialPort.setSelectedIndex(1);
        }

    // At this point, serialPortActionPerformed will get triggered.  If you
    // only have one serial port, it'll start to use it.  If you have more
    // than one, the drop-down will still be on blank, so you'll have to
    // set it manually.
    }

    private void setLAF() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Error setting LAF: " + e);
        }

    }

    /** 
     * Netbeans initComponents stuff.
     */
    private void initComponents() {

        leftPanel = new javax.swing.JPanel();
        topLeft = new javax.swing.JPanel();
        serialPortLabel = new javax.swing.JLabel("Serial Port");
        serialPort = new javax.swing.JComboBox();
        baudRateLabel = new javax.swing.JLabel("Baud Rate");
        baudRate = new javax.swing.JComboBox();
        bottomLeft = new javax.swing.JPanel();
        exitButton = new javax.swing.JButton("Exit");
        rightPanel = new javax.swing.JPanel();
        bgPanel = new javax.swing.JPanel();
        mainPanel = new javax.swing.JPanel();
        aboutPanel = new javax.swing.JPanel();
        aboutText = new javax.swing.JLabel("This software is released under the GNU GPL v3.  For more information, visit http://www.onlydreaming.net/software/crapterminal");
        jSplitPane1 = new javax.swing.JSplitPane();
        incomingScrollPane = new javax.swing.JScrollPane();
        incoming = new javax.swing.JTextArea("");
        sendPanel = new javax.swing.JPanel();
        sendButton = new javax.swing.JButton("Send");
        outgoingScrollPane = new javax.swing.JScrollPane();
        outgoing = new javax.swing.JTextArea();
        sendFile = new javax.swing.JButton("Send File");

        sendFile.setToolTipText("Send a block of text or binary data.");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(java.awt.SystemColor.control);
        setBounds(new java.awt.Rectangle(50, 50, 0, 0));
        getContentPane().setLayout(new java.awt.BorderLayout(0, 0));

        bgPanel.setLayout(new java.awt.BorderLayout(0, 0));

        mainPanel.setLayout(new java.awt.BorderLayout(5, 5));

        leftPanel.setBorder(
                javax.swing.BorderFactory.createEmptyBorder(0, 5, 5, 0));
        leftPanel.setLayout(new java.awt.BorderLayout(20, 20));

        topLeft.setLayout(new java.awt.GridLayout(0, 1, 5, 2));

        topLeft.add(serialPortLabel);

        serialPort.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serialPortActionPerformed(evt);
            }
        });
        topLeft.add(serialPort);

        topLeft.add(baudRateLabel);

        baudRate.setModel(new javax.swing.DefaultComboBoxModel(new String[]{"1200", "2400", "4800", "9600", "19200", "28800",
                    "38400", "57600", "115200"}));
        baudRate.setSelectedIndex(4);
        baudRate.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                baudRateActionPerformed(evt);
            }
        });
        topLeft.add(baudRate);

        leftPanel.add(topLeft, java.awt.BorderLayout.NORTH);

        bottomLeft.setLayout(new java.awt.GridLayout(0, 1, 5, 2));

        exitButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });
        bottomLeft.add(exitButton);

        leftPanel.add(bottomLeft, java.awt.BorderLayout.SOUTH);

        mainPanel.add(leftPanel, java.awt.BorderLayout.WEST);

        rightPanel.setLayout(new java.awt.BorderLayout(5, 5));

        jSplitPane1.setDividerLocation(500);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(0.9);

        incomingScrollPane.setAutoscrolls(true);
        incomingScrollPane.setPreferredSize(new java.awt.Dimension(583, 500));

        incoming.setColumns(80);
        incoming.setEditable(false);
        incoming.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        incoming.setLineWrap(true);
        incoming.setWrapStyleWord(true);
        incomingScrollPane.setViewportView(incoming);

        jSplitPane1.setLeftComponent(incomingScrollPane);

        sendPanel.setLayout(new java.awt.BorderLayout());

        sendButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendButtonActionPerformed(evt);
            }
        });
        sendPanel.add(sendButton, java.awt.BorderLayout.EAST);

        sendFile.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendFile(evt);
            }
        });

        sendPanel.add(sendFile, java.awt.BorderLayout.EAST);

        outgoing.setColumns(20);
        outgoing.setRows(1);
        outgoing.addKeyListener(new java.awt.event.KeyAdapter() {

            @Override
            public void keyTyped(java.awt.event.KeyEvent evt) {
                outgoingKeyTyped(evt);
            }
        });
        outgoingScrollPane.setViewportView(outgoing);

        sendPanel.add(outgoingScrollPane, java.awt.BorderLayout.CENTER);

        jSplitPane1.setRightComponent(sendPanel);

        rightPanel.add(jSplitPane1, java.awt.BorderLayout.CENTER);

        mainPanel.add(rightPanel, java.awt.BorderLayout.CENTER);

        bgPanel.add(mainPanel, java.awt.BorderLayout.CENTER);

        aboutPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        aboutPanel.add(aboutText);

        bgPanel.add(aboutPanel, java.awt.BorderLayout.SOUTH);

        getContentPane().add(bgPanel, java.awt.BorderLayout.CENTER);

        pack();

    }

    /**
     * Actions performed on a new serial port being selected.  (Also, this is
     * executed as soon as the list of ports is populated.)  Creates a new
     * serial client on the new port, and re-attaches the reader to its
     * OutputStream.  Reading is disabled while this is happenning.
     * @param evt
     */
    private void serialPortActionPerformed(java.awt.event.ActionEvent evt) {
        // Disable the reader thread temporarily.  (Or until the user selects a
        // real serial port, if we're on the first execution of this method!)
        continueWithReader = false;
        if (serialPort.getSelectedItem() == "") {
            // If we haven't selected a serial port, just give up and wait until
            // we have.
            return;
        }

        if (reader != null) {
            try {
                // Close the old reader.
                reader.close();
            } catch (IOException ex) {
                // If we get here, it was probably just because the last reader
                // quit unexpectedly, so we don't worry and make a new one
                // anyway.
            }
        }
        if (serialClient != null) {
            // Also close the serial client if it exists (doesn't exist on first
            // run)
            serialClient.close();
        }

        // Make a new serial client and attach the reader.
        serialClient = new SerialClient(serialPort.getSelectedItem().toString(),
                (Integer) Integer.valueOf(baudRate.getSelectedItem().toString()));
        reader =
                new BufferedReader(new InputStreamReader(
                serialClient.getInputStream()));
        writer = serialClient.getOutputStream();
        // Blank the display
        incoming.setText(null);
        // Enable reading again.
        continueWithReader = true;
    }

    /**
     * Actions performed on a new baud rate being selected.  Chains into the
     * "serial port changed" function, as it needs to do the same thing anyway.
     * @param evt
     */
    private void baudRateActionPerformed(java.awt.event.ActionEvent evt) {
        serialPortActionPerformed(null);
    }

    /**
     * Sends the contents of a file as a binary blob.
     * @param evt
     */
    private void sendFile(java.awt.event.ActionEvent evt) {
        JFileChooser fc = new JFileChooser();
        fc.showOpenDialog(null);
        if (fc.getSelectedFile() != null) {
            try {
                FileInputStream r = new FileInputStream(fc.getSelectedFile());
                byte[] buf = new byte[1024];
                int bytesRead = r.read(buf);
                byte[] packet = new byte[bytesRead + 4];
                System.arraycopy(buf, 0, packet, 2, bytesRead);
                byte[] checksum = calcChecksum(packet);
                System.arraycopy(checksum, 0, packet, bytesRead+2, 2);
                packet[0] = (byte)Math.floor(bytesRead/256);
                packet[1] = (byte)bytesRead;

                writer.write(packet);
                writer.flush();

                String display = "[BINARY DATA:  ";
                display = display+String.format("%02X %02X   ", packet[0], packet[1]);
                for (int i=2; i<packet.length-2; i++) {
                    display = display+String.format("%02X ", packet[i]);
                }
                display = display+String.format("   %02X %02X  ]", packet[packet.length-2], packet[packet.length-1]);
                displayText(display, 2);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        outgoing.requestFocusInWindow();
    }

    public byte[] calcChecksum(byte[] packet) {
        byte[] checksum = new byte[2];
        long sum = 0;
        for (int i=0; i<packet.length; i++) {
            sum += (packet[i] & 0xff);
        }
        checksum[0] = (byte) (sum/256L);
        checksum[1] = (byte) (sum%256L);
        return checksum;
    }

    /**
     * Closes things down tidily and exits.
     * @param evt
     */
    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {
        System.exit(0);
    }

    private void sendButtonActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            outputText(outgoing.getText());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        outgoing.setText(null);
        outgoing.requestFocusInWindow();
    }

    /**
     * Runs whenever a key is typed, so that hitting the Enter key does a send.
     * @param evt
     */
    private void outgoingKeyTyped(java.awt.event.KeyEvent evt) {
        if (evt.getKeyChar() == 10) {
            sendButtonActionPerformed(null);
        }
    }

    /**
     * Outputs text to serial and screen
     * @param text
     * @throws java.io.IOException
     */
    private void outputText(String text) throws IOException {
        char[] textChars = text.toCharArray();
        byte[] textBytes = new byte[textChars.length+4];
        for (int i=0; i<textChars.length; i++) {
            textBytes[i+2] = (byte) textChars[i];
        }
        System.arraycopy(new byte[]{13,10}, 0, textBytes, 0, 2);
        System.arraycopy(new byte[]{13,10}, 0, textBytes, textChars.length+2, 2);
        writer.write(textBytes);
        writer.flush();
        displayText(text + "\r\n", 2);
    }

    /**
     * Displays incoming or outgoing text on-screen, formatting lines nicely
     * so we can see what's what.
     * @param text
     * @param direction
     */
    private void displayText(String text, int direction) {
        if ((direction != lastDirection) && !lineEmpty()) {
            incoming.append("\r\n");
        }
        if ((direction != lastDirection) || lineEmpty()) {
            if (direction == 1) {
                incoming.append("<-- ");
            } else if (direction == 2) {
                incoming.append("--> ");
            }
            lastDirection = direction;
        }
        incoming.append(text);
        // Scroll to the end, so latest data is visible.
        incoming.setCaretPosition(incoming.getDocument().getLength());
    }

    /**
     * Checks if the last line of the "incoming" box is empty or not.
     * @return
     */
    private boolean lineEmpty() {
        int i = incoming.getLineCount() - 1;
        boolean empty = false;
        try {
            empty = (incoming.getLineStartOffset(i) ==
                    incoming.getLineEndOffset(i));
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
        return empty;
    }
}
