package seaBattle.gameLogic;

import java.util.List;

public class GameSession {
    private Player A;
    private boolean aready;
    private Player B;
    private boolean bready;
    private long sessionId;
    boolean gameRuns;

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
        gameRuns = false;
        aready = false;
        bready = false;
    }

    public void gameStart() {
        gameRuns = true;
    }

    public void gameEnd() {
        gameRuns = false;
        aready = false;
        bready = false;
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

    public boolean setPlaceShip(String nic, List<Ship> ships) {
        if (gameRuns) {
            return false;
        }
        if (A.getNic() == nic) {
            A.setPlaceShips(ships);
        } else if(B.getNic() == nic) {
            A.setPlaceShips(ships);
        } else {
            // throw ex;
            return false;
        }
        return true;
    }

    public boolean playerReady(String nic) {
        if (A.getNic() == nic) {
            aready = true;
        } else if(B.getNic() == nic) {
            bready = true;
        } else {
            // throw ex;
            return false;
        }
        if (aready && bready) {
            gameStart();
        }
        return true;
    }
}
