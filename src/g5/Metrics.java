package g5; // modify the package name to reflect your team

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;

public class Metrics{
	GameHistory fullHistory; //all matches from past rounds

	public Metrics(GameHistory history) {
		fullHistory = history;
	}

	/**
	 * Calculate the highest possible finishing position for a given team,
	 * i.e. the average rank over a total of k rounds
	 * @param teamID	team ID
	 * @param round 	current round
	 * @param k			total number of rounds  
	**/
	public double getHighestFinalRank(Integer teamID, Integer round, Integer k) {
		Map<Integer, Map<Integer, Double>> allAvgRankings = fullHistory.getAllAverageRankingsMap();
		Map<Integer, Double> currentAvgRankings= allAvgRankings.get(round - 1);
		Double currentTeamAvgRank = currentAvgRankings.get(teamID);
		Double highestTeamFinalRank = (currentTeamAvgRank * (round - 1) + (k - round + 1) * 1) / k;
		return highestTeamFinalRank;
	}

	/**
	 * Calculate the lowest possible finishing position for a given team,
	 * i.e. the average rank over a total of k rounds
	 * @param teamID	team ID
	 * @param round 	current round
	 * @param k			total number of rounds  
	**/
	public double getLowestFinalRank(Integer teamID, Integer round, Integer k) {
		Map<Integer, Map<Integer, Double>> allAvgRankings = fullHistory.getAllAverageRankingsMap();
		Map<Integer, Double> currentAvgRankings= allAvgRankings.get(round - 1);
		Double currentTeamAvgRank = currentAvgRankings.get(teamID);
		Integer numTeams = currentAvgRankings.size();
		Double lowestTeamFinalRank = (currentTeamAvgRank * (round - 1) + (k - round + 1) * numTeams) / k;
		return lowestTeamFinalRank;
	}
}