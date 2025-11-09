package seaBattle.gameLogic;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import seaBattle.gameLogic.Ship.Orientation;

public class Player {
    private String userNic;
    
    private List<Ship> ships;
    private int[][] field;

    public Player(String nic) {
        userNic = nic;
        field = createRandomField();
    }
    
    public Player(String nic, List<Ship> ships) {
        checkShips(ships);
        userNic = nic;
        field = createField(ships);
    }

    public boolean checkShips(List<Ship> ships) {
        int[][] f = new int[12][12];
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 12; j++) {
                f[i][j] = 0;
            }
        }
        int count1 = 0;
        int count2 = 0;
        int count3 = 0;
        int count4 = 0;
        if (ships.size() != 10) {
            throw new InvalidParameterException("Wrond number of ships");
        }
        for (Ship s : ships) {
            switch (s.getLength()) {
                case 1:
                    count1++;
                    break;
                case 2:
                    count2++;
                    break;
                case 3:
                    count3++;
                    break;
                case 4:
                    count4++;
                    break;
                default:
                    throw new InvalidParameterException("Unknown ship type");
            }

            if (count1 > 4 || count2 > 3 || count3 > 2 || count4 > 1) {
                throw new InvalidParameterException("Wrond number of ships");
            }

            List<int[]> que = new ArrayList<>();

            for (int i = 0; i < s.getLength(); i++) {
                int x;
                int y;
                if (s.getOrientation() == Orientation.horizontal) {
                    x = s.getX() + i;
                    y = s.getY();
                }
                else {
                    x = s.getX();
                    y = s.getY() + i;
                }

                if (f[x][y] != 0) {
                    throw new InvalidParameterException("Wrong ship placement");
                }

                f[x][y] = 1;

                que.add(new int[]{x - 1, y - 1});
                que.add(new int[]{x - 1, y});
                que.add(new int[]{x - 1, y + 1});
                que.add(new int[]{x, y - 1});
                que.add(new int[]{x, y + 1});
                que.add(new int[]{x + 1, y - 1});
                que.add(new int[]{x + 1, y});
                que.add(new int[]{x + 1, y + 1});
            }
            for (int[] coord : que) {
                f[coord[0]][coord[1]] = 1;
            }
        }
        return true;
    }

    private int[][] createField(List<Ship> ships) {
        int[][] resField = new int[12][12];
        for (Ship s : ships) {
            for (int i = 0; i < s.getLength(); i++) {
                int x;
                int y;
                if (s.getOrientation() == Orientation.horizontal) {
                    x = s.getX() + i;
                    y = s.getY();
                }
                else {
                    x = s.getX();
                    y = s.getY() + i;
                }

                resField[x][y] = 1;
            }
        }
        return resField;
    }

    private int[][] createRandomField() {
        int[][] resField = new int[12][12];
        return resField;
    }

    public String getNic() {
        return userNic;
    }

    public List<Ship> getShips() {
        return ships;
    }

    public int[][] getField() {
        return field;
    }
}
