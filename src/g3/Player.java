package g3;

import java.util.*;

import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;
import weka.classifiers.lazy.IBk;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import static java.lang.Math.*;



public class Player extends sim.Player {

    private static final int NUM_PLAYERS = 10;

    private ArrayList<Attribute> attributes = new ArrayList<>();
    private Instances[] movesDataset = new Instances[NUM_PLAYERS + 1];

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

        // Define the classification features
        attributes.add(new Attribute("Player score"));
        attributes.add(new Attribute("Opponent score"));
        attributes.add(new Attribute("Leaderboard distance"));
        attributes.add(new Attribute("Reallocated player score"));

        // Initialize array of Instances
        for (int i = 1; i <= NUM_PLAYERS; i++) {
            movesDataset[i] = new Instances("Moves history of player " + i, attributes, 0);
            // Set last attribute as the class label
            movesDataset[i].setClass(attributes.get(attributes.size() - 1));
        }
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
    public List<Game> basicReallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {

        List<Game> reallocatedPlayerGames = new ArrayList<>();

        List<Game> wonGames = getWinningGames(playerGames);
        List<Game> drawnGames = getDrawnGames(playerGames);
        List<Game> lostGames = getLosingGames(playerGames);

        int extraGoals = 0;

        // For won games, leave at least 2 buffer goals
        int bufferWinGoals = 2;
        for (Game game : wonGames) {
            int goalsDifference = game.getNumPlayerGoals() - game.getNumOpponentGoals();

            if (goalsDifference == 1) {
                extraGoals += game.getHalfNumPlayerGoals();
                game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
            }
            else if (goalsDifference > bufferWinGoals) {
                if (goalsDifference - bufferWinGoals > game.getHalfNumPlayerGoals()) {
                    extraGoals += game.getHalfNumPlayerGoals();
                    game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
                }
                else {
                    extraGoals += goalsDifference - bufferWinGoals;
                    game.setNumPlayerGoals(game.getNumOpponentGoals() + bufferWinGoals);
                }

            }
        }

        // For drawn games, first allocate half of all goals to extraGoals
        for (Game game : drawnGames) {
            extraGoals += game.getHalfNumPlayerGoals();
            game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
        }

        // Allocate all points to lost games
        // First, sort lost games by the margin needed to win
        Collections.sort(lostGames, new Comparator<Game>() {
            @Override
            public int compare(Game g1, Game g2) {
                int g1Margin = g1.getNumOpponentGoals() - g1.getNumPlayerGoals();
                int g2Margin = g2.getNumOpponentGoals() - g2.getNumPlayerGoals();

                return g1Margin - g2Margin;
            }
        });

        for (Game game : lostGames) {
            if (extraGoals > 0) {
                int margin = min(game.getNumOpponentGoals() - game.getNumPlayerGoals() + 1, 8-game.getNumPlayerGoals());


                if (extraGoals >= margin) {
                    extraGoals -= margin;
                    game.setNumPlayerGoals(game.getNumPlayerGoals() + margin);
                }
                else {
                    game.setNumPlayerGoals(game.getNumPlayerGoals() + extraGoals);
                    extraGoals = 0;
                }

            }
        }

        reallocatedPlayerGames.addAll(wonGames);
        reallocatedPlayerGames.addAll(drawnGames);
        reallocatedPlayerGames.addAll(lostGames);

        if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
            return reallocatedPlayerGames;
        return playerGames;
    }

    // Source - https://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values?page=1&tab=votes#tab-top
    private static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    private int computeLeaderboardDistance(int player, int opponent, int round, GameHistory gameHistory) {
        Set<Integer> leaderboard = sortByValue(gameHistory.getAllAverageRankingsMap().get(round)).keySet();
        int playerPosition = 0;
        int opponentPosition = 0;
        int index = 0;
        Iterator<Integer> it = leaderboard.iterator();

        while (it.hasNext()) {
            index++;
            int currentPlayer = it.next();
            if (currentPlayer == player)
                playerPosition = index;
            if (currentPlayer == opponent)
                opponentPosition = index;
        };

        return opponentPosition - playerPosition;
    }

    private void updateDataset(Integer round, GameHistory gameHistory) {

        // Get history from last 2 rounds
        Map<Integer, List<Game>> oldGames = gameHistory.getAllGamesMap().get(round-2);
        Map<Integer, List<Game>> newGames = gameHistory.getAllGamesMap().get(round-1);

        // Iterate through all players' history and add their moves to the dataset
        for (int i = 1; i <= NUM_PLAYERS; i++) {
            List<Game> playerOldGames = oldGames.get(i);
            List<Game> playerNewGames = newGames.get(i);

            for (int j = 0; j < playerNewGames.size(); j++) {
                int opponentID = playerOldGames.get(j).getID();

                int playerOldGoals = playerOldGames.get(j).getNumPlayerGoals();
                int opponentOldGoals = playerOldGames.get(j).getNumOpponentGoals();
                int playerNewGoals = playerNewGames.get(j).getNumPlayerGoals();

                // Get leaderboard distance between the 2 players at old round
                int playersDistance = computeLeaderboardDistance(i, opponentID, round-2, gameHistory);

                // Create training instance Xi = [playerOldGoals, opponentOldGoals, playersDistance], yi = playerNewGoals
                Instance newInstance = new DenseInstance(4);
                newInstance.setDataset(movesDataset[i]);
                newInstance.setValue(attributes.get(0), playerOldGoals);
                newInstance.setValue(attributes.get(1), opponentOldGoals);
                newInstance.setValue(attributes.get(2), playersDistance);
                newInstance.setValue(attributes.get(3), playerNewGoals);
                movesDataset[i].add(newInstance);

                if (!playerNewGames.get(j).getID().equals(playerOldGames.get(j).getID()))
                    System.out.println("Different indices!!!");
            }
        }

    }


    public List<Game> knnBasedReallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
        List<Game> reallocatedPlayerGames = new ArrayList<>();

        List<Game> wonGames = getWinningGames(playerGames);
        List<Game> drawnGames = getDrawnGames(playerGames);
        List<Game> lostGames = getLosingGames(playerGames);

        int extraGoals = 0;

        Map<Integer, Double> predictions = new HashMap<>();

        // For each opponent, predict their next allocation
        for (Game game : playerGames) {
            int opponentID = game.getID();

            // Create features instance
            Instance currentInstance = new DenseInstance(3);
            currentInstance.setDataset(movesDataset[opponentID]);
            currentInstance.setValue(attributes.get(0), game.getNumOpponentGoals());
            currentInstance.setValue(attributes.get(1), game.getNumPlayerGoals());

            try {
                currentInstance.setValue(attributes.get(2), computeLeaderboardDistance(opponentID, teamID, round-1, gameHistory));

            } catch (Exception e) {
                System.out.println(e);
            }

            // Train and predict
            IBk knnClassifier = new IBk(3);
            try {
                knnClassifier.buildClassifier(movesDataset[opponentID]);
                double prediction = knnClassifier.classifyInstance(currentInstance);
                predictions.put(opponentID, prediction);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ArrayList<Integer> won1InitialGoals = new ArrayList<>();
        ArrayList<Integer> won2InitialGoals = new ArrayList<>();
        ArrayList<Integer> won3InitialGoals = new ArrayList<>();

        ArrayList<Game> won1 = new ArrayList<Game>();
        ArrayList<Game> won2 = new ArrayList<Game>();
        ArrayList<Game> won3 = new ArrayList<Game>();

        // For won games, if expected < our current, remove goals as to still win, else remove half goals
        for (Game game : wonGames) {
            int prediction = (int) ceil(predictions.get(game.getID()));
            int leaderboardDistace = computeLeaderboardDistance(teamID, game.getID(), round-1, gameHistory);

            if (prediction < game.getNumPlayerGoals()) {
                won1.add(game);
                won1InitialGoals.add((int) game.getNumPlayerGoals());

                int availableGoals = game.getNumPlayerGoals() - prediction - 1;
                extraGoals += availableGoals;
                game.setNumPlayerGoals(game.getNumPlayerGoals() - availableGoals);

            }
            // if expect a tie, but opponent is behind, we can afford to lose this game
            else if ( prediction == game.getNumPlayerGoals() && leaderboardDistace > 2){
                won2.add(game);
                won2InitialGoals.add((int) game.getNumPlayerGoals());

                extraGoals += game.getHalfNumPlayerGoals();
                game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());

            }
            // if expect to lose, take away half
            else if (prediction > game.getNumPlayerGoals()) {
                won3.add(game);
                won3InitialGoals.add((int) game.getNumPlayerGoals());

                extraGoals += game.getHalfNumPlayerGoals();
                game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
            }
        }

        // For drawn games, if opponent is far behind, lose the game
        for (Game game : drawnGames) {
            int prediction = (int) ceil(predictions.get(game.getID()));
            int leaderboardDistace = computeLeaderboardDistance(teamID, game.getID(), round-1, gameHistory);

            // If I expect to win without adding goals, subtract the maximum goals as to still win
            if (prediction < game.getNumPlayerGoals()) {
                int availableGoals = game.getNumPlayerGoals() - prediction - 1;
                extraGoals += availableGoals;
                game.setNumPlayerGoals(game.getNumPlayerGoals() - availableGoals);

                reallocatedPlayerGames.add(game);
            }
            // Else if opponent is far behind, afford to lose
            else if (leaderboardDistace > 2) {
                extraGoals += game.getHalfNumPlayerGoals();
                game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());

                reallocatedPlayerGames.add(game);
            }
        }
        //Remove already reallocated games from drawn games
        for (Game game : reallocatedPlayerGames) {
            drawnGames.remove(game);
        }


        // Merge draws and losses
        ArrayList<Game> remainingGames = new ArrayList<>();
        remainingGames.addAll(drawnGames);
        remainingGames.addAll(lostGames);

        // First, sort lost and draw games by the margin needed to win
        Collections.sort(remainingGames, new Comparator<Game>() {
            @Override
            public int compare(Game g1, Game g2) {
                int g1Prediction = (int) ceil(predictions.get(g1.getID()));
                int g2Prediction = (int) ceil(predictions.get(g2.getID()));

                int g1Margin = g1Prediction - g1.getNumPlayerGoals();
                int g2Margin = g2Prediction - g2.getNumPlayerGoals();

                return g1Margin - g2Margin;
            }
        });

        for (Game game : remainingGames) {
            int prediction = (int) ceil(predictions.get(game.getID()));

            if (extraGoals > 0) {
                // If don't expect to win, allocate more goals
                if (prediction >= game.getNumPlayerGoals()) {
                    int margin = min(prediction - game.getNumPlayerGoals() + 1, 8-game.getNumPlayerGoals());

                    if (extraGoals >= margin) {
                        extraGoals -= margin;
                        game.setNumPlayerGoals(game.getNumPlayerGoals() + margin);
                    }
                    else {
                        game.setNumPlayerGoals(game.getNumPlayerGoals() + extraGoals);
                        extraGoals = 0;
                    }

                }

            }
        }

        boolean updated = true;

        // If have free goals left
        while (extraGoals > 0 && updated) {
            updated = false;
            for (Game game : remainingGames) {
                if (extraGoals > 0 && game.getNumPlayerGoals() < 8) {
                    extraGoals -= 1;
                    game.setNumPlayerGoals(game.getNumPlayerGoals() + 1);
                    updated = true;
                }
            }
        }
        updated = true;
        while (extraGoals > 0 && updated) {
            updated = false;
            for (Game game : reallocatedPlayerGames) {
                if (extraGoals > 0 && game.getNumPlayerGoals() < 8) {
                    extraGoals -= 1;
                    game.setNumPlayerGoals(game.getNumPlayerGoals() + 1);
                    updated = true;
                }
            }
        }
        updated = true;
        while (extraGoals > 0 && updated) {
            updated = false;
            for (int t = 0; t < won1.size(); t++) {
                if (extraGoals > 0 && won1InitialGoals.get(t) > won1.get(t).getNumPlayerGoals()) {
                    extraGoals -= 1;
                    won1.get(t).setNumPlayerGoals(won1.get(t).getNumPlayerGoals() + 1);
                    updated = true;
                }
            }
        }
        updated = true;
        while (extraGoals > 0 && updated) {
            updated = false;
            for (int t = 0; t < won2.size(); t++) {
                if (extraGoals > 0 && won2InitialGoals.get(t) > won2.get(t).getNumPlayerGoals()) {
                    extraGoals -= 1;
                    won2.get(t).setNumPlayerGoals(won2.get(t).getNumPlayerGoals() + 1);
                    updated = true;
                }
            }
        }
        updated = true;
        while (extraGoals > 0 && updated) {
            updated = false;
            for (int t = 0; t < won1.size(); t++) {
                if (extraGoals > 0 && won3InitialGoals.get(t) > won3.get(t).getNumPlayerGoals()) {
                    extraGoals -= 1;
                    won3.get(t).setNumPlayerGoals(won3.get(t).getNumPlayerGoals() + 1);
                    updated = true;
                }
            }
        }

        reallocatedPlayerGames.addAll(remainingGames);
        reallocatedPlayerGames.addAll(wonGames);


        if(ourcheckConstraintsSatisfied(playerGames, reallocatedPlayerGames))
            return reallocatedPlayerGames;
        System.out.println("Unsatisfied constraints in  g6!");
        return playerGames;
    }




    public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
        if (round > 2)
            updateDataset(round, gameHistory);

        if (round > 3)
            return knnBasedReallocate(round, gameHistory, playerGames, opponentGamesMap);

        return basicReallocate(round, gameHistory, playerGames, opponentGamesMap);
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

    private boolean ourcheckConstraintsSatisfied(List<Game> originalPlayerGames, List<Game> reallocatedPlayerGames) {

        Map<Integer, Game> originalPlayerGamesMap = new HashMap<>();
        for(Game originalPlayerGame : originalPlayerGames)
            originalPlayerGamesMap.put(originalPlayerGame.getID(), originalPlayerGame);
        Map<Integer, Game> reallocatedPlayerGamesMap = new HashMap<>();
        for(Game reallocatedPlayerGame : reallocatedPlayerGames)
            reallocatedPlayerGamesMap.put(reallocatedPlayerGame.getID(), reallocatedPlayerGame);

        int totalNumOriginalPlayerGoals = 0, totalNumReallocatedPlayerGoals = 0;
        for(Game originalPlayerGame : originalPlayerGames) {
            if(!reallocatedPlayerGamesMap.containsKey(originalPlayerGame.getID()))
                continue;
            Game reallocatedPlayerGame = reallocatedPlayerGamesMap.get(originalPlayerGame.getID());
            boolean isOriginalWinningGame = hasWonGame(originalPlayerGame);
            boolean isOriginalLosingGame = hasLostGame(originalPlayerGame);
            boolean isOriginalDrawnGame = hasDrawnGame(originalPlayerGame);

            // Constraint 1
            if(reallocatedPlayerGame.getNumPlayerGoals() < 0 || reallocatedPlayerGame.getNumPlayerGoals() > Game.getMaxGoalThreshold()) {
                simPrinter.println("constraint 1-------------------------------------------");
                simPrinter.println(reallocatedPlayerGame.getScoreAsString());
                return false;
            }

            // Constraint 2
            if(!originalPlayerGame.getNumOpponentGoals().equals(reallocatedPlayerGame.getNumOpponentGoals())) {
                simPrinter.println("constraint 2----------------------------------------------------");
                return false;
            }

            // Constraint 3
            boolean numPlayerGoalsIncreased = reallocatedPlayerGame.getNumPlayerGoals() > originalPlayerGame.getNumPlayerGoals();
            if(isOriginalWinningGame && numPlayerGoalsIncreased) {
                simPrinter.println("constraint 3--------------------------------------------");
                return false;
            }

            // Constraint 4
            int halfNumPlayerGoals = originalPlayerGame.getHalfNumPlayerGoals();
            boolean numReallocatedPlayerGoalsLessThanHalf =
                    reallocatedPlayerGame.getNumPlayerGoals() < (originalPlayerGame.getNumPlayerGoals() - halfNumPlayerGoals);
            if((isOriginalWinningGame || isOriginalDrawnGame) && numReallocatedPlayerGoalsLessThanHalf) {
                simPrinter.println("constraint 4---------------------------------");
                simPrinter.println(reallocatedPlayerGame.getNumPlayerGoals());
                simPrinter.println(originalPlayerGame.getNumPlayerGoals() - halfNumPlayerGoals);

                return false;
            }

            totalNumOriginalPlayerGoals += originalPlayerGame.getNumPlayerGoals();
            totalNumReallocatedPlayerGoals += reallocatedPlayerGame.getNumPlayerGoals();

            // Constraint 5
            boolean numPlayerGoalsDecreased = reallocatedPlayerGame.getNumPlayerGoals() < originalPlayerGame.getNumPlayerGoals();
            if(isOriginalLosingGame && numPlayerGoalsDecreased) {
                simPrinter.println("constraint 5------------------------------------------");
                simPrinter.println(originalPlayerGame.getScoreAsString());
                simPrinter.println(reallocatedPlayerGame.getScoreAsString());
                return false;
            }
        }

        // Constraint 6
        if(totalNumOriginalPlayerGoals != totalNumReallocatedPlayerGoals) {
            simPrinter.println("constraint 6---------------------------------------------");
            simPrinter.println("Original Goals: " + Integer.toString(totalNumOriginalPlayerGoals));
            simPrinter.println("Reallocated Goals: " + Integer.toString(totalNumReallocatedPlayerGoals));
            return false;
        }

        return true;
    }
}