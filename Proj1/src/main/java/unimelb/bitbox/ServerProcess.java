package unimelb.bitbox;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager;

public class ServerProcess extends Thread {
	private static Logger log = Logger.getLogger(ServerProcess.class.getName());
	private FileSystemManager fileSystemManager;
	
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
	
	private boolean isHandshake;
	
	public ServerProcess(FileSystemManager fileSystemManager, Socket socket) {
		this.fileSystemManager = fileSystemManager;
		this.socket = socket;
		this.isHandshake = false;
	}
	
	@Override
	public void run() {
		try {
			// Input stream Output Stream
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

			Document handShakeReque = Document.parse(reader.readLine());

			if (!handShakeReque.containsKey("command")) {
				// not exist cmd message
				Document output = Protocol.INVALID_PROTOCOL("message must contain a command field as string");
				writer.println(output.toJson());
				socket.close();
			}else if (!handShakeReque.containsKey("hostPort")) {
				// Cannot Identify peer, Exit.
				Document output = Protocol.INVALID_PROTOCOL("message must contain host and port for handshake");
				writer.println(output.toJson());
				socket.close();
			}else {
				// Otherwise
				if (!handShakeReque.getString("command").equals("HANDSHAKE_REQUEST")) {
					// Not Valid Command
					Document output = Protocol.INVALID_PROTOCOL("handshake is required");
					writer.println(output.toJson());
					socket.close();
				} else {
					Document hostPort = (Document)handShakeReque.get("hostPort");
					HostPort currentClient= new HostPort(hostPort);
					// HANDSHAKE_REQUEST is received
					if(PeerStatistics.isPeerFull()){
						// peer full
						Document handShakeRspon = Protocol.CONNECTION_REFUSED(PeerStatistics.peerListToDoc());
						writer.println(handShakeRspon.toJson());
						socket.close();
					}
					else {
						// handshake response
						log.info("Handshake success with client:" + socket.getInetAddress().getHostName()
								+ ":" + socket.getPort());
						PeerStatistics.addPeer(currentClient);
						Document handShakeRspon = Protocol.HANDSHAKE_RESPONSE(currentClient);
						writer.println(handShakeRspon.toJson());
						this.isHandshake = true;
						this.reader = reader;
						this.writer = writer;
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		// if handshake success, process event
		while(this.isHandshake) {
			try {
				Document request = Document.parse(reader.readLine());
				System.out.println(request.toJson());
				RequestProcessor requestprocessor = new RequestProcessor(this.fileSystemManager, request, this.socket);
				requestprocessor.start();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}	

	}

}
