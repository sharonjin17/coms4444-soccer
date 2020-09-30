package g1; 

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;

public class Player extends sim.Player {
     // for use of calculating how many points the opponents have to reallocate each round 
     // TeamID --> Available Points
     Map<Integer, Integer> availableForReallocation = new HashMap<>();
     
     //how fast to change moving average
     float alpha = 0.2f;
     int lastRoundScore = 0;
     float avg1 = 0;
     float avg2 = 0;
     float avg3 = 0;
     int freq1 = 0;
     int freq2 = 0;
     int freq3 = 0;
     int cutoff = 50;
     int lastStrat = 0;

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
          this.simPrinter = simPrinter;
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
          
          // 'remember' what your team got last round to guage improvements
          lastRoundScore = gameHistory.getAllRoundPointsMap().get(round-1).get(teamID).getTotalPoints();
          
          //update exponentially moving average for strategy that was last used
          float temp = updateStrats();
          // printOutRoundStats(round, temp);     

          // prepare containers for adjusting the player games 
          List<Game> reallocatedPlayerGames = new ArrayList<>();
          List<Game> wonGames = getWinningGames(playerGames);
          List<Game> drawnGames = getDrawnGames(playerGames);
          List<Game> lostGames = getLosingGames(playerGames);
          this.sortGamesLists(wonGames, drawnGames, lostGames);

          // Stores round available points in a map : Round # --> TeamID --> Available Points
          this.addTeamsReallocatablePts(this.teamID, wonGames, drawnGames);
          this.addOpponentsReallocatablePts(opponentGamesMap);
          // availableForReallocation.forEach((key,value) -> this.simPrinter.println("Team " + key + " => " + value));

          int currStrat = 1;
          //if a strategy hasn't been tried, try it!
          if(freq1 == 0) {
               currStrat = 1;
          }
          else if(freq2 == 0) {
               currStrat = 2;
          }
          else if(freq3 == 0) {
               currStrat = 3;
          }
          
          //otherwise use weighted randomness
          else {
               int[] stats = calcStats(avg1, avg2, avg3);
               int cutoff1 = stats[0];
               int cutoff2 = stats[1];
               int max = stats[2];
          
               int rand = random.nextInt(max);
               
               // this.printOutAveragesAndCutoffs(max, cutoff1, cutoff2, rand);
 
               currStrat = stratCutoff(currStrat, rand, cutoff1, cutoff2);               
          }
          
          // this.simPrinter.println("strategy selected is: " + currStrat);
          // this.simPrinter.println("rand was: " + rand);
          if(currStrat == 1) {
               freq1++;
               lastStrat = 1;
               strategy2composite(wonGames, drawnGames, lostGames, round, gameHistory);
          }
          else if(currStrat == 2) {
               freq2++;
               lastStrat = 2;
               strategy5(wonGames, drawnGames, lostGames, round);
          }
          else {
               freq3++;
               lastStrat = 3;
               strategy2(wonGames, drawnGames, lostGames, round);
          }

          reallocatedPlayerGames.addAll(wonGames);
          reallocatedPlayerGames.addAll(drawnGames);
          reallocatedPlayerGames.addAll(lostGames);

          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
               return reallocatedPlayerGames;
          // this.simPrinter.println("Group 1 is breaking constraints");
          return playerGames;
     }


     /* strategy methods
          uses 3 strategies, deciding with exponentially weighted moving average,
          giving preference to those that have performing well in recent games:
               - strategy 2 = sort wins from biggest margin --> smallest margin
                                   draws from highest points --> lowest points 
                                   losses from lowest margin --> highest margin 
                              useful if there are a large number of losses
                              Sacrifice points from wins (with randomness)
                              Sacrifice draws only if we have more realloctable pts than the opponent 
                              Give points to losses + draws with some randomness
                              Return unallocated points
                              
               - strategy 2 composite = similar to strategy 2, but with extra requirements
                              only subtract from a win where we are doing worse then the opponent in the prev rankings
                              give up on draws only if we have less available goals than the opponent 
                              give as many points to losses
                              return unallocated points
               - strategy 5 = worry about how the apponent is doing wrt their available goals to reallocate
                              check before taking away from wins
                              adjust conservativeness/riskiness cutoff depending on how well they are doing compared to us
                              reallocate goals to draws and losses, following a sort by reallocatable pts
                              return unallocated points
     */
     
     private void strategy2(List<Game> wonGames, List<Game> drawnGames, List<Game> lostGames, Integer round) {
          // as per g5's early approach, we will reallocate goals from wins to draws in order to maximize the number of wins 
          int excessGoals = 0;

          // for reallocating back to games we took from 
          HashMap<Game,Integer> excessSuccess = new HashMap<>();

          for (Game winningGame : wonGames) {
               if (drawnGames.size() == 0 && lostGames.size() == 0) 
                    break;
               int playerGoals = winningGame.getNumPlayerGoals();
               int margin = playerGoals - winningGame.getNumOpponentGoals();

               // randomize whether we are leaving a margin of 1 or 2 on the win
               int subtractedGoals = Math.min(this.random.nextInt(2) + margin - 2, winningGame.getHalfNumPlayerGoals());
               subtractedGoals = Math.max(subtractedGoals,0);
               // this.simPrinter.println("Subtracted goals from wins: " + subtractedGoals);
               excessGoals += subtractedGoals;
               if (subtractedGoals > 0) excessSuccess.put(winningGame, subtractedGoals);

               winningGame.setNumPlayerGoals(playerGoals - subtractedGoals);
          }
          
          // give up on draws only if we have more available points than the opponent
          for (Game drawnGame : drawnGames) {
               if (this.availableForReallocation.get(this.teamID) > this.availableForReallocation.get(drawnGame.getID())) {
                    int playerGoals = drawnGame.getNumPlayerGoals();
                    int half = drawnGame.getHalfNumPlayerGoals();
                    // this.simPrinter.println("Subtracted goals from draws: " + half);
                    excessGoals+= half;
                    if (half > 0) excessSuccess.put(drawnGame, half);

                    drawnGame.setNumPlayerGoals(playerGoals-half);
               }
          }

          // add goals to losses
          while (excessGoals > 0) {
               for (Game lostGame : lostGames) {
                    int playerGoals = lostGame.getNumPlayerGoals();
                    int addedGoals = Math.min(excessGoals, Math.min(lostGame.getNumOpponentGoals() - playerGoals + 1, 8 - playerGoals));

                    // distribute goals once for many losses, randomizing the amount 
                    if (excessGoals > 0 && playerGoals < 8 && addedGoals > 0) {
                         excessGoals -= addedGoals;
                         lostGame.setNumPlayerGoals(playerGoals + addedGoals);
                         // this.simPrinter.println("Added goals to loss: " + addedGoals);
                    }
                    // this.simPrinter.println("playerGoals is " + playerGoals);
                    // this.simPrinter.println("addedGoals is " + addedGoals);
               }

               // now that we have the hopeful max # of points from the wins, we reallocate to draws 
               for (Game drawnGame : drawnGames) {
                    int playerGoals = drawnGame.getNumPlayerGoals();
                    // distribute goals once for many draws, randomizing the amount 
                    int addedGoals = this.random.nextInt(2);
                    if (excessGoals > 0 && playerGoals < 8) {
                         if ((playerGoals + addedGoals) > 8) addedGoals = 1;
                         excessGoals -= addedGoals;
                         drawnGame.setNumPlayerGoals(playerGoals + addedGoals);
                         // this.simPrinter.println("Added goals to draw: " + addedGoals);
                    }
               }

               // add back to wins if there are any left 
               for (Game wonGame : excessSuccess.keySet()) {
                    if(excessGoals == 0) {
                         break;
                    }
                    int playerGoals = wonGame.getNumPlayerGoals();
                    int margin = excessSuccess.get(wonGame);

                    if(margin > excessGoals) margin = excessGoals;

                    //replacing that game with new score
                    wonGames.remove(wonGame);
                    wonGame.setNumPlayerGoals(playerGoals + margin);
                    wonGames.add(wonGame);
                    excessGoals -= margin;
                    // this.simPrinter.println("Added goals back to won game: " + margin);
               }    
          }
     }

     private void strategy2composite(List<Game> wonGames, List<Game> drawnGames, List<Game> lostGames, Integer round, GameHistory GH) {
          int excessGoals = 0;
          HashMap<Game,Integer> excessSuccess = new HashMap<>();
          for (Game winningGame : wonGames) {
               if (drawnGames.size() == 0 && lostGames.size() == 0) 
                    break;
               int playerGoals = winningGame.getNumPlayerGoals();
               int margin = playerGoals - winningGame.getNumOpponentGoals();

               // only take away points if we are doing worse then the other team in the rankings
               if (GH.getAllRoundRankingsMap().get(round).get(this.teamID) > GH.getAllRoundRankingsMap().get(round).get(this.teamID)) {
                    // randomize whether we are leaving a margin of 1 or 2 on the win
                    int subtractedGoals = Math.min(this.random.nextInt(2) + margin - 2, winningGame.getHalfNumPlayerGoals());
                    subtractedGoals = Math.max(subtractedGoals,0);
                    
                    if (subtractedGoals > 0)
                         excessSuccess.put(winningGame, subtractedGoals);
                    
                    excessGoals += subtractedGoals;
                    winningGame.setNumPlayerGoals(playerGoals - subtractedGoals);
                    // this.simPrinter.println("Subtracted goals from wins: " + subtractedGoals);
               }
          }
          
          // give up on draws only if we have less available points than the opponent
          for (Game drawnGame : drawnGames) {
               if (this.availableForReallocation.get(this.teamID) < this.availableForReallocation.get(drawnGame.getID())) {
                    int playerGoals = drawnGame.getNumPlayerGoals();
                    int half = drawnGame.getHalfNumPlayerGoals();
                    excessGoals+= half;
                    if (half > 0) excessSuccess.put(drawnGame, half);
                    drawnGame.setNumPlayerGoals(playerGoals-half);
                    // this.simPrinter.println("Subtracted goals from draws: " + half);
               }
          }

          // add all goals to losses
          while (excessGoals > 0) {
               for (Game lostGame : lostGames) {
                    int playerGoals = lostGame.getNumPlayerGoals();
                    // int addedGoals = 1;
                    int addedGoals = Math.min(excessGoals, Math.min(lostGame.getNumOpponentGoals() - playerGoals + 1, 8 - playerGoals));
                    
                    // distribute goals once for many losses, randomizing the amount 
                    if (excessGoals > 0 && playerGoals < 8 && addedGoals > 0) {
                         excessGoals -= addedGoals;
                         lostGame.setNumPlayerGoals(playerGoals + addedGoals);
                         // this.simPrinter.println("Added goals to loss: " + addedGoals);
                    }
                    // this.simPrinter.println("playerGoals is " + playerGoals);
                    // this.simPrinter.println("addedGoals is " + addedGoals);
               }

               // add back to wins if there are any left 
               for (Game wonGame : excessSuccess.keySet()) {
                    if(excessGoals == 0) {
                         break;
                    }
                    int playerGoals = wonGame.getNumPlayerGoals();
                    int margin = excessSuccess.get(wonGame);

                    if(margin > excessGoals) {
                         margin = excessGoals;
                    }

                    //replacing that game with new score
                    wonGames.remove(wonGame);
                    wonGame.setNumPlayerGoals(playerGoals + margin);
                    wonGames.add(wonGame);
                    excessGoals -= margin;
                    // this.simPrinter.println("Added goals back to won game: " + margin);
               } 
          }
     }

     // modify approach to beating teams using the amount of allocatable goals they have for a given round
     private void strategy5(List<Game> wonGames, List<Game> drawnGames, List<Game> lostGames, Integer round) {
          //holds how many points we took away from won games
          HashMap<Game,Integer> wonGamesTakenAway = new HashMap<>();
          
          int excessGoals = 0;

          List<Game> order = new ArrayList<>();
          order.addAll(wonGames);
          order.addAll(drawnGames);
          order.addAll(lostGames);

          //sort all games by how many points the opposing team has to allocate
          order.sort( (Game g1, Game g2) -> (availableForReallocation.get(g1.getID()) - availableForReallocation.get((g2.getID()))) );
          
          // this.simPrinter.println("lowest to highest to allocate:");
          // for(Game game : order) {
          //      this.simPrinter.println("team " + game.getID() + " can allocate " + availableForReallocation.get(game.getID()));
          // }

          //holds the teamID to the rank of how many goals they can allocate this round
          HashMap<Integer,Integer> idToAllocatableOrder = new HashMap<>();
          
          for(int i = 0; i < order.size(); i++) {
               idToAllocatableOrder.put(order.get(i).getID(), i);
          }

          //loop through won games, take away points
          for (Game winningGame : wonGames) {

               if (drawnGames.size() == 0 && lostGames.size() == 0) 
                    break;

               int playerGoals = winningGame.getNumPlayerGoals();
               int subtractedGoals;
               
               //if they are in the upper half of teams wrt allocatable goals, give up on game
               if(idToAllocatableOrder.get(winningGame.getID()) > order.size()/2) {
                    subtractedGoals = winningGame.getHalfNumPlayerGoals();
               }
               //if they are in the lower half of teams wrt allocatable goals, leave some goals
               else {
                    int winningBy = winningGame.getNumPlayerGoals() - winningGame.getNumOpponentGoals();
                    subtractedGoals = Math.min(winningBy - 2, winningGame.getHalfNumPlayerGoals());
               }

               subtractedGoals = Math.max(subtractedGoals,0);
               excessGoals += subtractedGoals;
               winningGame.setNumPlayerGoals(playerGoals - subtractedGoals);
               
               if(subtractedGoals > 0) {
                    wonGamesTakenAway.put(winningGame, subtractedGoals);
               }
               // this.simPrinter.println("team rank is " + idToAllocatableOrder.get(winningGame.getID()));
               // this.simPrinter.println("score is " + winningGame.getScoreAsString());
               // this.simPrinter.println("Subtracted goals from wins: " + subtractedGoals);
          }

          List<Game> drawnAndLosses = new ArrayList<>();
          drawnAndLosses.addAll(lostGames);

          // give up on draws only if we have more available points than the opponent
          for (Game drawnGame : drawnGames) {
               //giving up on draw
               if (this.availableForReallocation.get(this.teamID) > this.availableForReallocation.get(drawnGame.getID())) {
                    int playerGoals = drawnGame.getNumPlayerGoals();
                    int half = drawnGame.getHalfNumPlayerGoals();
                    // this.simPrinter.println("Subtracted goals from draws: " + half);
                    excessGoals+= half;
                    drawnGame.setNumPlayerGoals(playerGoals-half);
                    wonGamesTakenAway.put(drawnGame, half);
               }
               //try to win draw
               else {
                    drawnAndLosses.add(drawnGame);
               }
          }
          
          while (excessGoals > 0) {
               // now that we have the hopeful max # of points from the wins, we reallocate to draws 
               for (Game game : drawnAndLosses) {
                    int playerGoals = game.getNumPlayerGoals();

                    if(excessGoals > 0) {
                         int losingBy = game.getNumOpponentGoals()- game.getNumPlayerGoals();

                         int halfOpponent = game.getNumOpponentGoals()/2;

                         //if they have a lot of goals to allocate, be aggressive
                         int addedGoals;
                         if(idToAllocatableOrder.get(game.getID()) > order.size()/2) {
                              addedGoals = losingBy + 1;
                         }
                         //otherwise, be conservative, assume the opponent will take away a lot of goals from their won game
                         else {
                              addedGoals = losingBy - halfOpponent + 2;
                              addedGoals = Math.min(losingBy+1, addedGoals);
                         }

                         //ensure valid allocation
                         addedGoals = Math.max(0, addedGoals);
                         if(addedGoals + game.getNumPlayerGoals() > 8) {
                              addedGoals = 8-game.getNumPlayerGoals();
                         }
                         if(addedGoals > excessGoals) {
                              addedGoals = excessGoals;
                         }

                         excessGoals -= addedGoals;
                         game.setNumPlayerGoals(playerGoals + addedGoals);
                         // this.simPrinter.println("Added goals to game: " + addedGoals);
                    }
               }

               // add back to wins if there are any left 
               if(excessGoals > 0) {
                    for (Game wonGame : wonGamesTakenAway.keySet()) {
                         if(excessGoals == 0) break;

                         int playerGoals = wonGame.getNumPlayerGoals();
                         int margin = wonGamesTakenAway.get(wonGame);

                         if(margin > excessGoals) margin = excessGoals;

                         //replacing that game with new score
                         wonGames.remove(wonGame);
                         wonGame.setNumPlayerGoals(playerGoals + margin);
                         wonGames.add(wonGame);
                         excessGoals -= margin;
                         // this.simPrinter.println("Added goals back to whence they came: " + margin);
                    }     
               }
          }
     }

     //calculate cutoff values for weighted randomness
     private int[] calcStats(float av1, float av2, float av3) {
         float cutoff1 = av1*av1*av1*av1;
         float cutoff2 = cutoff1 + av2*av2*av2*av2;
         float total = cutoff2 + av3*av3*av3*av3;

         cutoff1 = (int) ((cutoff1/total)*100);
         cutoff2 = (int) ((cutoff2/total)*100);
         total = 100;

         return new int[] {(int) cutoff1, (int) cutoff2, (int) total};
     }

     private int stratCutoff(int currStrat, int rand, int cutoff1, int cutoff2) {
          if(rand < cutoff1) return 1;
          if(rand < cutoff2) return 2;
          else return 3;
     }

     private void printOutRoundStats(Integer round, float t) {
          this.simPrinter.println("\nStarting reallocation");
          this.simPrinter.println("Round is " + round);
          this.simPrinter.println("Last round strategy was " + this.lastStrat);
          this.simPrinter.println("Last round scored " + this.lastRoundScore);
          this.simPrinter.println("Avg is: " + t);
     }

     private void printOutAveragesAndCutoffs(int m, int co1, int co2, int r) {
          this.simPrinter.println("max is " + m);
          this.simPrinter.println("average 1 is "+ this.avg1);
          this.simPrinter.println("average 2 is "+ this.avg2);
          this.simPrinter.println("average 3 is "+ this.avg3);
          this.simPrinter.println("cutoff1 is " + co1);
          this.simPrinter.println("cutoff2 is " + co2);
          this.simPrinter.println("rand is " + r);
     }

     private void sortGamesLists(List<Game> wonGames, List<Game> drawnGames, List<Game> lostGames) {
          // wins: biggest margin --> smallest margin
          wonGames.sort(
               (Game g1, Game g2) -> (g2.getNumPlayerGoals() - g2.getNumOpponentGoals() 
               - (g1.getNumPlayerGoals() - g1.getNumOpponentGoals()))
          );

          // draws: highest points --> lowest points 
          drawnGames.sort(
               (Game g1, Game g2) -> (g2.getNumPlayerGoals() - g1.getNumOpponentGoals())
          );

          // losses: lowest margin --> highest margin 
          lostGames.sort(
               (Game g1, Game g2) -> (g2.getNumPlayerGoals() - g2.getNumOpponentGoals() 
               - (g1.getNumPlayerGoals() - g1.getNumOpponentGoals()))
          );
       
          // For printing our game totals vvv 
          // this.simPrinter.println("\nwon games are:");
          // for(Game won : wonGames) {
          //      this.simPrinter.println(won.getScoreAsString());
          // }
          // this.simPrinter.println("\ndrawn games are:");
          // for(Game won : drawnGames) {
          //      this.simPrinter.println(won.getScoreAsString());
          // }
          // this.simPrinter.println("\nlost games are:");
          // for(Game won : lostGames) {
          //      this.simPrinter.println(won.getScoreAsString());
          // }
     }
    
     // helper methods stolen from random >:D
     private List<Game> getWinningGames(List<Game> playerGames) {
    	     List<Game> winningGames = new ArrayList<>();
    	     for(Game game : playerGames) {
    		     int numPlayerGoals = game.getNumPlayerGoals();
    		     int numOpponentGoals = game.getNumOpponentGoals();
    		     if (numPlayerGoals > numOpponentGoals)
    			    winningGames.add(game.cloneGame());
    	     }
    	     return winningGames;
     }

     private List<Game> getDrawnGames(List<Game> playerGames) {
    	     List<Game> drawnGames = new ArrayList<>();
    	     for(Game game : playerGames) {
    	   	    int numPlayerGoals = game.getNumPlayerGoals();
    		    int numOpponentGoals = game.getNumOpponentGoals();
    		    if (numPlayerGoals == numOpponentGoals)
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

     // calculates the total number of points available to a team per round and adds it to the HashMap
     private void addTeamsReallocatablePts(Integer teamID, List<Game> wonGames, List<Game> drawnGames) {
          int availablePoints = 0;
          for (Game g : wonGames) {
               availablePoints += g.getHalfNumPlayerGoals();
          }
          for (Game g : drawnGames) {
               availablePoints += g.getHalfNumPlayerGoals();
          }
          availableForReallocation.put(teamID, availablePoints);
     }

     // wrapper for addTeamsReallocatablePts to use for all teams
     private void addOpponentsReallocatablePts(Map<Integer, List<Game>> opponentGamesMap) {
          opponentGamesMap.forEach( (key,value) 
               -> addTeamsReallocatablePts(key, getWinningGames(value), getDrawnGames(value))
          );
     }

     // exponentially weighted moving average calculator 
     private float updateStrats() {
          if (lastStrat == 1) { 
               if (freq1 == 1) avg1 = lastRoundScore;
               else avg1 = alpha*lastRoundScore + (1-alpha)*avg1;
               return avg1;
          }
          if (lastStrat == 2) { 
               if (freq2 == 1) avg2 = lastRoundScore;
               else avg2 = alpha*lastRoundScore + (1-alpha)*avg2;
               return avg2;
          } 
          if (lastStrat == 3) { 
               if (freq3 == 1) avg3 = lastRoundScore;
               else avg3 = alpha*lastRoundScore + (1-alpha)*avg3;
               return avg3;
          } 
          return 0.0f;
     }

}