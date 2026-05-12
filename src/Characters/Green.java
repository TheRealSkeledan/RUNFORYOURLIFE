// Green.java

package Characters;

import Abstract.Character;
import java.io.IOException;

public class Green extends Character {
    public Green(int x, int y) throws IOException {
        super("Green", x, y, 5, 4, 3, 4, 0.5);
    }
}