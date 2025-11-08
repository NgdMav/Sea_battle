package seaBattle.gameLogic;

import java.io.Serializable;
import java.security.InvalidParameterException;

public class Ship implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    enum Orientation {
        horizontal,
        vertical
    }

    private int x;
    private int y;
    private int length;
    private Orientation or;

    public Ship(int x, int y, int length, Orientation or) {
        if (x < 1 || x > 10 || y < 1 || y > 10) {
            throw new InvalidParameterException("Wrong placement of ship");
        }
        if (length < 1 || length > 4) {
            throw new InvalidParameterException("Wrong length of ship");
        }
        if (or == Orientation.horizontal) {
            if (x > 11 - length) {
                throw new InvalidParameterException("Ship does not fit");
            }
        }
        else {
            if (y > 11 - length) {
                throw new InvalidParameterException("Ship does not fit");
            }
        }

        this.x = x;
        this.y = y;
        this.length = length;
        this.or = or;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
    
    public int getLength() {
        return length;
    }
    
    public Orientation getOrientation() {
        return or;
    }
}