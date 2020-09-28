package g4;

import java.util.*;

import sim.Game;
import sim.SimPrinter;

public class MovePredictor {

    public Map<Integer, TeamTracker> teamTrackers;
    private SimPrinter simPrinter;

    public MovePredictor(SimPrinter simPrinter) {
        this.teamTrackers = new HashMap<Integer, TeamTracker>();
        this.simPrinter = simPrinter;
    }

    public void quickMethod(String str) {
        simPrinter.println(str);
    }

    public void trackData(Map<Integer, List<Game>> opponentGamesMap) {
        if (teamTrackers.size() == 0) {
            for (Map.Entry<Integer,List<Game>> entry : opponentGamesMap.entrySet()) {
                TeamTracker teamTracker = new TeamTracker(entry.getKey(), entry.getValue());
                teamTrackers.put(entry.getKey(), teamTracker);
            }
        }

        for (Map.Entry<Integer,List<Game>> entry : opponentGamesMap.entrySet()) {
            teamTrackers.get(entry.getKey()).trackRound(entry.getValue());
        }
    }

    public Move getMostProbableNextMove(Game game) {
        int teamId = game.getID();
        Map<Move, Double> nextMoveProbs = getNextMoveProbs(game);
        Move nextMove = Move.NO_CHANGE;
        double prob = 0;
        for (Map.Entry<Move, Double> entry : nextMoveProbs.entrySet()) {
            if (entry.getValue() > prob) {
                nextMove = entry.getKey();
                prob = entry.getValue();
            }
        }
        return nextMove;
    }

    public Map<Move, Double> getNextMoveProbs(Game game) {
        int teamId = game.getID();
        TeamTracker teamTracker = teamTrackers.get(teamId);
        return teamTracker.getNextMoveProbs(game);
    }

    public void printOpponentGames(Map<Integer, List<Game>> opponentGamesMap) {
        for (Map.Entry<Integer,List<Game>> entry : opponentGamesMap.entrySet()) {
            System.out.println("Team ID: " + entry.getKey().toString());
            printGameList(entry.getValue());
        }
    }

    private void printGameList(List<Game> games) {
        for (Game game : games) {
            System.out.print(game.getID().toString() + ": ");
            System.out.print("player: " + game.getNumPlayerGoals().toString());
            System.out.print(", opp: " + game.getNumOpponentGoals().toString());
            System.out.println("");
        }
    }
}
