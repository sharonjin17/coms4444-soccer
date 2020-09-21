package g5;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;

public class Player extends sim.Player {

    /**
     * Player constructor
     *
     * @param teamID      team ID
     * @param rounds      number of rounds
     * @param seed        random seed
     * @param simPrinter  simulation printer
     *
     */
    public Player(Integer teamID, Integer rounds, Integer seed, SimPrinter simPrinter) {
        super(teamID, rounds, seed, simPrinter);
    }

    /**
     * Reallocate player goals
     *
     * @param round             current round
     * @param gameHistory       cumulative game history from all previous rounds
     * @param playerGames       state of player games before reallocation
     * @param opponentGamesMap  state of opponent games before reallocation (map of opponent team IDs to their games)
     * @return                  state of player games after reallocation
     *
     */
    @Override
    public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames,
                                 Map<Integer, List<Game>> opponentGamesMap) {


        ArrayList<Game> reallocatedGames = new ArrayList<Game>();

        List<Game> clonePlayerGames = new ArrayList<Game>();
        for (Game g: playerGames) {
            clonePlayerGames.add(g.cloneGame());
        }

        int excessGoals = 0;
        //System.out.println(this.teamID);

        for (Game game : clonePlayerGames) {
            int playerGoals = game.getNumPlayerGoals();
            int margin = playerGoals - game.getNumOpponentGoals();
            // get win and draw games and retrieve the excess goals
            if (margin >= 0) {
                int subtract = game.getHalfNumPlayerGoals();
                excessGoals += subtract;
                game.setNumPlayerGoals(playerGoals-subtract);
            }
        }

        for (Integer teamID : opponentGamesMap.keySet()) {
            List<Game> opponentGamesList = opponentGamesMap.get(teamID);
            //System.out.println("teamID: "+teamID);
            int adjustedGoal = computeExpectation(opponentGamesList);
            //System.out.println("adjustedGoal: "+adjustedGoal);
            for (Game g : clonePlayerGames) {
                if (g.getID().equals(teamID)) {
                    g.setNumOpponentGoals(adjustedGoal);
                }
            }
        }
        //printGameList(clonePlayerGames);
        Comparator<Game> compGoal = new Comparator<Game>() {

            @Override
            public int compare(Game g1, Game g2) {
                int g1m = g1.getNumPlayerGoals()-g1.getNumOpponentGoals();
                int g2m = g2.getNumPlayerGoals()-g2.getNumOpponentGoals();
                return Math.abs(g1m) - Math.abs(g2m);
            }
        };

        clonePlayerGames.sort(compGoal);

        //System.out.println("ExcessGoals: "+excessGoals);
        int savedGoals1 = excessGoals;
        List<Game> losingAndDraw = new ArrayList<Game>();

        for (Game g : clonePlayerGames) {
            int gm = g.getNumPlayerGoals()-g.getNumOpponentGoals();
            Game origin = findSameGameID(playerGames, g.getID()).cloneGame();
            if (gm > 0) {
                origin.setNumPlayerGoals(g.getNumPlayerGoals());
                reallocatedGames.add(origin);
            }
            else if (excessGoals >= -gm+1) {
                int goals = g.getNumPlayerGoals()-gm+1;
                if (goals > g.getMaxGoalThreshold()) {
                    origin.setNumPlayerGoals(origin.getMaxGoalThreshold());
                    excessGoals -= origin.getMaxGoalThreshold() - origin.getNumPlayerGoals();
                }
                else {
                    origin.setNumPlayerGoals(goals);
                    excessGoals -= -gm+1;
                }
                reallocatedGames.add(origin);
                losingAndDraw.add(origin);
            }
            else {
                origin.setNumPlayerGoals(g.getNumPlayerGoals());
                reallocatedGames.add(origin);
                losingAndDraw.add(origin);
            }
        }
        int savedGoals2 = excessGoals;
        //printGameList(reallocatedGames);
        //System.out.println("ExcessGoals after: "+excessGoals);
        while (excessGoals > 0) {
            for (Game g: losingAndDraw) {
                if (excessGoals <= 0) {
                    break;
                }
                if (g.getNumPlayerGoals() < g.getMaxGoalThreshold()) {
                    g.setNumPlayerGoals(g.getNumPlayerGoals()+1);
                    excessGoals -= 1;
                }
            }
        }

        compGoal = new Comparator<Game>() {

            @Override
            public int compare(Game g1, Game g2) {
                return g1.getID()-g2.getID();
            }
        };
        reallocatedGames.sort(compGoal);



        if(checkConstraintsSatisfied(playerGames, reallocatedGames)) {
            return reallocatedGames;
        }
        //System.out.println("Nothing changed");
        //System.out.println("PlayerGames: ");
        //printGameList(playerGames);
        //System.out.println("ReallocatedGames: ");
        //printGameList(reallocatedGames);
        //System.out.println("ExcessGoals: "+savedGoals1+" "+savedGoals2);
        return playerGames;
    }

    public void printGameList(List<Game> games) {
        System.out.println("\nPrintingGames: ");
        for (Game g: games) {
            System.out.println("GameID: "+g.getID());
            System.out.println("Score: "+g.getScoreAsString());
        }
    }

    public Game findSameGameID(List<Game> games, int gid) {
        for (Game g : games) {
            if (g.getID().equals(gid)) {
                return g;
            }
        }
        return null;
    }

    public int computeExpectation(List<Game> GamesList) {
        List<Game> winningGames = getWinningGames(GamesList);
        List<Game> drawnGames = getDrawnGames(GamesList);
        List<Game> losingGames = getLosingGames(GamesList);
        int adjustedGoal = -1;
        int totalGoals = 0;
        for (Game g : winningGames) {
            int expectSubtractGoals = g.getHalfNumPlayerGoals()/2;
            totalGoals += expectSubtractGoals;
            if (g.getID().equals(this.teamID)) {
                //System.out.println("Previous goals win: "+g.getNumPlayerGoals());
                adjustedGoal = g.getNumPlayerGoals() - expectSubtractGoals;
            }
        }
        int expectPerGame = Math.max(totalGoals/(drawnGames.size()+losingGames.size()), 1);
        //System.out.println("totalGoal: "+totalGoals + " ; expectPerGame: "+expectPerGame);
        int afterGoal = -1;
        for (Game g : losingGames) {
            afterGoal = g.getNumPlayerGoals();
            if (totalGoals >= 0) {
                if (g.getMaxGoalThreshold() - g.getNumPlayerGoals() < expectPerGame) {
                    afterGoal = g.getMaxGoalThreshold();
                    totalGoals -= afterGoal - g.getNumPlayerGoals();
                }
                else {
                    afterGoal = g.getNumPlayerGoals() + expectPerGame;
                    totalGoals -= expectPerGame;
                }
            }
            if (g.getID().equals(this.teamID)) {
                //System.out.println("Previous goals lose: "+g.getNumPlayerGoals());
                adjustedGoal = afterGoal;
            }
        }
        for (Game g : drawnGames) {
            afterGoal = g.getNumPlayerGoals();
            if (totalGoals >= 0) {
                if (g.getMaxGoalThreshold() - g.getNumPlayerGoals() < expectPerGame) {
                    afterGoal = g.getMaxGoalThreshold();
                    totalGoals -= afterGoal - g.getNumPlayerGoals();
                }
                else {
                    afterGoal = g.getNumPlayerGoals() + expectPerGame;
                    totalGoals -= expectPerGame;
                }
            }
            if (g.getID().equals(this.teamID)) {
                //System.out.println("Previous goals draw: "+g.getNumPlayerGoals());
                adjustedGoal = afterGoal;
            }
        }

        return adjustedGoal;
    }

    private List<Game> getWinningGames(List<Game> playerGames) {
        List<Game> winningGames = new ArrayList<>();
        for(Game game : playerGames) {
            int numPlayerGoals = game.getNumPlayerGoals();
            int numOpponentGoals = game.getNumOpponentGoals();
            if(numPlayerGoals > numOpponentGoals)
                winningGames.add(game.cloneGame());
        }
        return winningGames;
    }

    private List<Game> getDrawnGames(List<Game> playerGames) {
        List<Game> drawnGames = new ArrayList<>();
        for(Game game : playerGames) {
            int numPlayerGoals = game.getNumPlayerGoals();
            int numOpponentGoals = game.getNumOpponentGoals();
            if(numPlayerGoals == numOpponentGoals)
                drawnGames.add(game.cloneGame());
        }
        return drawnGames;
    }

    private List<Game> getLosingGames(List<Game> playerGames) {
        List<Game> losingGames = new ArrayList<>();
        for(Game game : playerGames) {
            int numPlayerGoals = game.getNumPlayerGoals();
            int numOpponentGoals = game.getNumOpponentGoals();
            if(numPlayerGoals < numOpponentGoals)
                losingGames.add(game.cloneGame());
        }
        return losingGames;
    }
}
