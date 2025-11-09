package seaBattle.gameLogic;

import java.util.List;

public class GameSession {
    private Player A;
    private Player B;
    private long sessionId;

    public GameSession(long sessionId, String playerANic, String playerBNic, List<Ship> playerAShips, List<Ship> playerBShips) {
        this.sessionId = sessionId;
        if (playerAShips == null) {
            A = new Player(playerANic);
        }
        else {
            A = new Player(playerANic, playerAShips);
        }
        if (playerBShips == null) {
            B = new Player(playerBNic);
        }
        else {
            B = new Player(playerBNic, playerBShips);
        }
    }

    public long getSessionId() {
        return sessionId;
    }

    public Player getPlayerA() {
        return A;
    }

    public Player getPlayerB() {
        return B;
    }
}
