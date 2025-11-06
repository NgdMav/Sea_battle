package seaBattle.protocol;

interface CMD {
	static final byte CMD_PING = 1;
	static final byte CMD_PONG = 2;
	static final byte CMD_CONNECT = 3;
	static final byte CMD_DISCONNECT = 4;
	static final byte CMD_USER = 5;
}

interface RESULT {
	static final int RESULT_CODE_OK = 0;
	static final int RESULT_CODE_DECLINE = 1;
	static final int RESULT_CODE_ERROR = -1;
}	

interface PORT {
	static final int PORT = 4242;
}

public class Protocol implements CMD, RESULT, PORT {
	private static final byte CMD_MIN = CMD_PING;
	private static final byte CMD_MAX = CMD_USER;
	
	public static boolean validID( byte id ) {
		return id >= CMD_MIN && id <= CMD_MAX; 
	}
}

