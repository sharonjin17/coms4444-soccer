package g12;

import java.util.*;
import g12.MultipleLinearRegression;

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

     public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
          if(round < 10) return firstTen(round, gameHistory, playerGames, opponentGamesMap);
          return regressionReallocation(round, gameHistory, playerGames, opponentGamesMap);
     }

     private List<Game> regressionReallocation(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
          List<Game> reallocatedPlayerGames = new ArrayList<>();
          Map<Integer, MultipleLinearRegression> regressionMap = new HashMap<Integer, MultipleLinearRegression>();

          List<Game> wonGames = getWinningGames(playerGames);
          List<Game> drawnGames = getDrawnGames(playerGames);
          List<Game> lostGames = getLosingGames(playerGames);

          Map<Integer, Double> rankedMap = new HashMap<Integer, Double>();
          Map<Integer, String> rankedMapS = new HashMap<Integer, String>();

          if(!gameHistory.getAllGamesMap().isEmpty() && !gameHistory.getAllAverageRankingsMap().isEmpty()) {
               List<Double> averageRank = new ArrayList<Double>(gameHistory.getAllAverageRankingsMap().get(round-1).values());
               for(int i = 0; i < 9; i++) {
                    int opoID = i;
                    if(i >= teamID) opoID = opoID + 1;
                    Double opoRank = averageRank.get(opoID);
                    Double ourRank = averageRank.get(teamID);
                    rankedMap.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),(Math.abs(ourRank-opoRank)));
                    rankedMapS.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getScoreAsString());
               }
               //System.out.println(rankedMapS);
          }

          System.out.println(round);

          int goalBank = 0;

          for(Game game : wonGames) {
               goalBank += game.getHalfNumPlayerGoals();
          }
          for(Game game : drawnGames) {
               goalBank += game.getHalfNumPlayerGoals();
          }
          for(Game game : lostGames) {
               goalBank += game.getHalfNumPlayerGoals();
          }

//          for(int i = 0; i < 9; i++) {
//               System.out.println(gameHistory.getAllGamesMap().get(round-1).get(0).get(i).getScoreAsString());
//               System.out.println(gameHistory.getAllGamesMap().get(round-1).get(teamID).get(i).getScore().getNumPlayerGoals());
//          }

//          for(int i = 1; i < 11; i++) {
//               MultipleLinearRegression regression = getTeamRegression(round, gameHistory, i);
//               System.out.printf("%.2f + %.2f beta1 + %.2f beta2 + %.2f beta3  (R^2 = %.2f)\n",
//                       regression.beta(0), regression.beta(1), regression.beta(2), regression.beta(3), regression.R2());
//          }

          for(Game game : playerGames) {
               regressionMap.put(game.getID(), getTeamRegression(round, gameHistory, game.getID()));
          }

          Comparator<Game> R2Comparator = (Game g1, Game g2) ->
          {return (int) Math.round((regressionMap.get(g1.getID()).R2() - regressionMap.get(g2.getID()).R2())*1000);};

          Collections.sort(lostGames, R2Comparator.reversed());
          Collections.sort(drawnGames, R2Comparator.reversed());

          int usedGoals = 0;
          int margin = 1;
          int maxMargin = 2;
          double accuracyThresh = 0.80;
          double accuracyMax = 0.95;
          int discrete = 0;
          for(Game game : lostGames) {
               int range = game.getNumPlayerGoals()-game.getNumOpponentGoals();
               if(game.getNumPlayerGoals() < game.getNumOpponentGoals()) discrete=0;
               if(game.getNumPlayerGoals() > game.getNumOpponentGoals()) discrete=3;
               if(game.getNumPlayerGoals() == game.getNumOpponentGoals()) discrete=1;
               //System.out.println(usedGoals);
               double prediction = regressionMap.get(game.getID()).beta(0) + regressionMap.get(game.getID()).beta(1) * game.getNumOpponentGoals()
                       + regressionMap.get(game.getID()).beta(2) * game.getNumPlayerGoals() + regressionMap.get(game.getID()).beta(3) * discrete;
               System.out.println(game.getScoreAsString() + " " + (int) Math.round(prediction) + " PERDI " + range);
               System.out.printf("%.2f + %.2f beta1 + %.2f beta2 + %.2f beta3  (R^2 = %.2f)\n",
                       regressionMap.get(game.getID()).beta(0), regressionMap.get(game.getID()).beta(1), regressionMap.get(game.getID()).beta(2),
                       regressionMap.get(game.getID()).beta(3), regressionMap.get(game.getID()).R2());
               if(goalBank > ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin) && ((int) Math.round(prediction) + margin) <= 8
               && (Math.abs(range) < maxMargin) && regressionMap.get(game.getID()).R2() > accuracyThresh) {
                    goalBank -= ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    usedGoals += ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    game.setNumPlayerGoals((int) Math.round(prediction) + margin);
               }
               else if(regressionMap.get(game.getID()).R2() >= accuracyMax &&
                       goalBank > ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin) && ((int) Math.round(prediction) + margin) <= 8) {
                    goalBank -= ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    usedGoals += ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    game.setNumPlayerGoals((int) Math.round(prediction) + margin);
               }
               else if(range > 3 || game.getNumPlayerGoals() > 6) {
                    usedGoals -= game.getNumPlayerGoals()-game.getHalfNumPlayerGoals();
                    game.setNumPlayerGoals(game.getHalfNumPlayerGoals());
               }
          }
          for(Game game : drawnGames) {
               int range = game.getNumPlayerGoals()-game.getNumOpponentGoals();
               if(game.getNumPlayerGoals() < game.getNumOpponentGoals()) discrete=0;
               else if(game.getNumPlayerGoals() > game.getNumOpponentGoals()) discrete=3;
               else if(game.getNumPlayerGoals() == game.getNumOpponentGoals()) discrete=1;
               double prediction = regressionMap.get(game.getID()).beta(0) + regressionMap.get(game.getID()).beta(1) * game.getNumOpponentGoals()
                       + regressionMap.get(game.getID()).beta(2) * game.getNumPlayerGoals() + regressionMap.get(game.getID()).beta(3) * discrete;
               System.out.println(game.getScoreAsString() + " " + (int) Math.round(prediction) + " EMPATEI");
               System.out.printf("%.2f + %.2f beta1 + %.2f beta2 + %.2f beta3  (R^2 = %.2f)\n",
                       regressionMap.get(game.getID()).beta(0), regressionMap.get(game.getID()).beta(1), regressionMap.get(game.getID()).beta(2),
                       regressionMap.get(game.getID()).beta(3), regressionMap.get(game.getID()).R2());
               if(goalBank > ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin) && ((int) Math.round(prediction) + margin) <= 8
               && regressionMap.get(game.getID()).R2() > accuracyThresh) {
                    goalBank -= ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    usedGoals += ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    game.setNumPlayerGoals((int) Math.round(prediction) + margin);
               }
               else if(regressionMap.get(game.getID()).R2() >= accuracyMax &&
                       goalBank > ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin) && ((int) Math.round(prediction) + margin) <= 8) {
                    goalBank -= ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    usedGoals += ((int) Math.round(prediction) - game.getNumPlayerGoals() + margin);
                    game.setNumPlayerGoals((int) Math.round(prediction) + margin);
               }
               else if(game.getNumPlayerGoals() > 5 || (prediction == 8 && regressionMap.get(game.getID()).R2() >= accuracyMax)) {
                    usedGoals -= game.getNumPlayerGoals()-game.getHalfNumPlayerGoals();
               }
          }

          Comparator<Game> rangeComparatorWon = (Game g1, Game g2) ->
          {return (g1.getNumPlayerGoals()) - (g2.getNumPlayerGoals());};

          Comparator<Game> rangeComparatorRank = (Game g1, Game g2) ->
          {return (int) Math.round((rankedMap.get(g1.getID()) - rankedMap.get(g2.getID()))*1000);};

          //Collections.sort(wonGames, rangeComparatorRank.reversed());

          Collections.sort(wonGames, rangeComparatorWon.reversed());

          for(Game game : wonGames) {
               int range = game.getNumPlayerGoals()-game.getNumOpponentGoals();
               if(game.getNumPlayerGoals() < game.getNumOpponentGoals()) discrete=0;
               else if(game.getNumPlayerGoals() > game.getNumOpponentGoals()) discrete=3;
               else if(game.getNumPlayerGoals() == game.getNumOpponentGoals()) discrete=1;
               double prediction = regressionMap.get(game.getID()).beta(0) + regressionMap.get(game.getID()).beta(1) * game.getNumOpponentGoals()
                       + regressionMap.get(game.getID()).beta(2) * game.getNumPlayerGoals() + regressionMap.get(game.getID()).beta(3) * discrete;
               System.out.println(game.getScoreAsString() + " " + (int) Math.round(prediction) + " GANHEI");
               System.out.printf("%.2f + %.2f beta1 + %.2f beta2 + %.2f beta3  (R^2 = %.2f)\n",
                       regressionMap.get(game.getID()).beta(0), regressionMap.get(game.getID()).beta(1), regressionMap.get(game.getID()).beta(2),
                       regressionMap.get(game.getID()).beta(3), regressionMap.get(game.getID()).R2());
               if(usedGoals > 0) {
                    usedGoals -= game.getNumPlayerGoals()-game.getHalfNumPlayerGoals();
                    game.setNumPlayerGoals(game.getHalfNumPlayerGoals());
               }
          }

          System.out.println(checkConstraintsSatisfiedTest(playerGames, reallocatedPlayerGames));

          reallocatedPlayerGames.addAll(wonGames);
          reallocatedPlayerGames.addAll(drawnGames);
          reallocatedPlayerGames.addAll(lostGames);

          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)) {
               //System.out.println("ola");
               return reallocatedPlayerGames;
          }
          return playerGames;
          //return firstTen(round, gameHistory, playerGames, opponentGamesMap);
     }



     private List<Game> firstTen(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {

          List<Game> reallocatedPlayerGames = new ArrayList<>();

          Map<Integer, Double> rankedMap = new HashMap<Integer, Double>();
          Map<Integer, String> rankedMapS = new HashMap<Integer, String>();

          List<Game> wonGames = getWinningGames(playerGames);
          List<Game> drawnGames = getDrawnGames(playerGames);
          List<Game> lostGames = getLosingGames(playerGames);

          List<Game> lostOrDrawnGamesWithReallocationCapacity = new ArrayList<>(lostGames);
          lostOrDrawnGamesWithReallocationCapacity.addAll(drawnGames);
          for(Game lostGame : lostGames) {
               if (lostGame.maxPlayerGoalsReached()) {
                    lostOrDrawnGamesWithReallocationCapacity.remove(lostGame);
               }
          }
          for(Game drawnGame : drawnGames) {
               if (drawnGame.maxPlayerGoalsReached()) {
                    lostOrDrawnGamesWithReallocationCapacity.remove(drawnGame);
               }
          }

          if(!gameHistory.getAllGamesMap().isEmpty() && !gameHistory.getAllAverageRankingsMap().isEmpty()) {
               List<Double> averageRank = new ArrayList<Double>(gameHistory.getAllAverageRankingsMap().get(round-1).values());
               for(int i = 0; i < 9; i++) {
                    int opoID = i;
                    if(i >= teamID) opoID = opoID + 1;
                    Double opoRank = averageRank.get(opoID);
                    Double ourRank = averageRank.get(teamID);
                    rankedMap.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),(Math.abs(ourRank-opoRank)));
                    rankedMapS.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getScoreAsString());
               }
               //System.out.println(rankedMapS);
          }

          Comparator<Game> rangeComparatorWon = (Game g1, Game g2) ->
          {return (g1.getNumPlayerGoals()-g1.getNumOpponentGoals()) - (g2.getNumPlayerGoals()-g2.getNumOpponentGoals());};
          Comparator<Game> rangeComparatorRank = (Game g1, Game g2) ->
          {return (int) Math.round((rankedMap.get(g1.getID()) - rankedMap.get(g2.getID()))*1000);};

          Collections.sort(wonGames, rangeComparatorWon.reversed());

          if(round > rounds/4) {
               Collections.sort(lostOrDrawnGamesWithReallocationCapacity, rangeComparatorRank.reversed());
          }

          int i = 0;
          for(Game lossOrDrew : lostOrDrawnGamesWithReallocationCapacity) {
               //System.out.println(lossOrDrew.getID() + " " + lossOrDrew.getScoreAsString());
               int rangeWon = Math.min((wonGames.get(i).getNumPlayerGoals()-wonGames.get(i).getNumOpponentGoals()),
                       wonGames.get(i).getHalfNumPlayerGoals());
               int rangeLD = lossOrDrew.getNumOpponentGoals() - lossOrDrew.getNumPlayerGoals();
               if(rangeLD < rangeWon && lossOrDrew.getNumPlayerGoals() + rangeLD + 1 <= 8) {
                    lossOrDrew.setNumPlayerGoals(lossOrDrew.getNumPlayerGoals() + rangeLD + 1);
                    wonGames.get(i).setNumPlayerGoals(wonGames.get(i).getNumPlayerGoals() - rangeLD - 1);
                    //lostOrDrawnGamesWithReallocationCapacity.remove(lossOrDrew);
                    i += 1;
               }
          }

          Collections.sort(lostOrDrawnGamesWithReallocationCapacity, rangeComparatorWon);
          Collections.sort(wonGames, rangeComparatorWon.reversed());

          i = 0;
          for(Game lossOrDrew : lostOrDrawnGamesWithReallocationCapacity) {
               //System.out.println(lossOrDrew.getID() + " " + lossOrDrew.getScoreAsString());
               int rangeWon = Math.min((wonGames.get(i).getNumPlayerGoals()-wonGames.get(i).getNumOpponentGoals()),
                       wonGames.get(i).getHalfNumPlayerGoals());
               int rangeLD = lossOrDrew.getNumOpponentGoals() - lossOrDrew.getNumPlayerGoals();
               if(rangeLD < rangeWon && lossOrDrew.getNumPlayerGoals() + rangeLD + 1 <= 8) {
                    lossOrDrew.setNumPlayerGoals(lossOrDrew.getNumPlayerGoals() + rangeLD + 1);
                    wonGames.get(i).setNumPlayerGoals(wonGames.get(i).getNumPlayerGoals() - rangeLD - 1);
                    //lostOrDrawnGamesWithReallocationCapacity.remove(lossOrDrew);
                    i += 1;
               }
          }

          reallocatedPlayerGames.addAll(wonGames);
          reallocatedPlayerGames.addAll(drawnGames);
          reallocatedPlayerGames.addAll(lostGames);

          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)) {
               return reallocatedPlayerGames;
          }
          return playerGames;
     }

     public MultipleLinearRegression getTeamRegression(Integer round, GameHistory gameHistory, Integer id) {
          double[][] x = new double[(round-1)*9][4];
          for(int i = 0; i < round-1; i++) {
               for(int j = 0; j < 9; j++) {
                    x[i*9+j][0]=1;
                    x[i*9+j][1]=gameHistory.getAllGamesMap().get(i).get(id).get(j).getScore().getNumPlayerGoals();
                    x[i*9+j][2]=gameHistory.getAllGamesMap().get(i).get(id).get(j).getScore().getNumOpponentGoals();
                    if(x[i*9+j][1] < x[i*9+j][2]) x[i*9+j][3]=0;
                    else if(x[i*9+j][1] > x[i*9+j][2]) x[i*9+j][3]=3;
                    else if(x[i*9+j][1] == x[i*9+j][2]) x[i*9+j][3]=1;
               }
          }
          double[] y = new double[(round-1)*9];
          for(int i = 1; i < round; i++) {
               for(int j = 0; j < 9; j++) {
                    y[(i-1)*9+j]=gameHistory.getAllGamesMap().get(i).get(id).get(j).getScore().getNumPlayerGoals();
               }
          }
//
//          for(int i = 0; i < round-1; i++) {
//               for(int j = 0; j < 9; j++) {
//                    System.out.printf("x:%.2f | y:%.2f | ", x[i*9+j][1], y[i*9+j]);
//               }
//               System.out.println("ola");
//          }

          return new MultipleLinearRegression(x, y);

//          System.out.printf("%.2f + %.2f beta1 + %.2f beta2 + %.2f beta3  (R^2 = %.2f)\n",
//                  regression.beta(0), regression.beta(1), regression.beta(2), regression.beta(3), regression.R2());
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

     public int checkConstraintsSatisfiedTest(List<Game> originalPlayerGames, List<Game> reallocatedPlayerGames) {

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
                    return 1;
               }

               // Constraint 2
               if(!originalPlayerGame.getNumOpponentGoals().equals(reallocatedPlayerGame.getNumOpponentGoals())) {
                    return 2;
               }

               // Constraint 3
               boolean numPlayerGoalsIncreased = reallocatedPlayerGame.getNumPlayerGoals() > originalPlayerGame.getNumPlayerGoals();
               if(isOriginalWinningGame && numPlayerGoalsIncreased) {
                    return 3;
               }

               // Constraint 4
               int halfNumPlayerGoals = originalPlayerGame.getHalfNumPlayerGoals();
               boolean numReallocatedPlayerGoalsLessThanHalf =
                       reallocatedPlayerGame.getNumPlayerGoals() < (originalPlayerGame.getNumPlayerGoals() - halfNumPlayerGoals);
               if((isOriginalWinningGame || isOriginalDrawnGame) && numReallocatedPlayerGoalsLessThanHalf) {
                    return 4;
               }

               totalNumOriginalPlayerGoals += originalPlayerGame.getNumPlayerGoals();
               totalNumReallocatedPlayerGoals += reallocatedPlayerGame.getNumPlayerGoals();

               // Constraint 5
               boolean numPlayerGoalsDecreased = reallocatedPlayerGame.getNumPlayerGoals() < originalPlayerGame.getNumPlayerGoals();
               if(isOriginalLosingGame && numPlayerGoalsDecreased) {
                    return 5;
               }

          }

          // Constraint 6
          if(totalNumOriginalPlayerGoals != totalNumReallocatedPlayerGoals) {
               return 6;
          }

          return 7;
     }
}
