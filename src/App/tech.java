package App;

public class tech {
    public boolean locked = true;
    public int cost;
    public String name;

    public tech(boolean lock, int initCost, String n) {
        locked = lock;
        cost = initCost;
        name = n;
    }

    public double updateWorker() {

        return cost *= 1.05;
    }

}
