package g3;

import java.util.*;

import sim.Game;
import sim.GameHistory;
import sim.Score;
import sim.PlayerPoints;
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
      *
      */
     
	 private int HIGH_MARGIN_LOSS = 0;
	 private int LOW_MARGIN_WIN = 1; 
     /*
      * 1. Expectation Maximization 
      * 2. Historic Weighting of outcomes with alpha and beta search 
      * 3. Backward Induction 
      * 4. Statistical Analytics
      * 
      * 
      * 5. Tracking Tendency 
      * 	-> if they're below us what do they do 
      * 
      * 
      */
	 
	private List<Game> performanceBasedReallocation(Integer round, int acceptableLossMargin,  GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap, int window) { 
		List<Game> wins = getWinningGames(playerGames);
		List<Game> draws = getDrawnGames(playerGames);
		List<Game> losses = getLosingGames(playerGames);
		Map<Integer, Set<Integer>> playerSet = PerformanceBasedReallocation.getPlayersInWindow(teamID, gameHistory, losses, draws, wins, window, round);
		Set<Integer> worseRatedTeams = playerSet.get(PerformanceBasedReallocation.WORSE_RATED_TEAMS);
		Set<Integer> betterRatedTeams = playerSet.get(PerformanceBasedReallocation.BETTER_RATED_TEAMS);
		
		List<Game> reallocatedPlayerGames = new ArrayList<>();
		
		int extraGoals = 0;
		
		 // For won games, leave at least 2 buffer goals
		int bufferWinGoals = 2;
		for (Game game : wins) {
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
		 for (Game game : wins) {
		 	 if (!betterRatedTeams.contains(game.getID())) {
		          int goalsDifference = game.getNumPlayerGoals() - game.getNumOpponentGoals();
		
		          if (goalsDifference == 1) {
		               extraGoals += game.getHalfNumPlayerGoals();
		               game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
		          }
		 	 }
		 }
		
		 // For drawn games, first allocate half of all goals to extraGoals
		 for (Game game : draws) {
		      extraGoals += game.getHalfNumPlayerGoals();
		      game.setNumPlayerGoals(game.getNumPlayerGoals() - game.getHalfNumPlayerGoals());
		 }
		
		 // Allocate all points to lost games
		 // First, sort lost games by the margin needed to win
	     Collections.sort(losses, new Comparator<Game>() {
	          @Override
	          public int compare(Game g1, Game g2) {
	               int g1Margin = g1.getNumOpponentGoals() - g1.getNumPlayerGoals();
	               int g2Margin = g2.getNumOpponentGoals() - g2.getNumPlayerGoals();
	               return g1Margin - g2Margin;
	          }
	     });
	     
	     for (Game game: losses) { 
	     	if (extraGoals > 0 && betterRatedTeams.contains(game.getID())) {
	     		int margin = game.getNumOpponentGoals() - game.getNumPlayerGoals() + 1;
	     		if (margin <= acceptableLossMargin && extraGoals > margin) { 
	     			extraGoals -= margin;
	     			game.setNumPlayerGoals(game.getNumPlayerGoals() + margin);
	     		}
	     	}
	     }
	     
	     for (Game game : losses) {
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
		          reallocatedPlayerGames.addAll(wins);
		          reallocatedPlayerGames.addAll(draws);
		          reallocatedPlayerGames.addAll(losses);
		
		          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
		               return reallocatedPlayerGames;
	    }
	    
	     return reallocatedPlayerGames;
	}
		
	 
	 
	 private double calculateAverage(List<Integer> list) {
		double sum = 0;
		for (int i = 0; i<list.size(); i++) {
			sum += list.get(i);
		}
		return sum / list.size();
	 }
	 
	 private double calculateKeyExponentialMovingAverage(List<List<Integer>> result, double alpha) {
		double final_value = 0.0;
		boolean value_set = false; 
		for (List<Integer> list: result) {
			double average = calculateAverage(list);
			if (!value_set) {
				final_value = average;
				value_set = true;
			}
			else {
				final_value = (1 - alpha)*final_value + alpha*average;
			}
		}
		return final_value;
	 }
	 
	 private double predictedAllocation(int gameID, GameHistory gameHistory, int round, int predict_type) {
		 // calculate tendency
		 List<List<Integer>> results = new LinkedList<List<Integer>>();
		 if (predict_type == HIGH_MARGIN_LOSS) { 
			 for (int i = Math.max(round - 5, 0); i < round-1; i++) { 
				 List<Integer> currentRoundResults = new LinkedList<Integer>(); 
				 // look at prior round 
				 List<Game> opponentGamesForCurrentRound = gameHistory.getAllGamesMap().get(i).get(gameID);
				 List<Game> opponentGamesForNextRound = gameHistory.getAllGamesMap().get(i+1).get(gameID);
				 for (Game game: opponentGamesForCurrentRound) {
					 if (game.getNumPlayerGoals() - game.getNumOpponentGoals() > 2) {	
						 int opponentGoals = game.getNumOpponentGoals();
						 int opponentToSearchFor = game.getID();
						 for (Game futureGame: opponentGamesForNextRound) {
							 if (futureGame.getID() == opponentToSearchFor) {
								 int newGoals = futureGame.getNumPlayerGoals();
				        		 simPrinter.println("New Goals");
								 simPrinter.println(newGoals);
				        		 simPrinter.println("Old Goals");
								 simPrinter.println(game.getNumPlayerGoals());
								 int allocation = newGoals - game.getNumPlayerGoals();
								 simPrinter.println("midway allocation");
								 simPrinter.println(allocation);
								 currentRoundResults.add(allocation);
							 }
						 }
					 }
				 }
				 results.add(currentRoundResults);
				 //look at the next round 
			 }	 
		 }
		 return this.calculateKeyExponentialMovingAverage(results, 0.8);
	 }
	 
	 private List<Game> expectedValueBasedAllocation(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
		List<Game> clonePlayerGames = new ArrayList<Game>();
        for (Game g: playerGames) {
            clonePlayerGames.add(g.cloneGame());
        }
		 
		 for (Integer teamID : opponentGamesMap.keySet()) {
	            List<Game> opponentGamesList = opponentGamesMap.get(teamID);
	            int adjustedGoal = ExpectedValuePlayer.getExpectedGoals(opponentGamesList, teamID);
	            for (Game g : clonePlayerGames) {
	                if (g.getID().equals(teamID)) {
	                    g.setNumOpponentGoals(adjustedGoal);
	                }
	            }
		 }
		
		 return normalStrategy(round, gameHistory, clonePlayerGames, opponentGamesMap); 
	 }
	 
     // public List<Integer> getPredictedOpponentOutcomes(GameHistory gameHistory)
     
     public List<Integer> getHighestRatedPlayers(GameHistory gameHistory, Integer round, Integer k) {
    	Map<Integer, Map<Integer, PlayerPoints>> cumulativePointsMap = gameHistory.getAllCumulativePointsMap();
    	Map<Integer, PlayerPoints> roundMap = cumulativePointsMap.get(round-1);
    	Set<Integer> set = roundMap.keySet();
        List<Integer> keys = new ArrayList<Integer>(set);
    	Collections.sort(keys, new Comparator<Integer>() {
    		@Override
    		public int compare(Integer s1, Integer s2) { 
    			if (roundMap.get(s1).getTotalPoints()  < roundMap.get(s2).getTotalPoints() ) {
                    return 1;
                }
    			else if (roundMap.get(s1).getTotalPoints()  > roundMap.get(s2).getTotalPoints()) {
    				return -1; 
    			}
                return 0;
    		}
    	});
    	return keys.subList(0,k);
     }
     
     private List<Game> normalStrategy(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap)  { 
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
         
         /*
         int count = 2;
         for (int i = lostGames.size() - 1; i >= lostGames.size() - 2; i--) { 
       	  Game lostGame = lostGames.get(i); 
       	  if (lostGame.getNumOpponentGoals() - lostGame.getNumPlayerGoals() > 2) {
	        	  int opponentId = lostGame.getID(); 
	        	  try {
	        		  double allocation = predictedAllocation(opponentId, gameHistory, round, HIGH_MARGIN_LOSS);
	        		  simPrinter.println("allocation");
		        	  simPrinter.println(allocation);
		        	  if (extraGoals > allocation) { 
		        		  extraGoals -= (int) allocation;
		        		  lostGame.setNumPlayerGoals(lostGame.getNumPlayerGoals() + (int) allocation);
		        	  }
	        	  }
	        	  catch (Exception e) {
	        		  e.printStackTrace();
	        	  }
       	  }
         }
         */
         
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
	          reallocatedPlayerGames.addAll(wonGames);
	          reallocatedPlayerGames.addAll(drawnGames);
	          reallocatedPlayerGames.addAll(lostGames);
	
	          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
	               return reallocatedPlayerGames;
        }
        
         return playerGames;
    	 
     }
     
     
     public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
          
    	 return performanceBasedReallocation(round, 1, gameHistory, playerGames, opponentGamesMap, 1);
    	 // return this.expectedValueBasedAllocation(round, gameHistory, playerGames, opponentGamesMap);
    	 /*
	    	  List<Game> reallocatedPlayerGames = new ArrayList<>();
	
	          List<Game> wonGames = getWinningGames(playerGames);
	          List<Game> drawnGames = getDrawnGames(playerGames);
	          List<Game> lostGames = getLosingGames(playerGames);
	
	          int extraGoals = 0;
	          
	          simPrinter.println(gameHistory.getAllAverageRankingsMap());
	
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
	          
	          int count = 2;
	          for (int i = lostGames.size() - 1; i >= lostGames.size() - 2; i--) { 
	        	  Game lostGame = lostGames.get(i); 
	        	  if (lostGame.getNumOpponentGoals() - lostGame.getNumPlayerGoals() > 2) {
	        		  simPrinter.println("What the fuck");
	        		  simPrinter.println(round);
	            	  simPrinter.println(lostGame.getNumOpponentGoals() - lostGame.getNumPlayerGoals());
		        	  int opponentId = lostGame.getID(); 
		        	  try {
		        		  double allocation = predictedAllocation(opponentId, gameHistory, round, HIGH_MARGIN_LOSS);
		        		  simPrinter.println("allocation");
			        	  simPrinter.println(allocation);
			        	  if (extraGoals > allocation) { 
			        		  extraGoals -= (int) allocation;
			        		  lostGame.setNumPlayerGoals(lostGame.getNumPlayerGoals() + (int) allocation);
			        	  }
		        	  }
		        	  catch (Exception e) {
		        		  e.printStackTrace();
		        	  }
	        	  }
	          }
	          
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
		          reallocatedPlayerGames.addAll(wonGames);
		          reallocatedPlayerGames.addAll(drawnGames);
		          reallocatedPlayerGames.addAll(lostGames);
		
		          if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
		               return reallocatedPlayerGames;
	         }
	         
	          return playerGames;
	     }
		 */
	     /* private List<Game> analyzeGamesLost(List<Game> lostGames, GameHistory gameHistory) { 
	    	 for (Game game: lostGames) { 
	    		 int teamID = game.getID(); 
	    		 
	    		 
	    	 }
	    	
	     } */
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
