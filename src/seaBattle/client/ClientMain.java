package seaBattle.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.TreeMap;

import seaBattle.gameLogic.Player;
import seaBattle.gameLogic.Ship;
import seaBattle.protocol.Protocol;
import seaBattle.protocol.messages.Message;
import seaBattle.protocol.messages.messages.MessageChallenge;
import seaBattle.protocol.messages.messages.MessageConnect;
import seaBattle.protocol.messages.messages.MessageDisconnect;
import seaBattle.protocol.messages.messages.MessagePing;
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
import seaBattle.protocol.messages.messagesResult.MessageUserResult;

@SuppressWarnings("removal")
public class ClientMain {
	// arguments: userNic userFullName [host]
	public static void main(String[] args) {
		if (args.length < 2 || args.length > 3) {
			System.err.println("Invalid number of arguments\n" + "Use: nic name [host]");
			waitKeyToStop();
			return;
		}
		try (Socket sock = (args.length == 2 ? new Socket(InetAddress.getLocalHost(), Protocol.PORT)
				: new Socket(args[2], Protocol.PORT))) {
			log("CLIENT", "initialized");
			session(sock, args[0], args[1]);
		} catch (Exception e) {
			System.err.println(e);
		} finally {
			log("CLIENT", "bye...");
		}
	}

	public static void log(String tag, String msg) {
		System.out.printf("[%tT] [%s] %s%n", System.currentTimeMillis(), tag, msg);
	}

	static void waitKeyToStop() {
		log("System", "Press a key to stop...");
		try {
			System.in.read();
		} catch (IOException e) {
		}
	}

	static class Session {
		boolean connected = false;
		String userNic;
		String opponentNic;
		String userName;

		List<Long> pendingChallenges = new ArrayList<>();
		Long currentSessionId = null;

		boolean gameStarted = false;
		boolean myTurn = false;
		boolean shipsPlaced = false;

		Session(String nic, String name) {
			userNic = nic;
			userName = name;
		}
	}

	static void session(Socket s, String nic, String name) {
		try (Scanner in = new Scanner(System.in);
				ObjectInputStream is = new ObjectInputStream(s.getInputStream());
				ObjectOutputStream os = new ObjectOutputStream(s.getOutputStream())) {
			Session ses = new Session(nic, name);
			if (openSession(ses, is, os, in)) {
				ClientReceiver receiver = new ClientReceiver(is, ses);
				receiver.start();
				try {
					while (true) {
						Message msg = getCommand(ses, in);
						if (!processCommand(ses, msg, is, os)) {
							break;
						}
					}
				} finally {
					closeSession(ses, os);
				}
			}
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	static boolean openSession(Session ses, ObjectInputStream is, ObjectOutputStream os, Scanner in)
			throws IOException, ClassNotFoundException {
		os.writeObject(new MessageConnect(ses.userNic, ses.userName));
		MessageConnectResult msg = (MessageConnectResult) is.readObject();
		if (!msg.Error()) {
			log("CLIENT", "connected");
			ses.connected = true;
			return true;
		}
		log("CLIENT", "Unable to connect: " + msg.getMessage());
		log("CLIENT", "Press <Enter> to continue...");
		if (in.hasNextLine())
			in.nextLine();
		return false;
	}

	static void closeSession(Session ses, ObjectOutputStream os) throws IOException {
		if (ses.connected) {
			ses.connected = false;
			os.writeObject(new MessageDisconnect());
		}
	}

	static Message getCommand(Session ses, Scanner in) {
		while (true) {
			printPrompt(ses);
			if (!in.hasNextLine())
				break;
			String str = in.nextLine();
			byte cmd = translateCmd(str);
			switch (cmd) {
				case -1:
					return null;
				case Protocol.CMD_USER:
					return new MessageUser();
				case Protocol.CMD_PING:
					return new MessagePing();
				case Protocol.CMD_CHALLENGE:
					return challengeTo(in, ses);
				case Protocol.CMD_CHALLENGE_RESPONSE:
					return answerChallenge(in, ses);
				case Protocol.CMD_READY:
					return ready(ses);
				case Protocol.CMD_MOVE:
					return move(in, ses);
				case Protocol.CMD_FORFEIT:
					return forfeit(ses);
				case Protocol.CMD_GET_FIELD:
					return getField(ses);
				case Protocol.CMD_SHIP_PLACE:
					return placeShips(in, ses);
				case 0:
					continue;
				default:
					log("CLIENT", "Unknow command!");
					continue;
			}
		}
		return null;
	}

	static TreeMap<String, Byte> commands = new TreeMap<String, Byte>();
	static {
		commands.put("q", new Byte((byte) -1));
		commands.put("quit", new Byte((byte) -1));
		commands.put("u", new Byte(Protocol.CMD_USER));
		commands.put("users", new Byte(Protocol.CMD_USER));
		commands.put("ping", new Byte(Protocol.CMD_PING));
		commands.put("ch", new Byte(Protocol.CMD_CHALLENGE));
		commands.put("challenge", new Byte(Protocol.CMD_CHALLENGE));
		commands.put("a2ch", new Byte(Protocol.CMD_CHALLENGE_RESPONSE));
		commands.put("answer_to_challenge", new Byte(Protocol.CMD_CHALLENGE_RESPONSE));
		commands.put("plsh", new Byte(Protocol.CMD_SHIP_PLACE));
		commands.put("place_ships", new Byte(Protocol.CMD_SHIP_PLACE));
		commands.put("ready", new Byte(Protocol.CMD_READY));
		commands.put("move", new Byte(Protocol.CMD_MOVE));
		commands.put("forfeit", new Byte(Protocol.CMD_FORFEIT));
		commands.put("gf", new Byte(Protocol.CMD_GET_FIELD));
		commands.put("get_field", new Byte(Protocol.CMD_GET_FIELD));
	}

	static byte translateCmd(String str) {
		// returns -1-quit, 0-invalid cmd, Protocol.CMD_XXX
		str = str.trim();
		Byte r = commands.get(str);
		return (r == null ? 0 : r.byteValue());
	}

	static void printPrompt(Session ses) {
		System.out.println();
		System.out.println("==========================================");
		showHint(ses);
		System.out.println("==========================================");
		System.out.print("Enter command > ");
		System.out.flush();
	}

	static void showHint(Session ses) {
		System.out.println("\n--- COMMAND HINT ---");
		
		if (ses.currentSessionId == null) {
			System.out.println("Available commands:");
			System.out.println("  users (u)        - Show online users");
			System.out.println("  challenge (ch)   - Challenge another player");
			System.out.println("  a2ch             - Answer to challenge");
			System.out.println("  ping             - Test connection");
			System.out.println("  quit (q)         - Exit game");
			return;
		}
		
		if (!ses.shipsPlaced) {
			System.out.println("You are in a game with: " + ses.opponentNic);
			System.out.println("Available commands:");
			System.out.println("  place_ships (plsh)      - Place your ships manually");
			System.out.println("  place_ships random      - Place ships randomly");
			System.out.println("  forfeit                 - Surrender the game");
			return;
		}
		
		if (!ses.gameStarted) {
			System.out.println("Ships placed! Waiting for opponent...");
			System.out.println("Available commands:");
			System.out.println("  ready           - Confirm you are ready to play");
			System.out.println("  get_field (gf)  - View your field");
			System.out.println("  forfeit         - Surrender the game");
			return;
		}
		
		if (ses.myTurn) {
			System.out.println("*** YOUR TURN ***");
			System.out.println("Make a move: move x y");
			System.out.println("Example: move 5 3");
		} else {
			System.out.println("Opponent's turn... Waiting for their move");
			System.out.println("You can use: get_field (gf) - to view your field");
		}
		
		System.out.println("\nCommon commands:");
		System.out.println("  get_field (gf)  - View your field");
		System.out.println("  forfeit         - Surrender the game");
	}

	static boolean processCommand(Session ses, Message msg,
			ObjectInputStream is, ObjectOutputStream os)
			throws IOException, ClassNotFoundException {
		if (msg != null) {
			os.writeObject(msg);
			return true;
		}
		return false;
	}

	static void printUsers(MessageUserResult m) {
		if (m.getNics() != null) {
			System.out.println("Users {");
			for (String str : m.getNics()) {
				System.out.println("\t" + str);
			}
			System.out.println("}");
		}
	}

	static Message challengeTo(Scanner in, Session ses) {
		System.out.print("Enter opponent nick: ");
		String toNic = in.nextLine().trim();

		if (toNic.isEmpty()) {
			log("CLIENT", "Nick cannot be empty");
			return null;
		}

		return new MessageChallenge(ses.userNic, toNic);
	}

	static Message answerChallenge(Scanner in, Session ses) {

		if (ses.pendingChallenges.isEmpty()) {
			log("CLIENT", "No active challenges");
			return null;
		}

		System.out.println("Usage: a2ch <yes/no> [challengeId]");
		System.out.print("> ");
		String[] parts = in.nextLine().trim().split("\\s+");

		if (parts.length < 1 || parts.length > 2) {
			log("CLIENT", "Invalid format");
			return null;
		}

		boolean accepted;
		if (parts[0].equalsIgnoreCase("yes"))
			accepted = true;
		else if (parts[0].equalsIgnoreCase("no"))
			accepted = false;
		else {
			log("CLIENT", "Invalid answer (must be yes or no)");
			return null;
		}

		long id;
		if (parts.length == 2) {
			try {
				id = Long.parseLong(parts[1]);
			} catch (NumberFormatException e) {
				log("CLIENT", "Invalid challenge ID");
				return null;
			}

			if (!ses.pendingChallenges.contains(id)) {
				log("CLIENT", "No such challenge ID: " + id);
				return null;
			}

			ses.pendingChallenges.remove(id);
			return new MessageChallengeResponse(id, accepted);
		}

		id = ses.pendingChallenges.get(ses.pendingChallenges.size() - 1);
		ses.pendingChallenges.remove(ses.pendingChallenges.size() - 1);

		return new MessageChallengeResponse(id, accepted);
	}

	static Message placeShips(Scanner in, Session ses) {

		if (ses.currentSessionId == null) {
			log("CLIENT", "You are not in a session");
			return null;
		}

		System.out.println("Enter ships in format:");
		System.out.println("  x y len vert(0-hor / 1-vert)");
		System.out.println("Or type: random");
		System.out.println("--------------------------------");

		List<Ship> list = new ArrayList<>();

		String firstInput = in.nextLine().trim();

		if (firstInput.equalsIgnoreCase("random") || firstInput.equalsIgnoreCase("r")) {

			try {
				list = randomShips();
				System.out.println("Random ships successfully placed!");
			} catch (Exception e) {
				System.out.println("Random placement error: " + e.getMessage());
				return null;
			}

			return new MessagePlaceShips(ses.userNic, ses.currentSessionId, list);
		}

		if (!firstInput.isEmpty()) {
			String[] p = firstInput.split("\\s+");

			try {
				int x = Integer.parseInt(p[0]);
				int y = Integer.parseInt(p[1]);
				int len = Integer.parseInt(p[2]);
				boolean vert = p[3].equals("1");
				list.add(new Ship(x, y, len, vert));
				System.out.println("Ship added");
			} catch (Exception e) {
				System.out.println("Error: " + e.getMessage());
			}
		}

		while (true) {
			String line = in.nextLine().trim();
			if (line.isEmpty())
				break;

			String[] p = line.split("\\s+");
			if (p.length != 4) {
				System.out.println("Format must be: x y len vert");
				continue;
			}

			try {
				int x = Integer.parseInt(p[0]);
				int y = Integer.parseInt(p[1]);
				int len = Integer.parseInt(p[2]);
				boolean vert = p[3].equals("1");
				Ship ship = new Ship(x, y, len, vert);
				list.add(ship);

				System.out.println("Ship added: (" + x + "," + y +
						") len=" + len + " " +
						(vert ? "vertical" : "horizontal"));
			} catch (Exception e) {
				System.out.println("Error: " + e.getMessage());
			}
		}

		if (list.isEmpty()) {
			log("CLIENT", "No ships entered");
			return null;
		}

		try {
			System.out.println("Checking ships...");
			if (!Player.checkShips(list)) {
				System.out.println("Placement INVALID!");
				return null;
			}
			System.out.println("Placement OK!");
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
			return null;
		}

		return new MessagePlaceShips(ses.userNic, ses.currentSessionId, list);
	}

	static List<Ship> randomShips() {

		int[][] field = new int[12][12];
		List<Ship> result = new ArrayList<>();

		int[] sizes = { 4, 3, 3, 2, 2, 2, 1, 1, 1, 1 };

		for (int len : sizes) {
			boolean placed = false;

			for (int attempt = 0; attempt < 1000 && !placed; attempt++) {
				int x = 1 + (int) (Math.random() * 10);
				int y = 1 + (int) (Math.random() * 10);
				boolean vert = Math.random() < 0.5;

				try {
					Ship s = new Ship(x, y, len, vert);

					if (!canPlace(field, s))
						continue;

					placeOnField(field, s);
					result.add(s);
					placed = true;

				} catch (Exception ignore) {
				}
			}

			if (!placed) {
				throw new RuntimeException("Unable to place random ships");
			}
		}

		return result;
	}

	static boolean canPlace(int[][] field, Ship s) {

		int x = s.getX();
		int y = s.getY();
		int len = s.getLength();
		boolean vert = s.getOrientation() == Ship.Orientation.vertical;

		for (int i = 0; i < len; i++) {
			int cx = x + (vert ? 0 : i);
			int cy = y + (vert ? i : 0);

			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++) {
					int nx = cx + dx;
					int ny = cy + dy;
					if (nx >= 1 && nx <= 10 && ny >= 1 && ny <= 10) {
						if (field[nx][ny] != 0)
							return false;
					}
				}
		}

		return true;
	}

	static void placeOnField(int[][] field, Ship s) {
		int x = s.getX();
		int y = s.getY();
		int len = s.getLength();
		boolean vert = s.getOrientation() == Ship.Orientation.vertical;

		for (int i = 0; i < len; i++) {
			int cx = x + (vert ? 0 : i);
			int cy = y + (vert ? i : 0);
			field[cx][cy] = 1;
		}
	}

	static Message ready(Session ses) {
		if (ses.currentSessionId == null) {
			log("CLIENT", "You are not in a session");
			return null;
		}
		return new MessageReadyToPlay(ses.userNic, ses.currentSessionId);
	}

	static Message move(Scanner in, Session ses) {

		if (ses.currentSessionId == null) {
			log("CLIENT", "You are not in a session");
			return null;
		}

		System.out.print("Enter coordinates (x y): ");
		String[] p = in.nextLine().trim().split("\\s+");

		if (p.length != 2) {
			log("CLIENT", "Format: move x y");
			return null;
		}

		try {
			int x = Integer.parseInt(p[0]);
			int y = Integer.parseInt(p[1]);
			return new MessageMove(ses.userNic, ses.currentSessionId, x, y);
		} catch (Exception e) {
			log("CLIENT", "Invalid numbers");
			return null;
		}
	}

	static Message forfeit(Session ses) {
		if (ses.currentSessionId == null) {
			log("CLIENT", "You are not in a session");
			return null;
		}
		return new MessageForfeit(ses.userNic, ses.currentSessionId);
	}

	static Message getField(Session ses) {
		if (ses.currentSessionId == null) {
			log("CLIENT", "You are not in a session");
			return null;
		}
		return new MessageGetField(ses.userNic, ses.currentSessionId);
	}
}

class ClientReceiver extends Thread {

	private final ObjectInputStream is;
	public final ClientMain.Session session;

	public ClientReceiver(ObjectInputStream is, ClientMain.Session session) {
		this.is = is;
		this.session = session;
		setDaemon(true);
	}

	@Override
	public void run() {
		try {
			while (session.connected) {
				Message msg = (Message) is.readObject();
				handleMessage(msg);
			}
		} catch (Exception e) {
			ClientMain.log("CLIENT", "Receiver stopped: " + e.getMessage());
		}
	}

	private void handleMessage(Message msg) {

		switch (msg.getID()) {

			case Protocol.CMD_CHALLENGE_REQUEST:
				MessageChallengeRequest req = (MessageChallengeRequest) msg;
				session.pendingChallenges.add(req.getChallengeId());
				System.out.println("\n=== NEW CHALLENGE ===");
				System.out.println("Player " + req.getFrom() + " challenges you!");
				System.out.println("Challenge ID: " + req.getChallengeId());
				System.out.println("Type: a2ch <yes/no> <challengeId>");
				break;

			case Protocol.CMD_CHALLENGE: // ответ на наш запрос challenge
				MessageChallengeResult cres = (MessageChallengeResult) msg;
				System.out.println("\n=== CHALLENGE ANSWER ===");
				System.out.println("Challenge ID: " + cres.getChallengeId());
				System.out.println(cres.getMessage());
				break;

			case Protocol.CMD_GAME_STARTS:
				MessageGameStart start = (MessageGameStart) msg;

				session.currentSessionId = start.getSessionId();
				session.opponentNic = start.getOppNic();
				session.gameStarted = false;
				session.shipsPlaced = false;

				System.out.println("\n=== GAME STARTED ===");
				System.out.println("Session ID = " + start.getSessionId());
				System.out.println("Your opponent: " + start.getOppNic());
				System.out.println("Place ships: plsh OR plsh random");
				break;

			case Protocol.CMD_SHIP_PLACE:
				MessagePlaceShipsResult psr = (MessagePlaceShipsResult) msg;
				if (psr.Error()) {
					session.shipsPlaced = false;
					System.out.println("Ship placement failed: " + psr.getMessage());
				} else {
					session.shipsPlaced = true;
					System.out.println("Ships placed successfully!");
					System.out.println("Write 'ready' when finished.");
				}
				break;

			case Protocol.CMD_READY:
				// Уведомление о том, что противник готов
				System.out.println("\n=== GAME READY ===");
				session.gameStarted = true;
				
				String toStart = ((MessageReadyToPlay) msg).getFrom();
				System.out.println(toStart);
				if (session.userNic.equals(toStart)) {
					session.myTurn = true;
				}
				else {
					session.myTurn = false;
				}
				break;

			case Protocol.CMD_MOVE:
				MessageMoveResult move = (MessageMoveResult) msg;

				boolean isOurMove = move.getMessage().contains(session.userNic);
				System.out.println(isOurMove);

				if (isOurMove) {
					System.out.println("\n=== MOVE RESULT ===");
					System.out.println("Your move at: (" + move.getX() + ", " + move.getY() + ")");
					System.out.println("Hit: " + (move.getHitted() ? "YES" : "NO"));
					System.out.println("Sunk: " + (move.getSunked() ? "YES" : "NO"));

					if (move.getGameOver()) {
						System.out.println("\n*** GAME OVER ***");
						System.out.println("Winner: " + session.userNic);
						session.myTurn = false;
						session.gameStarted = false;
						break;
					}

					session.myTurn = move.getHitted();  // Если попали - ходим еще раз

					if (move.getEnemyField() != null) {
						System.out.println("\nEnemy field after your move:");
						printField(move.getEnemyField());
					}
				} else {
					// Ход противника
					System.out.println("\n=== OPPONENT'S MOVE ===");
					System.out.println("Opponent moved at: (" + move.getX() + ", " + move.getY() + ")");
					System.out.println("Hit: " + (move.getHitted() ? "YES" : "NO"));
					System.out.println("Sunk: " + (move.getSunked() ? "YES" : "NO"));

					if (move.getGameOver()) {
						System.out.println("\n*** GAME OVER ***");
						System.out.println("Winner: " + session.opponentNic);
						session.myTurn = false;
						session.gameStarted = false;
					} else {
						session.myTurn = !move.getHitted();
						System.out.println("It's your turn now! Use: move x y");
					}
				}
				break;

			case Protocol.CMD_OPPONENT_MOVE:
				// Ход противника
				MessageMoveResult opponentMove = (MessageMoveResult) msg;
				System.out.println("\n=== OPPONENT'S MOVE ===");
				System.out.println("Opponent moved at: (" + opponentMove.getX() + ", " + opponentMove.getY() + ")");
				System.out.println("Hit: " + (opponentMove.getHitted() ? "YES" : "NO"));
				System.out.println("Sunk: " + (opponentMove.getSunked() ? "YES" : "NO"));

				if (opponentMove.getGameOver()) {
					System.out.println("\n*** GAME OVER ***");
					System.out.println("Winner: " + session.opponentNic);
					session.myTurn = false;
					session.currentSessionId = null;
				} else {
					session.myTurn = true; // Теперь ваш ход
					System.out.println("It's your turn now! Use: move x y");
				}
				break;

			case Protocol.CMD_GET_FIELD:
				MessageGetFieldResult gf = (MessageGetFieldResult) msg;
				System.out.println("\n=== YOUR FIELD ===");
				printField(gf.getField());
				break;

			case Protocol.CMD_GAMEOVER:
				MessageGameOver over = (MessageGameOver) msg;
				System.out.println("\n=== GAME OVER ===");
				System.out.println("Winner: " + over.getWinnerNic());
				if (over.getWinnerNic().equals(session.userNic)) {
					System.out.println("Congratulations! You won!");
				} else {
					System.out.println("You lost. Better luck next time!");
				}
				session.currentSessionId = null;
				session.gameStarted = false;
				session.shipsPlaced = false;
				session.myTurn = false;
				break;

			case Protocol.CMD_PONG:
				System.out.println("[PONG RECEIVED]");
				break;

			case Protocol.CMD_USER:
				// Ответ на запрос списка пользователей
				MessageUserResult userResult = (MessageUserResult) msg;
				if (userResult.Error()) {
					System.out.println("Error getting users: " + userResult.getMessage());
				} else {
					System.out.println("\n=== ONLINE USERS ===");
					if (userResult.getNics() != null && userResult.getNics().length > 0) {
						for (String user : userResult.getNics()) {
							System.out.println(" - " + user);
						}
					} else {
						System.out.println("No other users online");
					}
				}
				break;

			case Protocol.CMD_CONNECT:
				MessageConnectResult connectResult = (MessageConnectResult) msg;
				if (connectResult.Error()) {
					System.out.println("Connection error: " + connectResult.getMessage());
					session.connected = false;
				}
				break;

			case Protocol.CMD_DISCONNECT:
				System.out.println("\n=== DISCONNECTED ===");
				System.out.println("Server disconnected");
				session.connected = false;
				break;

			case Protocol.CMD_FORFEIT:
				System.out.println("\n=== OPPONENT FORFEITED ===");
				System.out.println("Your opponent has forfeited the game!");
				System.out.println("You win!");
				session.currentSessionId = null;
				session.gameStarted = false;
				session.shipsPlaced = false;
				session.myTurn = false;
				break;

			case Protocol.CMD_ERROR:
				MessageError er = (MessageError) msg;
				System.out.println("ERROR: " + er.getMessage());
				break;

			default:
				System.out.println("Incoming message id=" + msg.getID());
		}
		showHint();
	}

	private void showHint() {

		System.out.println("\n--- HINT ---");

		if (!session.connected) {
			System.out.println("Disconnected from server");
			return;
		}

		// Нет активной сессии
		if (session.currentSessionId == null) {
			System.out.println("You are not in a game. Use: ch <nic> to challenge someone.");
			System.out.println("Available: users, challenge, ping, quit");
			return;
		}

		// Есть сессия, но корабли не размещены
		if (!session.shipsPlaced) {
			System.out.println("Place your ships: plsh OR plsh random");
			System.out.println("Available: plsh, plsh random, forfeit");
			return;
		}

		// Корабли размещены, но игра не началась (не готовы)
		if (!session.gameStarted) {
			System.out.println("Ships placed! Type 'ready' when you're ready to play.");
			System.out.println("Available: ready, get_field, forfeit");
			return;
		}

		// Игра активна
		if (session.myTurn) {
			System.out.println("It's YOUR TURN! Make a move: move x y");
			System.out.println("Available: move, get_field, forfeit");
		} else {
			System.out.println("Opponent's turn. Wait for their move...");
			System.out.println("Available: get_field, forfeit");
		}
	}

	static void printField(int[][] f) {
		if (f == null) {
			System.out.println("(no field data)");
			return;
		}

		System.out.println("    1 2 3 4 5 6 7 8 9 10");
		System.out.println("   ----------------------");

		for (int y = 1; y <= 10; y++) {
			System.out.printf("%2d | ", y);
			for (int x = 1; x <= 10; x++) {
				int v = f[x][y];
				char c = switch (v) {
					case Player.SHIP -> '#'; // ship
					case Player.HITTED -> 'X'; // hit
					case Player.MISS -> 'o'; // miss
					default -> '.';
				};
				System.out.print(c + " ");
			}
			System.out.println();
		}
	}
}