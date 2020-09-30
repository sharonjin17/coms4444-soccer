package sim;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameHistory implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private Map<Integer, Map<Integer, Double>> allRoundRankingsMap = new HashMap<>();
	private Map<Integer, Map<Integer, Double>> allAverageRankingsMap = new HashMap<>();
	private Map<Integer, Map<Integer, List<Game>>> allGamesMap = new HashMap<>();
	private Map<Integer, Map<Integer, PlayerPoints>> allRoundPointsMap = new HashMap<>();
	private Map<Integer, Map<Integer, PlayerPoints>> allCumulativePointsMap = new HashMap<>();
	
		
	public Map<Integer, Map<Integer, Double>> getAllRoundRankingsMap() {
		return allRoundRankingsMap;
	}
	
	public void addRoundRankings(Integer round, Map<Integer, Double> roundRankingsMap) {
		allRoundRankingsMap.put(round, roundRankingsMap);
	}
	
	public Map<Integer, Map<Integer, Double>> getAllAverageRankingsMap() {
		return allAverageRankingsMap;
	}

	public void addRoundAverageRankings(Integer round, Map<Integer, Double> roundAverageRankingsMap) {
		allAverageRankingsMap.put(round, roundAverageRankingsMap);
	}
	
	public Map<Integer, Map<Integer, List<Game>>> getAllGamesMap() {
		return allGamesMap;
	}
	
	public void addRoundGames(Integer round, Map<Integer, List<Game>> roundGamesMap) {
		allGamesMap.put(round, roundGamesMap);
	}

	public Map<Integer, Map<Integer, PlayerPoints>> getAllRoundPointsMap() {
		return allRoundPointsMap;
	}
	
	public void addRoundPoints(Integer round, Map<Integer, PlayerPoints> roundPointsMap) {
		allRoundPointsMap.put(round, roundPointsMap);
	}

	public Map<Integer, Map<Integer, PlayerPoints>> getAllCumulativePointsMap() {
		return allCumulativePointsMap;
	}
	
	public void addRoundCumulativePoints(Integer round, Map<Integer, PlayerPoints> roundCumulativePointsMap) {
		allCumulativePointsMap.put(round, roundCumulativePointsMap);
	}
}