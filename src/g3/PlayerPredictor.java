package g3;
import java.util.*;
import sim.Game;
import sim.SimPrinter;


public class PlayerPredictor {
	private Map<Integer, Map<Integer, Double>> userBehaviors; 
	public static int HIGH_MARGIN_WINS = 0; 
	public static int LOW_MARGIN_WINS = 1; 
	public static int LOW_MARGIN_LOSSES = 2;
	public static int HIGH_MARGIN_LOSSES = 3; 
	private int loss_margin_boundary; 
	private int win_margin_boundary;
	private Map<Integer,List<Game>> previousGamesMap; 
	private SimPrinter simPrinter; 
	
	public PlayerPredictor(int loss_margin_boundary, int win_margin_boundary, SimPrinter simPrinter) { 
		this.userBehaviors = new HashMap<Integer, Map<Integer, Double>>();
		for (int i = 1; i<=10; i++) { 
			HashMap<Integer, Double> teamHashMap = new HashMap<Integer, Double>(); 
			this.userBehaviors.put(i, teamHashMap);
			this.loss_margin_boundary = loss_margin_boundary;
			this.win_margin_boundary = win_margin_boundary; 
		}
		this.simPrinter = simPrinter;
	}
	
	private double getAverage(List<Integer> numberList) {
		if (numberList.size() != 0) { 
			double sum = 0; 
			for (int i = 0; i < numberList.size(); i++) { 
				sum += numberList.get(i);
			}
			return sum / (double) numberList.size(); 
		}
		return 0;
	}
	
	public void updateHistory(Map<Integer,List<Game>> opponentGamesMap, int round) {
		if (round != 1) { 
			for (Integer key: opponentGamesMap.keySet()) {
				List<Game> gameList = opponentGamesMap.get(key);
				List<Game> previousGameList = previousGamesMap.get(key);
				Map<Integer, List<Game>> partitions = PartitionGames.partitionGames(previousGameList);
				List<Game> winningGames = partitions.get(PartitionGames.WINNING_GAMES);
				List<Game> losingGames = partitions.get(PartitionGames.LOSING_GAMES);
				
				List<Integer> HIGH_MARGIN_WIN_ALLOCATIONS = new LinkedList<Integer>(); 
				List<Integer> LOW_MARGIN_WIN_ALLOCATIONS = new LinkedList<Integer>(); 
				List<Integer> LOW_MARGIN_LOSS_ALLOCATIONS = new LinkedList<Integer>(); 
				List<Integer> HIGH_MARGIN_LOSS_ALLOCATIONS = new LinkedList<Integer>();
				
				for (Game game: winningGames) {
					for (Game nextGame: gameList) {
						if (nextGame.getID().equals(game.getID())) {
							int goals_removed = game.getNumPlayerGoals() - nextGame.getNumPlayerGoals(); 
							if (game.getNumPlayerGoals() - game.getNumOpponentGoals() >= win_margin_boundary) {
								HIGH_MARGIN_WIN_ALLOCATIONS.add(goals_removed);
							}
							else {
								if (goals_removed > 0) {
									LOW_MARGIN_WIN_ALLOCATIONS.add(goals_removed);
								}
							}
						}
					}
				}
				
				for (Game game: losingGames) {
					for (Game nextGame: gameList) {
						if (nextGame.getID().equals(game.getID())) {
							int goals_added = nextGame.getNumPlayerGoals() - game.getNumPlayerGoals();
							if (game.getNumOpponentGoals() - game.getNumPlayerGoals() > loss_margin_boundary) {
								HIGH_MARGIN_LOSS_ALLOCATIONS.add(goals_added);
							}
							else {
								LOW_MARGIN_LOSS_ALLOCATIONS.add(goals_added);
							}
						}
					}
						
				}
				
				userBehaviors.get(key).put(HIGH_MARGIN_WINS, getAverage(HIGH_MARGIN_WIN_ALLOCATIONS));
				userBehaviors.get(key).put(LOW_MARGIN_WINS, getAverage(LOW_MARGIN_WIN_ALLOCATIONS));
				userBehaviors.get(key).put(LOW_MARGIN_LOSSES, getAverage(LOW_MARGIN_LOSS_ALLOCATIONS));
				userBehaviors.get(key).put(HIGH_MARGIN_LOSSES, getAverage(HIGH_MARGIN_LOSS_ALLOCATIONS));
			}
		}
		else {
			previousGamesMap = opponentGamesMap;
		}
	}
	
	public Map<Integer, Map<Integer, Double>> getPredictions() { 
		return userBehaviors;
	}
}
