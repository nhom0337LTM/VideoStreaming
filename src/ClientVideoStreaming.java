import RTP.RTPpacket;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class ClientVideoStreaming {
    JFrame f = new JFrame("Client");
    JButton setupButton = new JButton("Open");
    JButton playButton = new JButton("Play");
    JButton pauseButton = new JButton("Pause");
    JPanel mainPanel = new JPanel();
    JPanel buttonPanel = new JPanel();
    JLabel iconLabel = new JLabel();
    ImageIcon icon;
    DatagramPacket rcvdp; 
    DatagramSocket RTPsocket; 
    static int RTP_RCV_PORT = 25000; 
    Timer timer; 
    byte[] buf; 
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int state; 
    Socket RTSPsocket; 
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; 
    int RTSPSeqNb = 0; 
    int RTSPid = 0; 
    final static String CRLF = "\r\n";
    static int MJPEG_TYPE = 26; 

    public ClientVideoStreaming() {
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        buttonPanel.setLayout(new GridLayout(1, 0));
        buttonPanel.add(setupButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        //buttonPanel.add(tearButton);
        setupButton.addActionListener(new setupButtonListener());
        playButton.addActionListener(new playButtonListener());
        pauseButton.addActionListener(new pauseButtonListener());
        iconLabel.setIcon(null);
        mainPanel.setLayout(null);
        mainPanel.add(iconLabel);
        mainPanel.add(buttonPanel);
        iconLabel.setBounds(0, 0, 380, 280);
        buttonPanel.setBounds(0, 280, 380, 50);
        f.getContentPane().add(mainPanel, BorderLayout.CENTER);
        f.setSize(new Dimension(390, 370));
        f.setVisible(true);
        timer = new Timer(20, new timerListener());
        timer.setInitialDelay(0);
        timer.setCoalesce(true);
        buf = new byte[15000];
    }

    public static void main(String argv[]) throws Exception {
        ClientVideoStreaming theClient = new ClientVideoStreaming();
        int RTSP_server_port = Integer.parseInt("1107");
        String ServerHost = "localhost";
        InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);
        System.out.println(ServerIPAddr);
        VideoFileName = "D:\\downloadfile\\b.MJPEG";
        try {
            theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(
                    theClient.RTSPsocket.getInputStream()));
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
                    theClient.RTSPsocket.getOutputStream()));
            theClient.send_RTSP_request(VideoFileName);
            state = INIT;
        } catch (ConnectException e) {
            System.out.println("Could not connect to '" + ServerHost + ":"
                    + RTSP_server_port + "'");
            System.exit(0);
        }
    }

    public void send_RTSP_request(String request_type) {
        try {
            RTSPBufferedWriter.write(request_type + " " + VideoFileName
                    + " RTSP/1.0" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            if (request_type.equals("SETUP")) {
                RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= "
                        + RTP_RCV_PORT + CRLF);
            } else {
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            }
            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    class setupButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (state == INIT) {
                try {
                    RTPsocket = new DatagramSocket(RTP_RCV_PORT);
                    RTPsocket.setSoTimeout(5);

                } catch (SocketException se) {
                    System.out.println("Socket exception: " + se);
                    System.exit(0);
                }

                RTSPSeqNb = 1;
                send_RTSP_request("SETUP");
                if (parse_server_response() != 200) {
                    System.out.println("Invalid Server Response");
                } else {
                    state = READY;
                }
            }
        }
    }

    class playButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (state == READY) {
                RTSPSeqNb++;
                send_RTSP_request("PLAY");
                if (parse_server_response() != 200) {
                    System.out.println("Invalid Server Response");
                } else {
                    state = PLAYING;
                    timer.start();
                }
            }
        }
    }

    class pauseButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (state == PLAYING) {
                RTSPSeqNb++;
                send_RTSP_request("PAUSE");
                if (parse_server_response() != 200) {
                    System.out.println("Invalid Server Response");
                } else {
                    state = READY;
                    timer.stop();
                }
            }
        }
    }

    class timerListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            rcvdp = new DatagramPacket(buf, buf.length);
            try {
                RTPsocket.receive(rcvdp);
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp
                        .getLength());
                int payload_length = rtp_packet.getpayload_length();
                byte[] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Image image = toolkit.createImage(payload, 0, payload_length);
                icon = new ImageIcon(image);
                iconLabel.setIcon(icon);
            } catch (InterruptedIOException iioe) {
                
            } catch (IOException ioe) {
                System.out.println("Exception caught: " + ioe);
            }
        }
    }



    private int parse_server_response() {
        int reply_code = 0;
        try {
            String StatusLine = RTSPBufferedReader.readLine();
            System.out.println(StatusLine);
            StringTokenizer tokens = new StringTokenizer(StatusLine);
            tokens.nextToken(); 
            reply_code = Integer.parseInt(tokens.nextToken());
            if (reply_code == 200) {
                String SeqNumLine = RTSPBufferedReader.readLine();
                System.out.println(SeqNumLine);
                String SessionLine = RTSPBufferedReader.readLine();
                System.out.println(SessionLine);
                tokens = new StringTokenizer(SessionLine);
                tokens.nextToken(); 
                RTSPid = Integer.parseInt(tokens.nextToken());
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
        return (reply_code);
    }
}
