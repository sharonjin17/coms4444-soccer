package g3;

import java.util.*;

import sim.Game;

public class PartitionGames {
	
	public static int LOSING_GAMES = 0;
	public static int WINNING_GAMES = 1;
	public static int DRAW_GAMES = 2; 
	
	public static Map<Integer, List<Game>> partitionGames(List<Game> games) {
		Map<Integer, List<Game>> result = new HashMap<Integer, List<Game>>();
		List<Game> wonGames = new LinkedList<Game>(); 
		List<Game> drawnGames = new LinkedList<Game>(); 
		List<Game> lostGames = new LinkedList<Game>(); 
		
		for (Game game: games) {
			
			if (game.getNumPlayerGoals() > game.getNumOpponentGoals()) {
				wonGames.add(game);
			}
			else if (game.getNumPlayerGoals() == game.getNumOpponentGoals()) {
				drawnGames.add(game);
			}
			else {
				lostGames.add(game);
			}		
		}
		result.put(LOSING_GAMES, lostGames);
		result.put(WINNING_GAMES, wonGames);
		result.put(DRAW_GAMES, drawnGames);
		return result;
	}
}
