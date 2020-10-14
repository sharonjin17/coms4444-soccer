package g3;

import java.util.*;
import sim.Game;
import sim.SimPrinter;

public class ExpectedValuePlayer {

	public static int getExpectedGoals(List<Game> games, int teamID) {
		Map<Integer, List<Game>> gamesMap = PartitionGames.partitionGames(games);
		int adjustedGoal = 0; 
		int totalGoal = 0; 
		
		List<Game> wonGames = gamesMap.get(PartitionGames.WINNING_GAMES);
		List<Game> lostGames = gamesMap.get(PartitionGames.LOSING_GAMES);
		List<Game> drawnGames = gamesMap.get(PartitionGames.DRAW_GAMES);
		
		int totalGoals = 0; 
		for (Game game: wonGames) { 
			int expectedGoals = game.getNumPlayerGoals() - game.getNumOpponentGoals() + 1;
			totalGoal += expectedGoals;
			if (game.getID() == teamID) { 
				adjustedGoal = game.getNumPlayerGoals() - expectedGoals;
			}	
		}
		
		
		Collections.sort(lostGames, new Comparator<Game>() {
	          @Override
	          public int compare(Game g1, Game g2) {
	               int g1Margin = g1.getNumOpponentGoals() - g1.getNumPlayerGoals();
	               int g2Margin = g2.getNumOpponentGoals() - g2.getNumPlayerGoals();
	
	               return g1Margin - g2Margin;
	          }
	    });
		
		for (Game game:drawnGames) { 
			if (game.getID() == teamID) {
				adjustedGoal += game.getNumPlayerGoals() + totalGoal/(drawnGames.size() + lostGames.size()); 
			}
		}
		
		
		for (Game game: lostGames) { 
			if (totalGoals >= 0) {
				int goalsNeeded = game.getNumOpponentGoals() - game.getNumPlayerGoals() + 1; 
				totalGoals -= goalsNeeded;
				if (game.getID() == teamID) { 
					adjustedGoal = game.getNumPlayerGoals() + goalsNeeded; 
				}
			}
		}
		return adjustedGoal;
	}
}
