<<<<<<< HEAD
package g3;

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

          List<Game> wonGames = getWinningGames(playerGames);
          List<Game> drawnGames = getDrawnGames(playerGames);
          List<Game> lostGames = getLosingGames(playerGames);

          int extraGoals = 0;

          // For won games, leave at least 2 buffer goals
          int bufferWinGoals = 2;
          for (Game game : wonGames) {
               int goalsDifference = game.getNumPlayerGoals() - game.getNumOpponentGoals();

               if (goalsDifference > bufferWinGoals) {
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

          // For games won with 1 goal difference, take away half the goals
          for (Game game : wonGames) {
               int goalsDifference = game.getNumPlayerGoals() - game.getNumOpponentGoals();

               if (goalsDifference == 1) {
                    extraGoals += game.getHalfNumPlayerGoals();
                    game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
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
                    int margin = game.getNumOpponentGoals() - game.getNumPlayerGoals() + 1;

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
=======
odkd
>>>>>>> removed print statements
