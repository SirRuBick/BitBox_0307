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
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ProcessEvent extends Thread{
	private static Logger log = Logger.getLogger(ProcessEvent.class.getName());
	private FileSystemManager fileSystemManager; 
	private FileSystemEvent eventToHandle;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
	private boolean isComplete;
    
    
    public ProcessEvent(FileSystemManager fileSystemManager,FileSystemEvent eventToHandle,Socket socket) {
    	// initialize
    	this.fileSystemManager = fileSystemManager;
    	this.eventToHandle = eventToHandle;
    	this.socket = socket;
    	try {
    		this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
    		this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"), true);
    	}catch(IOException e) {
    		e.printStackTrace();
    	}
    	this.isComplete = false;

    }
    
    @Override
    public void run() {
    	log.info("Event Process for " + eventToHandle.toString());
    	Document request = new Document();
    	// handling event
    	switch(eventToHandle.event) {
    	case FILE_CREATE:
    		//processFileCreate();
    		request = Protocol.FILE_CREATE_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
    		break;
		case FILE_DELETE:
			//processFileDelete();
			request = Protocol.FILE_DELETE_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
			break;
		case FILE_MODIFY:
			//processFileModify();
			request = Protocol.FILE_MODIFY_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
			break;
		case DIRECTORY_CREATE:
			//processDirectoryCreate();
			request = Protocol.DIRECTORY_CREATE_REQUEST(this.eventToHandle.pathName);
			break;
		case  DIRECTORY_DELETE:
			//processDirectoryDelete();
			request = Protocol.DIRECTORY_DELETE_REQUEST(this.eventToHandle.pathName);
			break;
    	}
    	
    	writer.println(request.toJson());
    	log.info("Event Process ended for " + eventToHandle.toString());
    }
    
    // process functions for each event type
    
    private void processFileCreate() {
    	Document docToSend = Protocol.FILE_CREATE_REQUEST(eventToHandle.fileDescriptor, eventToHandle.pathName);
    	writer.println(docToSend.toJson());
    	
    	// Handling Responses
    	while(!isComplete) {
    		try {
				Document docRec = Document.parse(reader.readLine());
				String command = docRec.getString("command");
	
				if(command.equals("FILE_CREATE_RESPONSE")) {
					if(docRec.getBoolean("status")) {
						log.info("File Request success on remote peer, waiting for FILE_BYTES_REQUEST");
						break;
					}else {
						log.info("File Request fail on remote peer with message: " + docRec.getString("message"));
						this.isComplete = true;
					}
				}else {
					log.info("Expecting FILE_CREATE_RESPONSE, Receiving " + command +", stop sending file");
					this.isComplete = true;
					break;
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}

    	// start file byte operations
    	if(!isComplete) {
    		try {
				processFileByte();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
	}
    
    private void processFileDelete() {
    	Document docToSend = Protocol.FILE_DELETE_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
    	writer.println(docToSend.toJson());
    	// Handling Responses
    	while(!isComplete) {
    		try {
    			Document docRec = Document.parse(reader.readLine());
				String command = docRec.getString("command");
				switch(command) {
				case "FILE_DELETE_RESPONSE":
					if(docRec.getBoolean("status")) {
						log.info("File deleted on remote peer with message: " + docRec.getString("message"));
						this.isComplete = true;
					}else {
						log.info("File delete fail on remote peer with message: " + docRec.getString("message"));
						continue;
					}
					break;
				default:
					log.info("Expecting FILE_DELETE_RESPONSE, Receiving " + command +", stop event sharing");
					this.isComplete = true;
					break;
				}
    		}catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
    
    private void processFileModify() {
    	Document docToSend = Protocol.FILE_MODIFY_REQUEST(this.eventToHandle.fileDescriptor, this.eventToHandle.pathName);
    	writer.println(docToSend.toJson());
    	// Handling Responses
    	while(!isComplete) {
    		try {
    			Document docRec = Document.parse(reader.readLine());
				String command = docRec.getString("command");
				if(command.equals("FILE_MODIFY_RESPONSE")) {
					if(docRec.getBoolean("status")) {
						log.info("File modifier created on remote peer");
						// PROCEED to file byte
						break;
					}else {
						log.info("File modify fail on remote peer with message: " + docRec.getString("message"));
						this.isComplete = true;
					}
				}else {
					log.info("Expecting FILE_MODIFY_RESPONSE, Receiving " + command +", stop event sharing");
					this.isComplete = true;
				}
    		}catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	
    	// start file byte operations
    	if(!isComplete) {
    		try {
				processFileByte();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	
    }
    
    private void processDirectoryCreate() {
    	Document docToSend = Protocol.DIRECTORY_CREATE_REQUEST(this.eventToHandle.pathName);
    	writer.println(docToSend.toJson());
    	// Handling Responses
    	while(!isComplete) {
    		try {
    			Document docRec = Document.parse(reader.readLine());
				String command = docRec.getString("command");
				switch(command) {
				case "DIRECTORY_CREATE_RESPONSE":
					if(docRec.getBoolean("status")) {
						log.info("Directory created on remote peer");
						this.isComplete = true;
					}else {
						log.info("Directory create fail on remote server, waiting for a success response");
						continue;
					}
					break;
				default:
					log.info("Expecting DIRECTORY_CREATE_RESPONSE, Receiving " + command +", stop event sharing");
					this.isComplete = true;
					break;
				}
    		}catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
    
    private void processDirectoryDelete() {
    	Document docToSend = Protocol.DIRECTORY_DELETE_REQUEST(this.eventToHandle.pathName);
    	writer.println(docToSend.toJson());
    	// Handling Responses
    	while(!isComplete) {
    		try {
    			Document docRec = Document.parse(reader.readLine());
				String command = docRec.getString("command");
				switch(command) {
				case "DIRECTORY_DELETE_RESPONSE":
					if(docRec.getBoolean("status")) {
						log.info("Directory deleted on remote peer");
						this.isComplete = true;
					}else {
						log.info("Directory delete fail on remote server, waiting for a success response");
						continue;
					}
					break;
				default:
					log.info("Expecting DIRECTORY_DELETE_RESPONSE, Receiving " + command +", stop event sharing");
					this.isComplete = true;
					break;
				}
    		}catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
    
    private boolean processFileByte() throws IOException {
    	// the remote may or may not send FILE_BYTES_REQUEST after file create or file modify.
    	// so wait for some seconds, if the Bufferredreader is ready, then proceed, else end
    	try {
			this.sleep(1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	if(!this.reader.ready()) {
    		log.info("No file bytes request from remote peer");
    		return false;
    	}
    	while(!isComplete) {
    		Document docRec = Document.parse(reader.readLine());
			String command = docRec.getString("command");
			
			switch(command) {
			case "FILE_BYTES_REQUEST":
				log.info("File bytes request from remote peer for " + docRec.getString("pathName"));
				boolean readStatus = false;
				while(!readStatus) {
					// send the request to fileOperator to read file
					RespondOnReq requestOperator = new RespondOnReq(this.fileSystemManager);
					Document result = requestOperator.fileByteResponse(docRec);
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
							this.isComplete = true;
						}
					}
				}
				break;
			default:
				log.info("Expecting FILE_BYTES_REQUEST, Receiving " + command +", stop event sharing");
				this.isComplete = true;
				break;
			}
    	}
    	return this.isComplete;
    }
    
}
