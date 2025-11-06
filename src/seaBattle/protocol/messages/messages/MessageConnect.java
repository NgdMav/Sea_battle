package seaBattle.protocol.messages.messages;

import seaBattle.protocol.messages.Message;
import seaBattle.protocol.Protocol;

public class MessageConnect extends Message {
	
	private static final long serialVersionUID = 1L;
	
	public String userNic;
	public String userFullName;
	
	public MessageConnect( String userNic, String userFullName ) {
		super(Protocol.CMD_CONNECT);
		this.userNic = userNic;
		this.userFullName = userFullName;
	}
	
}