package g4;

public enum Move {
    INCREASE(0),
    NO_CHANGE(1),
    DECREASE(2);

    private final int value;

    Move(int value) {
        this.value = value;
    }

    public int getValue() { return value; }
}