import Model.VideoStream;
import RTP.RTPpacket;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class ServerVideoStreaming extends JFrame implements ActionListener {
    DatagramSocket RTPsocket; 
    DatagramPacket senddp;
    InetAddress ClientIPAddr; 
    int RTP_dest_port = 0; 
    JLabel label;
    int imagenb = 0; 
    VideoStream video; 
    static int MJPEG_TYPE = 26; 
    static int FRAME_PERIOD = 50; 							
    static int VIDEO_LENGTH = 500; 
    Timer timer;
    byte[] buf; 
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    //final static int TEARDOWN = 6;
    static int state; 
    Socket RTSPsocket;
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; 
    static int RTSP_ID = 123456;
    int RTSPSeqNb = 0; 
    final static String CRLF = "\r\n";

    public ServerVideoStreaming() {
        super("Server");
        timer = new Timer(FRAME_PERIOD, this);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);
        buf = new byte[15000];
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                timer.stop();
                System.exit(0);
            }
        });

        label = new JLabel("Send", JLabel.CENTER);
        getContentPane().add(label, BorderLayout.CENTER);
    }

    public static void main(String argv[]) throws Exception {
        ServerVideoStreaming theServer = new ServerVideoStreaming();
        theServer.pack();
        theServer.setVisible(true);
        int RTSPport = Integer.parseInt("1107");
        try {
            ServerSocket listenSocket = new ServerSocket(RTSPport);
            theServer.RTSPsocket = listenSocket.accept();
            listenSocket.close();

            theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();
            System.out.println(theServer.ClientIPAddr);
            state = INIT;

            RTSPBufferedReader = new BufferedReader(new InputStreamReader(
                    theServer.RTSPsocket.getInputStream()));
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
                    theServer.RTSPsocket.getOutputStream()));

            int request_type;
            boolean done = false;
            while (!done) {
                request_type = theServer.parse_RTSP_request(); // blocking
                if (request_type == SETUP) {
                    done = true;
                    state = READY;
                    System.out.println("New RTSP state: READY");
                    theServer.send_RTSP_response();
                    theServer.video = new VideoStream(VideoFileName);
                    theServer.RTPsocket = new DatagramSocket();
                }
            }

            while (true) {
                request_type = theServer.parse_RTSP_request(); // blocking
                if ((request_type == PLAY) && (state == READY)) {
                    theServer.send_RTSP_response();
                    theServer.timer.start();
                    state = PLAYING;
                    System.out.println("New RTSP state: PLAYING");
                } else if ((request_type == PAUSE) && (state == PLAYING)) {
                    theServer.send_RTSP_response();
                    theServer.timer.stop();
                    state = READY;
                    System.out.println("New RTSP state: READY");
                }
            }
        } catch (BindException e) {
            System.out.println("Could not init server on port '" + argv[0] + "'");
            System.exit(0);
        }

    }

    public void actionPerformed(ActionEvent e) {
        if (imagenb < VIDEO_LENGTH) {
            imagenb++;

            try {
                int image_length = video.getnextframe(buf);
                RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb,
                        imagenb * FRAME_PERIOD, buf, image_length);
                int packet_length = rtp_packet.getlength();
                byte[] packet_bits = new byte[packet_length];
                rtp_packet.getpacket(packet_bits);
                senddp = new DatagramPacket(packet_bits, packet_length,
                        ClientIPAddr, RTP_dest_port);
                RTPsocket.send(senddp);

                label.setText("Send frame #" + imagenb);
            } catch (Exception ex) {
                System.out.println("Exception caught: " + ex);
                System.exit(0);
            }
        } else {
            timer.stop();
        }
    }

    private int parse_RTSP_request() {
        int request_type = -1;
        try {
            String RequestLine = RTSPBufferedReader.readLine();
            System.out.println(RequestLine);

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();

            if ((new String(request_type_string)).compareTo("SETUP") == 0) {
                request_type = SETUP;
            } else if ((new String(request_type_string)).compareTo("PLAY") == 0) {
                request_type = PLAY;
            } else if ((new String(request_type_string)).compareTo("PAUSE") == 0) {
                request_type = PAUSE;
            }

            if (request_type == SETUP) {
                VideoFileName = tokens.nextToken();
            }

            String SeqNumLine = RTSPBufferedReader.readLine();
            System.out.println(SeqNumLine);
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());

            String LastLine = RTSPBufferedReader.readLine();
            System.out.println(LastLine);

            if (request_type == SETUP) {
                tokens = new StringTokenizer(LastLine);
                for (int i = 0; i < 3; i++) {
                    tokens.nextToken();
                }
                RTP_dest_port = Integer.parseInt(tokens.nextToken());
            }

        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
        return (request_type);
    }

    private void send_RTSP_response() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
}
