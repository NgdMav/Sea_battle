package seaBattle.protocol.messages.messagesResult;

import seaBattle.protocol.Protocol;
import seaBattle.protocol.messages.MessageResult;

public class MessagePlaceShipsResult extends MessageResult {
    
	private static final long serialVersionUID = 1L;
	
	public MessagePlaceShipsResult() {
		super(Protocol.CMD_SHIP_PLACE);
	}

	public MessagePlaceShipsResult(boolean isGood, String message) {
		super(Protocol.CMD_SHIP_PLACE, isGood, message);
	}

}
