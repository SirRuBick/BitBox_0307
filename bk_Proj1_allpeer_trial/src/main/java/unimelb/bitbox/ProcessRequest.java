package unimelb.bitbox;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.logging.Logger;
import java.net.Socket;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

public class ProcessRequest extends Thread{
	private static Logger log = Logger.getLogger(ProcessRequest.class.getName());
	private FileSystemManager fileSystemManager; 
	private Document request;
	private String command;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer; 
	
	public ProcessRequest(FileSystemManager fileSystemManager,Document request,Socket socket) {
		this.fileSystemManager = fileSystemManager;
		this.request = request;
		this.command = request.getString("command");
		this.socket = socket;
    	try {
    		this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
    		this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"), true);
    	}catch(IOException e) {
    		e.printStackTrace();
    	}
    	//this.isComplete = false;
	}
	
	@Override
	public void run() {
		switch(command) {
		case "HANDSHAKE_REQUEST":
			// Can possible move handshake request also here.
			processHandshake();
			break;
		case "FILE_CREATE_REQUEST":
			processFileCreate();
			break;
		case "FILE_DELETE_REQUEST":
			processFileDelete();
			break;
		case "FILE_MODIFY_REQUEST":
			processFileModify();
			break;
		case "DIRECTORY_CREATE_REQUEST":
			processDirectoryCreate();
			break;
		case "DIRECTORY_DELETE_REQUEST":
			processDirectoryDelete();
			break;
		case "FILE_BYTES_REQUEST":
			processFileByteRequest();
			break;
		case "FILE_BYTES_RESPONSE":
			processFileByte();
			break;
		case "FILE_CREATE_RESPONSE":
			processResponse();
			break;
		case "FILE_DELETE_RESPONSE":
			processResponse();
			break;
		case "FILE_MODIFY_RESPONSE":
			processResponse();
			break;
		case "DIRECTORY_CREATE_RESPONSE":
			processResponse();
			break;
		case "DIRECTORY_DELETE_RESPONSE":
			processResponse();
			break;
		default:
			processInvalid();
			break;
		}
		
		
	}
	
	private void processFileByteRequest() {
		log.info("File bytes request from remote peer for " + this.request.getString("pathName"));
		boolean readStatus = false;
		while(!readStatus) {
			// send the request to fileOperator to read file
			RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
			Document result = requestOperator.fileByteResponse(this.request);
			// if the file read success
			readStatus = result.getBoolean("status");
			if(readStatus) {
				// success reading file
				// send the FILE BYTE to remote peer
				writer.println(result.toJson());
				// check if whole file has been sent
				long fileSize = ((Document) result.get("fileDescriptor")).getLong("fileSize");
				long position = result.getLong("position");
				long length = result.getLong("length");
				if(position + length == fileSize) {
					log.info("File fully sent out to remote peer, total size: " + fileSize);
				}
			}
		}
	}
	
	private void processResponse() {
		String message = this.request.getString("message");
		boolean status = this.request.getBoolean("status");
		String result = "";
		if(status) {
			result = "success";
		}else {
			result = "fail";
		}
		log.info("Response from peer " + this.command +" "+ result +" with message: " + message);
	}
	
	private void processHandshake() {
		// receive handshake after handshake
		Document result = Protocol.INVALID_PROTOCOL("handshake request after successful handshake");
		writer.println(result.toJson());
	}
	
	private void processFileCreate() {
		log.info("Start Processing File Create: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileCreateResponse(this.request);

		// send result to remote peer
		writer.println(result.toJson());
		if(result.getBoolean("status") && !requestOperator.hasShortcut) {
			requestFileByte(result);
		}
	}
	
	private void processFileDelete() {
		log.info("Start Processing File Delete: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileDeleteResponse(this.request);
		if(result.getBoolean("status")) {
			log.info("Success delete file: " + this.request.getString("pathName"));
		}
		
		writer.println(result.toJson());
	}
	
	private void processFileModify() {
		log.info("Start Processing File Modify: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileModifyResponse(this.request);
		// send result to remote peer
		writer.println(result.toJson());
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(result.getBoolean("status") && !requestOperator.hasShortcut) {
			requestFileByte(result);
		}
	}
	
	private void processDirectoryCreate() {
		log.info("Start Processing Directory Create: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.directoryCreateResponse(this.request);
		if(result.getBoolean("status")) {
			log.info("Success create directory: " + this.request.getString("pathName"));
		}
		writer.println(result.toJson());
	}
	
	private void processDirectoryDelete() {
		log.info("Start Processing Directory Delete: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.directoryDeleteResponse(this.request);
		if(result.getBoolean("status")) {
			log.info("Success delete directory: " + this.request.getString("pathName"));
		}
		writer.println(result.toJson());
	}
	
	private void processFileByte() {
		log.info("Start Processing File Byte Response: " + this.request.getString("pathName"));
		RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
		Document result = requestOperator.fileByteRequest(this.request);
		long length = result.getLong("length");
		if(length !=0) {
			// send result to remote peer
			log.info("Requesting file byte from peer " + result.getString("pathName") + 
					"postion " + result.getLong("position") + "& length " + result.getLong("length"));
			writer.println(result.toJson());
		}else {
			log.info("No more file byte request to send");
		}
	}
	
	private void processInvalid() {
		log.info("Invalid Protocol received");
	}
	
	private void requestFileByte(Document result) {
		// proceed to send byte request
		Document fileDescriptor = (Document) this.request.get("fileDescriptor");
		long fileSize = fileDescriptor.getLong("fileSize");
		long position = 0;
		long length = 0;
		if(fileSize >= PeerMaster.blockSize) {
			length = PeerMaster.blockSize;
		}else {
			length = fileSize;
		}
		
		// send FILE_BYTES_REQUEST to remote peer
		Document docToSend = Protocol.FILE_BYTES_REQUEST(this.request, position, length);

		log.info("Requesting file byte from peer " + result.getString("pathName") + 
				"postion " + position + "& length " + length);
		writer.println(docToSend.toJson());			
	}
	

}
