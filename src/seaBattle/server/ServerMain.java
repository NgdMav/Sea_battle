package seaBattle.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import seaBattle.gameLogic.GameSession;
import seaBattle.gameLogic.Player.MoveResult;
import seaBattle.protocol.Protocol;
import seaBattle.protocol.cmd.CmdHandler;
import seaBattle.protocol.cmd.CommandThread;
import seaBattle.protocol.messages.Message;
import seaBattle.protocol.messages.messages.MessageChallenge;
import seaBattle.protocol.messages.messages.MessageConnect;
import seaBattle.protocol.messages.messages.MessageUser;
import seaBattle.protocol.messages.messagesRequest.MessageChallengeRequest;
import seaBattle.protocol.messages.messagesRequest.MessageForfeit;
import seaBattle.protocol.messages.messagesRequest.MessageGameStart;
import seaBattle.protocol.messages.messagesRequest.MessageGetField;
import seaBattle.protocol.messages.messagesRequest.MessageMove;
import seaBattle.protocol.messages.messagesRequest.MessagePlaceShips;
import seaBattle.protocol.messages.messagesRequest.MessageReadyToPlay;
import seaBattle.protocol.messages.messagesResponse.MessageChallengeResponse;
import seaBattle.protocol.messages.messagesResponse.MessageGetFieldResult;
import seaBattle.protocol.messages.messagesResult.MessageChallengeResult;
import seaBattle.protocol.messages.messagesResult.MessageConnectResult;
import seaBattle.protocol.messages.messagesResult.MessageError;
import seaBattle.protocol.messages.messagesResult.MessageGameOver;
import seaBattle.protocol.messages.messagesResult.MessageMoveResult;
import seaBattle.protocol.messages.messagesResult.MessagePlaceShipsResult;
import seaBattle.protocol.messages.messagesResult.MessagePong;
import seaBattle.protocol.messages.messagesResult.MessageUserResult;

public class ServerMain {
	
	private static int MAX_USERS = 100;

	public static void main(String[] args) {

		try ( ServerSocket serv = new ServerSocket( Protocol.PORT  )) {
			System.out.println("initialized");
			ServerStopThread tester = new ServerStopThread();
			tester.start();
			while (true) {
				Socket sock = accept( serv );
				if ( sock != null ) {
					if ( ServerMain.getNumUsers() < ServerMain.MAX_USERS )
					{
						System.out.println( sock.getInetAddress().getHostName() + " connected" );
						ServerClientHandler server = new ServerClientHandler(sock);
						server.start();
					}
					else
					{
						System.out.println( sock.getInetAddress().getHostName() + " connection rejected" );
						sock.close();
					}
				} 
				if ( ServerMain.getStopFlag() ) {
					break;
				}
			}
		} catch (IOException e) {
			System.err.println(e);
		} finally {
			stopAllUsers();
			System.out.println("stopped");	
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {		
		}
	}
	
	public static Socket accept( ServerSocket serv ) {	
		assert( serv != null );
		try {
			serv.setSoTimeout( 1000 );
			Socket sock = serv.accept();
			return sock;
		} catch (SocketException e) {
		} catch (IOException e) {
		}		
		return null;
	}
	
	private static void stopAllUsers() {
		String[] nic = getUsers();
		for (String user : nic ) {
			ServerClientHandler ut = getUser( user );
			if ( ut != null ) {
				ut.disconnect();
			}
		}
	}
	
	private static Object syncFlags = new Object();
	private static boolean stopFlag = false;
	public static boolean getStopFlag() {
		synchronized ( ServerMain.syncFlags ) {
			return stopFlag;
		}
	}
	public static void setStopFlag( boolean value ) {
		synchronized ( ServerMain.syncFlags ) {
			stopFlag = value;
		}
	}
	
	private static Object syncUsers = new Object();
	private static ConcurrentHashMap<String, ServerClientHandler> users = new ConcurrentHashMap<String, ServerClientHandler> ();
	
	public static ServerClientHandler getUser( String userNic ) {
		synchronized (ServerMain.syncUsers) {
			return ServerMain.users.get( userNic );
		}		
	}

	public static ServerClientHandler registerUser( String userNic, ServerClientHandler user ) {
		synchronized (ServerMain.syncUsers) {
			ServerClientHandler old = ServerMain.users.get( userNic );
			if ( old == null ) {
				ServerMain.users.put( userNic, user );
			}
			return old;
		}		
	}

	public static ServerClientHandler setUser( String userNic, ServerClientHandler user ) {
		synchronized (ServerMain.syncUsers) {
			ServerClientHandler res = ServerMain.users.put( userNic, user );
			if ( user == null ) {
				ServerMain.users.remove(userNic);
			}
			return res;
		}		
	}
	
	public static String[] getUsers() {
		synchronized (ServerMain.syncUsers) {
			return ServerMain.users.keySet().toArray( new String[0] );
		}		
	}
	
	public static int getNumUsers() {
		synchronized (ServerMain.syncUsers) {
			return ServerMain.users.keySet().size();
		}		
	}

	private static Object syncSession = new Object();
	private static ConcurrentHashMap<Long, GameSession> gameSessions = new ConcurrentHashMap<Long, GameSession>();
	private static long id = 1000000;

	public static long nextId() {
		return ++id;
	}

	public static GameSession getSession(long sessionId) {
		synchronized (ServerMain.syncSession) {
			return ServerMain.gameSessions.get(sessionId);
		}
	}

	public static GameSession registerSession( long sessionId, GameSession session ) {
		synchronized (ServerMain.syncSession) {
			GameSession old = ServerMain.gameSessions.get( sessionId );
			if ( old == null ) {
				ServerMain.gameSessions.put( sessionId, session );
			}
			return old;
		}		
	}

	public static GameSession setSession( long sessionId, GameSession session ) {
		synchronized (ServerMain.syncSession) {
			GameSession res = ServerMain.gameSessions.put( sessionId, session );
			if ( session == null ) {
				ServerMain.gameSessions.remove(sessionId);
			}
			return res;
		}		
	}
	
	public static Object[] getSessions() {
		synchronized (ServerMain.syncSession) {
			return ServerMain.gameSessions.keySet().toArray();
		}
	}
	
	public static int getNumSessions() {
		synchronized (ServerMain.syncSession) {
			return ServerMain.gameSessions.keySet().size();
		}		
	}

	private static final Object syncChallenges = new Object();
	private static final ConcurrentHashMap<Long, Challenge> challenges = new ConcurrentHashMap<>();

	public static Challenge getChallenge(long id) {
		synchronized (syncChallenges) {
			return challenges.get(id);
		}
	}

	public static void registerChallenge(Challenge challenge) {
		synchronized (syncChallenges) {
			challenges.put(challenge.getId(), challenge);
		}
	}

	public static void removeChallenge(long id) {
		synchronized (syncChallenges) {
			challenges.remove(id);
		}
	}

	private static long challengeId = 10000;
	public static long nextChallengeId() {
		return ++challengeId;
	}
}

class ServerStopThread extends CommandThread {
	
	static final String cmd  = "q";
	static final String cmdL = "quit";
	
	Scanner fin; 
	
	public ServerStopThread() {		
		fin = new Scanner( System.in );
		ServerMain.setStopFlag( false );
		putHandler ( cmd, cmdL, new CmdHandler() {
			@Override
			public boolean onCommand(int[] errorCode) {	return onCmdQuit(); }				
		});
		this.setDaemon(true);
		System.err.println( "Enter \'" + cmd + "\' or \'" + cmdL + "\' to stop server\n" );
	}
	
	public void run() {
		
		while (true) {			
			try {
				Thread.sleep( 1000 );
			} catch (InterruptedException e) {
				break;
			}
			if ( fin.hasNextLine()== false )
				continue;
			String str = fin.nextLine();
			if ( command( str )) {
				break;
			}
		}
	}
	
	public boolean onCmdQuit() {
		System.err.print("stop server...");
		fin.close();
		ServerMain.setStopFlag( true );
		return true;
	}
}

class ServerClientHandler extends Thread {
	
	private Socket              sock;
	private ObjectOutputStream 	os;
	private ObjectInputStream 	is;
	private InetAddress 		addr;
	
	private String userNic = null;
	private String userFullName;
	
	public ServerClientHandler(Socket s) throws IOException {
		sock = s;
		s.setSoTimeout(1000);
		os = new ObjectOutputStream( s.getOutputStream() );
		is = new ObjectInputStream( s.getInputStream());
		addr = s.getInetAddress();
		this.setDaemon(true);
	}
	
	public void run() {
		try {
			while ( true ) {
				Message msg = null;
				try {
					msg = ( Message ) is.readObject();
				} catch (IOException e) {
				} catch (ClassNotFoundException e) {
				}
				if (msg != null) switch ( msg.getID() ) {
			
					case Protocol.CMD_PING:
						os.writeObject(new MessagePong());
						break;

					case Protocol.CMD_CONNECT:
						if ( !connect( (MessageConnect) msg )) 
							return;
						break;
						
					case Protocol.CMD_DISCONNECT:
						return;
						
					case Protocol.CMD_USER:
						user(( MessageUser ) msg);
						break;
						
					case Protocol.CMD_CHALLENGE:
						MessageChallenge challenge = (MessageChallenge) msg;
						ServerClientHandler target = ServerMain.getUser(challenge.getToNic());

						if (target != null) {
							long cid = ServerMain.nextChallengeId();
							Challenge ch = new Challenge(cid, userNic, challenge.getToNic());
							ServerMain.registerChallenge(ch);
							target.sendMessage(new MessageChallengeRequest(userNic, cid));
							System.out.println("Challenge created: " + cid + " " + userNic + " → " + challenge.getToNic());
						} else {
							os.writeObject(new MessageError("Target player not found"));
						}
						
					case Protocol.CMD_CHALLENGE_RESPONSE:
						MessageChallengeResponse resp = (MessageChallengeResponse) msg;
						Challenge ch = ServerMain.getChallenge(resp.getChallengeId());

						if (ch == null) {
							os.writeObject(new MessageError("Challenge not found"));
							break;
						}

						ServerClientHandler initiator = ServerMain.getUser(ch.getFromNic());
						if (initiator == null) {
							os.writeObject(new MessageError("Initiator not found"));
							ServerMain.removeChallenge(ch.getId());
							break;
						}

						if (resp.getAccepted()) {
							long sessionId = ServerMain.nextId();
							GameSession session = new GameSession(sessionId, ch.getFromNic(), ch.getToNic(), null, null);
							ServerMain.registerSession(sessionId, session);

							initiator.sendMessage(new MessageGameStart("Server", sessionId, userNic, true));
							sendMessage(new MessageGameStart("Server", sessionId, initiator.userNic, false));

							System.out.println("Game session started: " + sessionId);
						} else {
							initiator.sendMessage(new MessageChallengeResult(false, "Challenge declined by " + ch.getToNic(), ch.getId()));
						}

						ServerMain.removeChallenge(ch.getId());
						break;	
							
					case Protocol.CMD_SHIP_PLACE:
						MessagePlaceShips mPlaceShips = (MessagePlaceShips) msg;
						GameSession session = ServerMain.getSession(mPlaceShips.getSessionId());
						boolean good = session.setPlaceShip(mPlaceShips.getFrom(), mPlaceShips.getShips());
						sendMessage(new MessagePlaceShipsResult(good, ""));
						break;	
					
					case Protocol.CMD_READY:
						MessageReadyToPlay mready = (MessageReadyToPlay) msg;
						session = ServerMain.getSession(mready.getSessionId());
						session.playerReady(mready.getFrom());
						break;

					case Protocol.CMD_MOVE:
						MessageMove msgmove = (MessageMove) msg;
						session = ServerMain.getSession(msgmove.getSessionId());
						MoveResult res = session.move(msgmove.getFrom(), msgmove.getX(), msgmove.getY());
						sendMessage(new MessageMoveResult(true, msgmove.getFrom() + " move done", msgmove.getSessionId(), msgmove.getX(), msgmove.getY(), res.hitted, res.sunked, res.gameOver, res.field));

						ServerClientHandler enemy = ServerMain.getUser(session.getEnemyNic(msgmove.getFrom()));
						enemy.sendMessage((new MessageMoveResult(true, msgmove.getFrom() + " move done", msgmove.getSessionId(), msgmove.getX(), msgmove.getY(), res.hitted, res.sunked, res.gameOver, res.field)));

						if (res.gameOver) {
							sendMessage(new MessageGameOver(true, "Game over", msgmove.getSessionId(), msgmove.getFrom()));
							enemy.sendMessage(new MessageGameOver(true, "Game over", msgmove.getSessionId(), msgmove.getFrom()));
						}
						break;
									
					case Protocol.CMD_FORFEIT:
						MessageForfeit msgforfeit = (MessageForfeit) msg;
						session = ServerMain.getSession(msgforfeit.getSessionId());
						session.gameEnd();

						enemy = ServerMain.getUser(session.getEnemyNic(msgforfeit.getFrom()));
						sendMessage(new MessageGameOver(true, "Game over", msgforfeit.getSessionId(), enemy.userNic));
						enemy.sendMessage(new MessageGameOver(true, "Game over", msgforfeit.getSessionId(), enemy.userNic));

						break;	

					case Protocol.CMD_GET_FIELD:
						MessageGetField msggetf = (MessageGetField) msg;
						session = ServerMain.getSession(msggetf.getSessionId());
						int[][] field = session.getField(msggetf.getFrom());
						sendMessage(new MessageGetFieldResult(field));
				}
			}	
		} catch (IOException e) {
			System.out.print("Disconnect...");
		} finally {
			disconnect();
		}
	}
	
	public synchronized void sendMessage(Message msg) {
		try {
			os.writeObject(msg);
			os.flush();
			System.out.println("Massege send to " + userNic + " (type = " + msg.getClass().toString() + ")");
		} catch (IOException e) {
			System.err.println("Error sending to " + userNic + ": " + e.getMessage());
		}
	}

	boolean connect( MessageConnect msg ) throws IOException {
		
		ServerClientHandler old = register( msg.getNic(), msg.getFullName() );
		if ( old == null )
		{
			os.writeObject( new MessageConnectResult());
			return true;
		} else {
			os.writeObject( new MessageConnectResult(false, "User " + old.userFullName + " already connected as " + userNic ));
			return false;
		}
	}
	
	void user( MessageUser msg ) throws IOException {
		
		String[] nics = ServerMain.getUsers();
		if ( nics != null ) 
			os.writeObject( new MessageUserResult( nics ));
		else
			os.writeObject( new MessageUserResult(false, "Unable to get users list", null ));
	}
	
	private boolean disconnected = false;
	public void disconnect() {
		if ( ! disconnected )
		try {			
			System.out.println( addr.getHostName() + " disconnected (UserNic = " + userNic + ")");
			unregister();
			os.close();
			is.close();
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			this.interrupt();
			disconnected = true;
		}
	}
	
	private void unregister() {
		if ( userNic != null ) {
			ServerMain.setUser( userNic, null );			
			userNic = null;
		}		
	}
	
	private ServerClientHandler register( String nic, String name ) {
		ServerClientHandler old = ServerMain.registerUser( nic, this );
		if ( old == null ) {
			if ( userNic == null ) {
				userNic = nic;
				userFullName = name;
				System.out.println("User \'"+ name+ "\' registered as \'"+ nic + "\'");
			}
		}
		return old;
	}
}

