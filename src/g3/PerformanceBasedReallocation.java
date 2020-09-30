package g3;
import sim.Game;
import sim.PlayerPoints;
import sim.GameHistory;
import java.util.*;

public class PerformanceBasedReallocation {
	
	public static int WORSE_RATED_TEAMS = 0; 
	public static int BETTER_RATED_TEAMS = 1; 
	
	public static Map<Integer, Set<Integer>>  getPlayersInWindow(Integer teamID, GameHistory history, List<Game> losses, List<Game> draws, List<Game> wins, int window, int round) { 
		Map<Integer, Map<Integer, Double>> getGameRankings = history.getAllAverageRankingsMap(); 
		Map<Integer, Double> roundMap = getGameRankings.get(round - 1);
		List<Map.Entry<Integer, Double>> list = new ArrayList<>(roundMap.entrySet());
		
		list.sort(Map.Entry.comparingByValue());
		int requiredIndex = 0; 
		for (int i=0; i < list.size(); i++) {
			if (list.get(i).getKey() == teamID) {
				requiredIndex = i; 
			}
		}
		
		Map<Integer, Set<Integer>> solution = new HashMap<Integer, Set<Integer>>();
				
		Set<Integer> worseRatedTeams = new HashSet<Integer>();  
		for (int i=requiredIndex - 1; i >= Math.max(0, requiredIndex - window); i--) {
			worseRatedTeams.add(list.get(i).getKey());
		}
		
		Set<Integer> betterRatedTeams = new HashSet<Integer>();  
		for (int i=requiredIndex + 1; i < Math.min(list.size() - 1, requiredIndex + window); i--) {
			betterRatedTeams.add(list.get(i).getKey());
		}
		
		solution.put(WORSE_RATED_TEAMS, worseRatedTeams);
		solution.put(BETTER_RATED_TEAMS, betterRatedTeams); 
		return solution;
	}
	
}
