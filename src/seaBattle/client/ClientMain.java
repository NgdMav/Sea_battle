package seaBattle.client;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.TreeMap;
import javax.naming.event.ObjectChangeListener;
import seaBattle.protocol.Protocol;
import seaBattle.protocol.messages.Message;
import seaBattle.protocol.messages.messages.MessageConnect;
import seaBattle.protocol.messages.messages.MessageDisconnect;
import seaBattle.protocol.messages.messages.MessagePing;
import seaBattle.protocol.messages.messages.MessageUser;
import seaBattle.protocol.messages.messagesResult.MessageConnectResult;
import seaBattle.protocol.messages.messagesResult.MessageError;

public class ClientMain 
{
	public static void main(String[] args)  
    {
		if (args.length < 2 || args.length > 3) 
        {
			System.err.println(	"Invalid number of arguments\n" + "Use: nickname fullname [host]" );
			waitKeyToStop();
			return;
		}
		try ( Socket sock = ( args.length == 2 ? 
				new Socket( InetAddress.getLocalHost(), Protocol.PORT):
				new Socket( args[2], Protocol.PORT ) )) 
        { 		
			System.err.println("initialized");
			session(sock, args[0], args[1]);
		} 
        catch ( Exception e) 
        {
			System.err.println(e);
		} 
        finally 
        {
			System.err.println("bye...");
		}
	}

	static void waitKeyToStop() 
    {
		System.err.println("Press a key to stop...");
		try 
        {
			System.in.read();
		} 
        catch (IOException e) 
        {
		}
	}

		static class Session {
		boolean connected = false;
		String userNickName = null;
		String userFullName = null;
		String userOpenentNick = null;
		List<Long> challenges = null;
		Long currentGameSessionID = null;
		boolean shipsReady = false;
		boolean gameStarted = false;
		boolean yourTurn = false;
		Session(String userNickName, String userFullName) 
		{
			this.userNickName = userNickName;
			this.userFullName = userFullName;
		}
	}
	static class ListenerThread extends Thread
	{
		ObjectInputStream ois;
		Session ses;
		public ListenerThread(ObjectInputStream ois, Session ses)
		{
			this.ois = ois;
			this.ses = ses;
			setDaemon(true);
		}
		@Override
		public void run()
		{
			while(true)
			{
				try
				{
					Message msg = (Message) ois.readObject();
					switch (msg.getID()) 
					{
						case Protocol.CMD_ERROR:
							System.out.println(((MessageError) msg).getMessage());
							break;
						case Protocol.CMD_PONG:
							System.out.println("pong");
							break;
						default:
							assert(false);
							break;
					}
				}
				catch(IOException | ClassNotFoundException e)
				{

				}
			}
		}
	}
	static void session(Socket s, String userNickName, String userFullName) 
	{
		try ( Scanner in = new Scanner(System.in);
			  ObjectInputStream is = new ObjectInputStream(s.getInputStream());
			  ObjectOutputStream os = new ObjectOutputStream(s.getOutputStream())) 
			  {
				Session ses = new Session(userNickName, userFullName);
				if ( openSession( ses, is, os, in )) 
				{ 
					ListenerThread lt = new ListenerThread(is, ses);
					lt.start();
					try 
					{
						while (true) 
						{
							Message msg = getCommand(ses, in);
							os.writeObject(msg);				
						}			
					} 
					finally 
					{
						closeSession(ses, os);
					}
				}
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	
	static boolean openSession(Session ses, ObjectInputStream is, ObjectOutputStream os, Scanner in) 
			throws IOException, ClassNotFoundException 
		{
		os.writeObject(new MessageConnect(ses.userNickName, ses.userFullName));
		MessageConnectResult msg = (MessageConnectResult) is.readObject();
		if (msg.Error() == false) 
		{
			System.err.println("connected");
			ses.connected = true;
			return true;
		}
		System.err.println("Unable to connect: "+ msg.getMessage());
		System.err.println("Press <Enter> to continue...");
		if(in.hasNextLine())
			in.nextLine();
		return false;
	}
	
	static void closeSession(Session ses, ObjectOutputStream os) throws IOException 
	{
		if ( ses.connected ) 
		{
			ses.connected = false;
			os.writeObject(new MessageDisconnect());
		}
	}

	static Message getCommand(Session ses, Scanner in) {	
		while (true) 
		{
			if (in.hasNextLine()== false)
				break;
			String str = in.nextLine();
			byte cmd = translateCmd(str);
			switch ( cmd ) 
			{
				case Protocol.CMD_PING:
				{
					return new MessagePing();
				}
				default: 
				{
					System.err.println("Unknown command!");
					continue;
				}
			}
		}
		return null;
	}
	static TreeMap<String,Byte> commands = new TreeMap<String,Byte>();
	static 
	{
		commands.put("ping", new Byte((byte) Protocol.CMD_PING));
	}
	
	static byte translateCmd(String str) 
	{
		str = str.trim();
		Byte r = commands.get(str);
		return (r == null ? 0 : r.byteValue());
	}
	
	static void printPrompt() 
	{
		System.out.println();
		System.out.print("ping >");
		System.out.flush();
	}
}
