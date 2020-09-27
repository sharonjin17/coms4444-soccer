package g4;

import sim.Game;

import java.util.*;

public class TeamTracker {
    private int teamId;
    private double[][] pointChangeProbDist;
    // point difference => change in player points next game
    private Map<Integer, ArrayList<Move>> rawPointChangeData;
    private Map<Integer, Game> pastGames;

    public TeamTracker(Integer teamId, List<Game> games) {
        this.teamId = teamId;
        this.pointChangeProbDist = initPointChangeProbDist();
        this.rawPointChangeData = new HashMap<Integer, ArrayList<Move>>();
        this.pastGames = new HashMap<Integer, Game>();
        for (Game game : games) {
            this.pastGames.put(game.getID(), game);
        }
    }

    public Map<Move, Double> getNextMoveProbs(Game game) {
        int pointDiff = game.getNumPlayerGoals() - game.getNumOpponentGoals();
        int pointDiffIndex = pointDiff + 8;
        Map<Move, Double> actionProbs = new HashMap<Move, Double>();
        for (Move pc : Move.values()) {
            actionProbs.put(pc, pointChangeProbDist[pointDiffIndex][pc.getValue()]);
        }
        return actionProbs;
    }

    public void trackRound(List<Game> games) {
        Set<Integer> updated = new HashSet<Integer>();
        for (Game game : games) {
            int pointDiff = collectGameData(game);
            updated.add(pointDiff);
        }
        recalcPointChangeProbs(updated);
    }

    // returns the game point difference
    // Used to see what point difference probabilities should be updated
    private int collectGameData(Game game) {
        Game pastGame = pastGames.get(game.getID());
        int numPointsChanged = game.getNumPlayerGoals() - pastGame.getNumPlayerGoals();
        Move move = Move.NO_CHANGE;
        if (numPointsChanged > 0) {
            move = Move.INCREASE;
        }
        else if (numPointsChanged < 0) {
            move = Move.DECREASE;
        }

        int pointDiff = pastGame.getNumPlayerGoals() - pastGame.getNumOpponentGoals();
        if (!rawPointChangeData.containsKey(pointDiff)) {
            ArrayList<Move> changes = new ArrayList<Move>();
            rawPointChangeData.put(pointDiff, changes);
        }
        rawPointChangeData.get(pointDiff).add(move);
        return pointDiff;
    }

    private void recalcPointChangeProbs(Set<Integer> updated) {
        for (int pointDiff : updated) {
            pointDiff += 8;
            double probSum = 0;
            for (Move pc : Move.values()) {
                double currentProb = pointChangeProbDist[pointDiff][pc.getValue()];
                int pcCount = Collections.frequency(rawPointChangeData.get(pointDiff - 8), pc);
                double newProb = (double) pcCount / pointChangeProbDist[pointDiff].length;
                pointChangeProbDist[pointDiff][pc.getValue()] = (currentProb + newProb)/2.0;
                probSum += pointChangeProbDist[pointDiff][pc.getValue()];
            }

            // normalize prob dist
            for (Move pc : Move.values()) {
                pointChangeProbDist[pointDiff][pc.getValue()] /= probSum;
            }
        }
    }

    // initialPD[x][0] = prob increase points given point difference x
    // initialPD[x][1] = prob no change in points given point difference x
    // initialPD[x][2] = prob decrease in points given point difference x
    // x is offset by 8, so if the point difference is -8, x = 0
    private double[][] initPointChangeProbDist() {
        double[][] initialPD = new double[17][3];
        for (int i=0; i < 8; i++) {
            initialPD[i][0] = 0.5; // prob add when player is losing
            initialPD[i][1] = 0.5; // prob no change when player is losing
            initialPD[i][2] = 0; // prob subtract (when losing player is not allowed to subtract)
        }

        // init draw response
        for (int i = 0; i < initialPD[8].length; i++) {
            initialPD[8][i] = 0.33;
        }

        for (int i= 9; i < initialPD.length; i++) {
            initialPD[i][0] = 0; // prob add when player is winning
            initialPD[i][1] = 0.5; // prob no change when player is winning
            initialPD[i][2] = 0.5; // prob subtract when player is winning
        }
        return initialPD;
    }
}
