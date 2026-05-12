// Red.java

package Characters;

import Abstract.Character;
import java.io.IOException;

public class Red extends Character {
    public Red(int x, int y) throws IOException {
        super("Red", x, y, 6, 5, 2, 3, 0.3, "bullet");
    }
}