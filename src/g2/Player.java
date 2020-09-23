package g2;

import java.util.*;

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

          List<Game> reallocatedPlayerGames = new ArrayList<>();
          Map<Integer, Double> rankedMap = new HashMap<Integer, Double>();
          Map<Integer, String> rankedMapS = new HashMap<Integer, String>();

          List<Game> wonGames = getWinningGames(playerGames);
          List<Game> drawnGames = getDrawnGames(playerGames);
          List<Game> lostGames = getLosingGames(playerGames);
          Map<Integer, Integer> goalsTaken = new HashMap<Integer, Integer>();

          int goalsToReallocate = 0;
          int excessGoals = 0;
          int mustWinMargin = 2;

          double ran = Math.random();
          if (ran>0.1){
            mustWinMargin = 2;
          }
          else {
            mustWinMargin = 3;
          } 
          int drawtoLoss = 7;
          int mustLossMargin = 7;

          List<Game> lostGamesWithReallocationCapacity = new ArrayList<>(lostGames);
          List<Game> lostOrDrawnGames = new ArrayList<>(lostGames);
          lostOrDrawnGames.addAll(drawnGames);
          List<Game> drawnGamesWithReallocationCapacity = new ArrayList<>(drawnGames);

          for(Game drawGame : drawnGames){
            if(drawGame.getNumPlayerGoals() >= drawtoLoss){
              if (ran>0.01){
                int numGoals = drawGame.getHalfNumPlayerGoals();

                drawGame.setNumPlayerGoals(drawGame.getNumPlayerGoals() - numGoals);
                goalsToReallocate += numGoals;
              }
              drawnGamesWithReallocationCapacity.remove(drawGame);
            } 
            else {
              int numGoals = drawGame.getHalfNumPlayerGoals();

              drawGame.setNumPlayerGoals(drawGame.getNumPlayerGoals() - numGoals);
              excessGoals += numGoals;
            }
          }

          for(Game lostGame : lostGames){
            int margin = lostGame.getNumOpponentGoals() - lostGame.getNumPlayerGoals();
            if(margin >= mustLossMargin){
              lostGamesWithReallocationCapacity.remove(lostGame);
            }
          }

          for(Game winGame: wonGames){
            int margin = winGame.getNumPlayerGoals() - winGame.getNumOpponentGoals();
            if(margin == mustWinMargin){
              goalsTaken.put(winGame.getID(), 0);
            }
            else if(margin > mustWinMargin){
              int halfNumPlayerGoals = winGame.getHalfNumPlayerGoals();
              int numGoals = (int) Math.min(halfNumPlayerGoals, margin - mustWinMargin);

              winGame.setNumPlayerGoals(winGame.getNumPlayerGoals() - numGoals);
              goalsToReallocate += numGoals;
              goalsTaken.put(winGame.getID(), numGoals);
            }
            else {
              int numGoals = winGame.getHalfNumPlayerGoals();

              winGame.setNumPlayerGoals(winGame.getNumPlayerGoals() - numGoals);
              goalsToReallocate += numGoals;
              if (margin > 1){
                goalsTaken.put(winGame.getID(), numGoals);
              }
              else{
                goalsTaken.put(winGame.getID(), 0);
              }
            }
          }

          if(!gameHistory.getAllGamesMap().isEmpty() && !gameHistory.getAllAverageRankingsMap().isEmpty()) {
            List<Double> averageRank = new ArrayList<Double>(gameHistory.getAllAverageRankingsMap().get(round-1).values());
            for(int i = 0; i < 9; i++) {
              int opoID = i;
              if(i >= teamID-1) {opoID = opoID + 1;}
              Double opoRank = averageRank.get(opoID);
              Double ourRank = averageRank.get(teamID-1);

              rankedMap.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),(Math.abs(ourRank-opoRank)));
            }
          }


          Comparator<Game> rangeComparatorLoss = (Game g1, Game g2) ->
          {return (g1.getNumOpponentGoals()-g1.getNumPlayerGoals()) - (g2.getNumOpponentGoals()-g2.getNumPlayerGoals());};
          Comparator<Game> rangeComparatorWon = (Game g1, Game g2) ->
          {return (g1.getNumPlayerGoals()-g1.getNumOpponentGoals()) - (g2.getNumPlayerGoals()-g2.getNumOpponentGoals());};
          Comparator<Game> rangeComparatorRank = (Game g1, Game g2) ->
          {return (int) Math.round((rankedMap.get(g1.getID()) - rankedMap.get(g2.getID()))*1000);};


          for (Game loss : lostGamesWithReallocationCapacity) {
            if (loss.getNumOpponentGoals() - loss.getNumPlayerGoals() <=2){
              if(goalsToReallocate >1 ){
                if(loss.getNumPlayerGoals() == 7){
                  loss.setNumPlayerGoals(loss.getNumPlayerGoals() + 1);
                  goalsToReallocate -= 1;
                }
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + 2);
                goalsToReallocate -= 2;
              }
              else {
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToReallocate);
                goalsToReallocate = 0;
              }
            }
            else if (loss.getNumOpponentGoals() - loss.getNumPlayerGoals() <=4){
              if(goalsToReallocate > 2){
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + 3);
                goalsToReallocate -= 3;
              }
              else {
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToReallocate);
                goalsToReallocate = 0;
              }
            }
            else{
              if(goalsToReallocate > 3){
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + 4);
                goalsToReallocate -= 4;
              }
              else {
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToReallocate);
                goalsToReallocate = 0;
              }
            }
          }

          if(excessGoals != 0) {
            try{
              if (drawnGamesWithReallocationCapacity.size() > 1){
                Collections.sort(drawnGamesWithReallocationCapacity, rangeComparatorRank);
              }  
            }
            catch(Exception e){
              System.out.println(e);
            }
            excessGoals += goalsToReallocate;
            goalsToReallocate = 0;

            for(Game draw : drawnGamesWithReallocationCapacity){
              int goalsToWin = draw.getNumOpponentGoals() - draw.getNumPlayerGoals() + 2;
              if(excessGoals > goalsToWin){
                draw.setNumPlayerGoals(draw.getNumPlayerGoals() + goalsToWin);
                excessGoals -= goalsToWin;
              }
              else {
                draw.setNumPlayerGoals(draw.getNumPlayerGoals() + excessGoals);
                excessGoals = 0;
              }
            }

          }

          excessGoals += goalsToReallocate;
          goalsToReallocate = 0;

          if(excessGoals != 0) {
            try{
              if (wonGames.size() > 1){
                Collections.sort(wonGames, rangeComparatorRank);
              }  
            }
            catch(Exception e){
              System.out.println(e);
            }
            for(Game win : wonGames){
              int goalsToWin = goalsTaken.get(win.getID());
              if(excessGoals > goalsToWin){
                win.setNumPlayerGoals(win.getNumPlayerGoals() + goalsToWin);
                excessGoals -= goalsToWin;
              }
              else {
                win.setNumPlayerGoals(win.getNumPlayerGoals() + excessGoals);
                excessGoals = 0;
              }
            }

          }

          if(excessGoals != 0) {
            try{
              if (lostOrDrawnGames.size() > 1){
                Collections.sort(lostOrDrawnGames, rangeComparatorRank);
              }  
            }
            catch(Exception e){
              System.out.println(e);
            }
            for(Game loss : lostOrDrawnGames){
              int goalsToWin =loss.getNumOpponentGoals() - loss.getNumPlayerGoals();
              if(goalsToWin > 0){
                if(excessGoals > goalsToWin){
                  loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToWin);
                  excessGoals -= goalsToWin;
                }
                else {
                  loss.setNumPlayerGoals(loss.getNumPlayerGoals() + excessGoals);
                  excessGoals = 0;
                }
              }
            }
          }


          reallocatedPlayerGames.addAll(lostGamesWithReallocationCapacity);
          reallocatedPlayerGames.addAll(drawnGamesWithReallocationCapacity);
          reallocatedPlayerGames.addAll(wonGames);
          reallocatedPlayerGames.addAll(drawnGames);
          reallocatedPlayerGames.addAll(lostGames);

          //System.out.println("Lol " + round + " " + goalsToReallocate + " " + excessGoals);    

          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)) {
               return reallocatedPlayerGames;
          }

          return playerGames;
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