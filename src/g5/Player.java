package g5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;

public class Player extends sim.Player {
	
	private HashMap<Integer, Map<Integer, ArrayList<Integer>>> expectationTable;
	private boolean tableInitialized;
	private Map<Integer, List<Game>> preOpponentGamesMap;
	private int currRound = 1;

	public Player(Integer teamID, Integer rounds, Integer seed, SimPrinter simPrinter) {
		super(teamID, rounds, seed, simPrinter);
		expectationTable = new HashMap<Integer, Map<Integer, ArrayList<Integer>>>();
		tableInitialized = false;
	}

	@Override
	public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames,
			Map<Integer, List<Game>> opponentGamesMap) {

		//System.out.println("Round: " + round);

		Map<Integer, Double> rankings = gameHistory.getAllAverageRankingsMap().get(currRound);
		if (!tableInitialized) {
			//System.out.println("Initializing expectation table...");
			initializeExpectationTable(opponentGamesMap); //Initialize expectationTable
			preOpponentGamesMap = opponentGamesMap;
			tableInitialized = true;
			
		}
		else {
			//System.out.println("Updating expectation table...");
			updateExpectationTable(opponentGamesMap, rankings); //Update expectationTable
			preOpponentGamesMap = opponentGamesMap;
		}
		
		
		//An example of how to use the expectation map
		//Note: extract goals BEFORE update opponent goals with expectation table, since new match scores will bring different wins and loses
		int excessGoals = 0;

		//keep a copy of PlayerGames before reallocation to call checkConstraintsSatisfied()
		//List<Game> prevPlayerGames = new ArrayList<Game>();
		//for (Game g: playerGames) {
        //    prevPlayerGames.add(g.cloneGame());
        //}
        //System.out.println("Cloning finished");

		List<Game> clonePlayerGames = new ArrayList<Game>();
		for (Game g: playerGames) {
            clonePlayerGames.add(g.cloneGame());
        }
		

        //System.out.println("start Updating excessgoals");
        for (Game game : clonePlayerGames) {
            int playerGoals = game.getNumPlayerGoals();
            int margin = playerGoals - game.getNumOpponentGoals();
            // get win and draw games and retrieve the excess goals
            if (margin >= 0) {
                int subtract = game.getHalfNumPlayerGoals();
                excessGoals += subtract;
                game.setNumPlayerGoals(playerGoals-subtract);
            }
        }
        //System.out.println("Excess goals: " + excessGoals);
        
		
        //Note: make a fresh new clone from the playerGames, don't use the one that extract all the scores
		
		if (currRound >= 10) {
			List<Game> newClonePlayerGames = new ArrayList<Game>();
			for (Game g: playerGames) {
	            newClonePlayerGames.add(g.cloneGame());
	        }
			double currRank = rankings.get(this.teamID);
			for (int i=0; i<newClonePlayerGames.size(); i++) {
				Game g = newClonePlayerGames.get(i);
				Game game = clonePlayerGames.get(i);
				assert(g.getID() == game.getID());
				double opponentRank = rankings.get(g.getID());
				Map<Integer, ArrayList<Integer>> opponentExpectationMap = expectationTable.get(g.getID());
				//Note that here is binary ranking * 100 + opponent goals * 10 + player goals, not player goals * 10 + opponent goals
				Integer index = (opponentRank < currRank ? 0:1) * 100 + g.getNumOpponentGoals() * 10 + g.getNumPlayerGoals();
				Integer mode = getModeFromList(opponentExpectationMap.get(index));
				if (mode != -1) {
					game.setNumOpponentGoals(mode);
				}
			}
		}
		
		
		//Sort clonePlayerGames by margin between player and opponent goals
        clonePlayerGames.sort(Comparator.comparing(g -> (g.getNumOpponentGoals() - g.getNumPlayerGoals())));


        //Allocate goals
		for (Game g: clonePlayerGames) {
		    int opponentGoals = g.getNumOpponentGoals();
		    int playerGoals = g.getNumPlayerGoals();
		    if (opponentGoals - playerGoals >= 0) {
		        int margin = opponentGoals - playerGoals + 1;
		        if (excessGoals < margin || (playerGoals + margin) > 8)
		            break;
		        g.setNumPlayerGoals(playerGoals + margin);
		        excessGoals -= margin;
		    }
		}

        //Distribute excess goals
		for (Game g: clonePlayerGames) {
		    int playerGoals = g.getNumPlayerGoals();
		    if (excessGoals > 0 && playerGoals < 8) {
		        g.setNumPlayerGoals(playerGoals + 1);
		        excessGoals--;
		    }
		}

        //Set playerGames with correct player goals
		for (Game g: clonePlayerGames) {
		    for (Game g2: playerGames) {
		        if (g.getID() == g2.getID()) {
		            g2.setNumPlayerGoals(g.getNumPlayerGoals());
		        }
		    }
		}
		
		//if(!checkConstraintsSatisfied(prevPlayerGames, playerGames)){
		//	System.out.println("Constraints NOT satisfied");
		//}
		
		return playerGames;
	}
	
	/**
	 * Initialize expectationTable. For each opponent, generate a new map of expectation table. 
	 * In each expectation table:
	 * For each possible goal (use 'binary ranking * 100 + current player goal * 10 + opponent player goal' as key)
	 * initialize a ArrayList as value.
	 * for binary ranking, 0 (teamRank < opponentRank) means higher rank, 1 means lower than or equal to 
	 * @param opponentGamesMap
	 */
	private void initializeExpectationTable(Map<Integer, List<Game>> opponentGamesMap) {
		for (Integer teamID : opponentGamesMap.keySet()) {
			HashMap<Integer, ArrayList<Integer>> value = new HashMap<Integer, ArrayList<Integer>>();
			for (int i=0; i<=Game.getMaxGoalThreshold(); i++) {
				for (int j=0; j<=Game.getMaxGoalThreshold(); j++) {
					for (int r=0; r<2; r++) {
						int index = r*100+i*10+j;
						ArrayList<Integer> list = new ArrayList<Integer>();
						value.put(index, list);
					}
				}
			}
			expectationTable.put(teamID, value);
		}
		//System.out.println("Expectation table initialized");
	}
	
	/**
	 * Update expectationTable. Compare opponentGamesMap with preOpponentGamesMap, 
	 * and add player goals of player's current match as a value into that player's expectation table's corresponding list,
	 * using previous match score and binary ranking (binary ranking * 100 + player goal * 10 + opponent player goal) as the key
	 * for binary ranking, 0 (teamRank < opponentRank) means higher rank, 1 means lower than or equal to 
	 * @param opponentGamesMap
	 * @param rankings
	 */
	private void updateExpectationTable(Map<Integer, List<Game>> opponentGamesMap, Map<Integer, Double> rankings) {
		
		for (Integer teamID : opponentGamesMap.keySet()) {
			//System.out.println("Team: " + teamID);
			List<Game> currOpponentGamesList = opponentGamesMap.get(teamID);
			List<Game> preOpponentGamesList = preOpponentGamesMap.get(teamID);
			//System.out.println("ranking: ");
			double teamRank = rankings.get(teamID);
			for (int i=0; i<currOpponentGamesList.size(); i++) {
				Game currGame = currOpponentGamesList.get(i);
				Game preGame = preOpponentGamesList.get(i);
				assert(currGame.getID() == preGame.getID());
				double opponentRank = rankings.get(currGame.getID());
				int key = (teamRank < opponentRank ? 0:1) * 100 + preGame.getNumPlayerGoals()*10+preGame.getNumOpponentGoals();
				//first get that team's table, then use the match score as key to get the list, and add current match's player goal into the list
				expectationTable.get(teamID).get(key).add(currGame.getNumPlayerGoals()); 
			}
		}
		//System.out.println("Expectation table updated!");
	}
	
	/**
	 * Get mode from list
	 * @param list
	 * @return		mode, -1 if empty list
	 */
	private int getModeFromList(ArrayList<Integer> list) {
		Map<Integer, Integer> map = new HashMap();
		for (int i : list) {
			Integer val = map.get(i);
			map.put(i, val == null ? 1 : val + 1);
		}
		int max = -1;
		for (int i : map.keySet()) {
			if (max == -1 || map.get(i)>map.get(max)) {
				max = i;
			}
		}
		return max;
	}
	
	/**
	 * Helper function to check if the table works correctly, can ignore if you don't need it
	 */
	public void printTableTotalLength() {
		int length = 0;
		for (Integer teamID : expectationTable.keySet()) {
			for (ArrayList<Integer> l : expectationTable.get(teamID).values()) {
				length += l.size();
			}
		}
		System.out.println("Total table length is: "+length);
	}
	
	/**
	 * Helper function to check if the table works correctly, can ignore if you don't need it
	 */
	public void printGameList(List<Game> games) {
        System.out.println("\nPrintingGames: ");
        for (Game g: games) {
            System.out.println("GameID: "+g.getID());
            System.out.println("Score: "+g.getScoreAsString());
        }
    }
}
